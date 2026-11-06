import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import kr.ac.siheung.tourpass.controller.ApiHandler;
import kr.ac.siheung.tourpass.service.Problem;

public final class HttpContractCheck {
    record Result(Map<String,Object> metadata,byte[] body){}
    static Result error(String method,int status) throws Exception {
        var metadata=new HashMap<String,Object>();
        var bytes=new ByteArrayOutputStream();
        var stream=new ServletOutputStream(){
            public void write(int value){bytes.write(value);}
            public boolean isReady(){return true;}
            public void setWriteListener(WriteListener listener){}
        };
        var request=(HttpServletRequest)Proxy.newProxyInstance(HttpContractCheck.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->{
            if(m.getName().equals("getMethod"))return method;
            throw new AssertionError(m.getName());
        });
        var response=(HttpServletResponse)Proxy.newProxyInstance(HttpContractCheck.class.getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->{
            switch(m.getName()){
                case "setStatus", "setContentType", "setContentLength" -> metadata.put(m.getName(),a[0]);
                case "setHeader" -> metadata.put((String)a[0],a[1]);
                case "getOutputStream" -> {return stream;}
                default -> throw new AssertionError(m.getName());
            }
            return null;
        });
        ApiHandler.error(request,response,new Problem(status,"TEST_ERROR"));
        return new Result(metadata,bytes.toByteArray());
    }
    public static void main(String[] args) throws Exception {
        for(int status:new int[]{400,401,403,404,405,500}){
            var get=error("GET",status);var head=error("HEAD",status);
            assert get.body().length>0;
            assert head.body().length==0;
            assert get.metadata().equals(head.metadata());
            assert get.metadata().get("setContentLength").equals(get.body().length);
            assert head.metadata().get("setStatus").equals(status);
            if(status==401)assert head.metadata().containsKey("WWW-Authenticate");
        }
        System.out.println("PASS: HEAD error responses preserve GET metadata without sending a body");
    }
}
