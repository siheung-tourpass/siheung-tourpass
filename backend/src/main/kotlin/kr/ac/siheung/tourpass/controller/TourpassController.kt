package kr.ac.siheung.tourpass.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/** Keep the existing form/JSON contracts while Spring MVC owns request dispatch. */
@Controller
class TourpassController {
    private val api = ApiHandler()
    private val web = WebHandler()

    @RequestMapping("/api/v1", "/api/v1/**")
    fun api(request: HttpServletRequest, response: HttpServletResponse) {
        api.handle(request, response)
    }

    @RequestMapping("/", "/login", "/logout", "/merchants", "/merchants/**", "/products/**",
        "/places/**", "/recommendations", "/journeys", "/journeys/**", "/passes", "/passes/**",
        "/feedback/**", "/merchant/**", "/admin", "/admin/**")
    fun web(request: HttpServletRequest, response: HttpServletResponse) {
        web.handle(request, response)
    }
}
