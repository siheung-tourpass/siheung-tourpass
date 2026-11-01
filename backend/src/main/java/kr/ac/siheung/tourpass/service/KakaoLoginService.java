package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

public final class KakaoLoginService {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ObjectMapper JSON=new ObjectMapper();
    private final String key,secret,redirect;
    private final URI tokenEndpoint,userEndpoint;

    public KakaoLoginService(){
        this(System.getenv("TOURPASS_KAKAO_CLIENT_ID"),System.getenv("TOURPASS_KAKAO_CLIENT_SECRET"),System.getenv("TOURPASS_KAKAO_REDIRECT_URI"),
            URI.create("https://kauth.kakao.com/oauth/token"),URI.create("https://kapi.kakao.com/v2/user/me"));
    }
    KakaoLoginService(String key,String secret,String redirect,URI tokenEndpoint,URI userEndpoint){
        this.key=key;this.secret=secret;this.redirect=redirect;this.tokenEndpoint=tokenEndpoint;this.userEndpoint=userEndpoint;
    }
    public boolean enabled(){return key!=null&&!key.isBlank()&&redirect!=null&&!redirect.isBlank();}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public String authorizationUrl(String state){
        require(enabled(),503,"KAKAO_UNAVAILABLE");
        return "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id="+encode(key)+"&redirect_uri="+encode(redirect)+"&state="+encode(state);
    }
    public record Attempt(String state,Instant expiresAt){
        public static Attempt create(){return new Attempt(AuthService.token(),Instant.now().plusSeconds(300));}
        public void validate(String supplied){
            require(supplied!=null&&Instant.now().isBefore(expiresAt)&&java.security.MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)),401,"KAKAO_STATE_INVALID");
        }
    }
    public long identify(String code,String state){
        require(enabled(),503,"KAKAO_UNAVAILABLE");
        require(code!=null&&!code.isBlank()&&code.length()<=2048,401,"KAKAO_FAILED");
        String body="grant_type=authorization_code&client_id="+encode(key)+"&redirect_uri="+encode(redirect)+"&code="+encode(code)+"&state="+encode(state);
        if(secret!=null&&!secret.isBlank())body+="&client_secret="+encode(secret);
        var token=read(HttpRequest.newBuilder(tokenEndpoint).header("Content-Type","application/x-www-form-urlencoded;charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body)));
        String accessToken=token.path("access_token").asText("");
        require(!accessToken.isBlank(),502,"KAKAO_FAILED");
        var user=read(HttpRequest.newBuilder(userEndpoint).header("Authorization","Bearer "+accessToken).GET());
        var id=user.path("id");require(id.isIntegralNumber()&&id.canConvertToLong()&&id.longValue()>0,502,"KAKAO_FAILED");
        return id.longValue();
    }
    private static JsonNode read(HttpRequest.Builder request){
        try{
            var response=HTTP.send(request.timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());
            require(response.statusCode()==200,502,"KAKAO_FAILED");
            var json=JSON.readTree(response.body());require(json!=null&&json.isObject(),502,"KAKAO_FAILED");return json;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new Problem(502,"KAKAO_FAILED");}
        catch(java.io.IOException|IllegalArgumentException e){throw new Problem(502,"KAKAO_FAILED");}
    }
}
