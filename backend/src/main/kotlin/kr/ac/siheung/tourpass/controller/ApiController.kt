package kr.ac.siheung.tourpass.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import kr.ac.siheung.tourpass.controller.ApiHandler.*
import kr.ac.siheung.tourpass.service.*

// Method checks stay inside the transaction so authorization and CSRF run first.
@Controller
@RequestMapping("/api/v1", method = [RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS])
class ApiController {
    private val api = ApiHandler()
    private val catalog = CatalogService()
    private val journeys = JourneyService()
    private val passes = PassService()
    private val redemption = RedemptionService()
    private val feedback = FeedbackService()
    private val ontology = OntologyService()
    private val admin = AdminService()

    @RequestMapping("", "/")
    fun index(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(mapOf("user" to x.user()))
    }

    @RequestMapping("/{collection:products|merchants}", "/{collection:products|merchants}/", "/{collection:products|merchants}/{id}", "/{collection:products|merchants}/{id}/")
    fun catalog(@PathVariable collection: String, @PathVariable(required = false) id: String?, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        if (id == null) ok(CatalogService.page(catalog.list(x.connection(), collection, Input.param(x.query()["regionId"])), x.query()))
        else ok(if (collection == "products") catalog.product(x.connection(), Input.id(id)) else catalog.merchant(x.connection(), Input.id(id)))
    }

    @RequestMapping("/places/{id}", "/places/{id}/")
    fun place(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(ontology.place(x.connection(), Input.id(id)))
    }

