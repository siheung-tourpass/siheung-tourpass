package kr.ac.siheung.tourpass.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import kr.ac.siheung.tourpass.service.Problem;

@Controller
public class TourpassController {
    private final WebHandler web = new WebHandler();

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void oversizedPhoto(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        WebHandler.error(request, response, new Problem(413, "INVALID_PHOTO"));
    }

    @RequestMapping({"/login"})
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::login);
    }

    @RequestMapping({"/login/kakao", "/login/kakao/callback"})
    public void kakaoLogin(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.kakaoLogin(request, response);
    }

    @RequestMapping({"/logout"})
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::logout);
    }

    @RequestMapping({"/"})
    public void home(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::home);
    }

    @RequestMapping({"/profile"})
    public void profile(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::profile);
    }

    @RequestMapping({"/merchants"})
    public void catalog(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::catalog);
    }

    @RequestMapping({"/products/detail"})
    public void product(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::product);
    }

    @RequestMapping({"/merchants/detail"})
    public void merchant(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::merchant);
    }

    @RequestMapping({"/places/detail"})
    public void place(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::place);
    }

    @RequestMapping({"/recommendations"})
    public void recommendations(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::recommendations);
    }

    @RequestMapping({"/journeys"})
    public void journeys(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journeys);
    }

    @RequestMapping({"/explore", "/journeys/new", "/journeys/edit"})
    public void journeyBuilder(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journeyBuilder);
    }

    @RequestMapping({"/journeys/photo"})
    public void journeyPhoto(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journeyPhoto);
    }

    @RequestMapping({"/journeys/photocards"})
    public void journeyPhotocards(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journeyPhotocards);
    }

    @RequestMapping({"/journeys/detail"})
    public void journey(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journey);
    }

    @RequestMapping({"/journeys/create", "/journeys/update"})
    public void saveDraft(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::saveDraft);
    }

    @RequestMapping({"/journeys/delete"})
    public void deleteJourney(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::deleteJourney);
    }

    @RequestMapping({"/journeys/save"})
    public void saveJourney(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::saveJourney);
    }

    @RequestMapping({"/journeys/add"})
    public void addPlace(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::addPlace);
    }

    @RequestMapping({"/journeys/record", "/journeys/move", "/journeys/remove"})
    public void journeyItem(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::journeyItem);
    }

    @RequestMapping({"/passes"})
    public void passes(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::passes);
    }

    @RequestMapping({"/passes/detail"})
    public void passDetail(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::passDetail);
    }

    @RequestMapping({"/passes/checkout"})
    public void checkout(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::checkout);
    }

    @RequestMapping({"/passes/issue"})
    public void issuePass(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::issuePass);
    }

    @RequestMapping({"/feedback/places/save", "/feedback/places/delete"})
    public void savePlaceFeedback(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::savePlaceFeedback);
    }

    @RequestMapping({"/admin/place-feedback"})
    public void placeFeedbackStatistics(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::placeFeedbackStatistics);
    }

    @RequestMapping({"/feedback/history"})
    public void history(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::history);
    }

    @RequestMapping({"/feedback/edit"})
    public void editFeedback(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::editFeedback);
    }

    @RequestMapping({"/feedback/create", "/feedback/update", "/feedback/delete"})
    public void saveFeedback(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::saveFeedback);
    }

    @RequestMapping({"/merchant/redeem"})
    public void redeem(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::redeem);
    }

    @RequestMapping({"/merchant/redeem/lookup"})
    public void lookup(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::lookup);
    }

    @RequestMapping({"/merchant/redeem/confirm"})
    public void confirm(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::confirm);
    }

    @RequestMapping({"/merchant/history"})
    public void merchantHistory(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::merchantHistory);
    }

    @RequestMapping({"/admin"})
    public void admin(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::admin);
    }

    @RequestMapping({"/admin/goods"})
    public void goods(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::goods);
    }

    @RequestMapping({"/cards/detail"})
    public void publicCard(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::publicCard);
    }

    @RequestMapping({"/admin/passes"})
    public void adminPasses(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::adminPasses);
    }

    @RequestMapping({"/admin/passes/detail"})
    public void adminPass(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::adminPass);
    }

    @RequestMapping({"/admin/passes/cancel"})
    public void cancelPass(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::cancelPass);
    }

    @RequestMapping({"/admin/catalog", "/admin/catalog/save", "/admin/catalog/deactivate", "/admin/ontology", "/admin/ontology/save", "/admin/ontology/deactivate", "/admin/ontology/review"})
    public void editor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::editor);
    }

    @RequestMapping({"/admin/relations", "/admin/relations/save", "/admin/relations/review", "/admin/relations/deactivate"})
    public void relations(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.handle(request, response, web::relations);
    }

    @RequestMapping({"/docs", "/api-docs"})
    public void apiDocs(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/assets/api-docs/index.html");
    }

    @RequestMapping({"/assets", "/{path:^(?!assets$|webjars$).*}/**"})
    public void missing(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        web.missing(request, response);
    }
}
