package kr.ac.siheung.tourpass.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class TourpassController {
    private val web = WebHandler()

    @RequestMapping("/login")
    fun login(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::login)

    @RequestMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::logout)

    @RequestMapping("/")
    fun home(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::home)

    @RequestMapping("/profile")
    fun profile(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::profile)

    @RequestMapping("/merchants")
    fun catalog(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::catalog)

    @RequestMapping("/products/detail")
    fun product(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::product)

    @RequestMapping("/merchants/detail")
    fun merchant(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::merchant)

    @RequestMapping("/places/detail")
    fun place(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::place)

    @RequestMapping("/recommendations")
    fun recommendations(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::recommendations)

    @RequestMapping("/journeys")
    fun journeys(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::journeys)

    @RequestMapping("/explore", "/journeys/new", "/journeys/edit")
    fun journeyBuilder(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::journeyBuilder)

    @RequestMapping("/journeys/photocards")
    fun journeyPhotocards(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::journeyPhotocards)

    @RequestMapping("/journeys/detail")
    fun journey(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::journey)

    @RequestMapping("/journeys/create", "/journeys/update")
    fun saveDraft(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::saveDraft)

    @RequestMapping("/journeys/delete")
    fun deleteJourney(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::deleteJourney)

    @RequestMapping("/journeys/save")
    fun saveJourney(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::saveJourney)

    @RequestMapping("/journeys/add")
    fun addPlace(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::addPlace)

    @RequestMapping("/journeys/record", "/journeys/move", "/journeys/remove")
    fun journeyItem(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::journeyItem)

    @RequestMapping("/passes")
    fun passes(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::passes)

    @RequestMapping("/passes/detail")
    fun passDetail(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::passDetail)

    @RequestMapping("/passes/issue")
    fun issuePass(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::issuePass)

    @RequestMapping("/feedback/places/save", "/feedback/places/delete")
    fun savePlaceFeedback(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::savePlaceFeedback)

    @RequestMapping("/admin/place-feedback")
    fun placeFeedbackStatistics(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::placeFeedbackStatistics)

    @RequestMapping("/feedback/history")
    fun history(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::history)

    @RequestMapping("/feedback/edit")
    fun editFeedback(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::editFeedback)

    @RequestMapping("/feedback/create", "/feedback/update", "/feedback/delete")
    fun saveFeedback(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::saveFeedback)

    @RequestMapping("/merchant/redeem")
    fun redeem(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::redeem)

    @RequestMapping("/merchant/redeem/lookup")
    fun lookup(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::lookup)

    @RequestMapping("/merchant/redeem/confirm")
    fun confirm(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::confirm)

    @RequestMapping("/merchant/history")
    fun merchantHistory(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::merchantHistory)

    @RequestMapping("/admin")
    fun admin(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::admin)

    @RequestMapping("/admin/goods")
    fun goods(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::goods)

    @RequestMapping("/cards/detail")
    fun publicCard(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::publicCard)

    @RequestMapping("/admin/passes")
    fun adminPasses(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::adminPasses)

    @RequestMapping("/admin/passes/detail")
    fun adminPass(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::adminPass)

    @RequestMapping("/admin/passes/cancel")
    fun cancelPass(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::cancelPass)

    @RequestMapping("/admin/catalog", "/admin/catalog/save", "/admin/catalog/deactivate", "/admin/ontology", "/admin/ontology/save", "/admin/ontology/deactivate", "/admin/ontology/review")
    fun editor(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::editor)

    @RequestMapping("/admin/relations", "/admin/relations/save", "/admin/relations/review", "/admin/relations/deactivate")
    fun relations(request: HttpServletRequest, response: HttpServletResponse) =
        web.handle(request, response, web::relations)

    @RequestMapping("/assets", "/{path:^(?!assets$).*}/**")
    fun missing(request: HttpServletRequest, response: HttpServletResponse) =
        web.missing(request, response)
}
