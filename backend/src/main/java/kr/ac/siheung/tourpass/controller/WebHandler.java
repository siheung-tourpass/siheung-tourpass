package kr.ac.siheung.tourpass.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.Connection;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import kr.ac.siheung.tourpass.config.Database;
import kr.ac.siheung.tourpass.filter.SecurityFilter;
import kr.ac.siheung.tourpass.dao.Sql;
import kr.ac.siheung.tourpass.service.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Server-rendered forms share the API's services and transaction boundary. */
public final class WebHandler {
    private static final DateTimeFormatter KST=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '한국 시간'").withZone(ZoneId.of("Asia/Seoul"));
    private record Page(String view,Object data,String redirect) {}
    private static Page page(String view,Object data){return new Page(view,data,null);}
    private static Page redirect(String path){return new Page(null,null,path);}
    public void handle(HttpServletRequest r,HttpServletResponse out) throws IOException,ServletException {
        try{
            var s=r.getSession();
            synchronized(s){
                Page result=Database.transaction(r.getMethod().equals("POST"),c->{
                    var user=SecurityFilter.authorize(c,r);
                    require(Set.of("GET","POST").contains(r.getMethod()),405,"METHOD_NOT_ALLOWED");
                    r.setAttribute("user",user);return route(c,r,user);
                });
                // Session changes happen only after the database transaction commits.
                if(r.getServletPath().equals("/login")&&r.getMethod().equals("POST")){
                    var user=Input.object(result.data());SessionState.login(r,user);
                    result=redirect(switch((String)user.get("role")){case "STAFF"->"/merchant/redeem";case "ADMIN"->"/admin";default->"/journeys";});
                }else if(r.getServletPath().equals("/logout"))s.invalidate();
                else if(result.data() instanceof RedemptionService.Lookup lookup){s.setAttribute("confirmation",lookup.confirmation());result=page("confirm",lookup.response());}
                else if(r.getServletPath().equals("/merchant/redeem/confirm"))s.setAttribute("confirmation",((RedemptionService.Confirmation)s.getAttribute("confirmation")).consume());
                if(result.redirect()!=null){out.setStatus(303);out.setHeader("Location",r.getContextPath()+result.redirect());return;}
                r.setAttribute("view",result.view());r.setAttribute("data",display(result.data()));r.setAttribute("csrf",s.getAttribute("csrf"));
                out.setContentType("text/html;charset=UTF-8");r.getRequestDispatcher("/WEB-INF/views/page.jsp").forward(r,out);
            }
        }catch(Problem e){error(r,out,e);}catch(Exception e){error(r,out,new Problem(500,"INTERNAL_ERROR"));}
    }
    public void missing(HttpServletRequest r,HttpServletResponse out) throws IOException,ServletException {
        try{Database.transaction(c->SecurityFilter.authorize(c,r));out.sendError(404);}
        catch(Problem e){error(r,out,e);}
    }
    public static void error(HttpServletRequest r,HttpServletResponse out,Problem e) throws IOException,ServletException {
        if(e.code.equals("AUTH_REQUIRED")){out.setStatus(303);out.setHeader("Location",r.getContextPath()+"/login");return;}
        r.setAttribute("view","error");r.setAttribute("message",message(e.code));r.setAttribute("errorCode",e.code);r.setAttribute("csrf",r.getSession().getAttribute("csrf"));out.setStatus(e.status);out.setContentType("text/html;charset=UTF-8");r.getRequestDispatcher("/WEB-INF/views/page.jsp").forward(r,out);
    }
    public static String message(String code){return switch(code){
        case "INVALID_INPUT","INVALID_CONCEPT"->"입력값을 확인해 주세요.";
        case "INVALID_CREDENTIALS"->"아이디와 비밀번호를 확인해 주세요.";
        case "FORBIDDEN"->"이 작업을 수행할 권한이 없습니다.";
        case "CSRF_INVALID"->"폼이 만료되었습니다. 화면을 다시 열고 제출해 주세요.";
        case "NOT_FOUND","PASS_CODE_NOT_FOUND"->"조회할 수 있는 자료가 없습니다.";
        case "ACTIVE_PASS_EXISTS"->"이미 유효한 패스가 있습니다. 내 패스를 확인해 주세요.";
        case "BENEFIT_ALREADY_USED"->"이미 사용한 혜택입니다.";
        case "PASS_EXPIRED","EXPIRED"->"만료된 패스입니다.";
        case "PASS_CANCELLED","CANCELLED"->"취소된 패스입니다.";
        case "PASS_NOT_STARTED","NOT_STARTED"->"사용 기간이 시작되지 않았습니다.";
        case "MERCHANT_INACTIVE"->"비활성 가맹점입니다.";
        case "BENEFIT_INACTIVE"->"비활성 혜택입니다.";
        case "PRODUCT_INACTIVE","NO_ISSUABLE_BENEFITS"->"현재 발급할 수 없는 상품입니다.";
        case "CONFIRMATION_USED"->"이미 처리한 확인 요청입니다. 이력을 확인해 주세요.";
        case "CONFIRMATION_EXPIRED","CONFIRMATION_INVALID"->"확인이 만료되었습니다. 코드를 다시 조회해 주세요.";
        case "THEME_CYCLE"->"테마 관계에 순환이 생겨 저장할 수 없습니다.";
        case "DUPLICATE_RELATION","DUPLICATE_RESOURCE","DUPLICATE_FEEDBACK"->"이미 등록된 자료입니다.";
        case "REVIEW_REQUIRED"->"근거와 연결 자료의 승인을 확인해 주세요.";
        case "JOURNEY_ARCHIVED","JOURNEY_LOCKED"->"제작 중인 패스만 구성을 바꾸거나 삭제할 수 있습니다.";
        case "TEMPLATE_UNAVAILABLE"->"이 템플릿의 장소 자료가 준비되지 않았습니다. 다른 템플릿이나 빈 여행으로 시작해 주세요.";
        case "JOURNEY_EMPTY"->"제작 확정 전에 카드를 한 장 이상 담아 주세요.";
        case "INVALID_JOURNEY_TRANSITION"->"제작 중 → 제작 확정 → 사용 완료 순서로 변경해 주세요.";
        case "PLANNING"->"제작 중";case "CONFIRMED"->"제작 확정";case "ARCHIVED"->"사용 완료";
        case "AVAILABLE"->"사용 가능";case "ACTIVE"->"유효";case "SUCCESS"->"사용 성공";
        case "APPROVED"->"승인";case "DRAFT"->"검토 대기";case "REJECTED"->"반려";
        case "RETRY_LATER"->"잠시 후 다시 시도해 주세요.";
        default->"요청을 처리하지 못했습니다.";
    };}
    public static Object display(Object value){
        if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();for(var e:map.entrySet()){
            String key=e.getKey().toString();Object v=e.getValue();copy.put(key,display(v));
            if(key.endsWith("At")&&v instanceof String text)try{copy.put(key+"Kst",KST.format(Instant.parse(text)));}catch(java.time.DateTimeException ignored){}
            if(Set.of("status","reason","reviewStatus","result").contains(key)&&v instanceof String text)copy.put(key+"Label",message(text));
        }return copy;}
        if(value instanceof List<?> list)return list.stream().map(WebHandler::display).toList();return value;
    }
    private static String param(HttpServletRequest r,String name){String[] v=r.getParameterValues(name);return Input.param(v==null?null:Arrays.asList(v));}
    private static String id(HttpServletRequest r){return Input.id(param(r,"id"));}
    private static Map<String,List<String>> query(HttpServletRequest r){var q=new LinkedHashMap<String,List<String>>();r.getParameterMap().forEach((k,v)->{if(!v[0].isBlank())q.put(k,Arrays.asList(v));});return q;}
    private static Map<String,Object> form(HttpServletRequest r,String... fields){var b=new LinkedHashMap<String,Object>();for(String f:fields)b.put(f,param(r,f));return b;}
    private static void method(boolean actual,boolean expected){require(actual==expected,405,"METHOD_NOT_ALLOWED");}
    private Page route(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath(),uid=user==null?null:(String)user.get("id"),role=user==null?null:(String)user.get("role");boolean post=r.getMethod().equals("POST");
        var catalog=new CatalogService();var passes=new PassService();var redemption=new RedemptionService();var feedback=new FeedbackService();var ontology=new OntologyService();var admin=new AdminService();var q=query(r);
        switch(path){
            case "/login":return post?page("login",new AuthService().login(c,form(r,"loginId","password"))):page("login",null);
            case "/logout":method(post,true);require(user!=null,401,"AUTH_REQUIRED");return redirect("/");
            case "/":method(post,false);return page("home",null);
            case "/profile":method(post,false);return page("profile",null);
            case "/events":method(post,false);r.setAttribute("eventData",display(EventCatalog.list(param(r,"date"))));return page("events",null);
            case "/merchants":method(post,false);return page("catalog",Map.of("products",catalog.list(c,"products",null),"merchants",catalog.list(c,"merchants",Input.param(q.get("regionId")))));
            case "/products/detail":method(post,false);return page("product",catalog.product(c,id(r)));
            case "/merchants/detail":method(post,false);return page("merchant",catalog.merchant(c,id(r)));
            case "/places/detail":method(post,false);journeyOptions(c,r,uid,role);return page("place",ontology.place(c,id(r)));
            case "/recommendations":method(post,false);journeyOptions(c,r,uid,role);r.setAttribute("options",ontology.options(c));return page("recommendations",new RecommendationService().recommend(c,uid,role,q));
            case "/journeys":{
                method(post,false);String status=param(r,"status");if(status==null)status="PLANNING";
                if(status.equals("PLANNING"))r.setAttribute("journeyTemplates",JourneyTemplates.list(ontology.browsePlaces(c)));
                r.setAttribute("journeyStatus",status);return page("journeys",new JourneyService().list(c,uid,status));
            }
            case "/explore", "/journeys/new":method(post,false);return builder(c,r,uid,null);
            case "/journeys/edit":method(post,false);return builder(c,r,uid,id(r));
            case "/journeys/detail":{
                method(post,false);var journey=new JourneyService().detail(c,uid,id(r));
                if(journey.get("status").equals("PLANNING"))return redirect("/journeys/edit?id="+id(r));
                r.setAttribute("journeyStatus",journey.get("status"));r.setAttribute("benefitPasses",passes.list(c,uid,Map.of()));return page("journey",journey);
            }
            case "/journeys/create", "/journeys/update":{
                method(post,true);String intent=param(r,"intent");if(intent==null)intent="draft";
                Input.choice(intent,"draft","confirm");String journeyId=path.endsWith("update")?id(r):null;
                var journey=new JourneyService().saveDraft(c,uid,journeyId,form(r,"name","travelDate"),
                    r.getParameterValues("cardKey")!=null?Arrays.asList(r.getParameterValues("cardKey")):
                    r.getParameterValues("placeId")==null?null:Arrays.asList(r.getParameterValues("placeId")),intent.equals("confirm"));
                return redirect(journey.get("status").equals("PLANNING")?"/journeys/edit?id="+journey.get("id"):"/journeys/detail?id="+journey.get("id"));
            }
            case "/journeys/delete":method(post,true);new JourneyService().delete(c,uid,id(r));return redirect("/journeys");
            case "/journeys/save":{
                method(post,true);var b=form(r,"name","travelDate","status","issuedPassId");b.keySet().removeIf(key->!r.getParameterMap().containsKey(key));if("".equals(b.get("issuedPassId")))b.put("issuedPassId",null);
                new JourneyService().save(c,uid,id(r),b);return redirect("/journeys/detail?id="+id(r));
            }
            case "/journeys/add":method(post,true);new JourneyService().add(c,uid,id(r),form(r,"placeId"));return redirect("/journeys/detail?id="+id(r));
            case "/journeys/record","/journeys/move","/journeys/remove":{
                method(post,true);String action=path.endsWith("record")?"record":path.equals("/journeys/move")?"move":"delete";Map<String,Object> b;
                if(action.equals("record")){b=form(r,"visitedOn","note");String rating=param(r,"rating");b.put("rating",rating==null||rating.isBlank()?null:Input.parseInteger(rating));}
                else b=action.equals("move")?form(r,"direction"):Map.of();
                new JourneyService().item(c,uid,id(r),Input.id(param(r,"itemId")),action,b);return redirect("/journeys/detail?id="+id(r)+(action.equals("record")?"#record-"+Input.id(param(r,"itemId")):""));
            }
            case "/passes":method(post,false);return page("passes",passes.list(c,uid,q));
            case "/passes/detail":method(post,false);return page("pass",passes.detail(c,uid,id(r),false));
            case "/passes/issue":method(post,true);return redirect("/passes/detail?id="+passes.issue(c,uid,form(r,"productId")).get("id"));
            case "/feedback/history":method(post,false);return page("history",redemption.history(c,uid,false,null));
            case "/feedback/edit":{
                method(post,false);String rid=id(r);var history=redemption.detail(c,uid,rid,false);Map<String,Object> existing=Map.of();
                if(Boolean.TRUE.equals(history.get("hasFeedback")))existing=feedback.get(c,uid,rid);
                return page("feedback",Map.of("redemption",history,"feedback",existing));
            }
            case "/feedback/create","/feedback/update","/feedback/delete":{
                method(post,true);String rid=id(r);if(path.endsWith("delete"))feedback.delete(c,uid,rid);else{
                    var b=form(r,"comment");b.put("rating",Input.parseInteger(param(r,"rating")));b.put("reasons",r.getParameterValues("reasons")==null?List.of():Arrays.asList(r.getParameterValues("reasons")));feedback.save(c,uid,rid,b,path.endsWith("create"));
                }return redirect("/feedback/history");
            }
            case "/merchant/redeem":method(post,false);return page("redeem",redemption.assignments(c,uid));
            case "/merchant/redeem/lookup":method(post,true);return page("confirm",redemption.lookup(c,uid,form(r,"merchantId","code")));
            case "/merchant/redeem/confirm":method(post,true);redemption.confirm(c,uid,(RedemptionService.Confirmation)r.getSession().getAttribute("confirmation"),form(r,"confirmationToken","issuedBenefitId"));return redirect("/merchant/history");
            case "/merchant/history":{
                method(post,false);var assignments=redemption.assignments(c,uid);r.setAttribute("assignments",assignments);String merchant=param(r,"merchantId");if(merchant==null&&!assignments.isEmpty())merchant=(String)assignments.get(0).get("merchantId");return page("history",merchant==null?List.of():redemption.history(c,uid,true,merchant));
            }
            case "/admin":method(post,false);return page("admin",null);
            case "/admin/passes":method(post,false);return page("passes",passes.list(c,null,q));
            case "/admin/passes/detail":method(post,false);return page("pass",passes.detail(c,null,id(r),true));
            case "/admin/passes/cancel":method(post,true);passes.cancel(c,uid,id(r),form(r,"reason"));return redirect("/admin/passes/detail?id="+id(r));
            case "/admin/catalog","/admin/catalog/save","/admin/catalog/deactivate","/admin/ontology","/admin/ontology/save","/admin/ontology/deactivate","/admin/ontology/review":{
                String collection=param(r,"collection");boolean semantic=path.startsWith("/admin/ontology");
                require((semantic?Set.of("places","regions","themes","companion-types"):Set.of("products","merchants","benefits")).contains(collection==null?"":collection),404,"NOT_FOUND");
                String raw=param(r,"id"),item=raw==null||raw.isBlank()?null:Input.id(raw);r.setAttribute("collection",collection);r.setAttribute("semantic",semantic);
                if(post){require(!path.equals("/admin/catalog")&&!path.equals("/admin/ontology"),405,"METHOD_NOT_ALLOWED");Map<String,Object> result;
                    if(path.endsWith("review")){require(collection.equals("places")&&item!=null,400,"INVALID_INPUT");result=ontology.review(c,uid,"place","id=?",new Object[]{item},form(r,"status"));}
                    else if(path.endsWith("deactivate")){require(item!=null,400,"INVALID_INPUT");result=admin.save(c,collection,item,Map.of("active",false));}
                    else result=admin.save(c,collection,item,adminBody(r,collection,item==null));
                    return redirect((semantic?"/admin/ontology":"/admin/catalog")+"?collection="+collection+"&id="+result.get("id"));
                }
                require(path.equals("/admin/catalog")||path.equals("/admin/ontology"),405,"METHOD_NOT_ALLOWED");
                var current=item==null?Map.<String,Object>of():admin.get(c,collection,item);r.setAttribute("current",display(current));var editorFields=fields(collection,item==null);r.setAttribute("fields",editorFields);var choices=new LinkedHashSet<String>();
                for(String field:editorFields)switch(field){
                    case "placeId"->choices.add("places");case "regionId"->choices.add("regions");
                    case "productId"->choices.add("products");case "merchantId"->choices.add("merchants");
                }
                r.setAttribute("options",adminOptions(c,admin,choices));
                return page("editor",admin.list(c,collection,q));
            }
            case "/admin/relations","/admin/relations/save","/admin/relations/review","/admin/relations/deactivate":{
                String type=param(r,"type");if(type==null)type="has-theme";var rel=OntologyService.relation(type);String from=param(r,"fromId"),to=param(r,"toId");
                r.setAttribute("relationType",type);
                if(post){from=Input.id(from);to=Input.id(to);
                    if(path.endsWith("review"))ontology.review(c,uid,rel.table(),rel.from()+"=? AND "+rel.to()+"=?",new Object[]{from,to},form(r,"status"));
                    else {require(path.endsWith("save")||path.endsWith("deactivate"),405,"METHOD_NOT_ALLOWED");boolean create="create".equals(param(r,"operation"));ontology.relationSave(c,type,from,to,path.endsWith("deactivate")?Map.of("active",false):Map.of("annotation",annotation(r)),create);}
                    return redirect("/admin/relations?type="+type+"&fromId="+from+"&toId="+to);
                }
                require(path.equals("/admin/relations"),405,"METHOD_NOT_ALLOWED");r.setAttribute("options",adminOptions(c,admin,type.equals("sub-theme-of")?List.of("themes"):List.of("places",type.equals("suitable-for")?"companion-types":"themes")));r.setAttribute("current",from==null&&to==null?Map.of():display(ontology.relationGet(c,type,Input.id(from),Input.id(to))));r.setAttribute("fromKey",Sql.camel(rel.from()));r.setAttribute("toKey",Sql.camel(rel.to()));return page("relations",ontology.relations(c,type));
            }
            default:throw new Problem(404,"NOT_FOUND");
        }
    }
    private static Page builder(Connection c,HttpServletRequest r,String uid,String id) throws Exception {
        var catalog=new CatalogService();var ontology=new OntologyService();
        var merchants=catalog.merchantCards(c);
        var events=EventCatalog.list(null);
        var currentEvents=new ArrayList<Map<String,Object>>();for(Object event:(List<?>)events.get("current"))currentEvents.add(Input.object(event));
        var pastEvents=new ArrayList<Map<String,Object>>();for(Object event:(List<?>)events.get("past"))pastEvents.add(Input.object(event));
        var selectedOrder=new ArrayList<String>();
        var places=ontology.browsePlaces(c);var ordered=new LinkedHashMap<String,Map<String,Object>>();
        var byKey=new HashMap<String,Map<String,Object>>();
        for(var place:places)byKey.put("place:"+place.get("id"),place);
        for(var merchant:merchants)byKey.put("merchant:"+merchant.get("id"),merchant);
        for(var event:currentEvents)byKey.put("event:"+event.get("code"),event);
        for(var event:pastEvents)byKey.put("event:"+event.get("code"),event);
        if(id!=null){
            var draft=new JourneyService().detail(c,uid,id);require(draft.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");r.setAttribute("draft",display(draft));
            for(var saved:(List<?>)draft.get("places")){
                var item=Input.object(saved);String key=(String)item.get("cardKey");selectedOrder.add(key);
                String kind=(String)item.get("cardKind"),source=key.substring(key.indexOf(':')+1);
                boolean savedOnly=!byKey.containsKey(key);
                var card=byKey.getOrDefault(key,new LinkedHashMap<>());
                if(savedOnly){
                    card.put(kind.equals("EVENT")?"code":"id",source);card.put("savedOnly",true);
                    if(!kind.equals("PLACE")){
                        card.put("benefits",List.of());card.put("sources",List.of());
                        card.put("description","현재 공개되지 않은 카드입니다. 담을 당시 정보를 표시합니다.");
                    }
                }
                card.put("selected",true);card.put("name",item.get("placeName"));card.put("regionName",item.get("regionName"));
                card.put("locationDescription",item.get("locationDescription"));card.put("materialKind",item.get("materialKind"));
                switch(kind){
                    case "PLACE"->ordered.put(source,card);
                    case "MERCHANT"->{if(savedOnly)merchants.add(card);}
                    case "EVENT"->{card.put("venue_name",item.get("locationDescription"));if(savedOnly)currentEvents.add(card);}
                }
            }
        }
        String templateCode=param(r,"template");
        if(id==null&&templateCode!=null){
            var template=JourneyTemplates.find(templateCode,places);
            r.setAttribute("builderName",template.get("name"));r.setAttribute("builderTemplate",template.get("name"));
            for(Object code:(List<?>)template.get("placeCodes")){
                var source=places.stream().filter(place->code.equals(place.get("code"))).findFirst().orElseThrow();
                var card=new LinkedHashMap<>(source);card.put("selected",true);
                String placeId=(String)card.get("id");ordered.put(placeId,card);selectedOrder.add("place:"+placeId);
            }
        }
        for(var place:places)ordered.putIfAbsent((String)place.get("id"),place);
        r.setAttribute("journeyStatus","PLANNING");r.setAttribute("merchantCards",merchants);r.setAttribute("options",ontology.options(c));
        r.setAttribute("selectedCardOrder",String.join(",",selectedOrder));
        r.setAttribute("eventData",display(Map.of("current",currentEvents,"past",pastEvents)));return page("journey-builder",new ArrayList<>(ordered.values()));
    }
    private static void journeyOptions(Connection c,HttpServletRequest r,String uid,String role) throws java.sql.SQLException {
        if("VISITOR".equals(role))r.setAttribute("journeys",new JourneyService().list(c,uid,"PLANNING"));
    }
    private static Map<String,Object> annotation(HttpServletRequest r){return form(r,"materialKind","sourceUrl","checkedOn","evidenceNote");}
    // Structured place metadata has its own form controls.
    private static List<String> fields(String collection,boolean create){return AdminService.fields(collection,create).stream().filter(f->!Set.of("annotation","sources").contains(f)).toList();}
    private static Map<String,Object> adminBody(HttpServletRequest r,String collection,boolean create){
        var b=new LinkedHashMap<String,Object>();for(String f:fields(collection,create)){String v=param(r,f);b.put(f,f.endsWith("Won")?v==null||v.isBlank()?null:Input.parseInteger(v):v);}
        if(!create)b.put("active","true".equals(Input.choice(param(r,"active"),"true","false")));
        if(collection.equals("places")){
            b.put("annotation",annotation(r));var sources=new ArrayList<Map<String,Object>>();String[] urls=r.getParameterValues("extraSourceUrl"),dates=r.getParameterValues("extraCheckedOn"),notes=r.getParameterValues("extraEvidenceNote");
            if(urls!=null){require(dates!=null&&notes!=null&&urls.length==dates.length&&urls.length==notes.length,400,"INVALID_INPUT");for(int i=0;i<urls.length;i++)if(!urls[i].isBlank()||!dates[i].isBlank()||!notes[i].isBlank())sources.add(Map.of("sourceUrl",urls[i],"checkedOn",dates[i],"evidenceNote",notes[i]));}b.put("sources",sources);
        }return b;
    }
    private static Map<String,Object> adminOptions(Connection c,AdminService admin,Collection<String> collections) throws Exception {var options=new LinkedHashMap<String,Object>();for(String collection:collections)options.put(collection,admin.list(c,collection,Map.of()));return options;}
}
