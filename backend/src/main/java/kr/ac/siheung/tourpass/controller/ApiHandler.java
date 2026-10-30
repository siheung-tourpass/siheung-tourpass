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
    public record Request(Connection connection,String method,String path,Map<String,Object> body,Map<String,List<String>> query,Map<String,Object> user){
        public String userId(){return user==null?null:(String)user.get("id");}
        public String role(){return user==null?null:(String)user.get("role");}
    }
    @FunctionalInterface public interface Action { Reply run(Request request) throws Exception; }
    public void handle(HttpServletRequest r,HttpServletResponse out,String methods,Action action) throws IOException {
        try {
            String path=r.getServletPath().substring("/api/v1".length()).replaceAll("/+$","");String method=r.getMethod();
            var allowed=new LinkedHashSet<>(Arrays.asList(methods.split(",")));allowed.remove("");
            if(allowed.contains("GET"))allowed.add("HEAD");if(!allowed.isEmpty())allowed.add("OPTIONS");
            Reply reply=Database.transaction(!Set.of("GET","HEAD","OPTIONS").contains(method),c->{
                var user=SecurityFilter.authorize(c,r);
                if(allowed.isEmpty())throw new Problem(404,"NOT_FOUND");
                out.setHeader("Allow",String.join(", ",allowed));
                require(allowed.contains(method),405,"METHOD_NOT_ALLOWED");
                if(method.equals("OPTIONS"))return new Reply(204,null,null);
                Map<String,Object> body=body(r);if(Set.of("GET","HEAD").contains(method))Input.fields(body,"");
                Map<String,List<String>> q=new LinkedHashMap<>();r.getParameterMap().forEach((k,v)->q.put(k,Arrays.asList(v)));
                return action.run(new Request(c,method.equals("HEAD")?"GET":method,path,body,q,user));
            });
            out.setStatus(reply.status());if(reply.location()!=null)out.setHeader("Location",r.getContextPath()+"/api/v1"+reply.location());
            if(reply.status()!=204){
                out.setContentType("application/json");
                Object representation=ApiLinks.represent(r.getContextPath()+"/api/v1",path,reply,allowed,r.getAttribute("user"),r.getParameterMap());
                // Serialize HEAD too so Content-Length matches the corresponding GET response.
                byte[] json=JSON.writeValueAsBytes(representation);out.setContentLength(json.length);
                if(!method.equals("HEAD"))out.getOutputStream().write(json);
            }
        }catch(Problem e){error(out,e);}catch(Exception e){error(out,new Problem(500,"INTERNAL_ERROR"));}
    }
    private static Map<String,Object> body(HttpServletRequest r) throws IOException {
        if(r.getContentLengthLong()==0||r.getContentLengthLong()==-1&&r.getHeader("Transfer-Encoding")==null)return new LinkedHashMap<>();
        require(r.getContentType()!=null&&r.getContentType().split(";")[0].strip().equalsIgnoreCase("application/json"),415,"UNSUPPORTED_MEDIA_TYPE");
        byte[] raw=r.getInputStream().readNBytes(65537);require(raw.length<=65536,400,"INVALID_INPUT");
        try{return Input.object(JSON.readValue(raw,Object.class));}catch(com.fasterxml.jackson.core.JacksonException e){throw new Problem(400,"INVALID_INPUT");}
    }
    public static void json(HttpServletResponse out,Object body) throws IOException {out.setContentType("application/json;charset=UTF-8");JSON.writeValue(out.getWriter(),body);}
    public static void error(HttpServletResponse out,Problem e) throws IOException {out.setStatus(e.status);if(e.status==401)out.setHeader("WWW-Authenticate","Basic realm=\"tourpass-api\", charset=\"UTF-8\"");out.setContentType("application/json;charset=UTF-8");String message=switch(e.code){case "BENEFIT_ALREADY_USED"->"이미 사용한 혜택입니다.";case "INVALID_INPUT"->"입력값을 확인해 주세요.";case "INTERNAL_ERROR"->"요청을 처리하지 못했습니다.";default->e.code;};JSON.writeValue(out.getWriter(),Map.of("error",Map.of("code",e.code,"message",message,"fieldErrors",List.of())));}
}
