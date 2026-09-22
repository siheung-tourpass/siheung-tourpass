package kr.ac.siheung.tourpass.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kr.ac.siheung.tourpass.service.AuthService;

/** Called only after the login transaction commits. */
final class SessionState {
    static void login(HttpServletRequest request,Map<String,Object> user){
        request.changeSessionId();var session=request.getSession();
        session.setAttribute("userId",user.get("id"));session.setAttribute("csrf",AuthService.token());session.removeAttribute("confirmation");
    }
}
