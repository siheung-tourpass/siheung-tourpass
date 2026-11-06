package kr.ac.siheung.tourpass.controller;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import kr.ac.siheung.tourpass.controller.ApiHandler.Reply;
import kr.ac.siheung.tourpass.service.*;
import static kr.ac.siheung.tourpass.controller.ApiHandler.*;

// Method checks stay inside the transaction so authorization and CSRF run first.
@Controller
@RequestMapping(value = "/api/v1", method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class ApiController {
    private final ApiHandler api = new ApiHandler();
    private final CatalogService catalog = new CatalogService();
    private final JourneyService journeys = new JourneyService();
    private final PassService passes = new PassService();
    private final RedemptionService redemption = new RedemptionService();
    private final FeedbackService feedback = new FeedbackService();
    private final OntologyService ontology = new OntologyService();
    private final AdminService admin = new AdminService();

    @RequestMapping({"", "/"})
    public void index(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(Collections.singletonMap("user", x.user())));
    }

    @RequestMapping({"/{collection:products|merchants}", "/{collection:products|merchants}/", "/{collection:products|merchants}/{id}", "/{collection:products|merchants}/{id}/"})
    public void catalog(@PathVariable String collection, @PathVariable(required = false) String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> {
            if (id == null) return ok(CatalogService.page(catalog.list(x.connection(), collection, Input.param(x.query().get("regionId"))), x.query()));
            return ok(catalog.detail(x.connection(), collection, Input.id(id)));
        });
    }

    @RequestMapping({"/places/{id}", "/places/{id}/"})
    public void place(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(ontology.place(x.connection(), Input.id(id))));
    }

    @RequestMapping({"/me/places/{id}/feedback", "/me/places/{id}/feedback/"})
    public void placeFeedback(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,PUT,DELETE", x -> {
            var key = Input.id(id);
            var service = new PlaceFeedbackService();
            return switch (x.method()) {
                case "GET" -> ok(service.get(x.connection(), x.userId(), key));
                case "PUT" -> {
                    boolean exists = !service.current(x.connection(), x.userId(), key).isEmpty();
                    var result = service.save(x.connection(), x.userId(), key, x.body());
                    yield exists ? ok(result) : created(result, x.path());
                }
                case "DELETE" -> {
                    Input.fields(x.body(), "");
                    service.delete(x.connection(), x.userId(), key);
                    yield new Reply(204, null, null);
                }
                default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
            };
        });
    }

    @RequestMapping({"/admin/place-feedback", "/admin/place-feedback/"})
    public void placeFeedbackStatistics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(CatalogService.page(new PlaceFeedbackService().statistics(x.connection()), x.query())));
    }

    @RequestMapping({"/recommendation-options", "/recommendation-options/"})
    public void options(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(ontology.options(x.connection())));
    }

    @RequestMapping({"/recommendations", "/recommendations/"})
    public void recommend(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(new RecommendationService().recommend(x.connection(), x.userId(), x.role(), x.query())));
    }

    @RequestMapping({"/me/journeys", "/me/journeys/"})
    public void journeys(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,POST", x -> switch (x.method()) {
            case "POST" -> {
                var journey = journeys.create(x.connection(), x.userId(), x.body());
                yield created(journey, x.path() + "/" + journey.get("id"));
            }
            case "GET" -> ok(CatalogService.page(journeys.list(x.connection(), x.userId(), Input.param(x.query().get("status"))), x.query()));
            default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
        });
    }

    @RequestMapping({"/me/journeys/{id}", "/me/journeys/{id}/"})
    public void journey(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,PATCH,DELETE", x -> {
            var key = Input.id(id);
            return switch (x.method()) {
                case "GET" -> ok(journeys.detail(x.connection(), x.userId(), key));
                case "PATCH" -> ok(journeys.save(x.connection(), x.userId(), key, x.body()));
                case "DELETE" -> {
                    Input.fields(x.body(), "");
                    journeys.delete(x.connection(), x.userId(), key);
                    yield new Reply(204, null, null);
                }
                default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
            };
        });
    }

    @RequestMapping({"/me/journeys/{id}/places", "/me/journeys/{id}/places/"})
    public void addCard(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "POST", x -> ok(journeys.add(x.connection(), x.userId(), Input.id(id), x.body())));
    }

    @RequestMapping({"/me/journeys/{id}/places/{item}", "/me/journeys/{id}/places/{item}/"})
    public void deleteCard(@PathVariable String id, @PathVariable String item, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "DELETE", x -> ok(journeys.item(x.connection(), x.userId(), Input.id(id), Input.id(item), "delete", x.body())));
    }

    @RequestMapping({"/me/journeys/{id}/places/{item}/{action:record|move}", "/me/journeys/{id}/places/{item}/{action:record|move}/"})
    public void updateCard(@PathVariable String id, @PathVariable String item, @PathVariable String action, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, action.equals("move") ? "POST" : "PUT", x -> ok(journeys.item(x.connection(), x.userId(), Input.id(id), Input.id(item), action, x.body())));
    }

    @RequestMapping({"/me/passes", "/me/passes/"})
    public void passes(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,POST", x -> switch (x.method()) {
            case "POST" -> {
                var pass = passes.issue(x.connection(), x.userId(), x.body());
                yield created(pass, "/me/passes/" + pass.get("id"));
            }
            case "GET" -> ok(CatalogService.page(passes.list(x.connection(), x.userId(), x.query()), x.query()));
            default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
        });
    }

    @RequestMapping({"/me/passes/{id}", "/me/passes/{id}/"})
    public void passDetail(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(passes.detail(x.connection(), x.userId(), Input.id(id), false)));
    }

    @RequestMapping({"/me/redemptions", "/me/redemptions/"})
    public void history(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(CatalogService.page(redemption.history(x.connection(), x.userId(), false, Input.param(x.query().get("passId"))), x.query())));
    }

    @RequestMapping({"/me/redemptions/{id}", "/me/redemptions/{id}/"})
    public void redemption(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(redemption.detail(x.connection(), x.userId(), Input.id(id), false)));
    }

    @RequestMapping({"/me/redemptions/{id}/feedback", "/me/redemptions/{id}/feedback/"})
    public void feedback(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,POST,PUT,DELETE", x -> {
            var key = Input.id(id);
            return switch (x.method()) {
                case "GET" -> ok(feedback.get(x.connection(), x.userId(), key));
                case "POST" -> created(feedback.save(x.connection(), x.userId(), key, x.body(), true), x.path());
                case "PUT" -> ok(feedback.save(x.connection(), x.userId(), key, x.body(), false));
                case "DELETE" -> {
                    Input.fields(x.body(), "");
                    feedback.delete(x.connection(), x.userId(), key);
                    yield new Reply(204, null, null);
                }
                default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
            };
        });
    }

    @RequestMapping({"/merchant/assignments", "/merchant/assignments/"})
    public void assignments(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(redemption.assignments(x.connection(), x.userId())));
    }

    @RequestMapping({"/merchant/pass-lookups", "/merchant/pass-lookups/"})
    public void lookup(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "POST", x -> {
            var result = redemption.createLookup(x.connection(), x.userId(), x.body());
            return created(result, "/merchant/pass-lookups/" + result.get("id"));
        });
    }

    @RequestMapping({"/merchant/pass-lookups/{token}", "/merchant/pass-lookups/{token}/"})
    public void passLookup(@PathVariable String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(redemption.getLookup(x.connection(), x.userId(), token)));
    }

    @RequestMapping({"/merchant/redemptions", "/merchant/redemptions/"})
    public void merchantRedemptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,POST", x -> switch (x.method()) {
            case "POST" -> {
                var result = redemption.confirmLookup(x.connection(), x.userId(), x.body());
                yield created(result, "/merchant/redemptions/" + result.get("id"));
            }
            case "GET" -> ok(CatalogService.page(redemption.history(x.connection(), x.userId(), true, Input.param(x.query().get("merchantId"))), x.query()));
            default -> throw new Problem(405, "METHOD_NOT_ALLOWED");
        });
    }

    @RequestMapping({"/merchant/redemptions/{id}", "/merchant/redemptions/{id}/"})
    public void merchantRedemption(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(redemption.detail(x.connection(), x.userId(), Input.id(id), true)));
    }

    @RequestMapping({"/admin/passes", "/admin/passes/"})
    public void adminPasses(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> {
            var owner = Input.param(x.query().get("ownerId"));
            if (owner != null) Input.id(owner);
            return ok(CatalogService.page(passes.list(x.connection(), owner, x.query()), x.query()));
        });
    }

    @RequestMapping({"/admin/passes/{id}", "/admin/passes/{id}/"})
    public void adminPass(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET", x -> ok(passes.detail(x.connection(), null, Input.id(id), true)));
    }

    @RequestMapping({"/admin/passes/{id}/cancellation", "/admin/passes/{id}/cancellation/"})
    public void cancel(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "PUT", x -> ok(passes.cancel(x.connection(), x.userId(), Input.id(id), x.body())));
    }

    @RequestMapping({"/admin/{collection:products|merchants|benefits}", "/admin/{collection:products|merchants|benefits}/", "/admin/{collection:products|merchants|benefits}/{id}", "/admin/{collection:products|merchants|benefits}/{id}/", "/admin/ontology/{collection:places|regions|themes|companion-types}", "/admin/ontology/{collection:places|regions|themes|companion-types}/", "/admin/ontology/{collection:places|regions|themes|companion-types}/{id}", "/admin/ontology/{collection:places|regions|themes|companion-types}/{id}/"})
    public void resource(@PathVariable String collection, @PathVariable(required = false) String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, id == null ? "GET,POST" : "GET,PATCH", x -> {
            var key = id == null ? null : Input.id(id);
            if (x.method().equals("GET")) {
                return key == null ? ok(CatalogService.page(admin.list(x.connection(), collection, x.query()), x.query())) : ok(admin.get(x.connection(), collection, key));
            }
            var result = admin.save(x.connection(), collection, key, x.body());
            return key == null ? created(result, x.path() + "/" + result.get("id")) : ok(result);
        });
    }

    @RequestMapping({"/admin/ontology/places/{id}/review", "/admin/ontology/places/{id}/review/"})
    public void reviewPlace(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "PUT", x -> ok(ontology.reviewPlace(x.connection(), x.userId(), Input.id(id), x.body())));
    }

    @RequestMapping({"/admin/ontology/relations", "/admin/ontology/relations/"})
    public void relations(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,POST", x -> {
            if (x.method().equals("GET")) {
                var type = Input.param(x.query().get("type"));
                return ok(CatalogService.page(ontology.relations(x.connection(), type == null ? "" : type), x.query()));
            }
            var body = x.body();
            Input.fields(body, "type,fromId,toId,annotation");
            var type = Input.text(body.get("type"), 30, false);
            var from = Input.id(body.get("fromId"));
            var to = Input.id(body.get("toId"));
            var result = ontology.relationSave(x.connection(), type, from, to, Collections.singletonMap("annotation", body.get("annotation")), true);
            return created(result, x.path() + "/" + type + "/" + from + "/" + to);
        });
    }

    @RequestMapping({"/admin/ontology/relations/{type}/{from}/{to}", "/admin/ontology/relations/{type}/{from}/{to}/"})
    public void relation(@PathVariable String type, @PathVariable String from, @PathVariable String to, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "GET,PATCH", x -> {
            OntologyService.validateRelation(type);
            var fromId = Input.id(from);
            var toId = Input.id(to);
            if (x.method().equals("GET")) return ok(ontology.relationGet(x.connection(), type, fromId, toId));
            return ok(ontology.relationSave(x.connection(), type, fromId, toId, x.body(), false));
        });
    }

    @RequestMapping({"/admin/ontology/relations/{type}/{from}/{to}/review", "/admin/ontology/relations/{type}/{from}/{to}/review/"})
    public void reviewRelation(@PathVariable String type, @PathVariable String from, @PathVariable String to, HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "PUT", x -> {
            OntologyService.validateRelation(type);
            var fromId = Input.id(from);
            var toId = Input.id(to);
            return ok(ontology.reviewRelation(x.connection(), x.userId(), type, fromId, toId, x.body()));
        });
    }

    @RequestMapping("/**")
    public void missing(HttpServletRequest request, HttpServletResponse response) throws IOException {
        api.handle(request, response, "", x -> { throw new Problem(404, "NOT_FOUND"); });
    }
}
