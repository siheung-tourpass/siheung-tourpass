package kr.ac.siheung.tourpass.filter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kr.ac.siheung.tourpass.controller.ApiHandler;
import kr.ac.siheung.tourpass.controller.WebHandler;
import kr.ac.siheung.tourpass.service.*;
public final class SecurityFilter implements Filter {
    public static boolean isApiPath(String path){return path.equals("/api/v1")||path.startsWith("/api/v1/");}
    public static String requiredRole(String path){
        if(isApiPath(path)){
            path=path.substring("/api/v1".length());
            return path.startsWith("/me/")?"VISITOR":path.startsWith("/merchant/")?"STAFF":path.startsWith("/admin/")?"ADMIN":null;
        }
        if(path.equals("/journeys")||path.startsWith("/journeys/")||path.equals("/passes")||path.startsWith("/passes/")||path.startsWith("/feedback/"))return "VISITOR";
        if(path.startsWith("/merchant/"))return "STAFF";
        if(path.equals("/admin")||path.startsWith("/admin/"))return "ADMIN";
        return null;
    }
    public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain) throws IOException,ServletException {
        var r=(HttpServletRequest)req;var out=(HttpServletResponse)res;r.setCharacterEncoding("UTF-8");out.setCharacterEncoding("UTF-8");out.setHeader("X-Content-Type-Options","nosniff");out.setHeader("Cache-Control","no-store");out.setHeader("Content-Security-Policy","default-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        try{
            if(r.getServletPath().startsWith("/assets/")){chain.doFilter(req,res);return;}
            if(isApiPath(r.getServletPath())){chain.doFilter(req,res);return;}
            var session=r.getSession();if(session.getAttribute("csrf")==null)session.setAttribute("csrf",AuthService.token());
            chain.doFilter(req,res);
        }catch(Problem e){if(isApiPath(r.getServletPath()))ApiHandler.error(out,e);else WebHandler.error(r,out,e);}
    }
    public static Map<String,Object> authorize(java.sql.Connection c,HttpServletRequest r) throws java.sql.SQLException {
        if(isApiPath(r.getServletPath()))return authorizeApi(c,r);
        var session=r.getSession();
        String id=(String)session.getAttribute("userId");String role=requiredRole(r.getServletPath());
        var user=new AuthService().current(c,id,role);r.setAttribute("user",user);
        if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())){
            String supplied=r.getParameter("csrf");String expected=(String)session.getAttribute("csrf");
            Problem.require(supplied!=null&&MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)),403,"CSRF_INVALID");
        }
        return user;
    }

    private static Map<String,Object> authorizeApi(java.sql.Connection c,HttpServletRequest r) throws java.sql.SQLException {
        String authorization=r.getHeader("Authorization");Map<String,Object> user=null;
        if(authorization!=null){
            Problem.require(authorization.length()<=4096&&authorization.regionMatches(true,0,"Basic ",0,6),401,"INVALID_CREDENTIALS");
            String credentials;
            try{credentials=new String(Base64.getDecoder().decode(authorization.substring(6)),StandardCharsets.UTF_8);}
            catch(IllegalArgumentException e){throw new Problem(401,"INVALID_CREDENTIALS");}
            int colon=credentials.indexOf(':');Problem.require(colon>0,401,"INVALID_CREDENTIALS");
            try{user=new AuthService().login(c,Map.of("loginId",credentials.substring(0,colon),"password",credentials.substring(colon+1)));}
            catch(Problem e){throw new Problem(401,"INVALID_CREDENTIALS");}
            catch(Exception e){throw new Problem(500,"INTERNAL_ERROR");}
        }
        String role=requiredRole(r.getServletPath());
        user=new AuthService().current(c,user==null?null:(String)user.get("id"),role);
        // Browsers may cache Basic credentials; cross-origin forms cannot set this header.
        if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod()))
            Problem.require("api".equals(r.getHeader("X-CSRF-Token")),403,"CSRF_INVALID");
        r.setAttribute("user",user);return user;
    }

}
