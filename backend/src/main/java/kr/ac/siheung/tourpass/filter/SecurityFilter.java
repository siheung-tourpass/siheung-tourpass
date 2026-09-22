package kr.ac.siheung.tourpass.filter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kr.ac.siheung.tourpass.controller.ApiHandler;
import kr.ac.siheung.tourpass.controller.WebHandler;
import kr.ac.siheung.tourpass.config.Database;
import kr.ac.siheung.tourpass.service.*;
public final class SecurityFilter implements Filter {
    public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain) throws IOException,ServletException {
        var r=(HttpServletRequest)req;var out=(HttpServletResponse)res;r.setCharacterEncoding("UTF-8");out.setCharacterEncoding("UTF-8");out.setHeader("X-Content-Type-Options","nosniff");out.setHeader("Cache-Control","no-store");out.setHeader("Content-Security-Policy","default-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        try{
            if(r.getServletPath().startsWith("/assets/")){chain.doFilter(req,res);return;}
            var session=r.getSession();if(session.getAttribute("csrf")==null)session.setAttribute("csrf",AuthService.token());
            String id=(String)session.getAttribute("userId");boolean api=(r.getServletPath().equals("/api/v1")||r.getServletPath().startsWith("/api/v1/"));String path=api?r.getServletPath().substring("/api/v1".length()):r.getServletPath();String role=AuthService.requiredRole(r.getServletPath());
            var user=Database.transaction(c->new AuthService().current(c,id,role));r.setAttribute("user",user);
            if("DELETE".equals(r.getMethod())&&"/session".equals(path)&&user==null)throw new Problem(401,"AUTH_REQUIRED");
            if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())){
                String supplied=api?r.getHeader("X-CSRF-Token"):r.getParameter("csrf");String expected=(String)session.getAttribute("csrf");
                Problem.require(supplied!=null&&MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)),403,"CSRF_INVALID");
            }
            chain.doFilter(req,res);
        }catch(Problem e){if((r.getServletPath().equals("/api/v1")||r.getServletPath().startsWith("/api/v1/")))ApiHandler.error(out,e);else WebHandler.error(r,out,e);}
    }
}
