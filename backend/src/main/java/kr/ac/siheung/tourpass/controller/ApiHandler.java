package kr.ac.siheung.tourpass.controller;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.StreamReadFeature;
import kr.ac.siheung.tourpass.config.Database;
import kr.ac.siheung.tourpass.filter.SecurityFilter;
import kr.ac.siheung.tourpass.service.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class ApiHandler {
    private static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Reply(int status,Object body,String location){}
    public static Reply ok(Object body){return new Reply(200,body,null);}
    public static Reply created(Object body,String path){return new Reply(201,body,path);}
    public record Request(Connection connection,String method,String path,Map<String,Object> body,Map<String,List<String>> query,Map<String,Object> user,RedemptionService.Confirmation confirmation){
        public String userId(){return user==null?null:(String)user.get("id");}
        public String role(){return user==null?null:(String)user.get("role");}
        public void method(String expected){require(method.equals(expected),405,"METHOD_NOT_ALLOWED");}
    }
    @FunctionalInterface public interface Action { Reply run(Request request) throws Exception; }
    public void handle(HttpServletRequest r,HttpServletResponse out,Action action) throws IOException {
        try {
            String path=r.getServletPath().substring("/api/v1".length());String method=r.getMethod();
            var session=r.getSession();
            synchronized(session){
                var confirmation=(RedemptionService.Confirmation)session.getAttribute("confirmation");
                Reply reply=Database.transaction(!method.equals("GET"),c->{
                    var user=SecurityFilter.authorize(c,r);
                    require(!path.isEmpty(),404,"NOT_FOUND");
                    require(Set.of("GET","POST","PUT","PATCH","DELETE").contains(method),405,"METHOD_NOT_ALLOWED");
                    Map<String,Object> body=body(r);if(method.equals("GET"))Input.fields(body,"");
                    Map<String,List<String>> q=new LinkedHashMap<>();r.getParameterMap().forEach((k,v)->q.put(k,Arrays.asList(v)));
                    return action.run(new Request(c,method,path.replaceAll("/+$",""),body,q,user,confirmation));
                });
                if(path.equals("/session")&&(method.equals("POST")||method.equals("GET"))){
                    @SuppressWarnings("unchecked") var u=(Map<String,Object>)reply.body();
                    if(method.equals("POST"))SessionState.login(r,u);
                    reply=ok(sessionResponse(u,session));
                }else if(path.equals("/session")&&method.equals("DELETE"))session.invalidate();
                else if(path.equals("/merchant/pass-lookups")&&reply.body() instanceof RedemptionService.Lookup lookup){session.setAttribute("confirmation",lookup.confirmation());reply=ok(lookup.response());}
                else if(path.equals("/merchant/redemptions")&&method.equals("POST"))session.setAttribute("confirmation",confirmation.consume());
                out.setStatus(reply.status());if(reply.location()!=null)out.setHeader("Location",r.getContextPath()+"/api/v1"+reply.location());
                if(reply.status()!=204){out.setContentType("application/json");JSON.writeValue(out.getWriter(),reply.body());}
            }
        }catch(Problem e){error(out,e);}catch(Exception e){error(out,new Problem(500,"INTERNAL_ERROR"));}
    }
    private static Map<String,Object> sessionResponse(Map<String,Object> u,HttpSession s){var m=new LinkedHashMap<String,Object>();m.put("authenticated",u!=null);m.put("user",u);m.put("csrfToken",s.getAttribute("csrf"));return m;}
    private static Map<String,Object> body(HttpServletRequest r) throws IOException {
        if(r.getContentLengthLong()==0||r.getContentLengthLong()==-1&&r.getHeader("Transfer-Encoding")==null)return new LinkedHashMap<>();
        require(r.getContentType()!=null&&r.getContentType().split(";")[0].strip().equalsIgnoreCase("application/json"),415,"UNSUPPORTED_MEDIA_TYPE");
        byte[] raw=r.getInputStream().readNBytes(65537);require(raw.length<=65536,400,"INVALID_INPUT");
        try{return Input.object(JSON.readValue(raw,Object.class));}catch(com.fasterxml.jackson.core.JacksonException e){throw new Problem(400,"INVALID_INPUT");}
    }
    public static void error(HttpServletResponse out,Problem e) throws IOException {out.setStatus(e.status);out.setContentType("application/json;charset=UTF-8");String message=switch(e.code){case "BENEFIT_ALREADY_USED"->"이미 사용한 혜택입니다.";case "INVALID_INPUT"->"입력값을 확인해 주세요.";case "INTERNAL_ERROR"->"요청을 처리하지 못했습니다.";default->e.code;};JSON.writeValue(out.getWriter(),Map.of("error",Map.of("code",e.code,"message",message,"fieldErrors",List.of())));}
}