    @RequestMapping("/me/places/{id}/feedback", "/me/places/{id}/feedback/")
    fun placeFeedback(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,PUT,DELETE") { x ->
        val key = Input.id(id)
        val service = PlaceFeedbackService()
        when (x.method()) {
            "GET" -> ok(service.get(x.connection(), x.userId(), key))
            "PUT" -> {
                val exists = service.current(x.connection(), x.userId(), key).isNotEmpty()
                val result = service.save(x.connection(), x.userId(), key, x.body())
                if (exists) ok(result) else created(result, x.path())
            }
            "DELETE" -> { Input.fields(x.body(), ""); service.delete(x.connection(), x.userId(), key); Reply(204, null, null) }
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/admin/place-feedback", "/admin/place-feedback/")
    fun placeFeedbackStatistics(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(CatalogService.page(PlaceFeedbackService().statistics(x.connection()), x.query()))
    }

    @RequestMapping("/recommendation-options", "/recommendation-options/")
    fun options(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(ontology.options(x.connection()))
    }

    @RequestMapping("/recommendations", "/recommendations/")
    fun recommend(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(RecommendationService().recommend(x.connection(), x.userId(), x.role(), x.query()))
    }

    @RequestMapping("/me/journeys", "/me/journeys/")
    fun journeys(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,POST") { x ->
        when (x.method()) {
            "POST" -> { val journey = journeys.create(x.connection(), x.userId(), x.body()); created(journey, "${x.path()}/${journey["id"]}") }
            "GET" -> ok(CatalogService.page(journeys.list(x.connection(), x.userId(), Input.param(x.query()["status"])), x.query()))
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/me/journeys/{id}", "/me/journeys/{id}/")
    fun journey(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,PATCH,DELETE") { x ->
        val key = Input.id(id)
        when (x.method()) {
            "GET" -> ok(journeys.detail(x.connection(), x.userId(), key))
            "PATCH" -> ok(journeys.save(x.connection(), x.userId(), key, x.body()))
            "DELETE" -> { Input.fields(x.body(), ""); journeys.delete(x.connection(), x.userId(), key); Reply(204, null, null) }
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/me/journeys/{id}/places", "/me/journeys/{id}/places/")
    fun addCard(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "POST") { x ->
        val key = Input.id(id)
        ok(journeys.add(x.connection(), x.userId(), key, x.body()))
    }

    @RequestMapping("/me/journeys/{id}/places/{item}", "/me/journeys/{id}/places/{item}/")
    fun deleteCard(@PathVariable id: String, @PathVariable item: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "DELETE") { x ->
        val key = Input.id(id)
        val itemId = Input.id(item)
        ok(journeys.item(x.connection(), x.userId(), key, itemId, "delete", x.body()))
    }

    @RequestMapping("/me/journeys/{id}/places/{item}/{action:record|move}", "/me/journeys/{id}/places/{item}/{action:record|move}/")
    fun updateCard(@PathVariable id: String, @PathVariable item: String, @PathVariable action: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, if (action == "move") "POST" else "PUT") { x ->
        val key = Input.id(id)
        val itemId = Input.id(item)
        ok(journeys.item(x.connection(), x.userId(), key, itemId, action, x.body()))
    }

    @RequestMapping("/me/passes", "/me/passes/")
    fun passes(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,POST") { x ->
        when (x.method()) {
            "POST" -> { val pass = passes.issue(x.connection(), x.userId(), x.body()); created(pass, "/me/passes/${pass["id"]}") }
            "GET" -> ok(CatalogService.page(passes.list(x.connection(), x.userId(), x.query()), x.query()))
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/me/passes/{id}", "/me/passes/{id}/")
    fun passDetail(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(passes.detail(x.connection(), x.userId(), Input.id(id), false))
    }

    @RequestMapping("/me/redemptions", "/me/redemptions/")
    fun history(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(CatalogService.page(redemption.history(x.connection(), x.userId(), false, Input.param(x.query()["passId"])), x.query()))
    }

    @RequestMapping("/me/redemptions/{id}", "/me/redemptions/{id}/")
    fun redemption(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        val key = Input.id(id)
        ok(redemption.detail(x.connection(), x.userId(), key, false))
    }

    @RequestMapping("/me/redemptions/{id}/feedback", "/me/redemptions/{id}/feedback/")
    fun feedback(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,POST,PUT,DELETE") { x ->
        val key = Input.id(id)
        when (x.method()) {
            "GET" -> ok(feedback.get(x.connection(), x.userId(), key))
            "POST" -> created(feedback.save(x.connection(), x.userId(), key, x.body(), true), x.path())
            "PUT" -> ok(feedback.save(x.connection(), x.userId(), key, x.body(), false))
            "DELETE" -> { Input.fields(x.body(), ""); feedback.delete(x.connection(), x.userId(), key); Reply(204, null, null) }
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/merchant/assignments", "/merchant/assignments/")
    fun assignments(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(redemption.assignments(x.connection(), x.userId()))
    }

    @RequestMapping("/merchant/pass-lookups", "/merchant/pass-lookups/")
    fun lookup(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "POST") { x ->
        val result = redemption.createLookup(x.connection(), x.userId(), x.body())
        created(result, "/merchant/pass-lookups/${result["id"]}")
    }

    @RequestMapping("/merchant/pass-lookups/{token}", "/merchant/pass-lookups/{token}/")
    fun passLookup(@PathVariable token: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(redemption.getLookup(x.connection(), x.userId(), token))
    }

    @RequestMapping("/merchant/redemptions", "/merchant/redemptions/")
    fun merchantRedemptions(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,POST") { x ->
        when (x.method()) {
            "POST" -> { val result = redemption.confirmLookup(x.connection(), x.userId(), x.body()); created(result, "/merchant/redemptions/${result["id"]}") }
            "GET" -> ok(CatalogService.page(redemption.history(x.connection(), x.userId(), true, Input.param(x.query()["merchantId"])), x.query()))
            else -> throw Problem(405, "METHOD_NOT_ALLOWED")
        }
    }

    @RequestMapping("/merchant/redemptions/{id}", "/merchant/redemptions/{id}/")
    fun merchantRedemption(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(redemption.detail(x.connection(), x.userId(), Input.id(id), true))
    }

    @RequestMapping("/admin/passes", "/admin/passes/")
    fun adminPasses(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        val owner = Input.param(x.query()["ownerId"])
        if (owner != null) Input.id(owner)
        ok(CatalogService.page(passes.list(x.connection(), owner, x.query()), x.query()))
    }

    @RequestMapping("/admin/passes/{id}", "/admin/passes/{id}/")
    fun adminPass(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET") { x ->
        ok(passes.detail(x.connection(), null, Input.id(id), true))
    }

    @RequestMapping("/admin/passes/{id}/cancellation", "/admin/passes/{id}/cancellation/")
    fun cancel(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "PUT") { x ->
        ok(passes.cancel(x.connection(), x.userId(), Input.id(id), x.body()))
    }

    @RequestMapping("/admin/{collection:products|merchants|benefits}", "/admin/{collection:products|merchants|benefits}/", "/admin/{collection:products|merchants|benefits}/{id}", "/admin/{collection:products|merchants|benefits}/{id}/", "/admin/ontology/{collection:places|regions|themes|companion-types}", "/admin/ontology/{collection:places|regions|themes|companion-types}/", "/admin/ontology/{collection:places|regions|themes|companion-types}/{id}", "/admin/ontology/{collection:places|regions|themes|companion-types}/{id}/")
    fun resource(@PathVariable collection: String, @PathVariable(required = false) id: String?, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, if (id == null) "GET,POST" else "GET,PATCH") { x ->
        val key = id?.let { Input.id(it) }
        if (x.method() == "GET") {
            if (key == null) ok(CatalogService.page(admin.list(x.connection(), collection, x.query()), x.query())) else ok(admin.get(x.connection(), collection, key))
        } else {
            val result = admin.save(x.connection(), collection, key, x.body())
            if (key == null) created(result, "${x.path()}/${result["id"]}") else ok(result)
        }
    }

    @RequestMapping("/admin/ontology/places/{id}/review", "/admin/ontology/places/{id}/review/")
    fun reviewPlace(@PathVariable id: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "PUT") { x ->
        ok(ontology.reviewPlace(x.connection(), x.userId(), Input.id(id), x.body()))
    }

    @RequestMapping("/admin/ontology/relations", "/admin/ontology/relations/")
    fun relations(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,POST") { x ->
        if (x.method() == "GET") {
            val type = Input.param(x.query()["type"]) ?: ""
            ok(CatalogService.page(ontology.relations(x.connection(), type), x.query()))
        } else {
            val body = x.body()
            Input.fields(body, "type,fromId,toId,annotation")
            val type = Input.text(body["type"], 30, false)
            val from = Input.id(body["fromId"])
            val to = Input.id(body["toId"])
            val result = ontology.relationSave(x.connection(), type, from, to, mapOf("annotation" to body["annotation"]), true)
            created(result, "${x.path()}/$type/$from/$to")
        }
    }

    @RequestMapping("/admin/ontology/relations/{type}/{from}/{to}", "/admin/ontology/relations/{type}/{from}/{to}/")
    fun relation(@PathVariable type: String, @PathVariable from: String, @PathVariable to: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "GET,PATCH") { x ->
        OntologyService.validateRelation(type)
        val fromId = Input.id(from)
        val toId = Input.id(to)
        if (x.method() == "GET") ok(ontology.relationGet(x.connection(), type, fromId, toId))
        else {
            ok(ontology.relationSave(x.connection(), type, fromId, toId, x.body(), false))
        }
    }

    @RequestMapping("/admin/ontology/relations/{type}/{from}/{to}/review", "/admin/ontology/relations/{type}/{from}/{to}/review/")
    fun reviewRelation(@PathVariable type: String, @PathVariable from: String, @PathVariable to: String, request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "PUT") { x ->
        OntologyService.validateRelation(type)
        val fromId = Input.id(from)
        val toId = Input.id(to)
        ok(ontology.reviewRelation(x.connection(), x.userId(), type, fromId, toId, x.body()))
    }

    @RequestMapping("/**")
    fun missing(request: HttpServletRequest, response: HttpServletResponse) = api.handle(request, response, "") {
        throw Problem(404, "NOT_FOUND")
    }
}
