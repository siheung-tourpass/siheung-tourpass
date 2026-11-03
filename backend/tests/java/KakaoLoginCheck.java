package kr.ac.siheung.tourpass.service;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class KakaoLoginCheck {
    private static void fails(String code,Runnable work){
        try{work.run();throw new AssertionError("Expected "+code);}catch(Problem e){assert e.code.equals(code):e.code;}
    }
    public static void main(String[] args) throws Exception {
        var attempt=KakaoLoginService.Attempt.create();attempt.validate(attempt.state());
        fails("KAKAO_STATE_INVALID",()->attempt.validate(null));
        fails("KAKAO_STATE_INVALID",()->attempt.validate("other-session"));
        fails("KAKAO_STATE_INVALID",()->new KakaoLoginService.Attempt(attempt.state(),Instant.now().minusSeconds(1)).validate(attempt.state()));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var tokenBody=new AtomicReference<String>();var bearer=new AtomicReference<String>();
        var response=new AtomicReference<>("{\"id\":9223372036854775807}");
        server.createContext("/token",exchange->{
            assert exchange.getRequestMethod().equals("POST");
            tokenBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] data="{\"access_token\":\"test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });
        server.createContext("/me",exchange->{
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });
        server.createContext("/failure",exchange->{exchange.sendResponseHeaders(401,-1);exchange.close();});
        server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var service=new KakaoLoginService("rest-key","s&ecret","https://example.com/login/kakao/callback",URI.create(base+"/token"),URI.create(base+"/me"));
            String url=service.authorizationUrl("state&value");
            assert url.startsWith("https://kauth.kakao.com/oauth/authorize?");assert url.contains("state=state%26value");assert !url.contains("s&ecret");
            assert service.identify("code+value","state&value")==Long.MAX_VALUE;
            assert tokenBody.get().contains("code=code%2Bvalue");assert tokenBody.get().contains("client_secret=s%26ecret");assert tokenBody.get().contains("state=state%26value");
            assert bearer.get().equals("Bearer test-token");
            for(String invalid:new String[]{"{}","{\"id\":0}","{\"id\":\"123\"}","{\"id\":1.2}","{\"id\":9223372036854775808}","not-json"}){
                response.set(invalid);fails("KAKAO_FAILED",()->service.identify("code","state"));
            }
            fails("KAKAO_FAILED",()->service.identify("","state"));
            var failure=new KakaoLoginService("key",null,"https://example.com/callback",URI.create(base+"/failure"),URI.create(base+"/me"));
            fails("KAKAO_FAILED",()->failure.identify("code","state"));
            var disabled=new KakaoLoginService(null,null,null,URI.create(base+"/token"),URI.create(base+"/me"));
            assert !disabled.enabled();fails("KAKAO_UNAVAILABLE",()->disabled.authorizationUrl("state"));
            assert !AuthService.verify("anything","");
        }finally{server.stop(0);}
        if(args.length>0){
            try(var c=java.sql.DriverManager.getConnection(args[0],System.getenv("TOURPASS_DB_USER"),System.getenv("TOURPASS_DB_PASSWORD"))){
                c.setAutoCommit(false);
                try{
                    var auth=new AuthService();
                    var first=auth.kakaoLogin(c,9223372036854775806L);
                    var again=auth.kakaoLogin(c,9223372036854775806L);
                    assert first.get("id").equals(again.get("id"));assert first.get("role").equals("VISITOR");
                    try(var q=c.prepareStatement("UPDATE app_user SET active=false WHERE id=?")){
                        q.setLong(1,Long.parseLong((String)first.get("id")));q.executeUpdate();
                    }
                    fails("FORBIDDEN",()->{try{auth.kakaoLogin(c,9223372036854775806L);}catch(java.sql.SQLException e){throw new RuntimeException(e);}});
                }finally{c.rollback();}
            }
            System.out.println("PASS: Kakao first signup, repeat identity and inactive account rejection (rolled back)");
        }
        System.out.println("PASS: Kakao state expiry, token exchange, identity validation and provider failures");
    }
}
