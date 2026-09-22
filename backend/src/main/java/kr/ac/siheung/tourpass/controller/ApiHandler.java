package kr.ac.siheung.tourpass.controller;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.StreamReadFeature;
import kr.ac.siheung.tourpass.config.Database;
import kr.ac.siheung.tourpass.service.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class ApiHandler {
    private static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private record Reply(int status,Object body,String location){}
    private static Reply ok(Object body){return new Reply(200,body,null);}
    private static Reply created(Object body,String path){return new Reply(201,body,path);}
    public void handle(HttpServletRequest r,HttpServletResponse out) throws IOException {
        try {
            String path=r.getServletPath().substring("/api/v1".length());require(!path.isEmpty(),404,"NOT_FOUND");String[] p=path.substring(1).split("/");String method=r.getMethod();
            require(Set.of("GET","POST","PUT","PATCH","DELETE").contains(method),405,"METHOD_NOT_ALLOWED");
            Map<String,Object> body=body(r);if(method.equals("GET"))Input.fields(body,"","");Map<String,List<String>> q=new LinkedHashMap<>();r.getParameterMap().forEach((k,v)->q.put(k,Arrays.asList(v)));
            var session=r.getSession();
            synchronized(session){
                String userId=(String)session.getAttribute("userId");var confirmation=(RedemptionService.Confirmation)session.getAttribute("confirmation");
                String role=AuthService.requiredRole(r.getServletPath());
                Reply reply=Database.transaction(!method.equals("GET"),c->{
                    var user=new AuthService().current(c,userId,role);
                    return dispatch(c,p,method,body,q,user,confirmation,session);
                });
                if(path.equals("/session")&&method.equals("POST")){
                    @SuppressWarnings("unchecked") var u=(Map<String,Object>)reply.body();r.changeSessionId();session.setAttribute("userId",u.get("id"));session.setAttribute("csrf",AuthService.token());session.removeAttribute("confirmation");reply=ok(sessionResponse(u,session));
                }else if(path.equals("/session")&&method.equals("GET")){
                    @SuppressWarnings("unchecked") var u=(Map<String,Object>)reply.body();reply=ok(sessionResponse(u,session));
                }else if(path.equals("/session")&&method.equals("DELETE"))session.invalidate();
                else if(path.equals("/merchant/pass-lookups")&&reply.body() instanceof RedemptionService.Lookup lookup){session.setAttribute("confirmation",lookup.confirmation());reply=ok(lookup.response());}
                else if(path.equals("/merchant/redemptions")&&method.equals("POST"))session.setAttribute("confirmation",confirmation.consume());
                out.setStatus(reply.status());if(reply.location()!=null)out.setHeader("Location",r.getContextPath()+"/api/v1"+reply.location());
                if(reply.status()!=204){out.setContentType("application/json");out.getWriter().write(JSON.writeValueAsString(reply.body()));}
            }
        }catch(Problem e){error(out,e);}catch(Exception e){error(out,new Problem(500,"INTERNAL_ERROR"));}
    }
    private static Map<String,Object> sessionResponse(Map<String,Object> u,HttpSession s){var m=new LinkedHashMap<String,Object>();m.put("authenticated",u!=null);m.put("user",u);m.put("csrfToken",s.getAttribute("csrf"));return m;}
    private static Map<String,Object> body(HttpServletRequest r) throws IOException {
        if(r.getContentLengthLong()==0||r.getContentLengthLong()==-1&&r.getHeader("Transfer-Encoding")==null)return new LinkedHashMap<>();
        require(r.getContentType()!=null&&r.getContentType().split(";")[0].strip().equalsIgnoreCase("application/json"),415,"UNSUPPORTED_MEDIA_TYPE");
        byte[] raw=r.getInputStream().readNBytes(65537);require(raw.length<=65536,400,"INVALID_INPUT");
        try{return Input.object(JSON.readValue(raw,Object.class));}catch(com.fasterxml.jackson.core.JacksonException e){throw new Problem(400,"INVALID_INPUT");}
    }
    public static void error(HttpServletResponse out,Problem e) throws IOException {out.setStatus(e.status);out.setContentType("application/json;charset=UTF-8");String message=switch(e.code){case "BENEFIT_ALREADY_USED"->"이미 사용한 혜택입니다.";case "INVALID_INPUT"->"입력값을 확인해 주세요.";case "INTERNAL_ERROR"->"요청을 처리하지 못했습니다.";default->e.code;};JSON.writeValue(out.getWriter(),Map.of("error",Map.of("code",e.code,"message",message,"fieldErrors",List.of())));}
    private Reply dispatch(Connection c,String[] p,String method,Map<String,Object> b,Map<String,List<String>> q,Map<String,Object> user,RedemptionService.Confirmation confirmation,HttpSession session) throws Exception {
        String path="/"+String.join("/",p),uid=user==null?null:(String)user.get("id"),role=user==null?null:(String)user.get("role");
        var catalog=new CatalogService();var passes=new PassService();var redemption=new RedemptionService();var feedback=new FeedbackService();var ontology=new OntologyService();var admin=new AdminService();
        if(path.equals("/session")){return switch(method){case "GET"->ok(user);case "POST"->ok(new AuthService().login(c,b));case "DELETE"->{require(user!=null,401,"AUTH_REQUIRED");Input.fields(b,"","");yield new Reply(204,null,null);}default->throw new Problem(405,"METHOD_NOT_ALLOWED");};}
        if(p[0].equals("products")||p[0].equals("merchants")){
            require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");require(p.length<=2,404,"NOT_FOUND");
            if(p.length==2)return ok(p[0].equals("products")?catalog.product(c,Input.id(p[1])):catalog.merchant(c,Input.id(p[1])));
            return ok(CatalogService.page(catalog.list(c,p[0],CatalogService.param(q,"regionId")),q));
        }
        if(p[0].equals("places")&&p.length==2){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(ontology.place(c,Input.id(p[1])));}
        if(path.equals("/recommendation-options")){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(ontology.options(c));}
        if(path.equals("/recommendations")){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(new RecommendationService().recommend(c,uid,role,q));}
        if(p[0].equals("me")&&p.length>=2){
            if(p[1].equals("journeys")){
                var journeys=new JourneyService();
                if(p.length==2){
                    if(method.equals("POST")){var j=journeys.create(c,uid,b);return created(j,path+"/"+j.get("id"));}
                    require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(CatalogService.page(journeys.list(c,uid,CatalogService.param(q,"status")),q));
                }
                String jid=Input.id(p[2]);
                if(p.length==3){if(method.equals("GET"))return ok(journeys.detail(c,uid,jid));require(method.equals("PATCH"),405,"METHOD_NOT_ALLOWED");return ok(journeys.save(c,uid,jid,b));}
                if(p[3].equals("places")){
                    if(p.length==4){require(method.equals("POST"),405,"METHOD_NOT_ALLOWED");return ok(journeys.add(c,uid,jid,b));}
                    String item=Input.id(p[4]);
                    if(p.length==5){require(method.equals("DELETE"),405,"METHOD_NOT_ALLOWED");return ok(journeys.item(c,uid,jid,item,"delete",b));}
                    if(p.length==6&&Set.of("record","move").contains(p[5])){require(method.equals("PUT"),405,"METHOD_NOT_ALLOWED");return ok(journeys.item(c,uid,jid,item,p[5],b));}
                }
                throw new Problem(404,"NOT_FOUND");
            }
            if(p[1].equals("passes")){
                if(p.length==2){if(method.equals("POST")){var pass=passes.issue(c,uid,b);return created(pass,"/me/passes/"+pass.get("id"));}require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(CatalogService.page(passes.list(c,uid,false,q),q));}
                if(p.length==3){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(passes.detail(c,uid,Input.id(p[2]),false));}
            }
            if(p[1].equals("redemptions")){
                if(p.length==2){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(CatalogService.page(redemption.history(c,uid,false,CatalogService.param(q,"passId")),q));}
                String rid=Input.id(p[2]);if(p.length==3){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(redemption.detail(c,uid,rid,false));}
                if(p.length==4&&p[3].equals("feedback"))return switch(method){case "GET"->ok(feedback.get(c,uid,rid));case "POST"->created(feedback.save(c,uid,rid,b,true),path);case "PUT"->ok(feedback.save(c,uid,rid,b,false));case "DELETE"->{Input.fields(b,"","");feedback.delete(c,uid,rid);yield new Reply(204,null,null);}default->throw new Problem(405,"METHOD_NOT_ALLOWED");};
            }
        }
        if(p[0].equals("merchant")){
            if(path.equals("/merchant/assignments")){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(redemption.assignments(c,uid));}
            if(path.equals("/merchant/pass-lookups")){require(method.equals("POST"),405,"METHOD_NOT_ALLOWED");return ok(redemption.lookup(c,uid,b));}
            if(path.equals("/merchant/redemptions")){if(method.equals("POST")){var rr=redemption.confirm(c,uid,confirmation,b);return created(rr,"/merchant/redemptions/"+rr.get("id"));}require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(CatalogService.page(redemption.history(c,uid,true,CatalogService.param(q,"merchantId")),q));}
            if(p.length==3&&p[1].equals("redemptions")){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(redemption.detail(c,uid,Input.id(p[2]),true));}
        }
        if(p[0].equals("admin")&&p.length>=2){
            if(p[1].equals("passes")){
                if(p.length==2){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");String owner=CatalogService.param(q,"ownerId");if(owner!=null)Input.id(owner);return ok(CatalogService.page(passes.list(c,owner,true,q),q));}
                if(p.length==3){require(method.equals("GET"),405,"METHOD_NOT_ALLOWED");return ok(passes.detail(c,null,Input.id(p[2]),true));}
                if(p.length==4&&p[3].equals("cancellation")){require(method.equals("PUT"),405,"METHOD_NOT_ALLOWED");return ok(passes.cancel(c,uid,Input.id(p[2]),b));}
            }
            if(Set.of("products","merchants","benefits").contains(p[1])&&p.length<=3)return resource(c,admin,p[1],p.length==3?Input.id(p[2]):null,method,b,q,path);
            if(p[1].equals("ontology"))return ontologyRoute(c,p,method,b,q,uid,path,admin,ontology);
        }
        throw new Problem(404,"NOT_FOUND");
    }
    private Reply resource(Connection c,AdminService admin,String collection,String id,String method,Map<String,Object> body,Map<String,List<String>> q,String path) throws SQLException {
        if(method.equals("GET")){if(id==null)return ok(CatalogService.page(admin.list(c,collection,q),q));return ok(admin.get(c,collection,id));}
        require(id==null?method.equals("POST"):method.equals("PATCH"),405,"METHOD_NOT_ALLOWED");var result=admin.save(c,collection,id,body);return id==null?created(result,path+"/"+result.get("id")):ok(result);
    }
    private Reply ontologyRoute(Connection c,String[] p,String method,Map<String,Object> b,Map<String,List<String>> q,String user,String path,AdminService admin,OntologyService ontology) throws SQLException {
        require(p.length>=3,404,"NOT_FOUND");
        if(p[2].equals("relations")){
            if(p.length==3){if(method.equals("GET")){String type=CatalogService.param(q,"type");OntologyService.relation(type==null?"":type);return ok(CatalogService.page(ontology.relations(c,type),q));}
                require(method.equals("POST"),405,"METHOD_NOT_ALLOWED");Input.fields(b,"type,fromId,toId,annotation","type,fromId,toId,annotation");String type=Input.text(b.get("type"),30,false),from=Input.id(b.get("fromId")),to=Input.id(b.get("toId"));var result=ontology.relationSave(c,type,from,to,Map.of("annotation",b.get("annotation")),true);return created(result,path+"/"+type+"/"+from+"/"+to);}
            require(p.length==6||p.length==7,404,"NOT_FOUND");var rel=OntologyService.relation(p[3]);String from=Input.id(p[4]),to=Input.id(p[5]),where=rel.from()+"=? AND "+rel.to()+"=?";Map<String,Object> result;
            if(p.length==7){require(p[6].equals("review"),404,"NOT_FOUND");require(method.equals("PUT"),405,"METHOD_NOT_ALLOWED");result=ontology.review(c,user,rel.table(),where,new Object[]{from,to},b);}
            else if(method.equals("GET")){return ok(ontology.relationGet(c,p[3],from,to));}
            else{require(method.equals("PATCH"),405,"METHOD_NOT_ALLOWED");result=ontology.relationSave(c,p[3],from,to,b,false);}return ok(result);
        }
        require(Set.of("places","regions","themes","companion-types").contains(p[2]),404,"NOT_FOUND");
        if(p.length==5&&p[2].equals("places")&&p[4].equals("review")){require(method.equals("PUT"),405,"METHOD_NOT_ALLOWED");var result=ontology.review(c,user,"place","id=?",new Object[]{Input.id(p[3])},b);return ok(result);}
        require(p.length==3||p.length==4,404,"NOT_FOUND");return resource(c,admin,p[2],p.length==4?Input.id(p[3]):null,method,b,q,path);
    }
}
