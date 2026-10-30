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
import kr.ac.siheung.tourpass.service.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Server-rendered forms share the API's services and transaction boundary. */
public final class WebHandler {
    private static final DateTimeFormatter KST=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '한국 시간'").withZone(ZoneId.of("Asia/Seoul"));
    private static final Map<String,String> FIELD_LABELS=Map.ofEntries(
        Map.entry("code","고유 코드"),
        Map.entry("name","이름"),
        Map.entry("description","소개·내용"),
        Map.entry("locationDescription","위치 설명 · 가맹점에는 ‘가상’ 포함"),
        Map.entry("reservationNote","예약 조건"),
        Map.entry("benefitType","혜택 유형"),
        Map.entry("basePriceWon","기준 금액 · 무료 혜택이면 비움"),
        Map.entry("discountWon","할인 금액 · 무료 혜택이면 비움"),
        Map.entry("payableWon","본인 부담 금액"),
        Map.entry("placeId","연결 장소"),
        Map.entry("regionId","지역"),
        Map.entry("productId","상품"),
        Map.entry("merchantId","가맹점")
    );
    public record Page(String view,Object data,String redirect) {}
    private static Page page(String view,Object data){return new Page(view,data,null);}
    private static Page redirect(String path){return new Page(null,null,path);}
    @FunctionalInterface public interface Action { Page run(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception; }
    public void handle(HttpServletRequest r,HttpServletResponse out,Action action) throws IOException,ServletException {
        try{
            var s=r.getSession();
            synchronized(s){
                Page result=Database.transaction(r.getMethod().equals("POST"),c->{
                    var user=SecurityFilter.authorize(c,r);
                    require(Set.of("GET","POST").contains(r.getMethod()),405,"METHOD_NOT_ALLOWED");
                    return action.run(c,r,user);
                });
                // Session changes happen only after the database transaction commits.
                if(r.getServletPath().equals("/login")&&r.getMethod().equals("POST")){
                    var user=Input.object(result.data());SessionState.login(r,user);
                    result=redirect(loginDestination(user));
                }else if(r.getServletPath().equals("/logout"))s.invalidate();
                else if(result.data() instanceof RedemptionService.Lookup lookup){s.setAttribute("confirmation",lookup.confirmation());result=page("confirm",lookup.response());}
                else if(r.getServletPath().equals("/merchant/redeem/confirm"))s.setAttribute("confirmation",((RedemptionService.Confirmation)s.getAttribute("confirmation")).consume());
                if("photo".equals(result.view())){out.setContentType("image/jpeg");out.getOutputStream().write((byte[])result.data());return;}
                if(result.view()!=null&&result.view().equals("json")){ApiHandler.json(out,result.data());return;}
                if(result.redirect()!=null){out.setStatus(303);out.setHeader("Location",r.getContextPath()+result.redirect());return;}
                r.setAttribute("view",result.view());r.setAttribute("data",display(result.data()));r.setAttribute("csrf",s.getAttribute("csrf"));
                out.setContentType("text/html;charset=UTF-8");r.getRequestDispatcher("/WEB-INF/views/page.jsp").forward(r,out);
            }
        }catch(Problem e){error(r,out,e);}catch(Exception e){r.getServletContext().log("Request failed: "+r.getServletPath(),e);error(r,out,new Problem(500,"INTERNAL_ERROR"));}
    }
    public void missing(HttpServletRequest r,HttpServletResponse out) throws IOException,ServletException {
        try{Database.transaction(c->SecurityFilter.authorize(c,r));out.sendError(404);}
        catch(Problem e){error(r,out,e);}
    }
    public static void error(HttpServletRequest r,HttpServletResponse out,Problem e) throws IOException,ServletException {
        if(e.code.equals("AUTH_REQUIRED")){out.setStatus(303);out.setHeader("Location",r.getContextPath()+"/login");return;}
        r.setAttribute("view",r.getServletPath().equals("/login") && Set.of("INVALID_CREDENTIALS","INVALID_INPUT","CSRF_INVALID").contains(e.code)?"login":"error");r.setAttribute("message",message(e.code));r.setAttribute("errorCode",e.code);r.setAttribute("csrf",r.getSession().getAttribute("csrf"));out.setStatus(e.status);out.setContentType("text/html;charset=UTF-8");r.getRequestDispatcher("/WEB-INF/views/page.jsp").forward(r,out);
    }
    public static String message(String code){return switch(code){
        case "INVALID_PHOTO"->"JPEG·PNG 사진을 선택해 주세요. 8MB 이하, 1,600만 화소까지 저장할 수 있습니다.";
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
        case "JOURNEY_NOT_COMPLETED"->"사용 완료한 여행에서만 포토카드를 확인할 수 있습니다.";
        case "JOURNEY_LOCKED"->"제작 중인 패스만 구성을 바꾸거나 삭제할 수 있습니다.";
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
    private static String param(HttpServletRequest r,String name){return Input.param(values(r,name));}
    private static List<String> values(HttpServletRequest r,String name){String[] values=r.getParameterValues(name);return values==null?null:Arrays.asList(values);}
    private static String id(HttpServletRequest r){return Input.id(param(r,"id"));}
    private static Map<String,List<String>> query(HttpServletRequest r){var q=new LinkedHashMap<String,List<String>>();r.getParameterMap().forEach((k,v)->{if(!v[0].isBlank())q.put(k,Arrays.asList(v));});return q;}
    private static Map<String,Object> form(HttpServletRequest r,String... fields){var b=new LinkedHashMap<String,Object>();for(String f:fields)b.put(f,param(r,f));return b;}
    private static void method(HttpServletRequest r,String expected){require(r.getMethod().equals(expected),405,"METHOD_NOT_ALLOWED");}
    private static String userId(Map<String,Object> user){return user==null?null:(String)user.get("id");}
    private static String role(Map<String,Object> user){return user==null?null:(String)user.get("role");}
    private final CatalogService catalog=new CatalogService();
    private final PassService passes=new PassService();
    private final RedemptionService redemption=new RedemptionService();
    private final FeedbackService feedback=new FeedbackService();
    private final PlaceFeedbackService placeFeedback=new PlaceFeedbackService();
    private final OntologyService ontology=new OntologyService();
    private final AdminService admin=new AdminService();

    private static String loginDestination(Map<String,Object> user){return "STAFF".equals(role(user))?"/merchant/redeem":"/";}

    public Page login(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        boolean post=r.getMethod().equals("POST");
        if(!post && user!=null)return redirect(loginDestination(user));
        return post?page("login",new AuthService().login(c,form(r,"loginId","password"))):page("login",null);
    }
    public Page logout(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"POST");require(user!=null,401,"AUTH_REQUIRED");return redirect("/");
    }
    public Page home(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");
        if("ADMIN".equals(role(user)))return page("home",adminOverview(c));
        r.setAttribute("homePhoto",Map.of("materialKind","REAL","image",PlaceImages.random()));
        if("VISITOR".equals(role(user))) {
            var journeys=new JourneyService().list(c,(String)user.get("id"),null);
            var drafts=journeys.stream().filter(j->"PLANNING".equals(j.get("status"))).toList();
            r.setAttribute("homeDraftCount",drafts.size());
            if(!drafts.isEmpty())r.setAttribute("homeLatestDraft",drafts.get(0));
            r.setAttribute("journeyTemplates",JourneyTemplates.list(ontology.browsePlaces(c)));
            r.setAttribute("homePassCount",journeys.stream().filter(j->"CONFIRMED".equals(j.get("status"))).count());
        }
        return page(user==null?"landing":"home",null);
    }
    public Page profile(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");return page("profile",null);
    }
    public Page catalog(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        var q=query(r);
        method(r,"GET");return page("catalog",Map.of("products",catalog.list(c,"products",null),"merchants",catalog.list(c,"merchants",Input.param(q.get("regionId")))));
    }
    public Page product(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");
        if("VISITOR".equals(role(user))){
            var active=passes.list(c,userId(user),Map.of("status",List.of("ACTIVE")));
            if(!active.isEmpty())r.setAttribute("activeBenefitPass",display(active.get(0)));
        }
        return page("product",catalog.product(c,id(r)));
    }
    public Page merchant(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");return page("merchant",catalog.merchant(c,id(r)));
    }
    public Page place(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        String role=role(user);
        method(r,"GET");journeyOptions(c,r,uid,role);var place=ontology.place(c,id(r));
        if("VISITOR".equals(role))r.setAttribute("placeFeedback",placeFeedback.current(c,uid,id(r)));
        return page("place",place);
    }
    public Page recommendations(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        String role=role(user);
        var q=query(r);
        method(r,"GET");
        var recommendations=new RecommendationService().recommend(c,uid,role,q);
        if("application/json".equals(r.getHeader("Accept")))return page("json",recommendations);
        journeyOptions(c,r,uid,role);r.setAttribute("options",ontology.options(c));r.setAttribute("selectedThemeIds",new HashSet<>(Objects.requireNonNullElse(values(r,"themeId"),List.of())));return page("recommendations",recommendations);
    }
    public Page journeys(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");String status=param(r,"status");if(status==null)status="PLANNING";
        if(status.equals("PLANNING"))r.setAttribute("journeyTemplates",JourneyTemplates.list(ontology.browsePlaces(c)));
        var journeys=new JourneyService().list(c,uid,status);journeyCovers(c,journeys);
        r.setAttribute("journeyStatus",status);r.setAttribute("journeyText",journeyText(status));return page("journeys",journeys);
    }
    public Page journeyBuilder(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");return builder(c,r,userId(user),r.getServletPath().equals("/journeys/edit")?id(r):null);
    }
    public Page journey(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");String journeyId=id(r);var journey=new JourneyService().detail(c,uid,journeyId);
        if(journey.get("status").equals("PLANNING"))return redirect("/journeys/edit?id="+journeyId);
        journeyCovers(c,List.of(journey));
        r.setAttribute("journeyStatus",journey.get("status"));r.setAttribute("benefitPasses",passes.list(c,uid,Map.of()));return page("journey",journey);
    }
    public Page saveDraft(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath();
        String uid=userId(user);
        method(r,"POST");String intent=param(r,"intent");if(intent==null)intent="draft";
        Input.choice(intent,"draft","confirm");String journeyId=path.endsWith("update")?id(r):null;
        var cards=values(r,"cardKey");
        var journey=new JourneyService().saveDraft(c,uid,journeyId,form(r,"name","travelDate"),
            cards!=null?cards:values(r,"placeId"),intent.equals("confirm"));
        return redirect("/journeys/"+(journey.get("status").equals("PLANNING")?"edit":"detail")+"?id="+journey.get("id"));
    }
    public Page deleteJourney(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");new JourneyService().delete(c,uid,id(r));return redirect("/journeys");
    }
    public Page saveJourney(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");var b=form(r,"name","travelDate","status","issuedPassId");b.keySet().removeIf(key->!r.getParameterMap().containsKey(key));if("".equals(b.get("issuedPassId")))b.put("issuedPassId",null);
        String journeyId=id(r);
        var journey=new JourneyService().save(c,uid,journeyId,b);return redirect(b.containsKey("status")?"/journeys?status="+journey.get("status"):"/journeys/detail?id="+journeyId);
    }
    public Page addPlace(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");String journeyId=id(r);new JourneyService().add(c,uid,journeyId,form(r,"placeId"));return redirect("/journeys/detail?id="+journeyId);
    }
    public Page journeyItem(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath();
        String uid=userId(user);
        method(r,"POST");String action=path.endsWith("record")?"record":path.equals("/journeys/move")?"move":"delete";Map<String,Object> b;
        if(action.equals("record")){b=form(r,"visitedOn","note");String rating=param(r,"rating");b.put("rating",rating==null||rating.isBlank()?null:Input.parseInteger(rating));}
        else b=action.equals("move")?form(r,"direction"):Map.of();
        String journeyId=id(r);
        String itemId=Input.id(param(r,"itemId"));
        new JourneyService().item(c,uid,journeyId,itemId,action,b);return redirect("/journeys/detail?id="+journeyId+(action.equals("record")?"#record-"+itemId:""));
    }
    public Page passes(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        var q=query(r);
        method(r,"GET");return page("passes",passes.list(c,uid,q));
    }
    public Page passDetail(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");return page("pass",passes.detail(c,uid,id(r),false));
    }
    public Page issuePass(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");return redirect("/passes/detail?id="+passes.issue(c,uid,form(r,"productId")).get("id"));
    }
    public Page history(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");return page("history",redemption.history(c,uid,false,null));
    }
    public Page savePlaceFeedback(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"POST");String place=id(r);
        boolean delete=r.getServletPath().endsWith("delete");
        if(delete)placeFeedback.delete(c,userId(user),place);
        else {var body=form(r,"comment");body.put("rating",Input.parseInteger(param(r,"rating")));placeFeedback.save(c,userId(user),place,body);}
        return redirect("/cards/detail?key=place:"+place+"&feedback="+(delete?"deleted":"saved")+"#place-feedback");
    }
    public Page placeFeedbackStatistics(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");return page("place-feedback-statistics",placeFeedback.statistics(c));
    }
    public Page editFeedback(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");String rid=id(r);var history=redemption.detail(c,uid,rid,false);
        var existing=Boolean.TRUE.equals(history.get("hasFeedback"))?feedback.get(c,uid,rid):Map.of();
        r.setAttribute("feedbackReasons",FeedbackService.REASONS);
        return page("feedback",Map.of("redemption",history,"feedback",existing));
    }
    public Page saveFeedback(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath();
        String uid=userId(user);
        method(r,"POST");String rid=id(r);if(path.endsWith("delete"))feedback.delete(c,uid,rid);else{
            var b=form(r,"comment");b.put("rating",Input.parseInteger(param(r,"rating")));b.put("reasons",Objects.requireNonNullElse(values(r,"reasons"),List.of()));feedback.save(c,uid,rid,b,path.endsWith("create"));
        }return redirect("/feedback/history");
    }
    public Page redeem(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");return page("redeem",redemption.assignments(c,uid));
    }
    public Page lookup(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");return page("confirm",redemption.lookup(c,uid,form(r,"merchantId","code")));
    }
    public Page confirm(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");redemption.confirm(c,uid,(RedemptionService.Confirmation)r.getSession().getAttribute("confirmation"),form(r,"confirmationToken","issuedBenefitId"));return redirect("/merchant/history");
    }
    public Page merchantHistory(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"GET");var assignments=redemption.assignments(c,uid);r.setAttribute("assignments",assignments);String merchant=param(r,"merchantId");if(merchant==null&&!assignments.isEmpty())merchant=(String)assignments.get(0).get("merchantId");return page("history",merchant==null?List.of():redemption.history(c,uid,true,merchant));
    }
    public Page admin(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");r.setAttribute("adminCards",display(catalog.cards(c)));return page("admin",adminOverview(c));
    }
    private Map<String,Object> adminOverview(Connection c) throws Exception {
        var places=admin.list(c,"places",Map.of());
        var events=EventCatalog.list(null);
        return Map.of("places",places.size(),"pending",places.stream().filter(p->"DRAFT".equals(p.get("reviewStatus"))&&Boolean.TRUE.equals(p.get("active"))).count(),
            "events",events.current().size()+events.past().size(),"dining",catalog.diningCards(c).size());
    }
    public Page journeyPhotocards(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");
        var journey=new JourneyService().photocards(c,(String)user.get("id"),id(r));
        var photoCards=new ArrayList<Map<String,Object>>();
        for(var item:(List<Map<String,Object>>)journey.get("places")){
            if(!Boolean.TRUE.equals(item.get("hasPhoto")))continue;
            var card=new LinkedHashMap<String,Object>();card.put("name",item.get("placeName"));card.put("kindLabel","여행 기록");
            card.put("visitedOn",item.get("visitedOn"));card.put("credit","직접 촬영한 사진");
            card.put("image",Map.of("path","/journeys/photo?id="+journey.get("id")+"&itemId="+item.get("id"),"alt",item.get("placeName")));
            photoCards.add(card);
        }
        r.setAttribute("photoCards",photoCards);
        r.setAttribute("journeyStatus","ARCHIVED");
        return page("journey-photocards",journey);
    }
    public Page journeyPhoto(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        var photos=new JourneyPhotoService();String journey=id(r),item=Input.id(param(r,"itemId")),uid=userId(user);
        if(r.getMethod().equals("GET"))return page("photo",photos.image(c,uid,journey,item));
        if("delete".equals(param(r,"intent")))photos.delete(c,uid,journey,item);
        else {
            var photo=r.getPart("photo");require(photo!=null&&photo.getSize()>0&&photo.getSize()<=JourneyPhotoService.MAX_BYTES,400,"INVALID_PHOTO");
            try(var input=photo.getInputStream()){photos.save(c,uid,journey,item,input.readNBytes(JourneyPhotoService.MAX_BYTES+1));}
        }
        return redirect("/journeys/detail?id="+journey+"#record-"+item);
    }
    public Page goods(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        var cards=catalog.cards(c);
        if(param(r,"key")==null)return redirect("/admin");
        var card=GoodsService.find(cards,param(r,"key"));
        r.setAttribute("selectedCard",display(card));
        if(r.getMethod().equals("POST")){
            var preview=GoodsService.preview(card,param(r,"baseUrl"),param(r,"caption"),param(r,"copies"));
            r.setAttribute("preview",display(preview));
            r.setAttribute("goodsBase",preview.get("baseUrl"));
        }
        return page("goods",null);
    }
    public Page publicCard(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");var card=GoodsService.find(catalog.cards(c),param(r,"key"));
        if("place".equals(card.get("kind"))&&"VISITOR".equals(role(user)))r.setAttribute("placeFeedback",placeFeedback.current(c,userId(user),(String)card.get("id")));
        return page("public-card",card);
    }
    public Page adminPasses(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        var q=query(r);
        method(r,"GET");return page("passes",passes.list(c,null,q));
    }
    public Page adminPass(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        method(r,"GET");return page("pass",passes.detail(c,null,id(r),true));
    }
    public Page cancelPass(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String uid=userId(user);
        method(r,"POST");String passId=id(r);passes.cancel(c,uid,passId,form(r,"reason"));return redirect("/admin/passes/detail?id="+passId);
    }
    public Page editor(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath();
        String uid=userId(user);
        boolean post=r.getMethod().equals("POST");
        var q=query(r);
        String collection=param(r,"collection");boolean semantic=path.startsWith("/admin/ontology");
        require((semantic?Set.of("places","regions","themes","companion-types"):Set.of("products","merchants","benefits")).contains(collection==null?"":collection),404,"NOT_FOUND");
        String raw=param(r,"id"),item=raw==null||raw.isBlank()?null:Input.id(raw);r.setAttribute("collection",collection);r.setAttribute("semantic",semantic);
        r.setAttribute("collectionLabel",switch(collection){case "products"->"상품";case "merchants"->"가맹점";case "benefits"->"혜택";case "places"->"관광지·검토";case "regions"->"지역";case "themes"->"테마";default->"동반 유형";});
        if(post){require(!path.equals("/admin/catalog")&&!path.equals("/admin/ontology"),405,"METHOD_NOT_ALLOWED");Map<String,Object> result;
            if(path.endsWith("review")){require(collection.equals("places")&&item!=null,400,"INVALID_INPUT");result=ontology.reviewPlace(c,uid,item,form(r,"status"));}
            else if(path.endsWith("deactivate")){require(item!=null,400,"INVALID_INPUT");result=admin.save(c,collection,item,Map.of("active",false));}
            else result=admin.save(c,collection,item,adminBody(r,collection,item==null));
            return redirect((semantic?"/admin/ontology":"/admin/catalog")+"?collection="+collection+"&id="+result.get("id")+"&saved=1");
        }
        require(path.equals("/admin/catalog")||path.equals("/admin/ontology"),405,"METHOD_NOT_ALLOWED");
        var current=item==null?Map.<String,Object>of():admin.get(c,collection,item);r.setAttribute("current",display(current));var editorFields=fields(collection,item==null);r.setAttribute("fields",editorFields);r.setAttribute("fieldLabels",FIELD_LABELS);var options=new LinkedHashMap<String,Object>();
        for(String field:editorFields){
            String target=switch(field){case "placeId"->"places";case "regionId"->"regions";case "productId"->"products";case "merchantId"->"merchants";default->null;};
            if(target!=null)options.put(field,admin.list(c,target,Map.of()));
        }
        r.setAttribute("options",options);
        var records=admin.list(c,collection,q);
        if(collection.equals("places"))records.forEach(PlaceImages::attach);
        if(!semantic){
            if(!collection.equals("benefits")){
                var linked=new HashMap<Object,List<Map<String,Object>>>();
                for(var benefit:admin.list(c,"benefits",Map.of()))
                    linked.computeIfAbsent(benefit.get(collection.equals("products")?"productId":"merchantId"),key->new ArrayList<>()).add(benefit);
                r.setAttribute("catalogBenefits",display(linked));
            }
            var merchants=new LinkedHashMap<Object,Object>();
            for(var merchant:admin.list(c,"merchants",Map.of()))merchants.put(merchant.get("id"),merchant.get("name"));
            r.setAttribute("merchantNames",merchants);
        }
        return page("editor",records);
    }
    public Page relations(Connection c,HttpServletRequest r,Map<String,Object> user) throws Exception {
        String path=r.getServletPath();
        String uid=userId(user);
        boolean post=r.getMethod().equals("POST");
        String type=param(r,"type");if(type==null)type="has-theme";var fields=OntologyService.relationFields(type);String from=param(r,"fromId"),to=param(r,"toId");
        r.setAttribute("relationType",type);
        if(post){from=Input.id(from);to=Input.id(to);
            if(path.endsWith("review"))ontology.reviewRelation(c,uid,type,from,to,form(r,"status"));
            else {require(path.endsWith("save")||path.endsWith("deactivate"),405,"METHOD_NOT_ALLOWED");boolean create="create".equals(param(r,"operation"));ontology.relationSave(c,type,from,to,path.endsWith("deactivate")?Map.of("active",false):Map.of("annotation",annotation(r)),create);}
            return redirect("/admin/relations?type="+type+"&fromId="+from+"&toId="+to);
        }
        require(path.equals("/admin/relations"),405,"METHOD_NOT_ALLOWED");
        String fromCollection=type.equals("sub-theme-of")?"themes":"places",toCollection=type.equals("suitable-for")?"companion-types":"themes";
        var options=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String collection:new LinkedHashSet<>(List.of(fromCollection,toCollection)))options.put(collection,admin.list(c,collection,Map.of()));
        r.setAttribute("options",options);r.setAttribute("current",from==null&&to==null?Map.of():display(ontology.relationGet(c,type,Input.id(from),Input.id(to))));
        r.setAttribute("fromKey",fields.get("fromKey"));r.setAttribute("toKey",fields.get("toKey"));
        var names=new HashMap<String,Map<Object,Object>>();
        for(var entry:options.entrySet()){
            var labels=new HashMap<Object,Object>();
            for(var item:entry.getValue())labels.put(item.get("id"),item.get("name"));
            names.put(entry.getKey(),labels);
        }
        var relations=ontology.relations(c,type);
        for(var item:relations){item.put("fromName",names.get(fromCollection).get(item.get(fields.get("fromKey"))));item.put("toName",names.get(toCollection).get(item.get(fields.get("toKey"))));}
        return page("relations",relations);
    }
    private static Page builder(Connection c,HttpServletRequest r,String uid,String id) throws Exception {
        var ontology=new OntologyService();
        var cards=new CatalogService().cards(c);
        var places=cards.stream().filter(card->"place".equals(card.get("kind"))).toList();
        var selectedOrder=new ArrayList<String>();
        var byKey=new LinkedHashMap<String,Map<String,Object>>();
        for(var card:cards)byKey.put((String)card.get("key"),card);
        if(id!=null){
            var draft=new JourneyService().cards(c,uid,id);require(draft.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");r.setAttribute("draft",display(draft));
            for(var saved:(List<?>)draft.get("places")){
                var item=Input.object(saved);String key=(String)item.get("cardKey");selectedOrder.add(key);
                String kind=(String)item.get("cardKind"),source=JourneyService.CardKey.parse(key).source();
                boolean savedOnly=!byKey.containsKey(key);
                var card=byKey.computeIfAbsent(key,k->new LinkedHashMap<>());
                if(savedOnly){
                    card.put(Set.of("EVENT","DINING").contains(kind)?"code":"id",source);card.put("savedOnly",true);
                    if(!kind.equals("PLACE")){
                        card.put("benefits",List.of());card.put("sources",List.of());
                        card.put("description","현재 공개되지 않은 카드입니다. 담을 당시 정보를 표시합니다.");
                    }
                }
                card.put("key",key);card.put("kind",kind.toLowerCase(Locale.ROOT));
                card.put("selected",true);card.put("name",item.get("placeName"));card.put("regionName",item.get("regionName"));
                card.put("locationDescription",item.get("locationDescription"));card.put("materialKind",item.get("materialKind"));
            }
        }
        String templateCode=param(r,"template");
        if(id==null&&templateCode!=null){
            var template=JourneyTemplates.find(templateCode,places);
            r.setAttribute("builderName",template.get("name"));r.setAttribute("builderTemplate",template.get("name"));
            var byCode=JourneyTemplates.index(places);
            for(Object code:(List<?>)template.get("placeCodes")){
                var card=byCode.get(code);card.put("selected",true);
                selectedOrder.add((String)card.get("key"));
            }
        }
        var order=new LinkedHashSet<String>();selectedOrder.stream().filter(key->key.startsWith("place:")).forEach(order::add);order.addAll(byKey.keySet());
        r.setAttribute("journeyStatus","PLANNING");r.setAttribute("options",ontology.options(c));
        r.setAttribute("selectedCardOrder",String.join(",",selectedOrder));
        return page("journey-builder",order.stream().map(byKey::get).sorted(Comparator.comparingInt(card->switch((String)card.get("kind")){
            case "place"->0;case "merchant","dining"->3;default->"일정 종료".equals(card.get("scheduleLabel"))?2:1;
        })).toList());
    }
    private static Map<String,String> journeyText(String status){
        return switch(status){
            case "CONFIRMED"->Map.of("title","내 패스","intro","제작을 확정한 패스입니다. 여행을 마치면 사용 완료로 보관하세요.","section","제작 확정","empty","제작을 확정한 패스가 없습니다","emptyHint","여행 만들기에서 카드를 골라 임시 저장하거나 제작을 확정해 보세요.");
            case "ARCHIVED"->Map.of("title","여행 기록","intro","사용을 마친 패스와 여행 기록을 아카이브에 보관합니다.","section","아카이브","empty","사용 완료한 패스가 없습니다","emptyHint","확정한 패스에서 사용 완료를 누르면 여기에 보관됩니다.");
            default->Map.of("title","여행 만들기","intro","카드를 탐색해 새 패스를 제작하거나 임시 저장한 패스를 이어 만드세요.","section","제작 중","empty","제작 중인 패스가 없습니다","emptyHint","여행 만들기에서 카드를 골라 임시 저장하거나 제작을 확정해 보세요.");
        };
    }
    private static void journeyCovers(Connection c,List<Map<String,Object>> journeys) throws java.sql.SQLException {
        if(journeys.isEmpty())return;
        var byId=new HashMap<Object,Map<String,Object>>();journeys.forEach(journey->byId.put(journey.get("id"),journey));
        var cards=new JourneyService().coverCards(c,journeys.stream().map(journey->(String)journey.get("id")).toList());
        for(var card:cards){
            var journey=byId.get(card.get("journeyId"));
            if(journey.containsKey("coverImage"))continue;
            Map<String,Object> source=null;
            if("PLACE".equals(card.get("cardKind"))&&card.get("code")!=null)source=PlaceImages.attach(card);
            else if("EVENT".equals(card.get("cardKind")))source=EventCatalog.find(JourneyService.CardKey.parse(card.get("cardKey")).source());
            if(source!=null&&source.get("image")!=null){
                journey.put("coverImage",source.get("image"));journey.put("coverKind",card.get("cardKind"));
            }
        }
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
}
