package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.AdminDao;
import kr.ac.siheung.tourpass.dao.OntologyDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class AdminService {
    public static String table(String collection){return switch(collection){case "products"->"product";case "merchants"->"merchant";case "benefits"->"benefit";case "places"->"place";case "regions"->"region";case "themes"->"theme";case "companion-types"->"companion_type";default->throw new Problem(404,"NOT_FOUND");};}
    public static List<String> fields(String collection,boolean create){
        String names=switch(table(collection)){case "product"->"name,description";case "merchant"->"placeId,name,locationDescription";case "benefit"->"name,description,benefitType,basePriceWon,discountWon,payableWon,reservationNote";case "place"->"name,regionId,description,locationDescription,annotation,sources";default->"name";};
        if(create)names="code,"+names+(collection.equals("benefits")?",productId,merchantId":"");return Arrays.asList(names.split(","));
    }
    public Map<String,Object> get(Connection c,String collection,String id) throws SQLException {
        var m=AdminDao.find(c,table(collection),id);require(m!=null,404,"NOT_FOUND");
        if(collection.equals("places"))m.put("sources",OntologyDao.sources(c,id));return CatalogService.demo(m);
    }
    public Map<String,Object> save(Connection c,String collection,String id,Map<String,Object> body) throws SQLException {
        String t=table(collection);boolean create=id==null;Map<String,Object> old=create?null:AdminDao.lock(c,t,id);require(create||old!=null,404,"NOT_FOUND");
        String fields=String.join(",",fields(collection,create));
        Input.fields(body,create?fields:"",create?fields:fields+",active");var values=new LinkedHashMap<String,Object>();
        for(var e:body.entrySet()){
            String key=e.getKey();Object v=e.getValue();
            if(key.equals("annotation")||key.equals("sources"))continue;
            values.put(key,switch(key){
                case "code"->Input.text(v,64,false);
                case "name"->Input.text(v,100,false);
                case "description"->Input.text(v,t.equals("place")?2000:1000,false);
                case "locationDescription","reservationNote"->Input.text(v,500,false);
                case "placeId","regionId","productId","merchantId"->Input.id(v);
                case "active"->Input.bool(v);
                case "benefitType"->Input.choice(v,"FREE_ONCE","DISCOUNT");
                case "basePriceWon","discountWon"->v==null?null:Input.integer(v,0,Integer.MAX_VALUE);
                case "payableWon"->Input.integer(v,0,Integer.MAX_VALUE);
                default->throw new Problem(400,"INVALID_INPUT");
            });
        }
        if(body.containsKey("annotation")){
            var annotation=OntologyService.annotation(body.get("annotation"));
            require(create||old.get("materialKind").equals(annotation.get("materialKind")),400,"INVALID_INPUT");
            values.putAll(annotation);
        }
        List<Map<String,Object>> validatedSources=List.of();
        var merged=new LinkedHashMap<String,Object>();if(old!=null)merged.putAll(old);merged.putAll(values);
        if(t.equals("merchant")){if(create||body.containsKey("placeId")){var place=AdminDao.approvedDemoPlace(c,merged.get("placeId"));require(place!=null,409,"REVIEW_REQUIRED");}require(merged.get("locationDescription").toString().contains("가상"),400,"INVALID_INPUT");}
        if(t.equals("benefit")){if(create){CatalogService.concept(c,"product",(String)merged.get("productId"));CatalogService.concept(c,"merchant",(String)merged.get("merchantId"));}price(merged);}
        if(t.equals("place")){
            if(create||body.containsKey("regionId"))CatalogService.concept(c,"region",(String)merged.get("regionId"));
            if(create||body.keySet().stream().anyMatch(k->!k.equals("active")))OntologyService.resetReview(values);
            if(body.containsKey("sources")||body.containsKey("annotation"))
                validatedSources=sources(merged,body.containsKey("sources")?Input.array(body.get("sources")):OntologyDao.sources(c,id));
        }
        if(create)id=AdminDao.create(c,t,values);
        else AdminDao.save(c,t,id,values);
        if(t.equals("place")&&body.containsKey("sources")){AdminDao.deleteSources(c,id);for(var s:validatedSources)AdminDao.addSource(c,id,s.get("sourceUrl"),s.get("checkedOn"),s.get("evidenceNote"));}
        var result=get(c,collection,id);return Set.of("place","region","theme","companion_type").contains(t)?OntologyService.revision(c,result,true):result;
    }
    private static List<Map<String,Object>> sources(Map<String,Object> place,List<?> sources){
        require(!place.get("materialKind").equals("DEMO")||sources.isEmpty(),400,"INVALID_INPUT");
        var urls=new HashSet<Object>();urls.add(place.get("sourceUrl"));
        var result=new ArrayList<Map<String,Object>>();
        for(Object item:sources){
            var source=Input.object(item);Input.fields(source,"sourceUrl,checkedOn,evidenceNote");
            require(urls.add(Input.url(source.get("sourceUrl"),false)),400,"INVALID_INPUT");
            Input.date(source.get("checkedOn"),false);Input.text(source.get("evidenceNote"),1000,false);
            result.add(source);
        }
        return result;
    }
    private static void price(Map<String,Object> m){
        Object base=m.get("basePriceWon"),discount=m.get("discountWon");int payable=((Number)m.get("payableWon")).intValue();
        if(m.get("benefitType").equals("FREE_ONCE")){
            require(base==null&&discount==null&&payable==0,400,"INVALID_INPUT");
            return;
        }
        require(base instanceof Number&&discount instanceof Number,400,"INVALID_INPUT");
        long basePrice=((Number)base).longValue(),discountPrice=((Number)discount).longValue();
        require(discountPrice<=basePrice&&payable==basePrice-discountPrice,400,"INVALID_INPUT");
    }
    public List<Map<String,Object>> list(Connection c,String collection,Map<String,List<String>> q) throws SQLException {
        String t=table(collection);var all=AdminDao.list(c,t);String active=Input.param(q.get("active")),region=Input.param(q.get("regionId")),review=Input.param(q.get("reviewStatus")),product=Input.param(q.get("productId")),merchant=Input.param(q.get("merchantId"));
        if(active!=null)Input.choice(active,"true","false");if(region!=null)Input.id(region);if(review!=null)Input.choice(review,"DRAFT","APPROVED","REJECTED");if(product!=null)Input.id(product);if(merchant!=null)Input.id(merchant);
        String search=Input.text(Input.param(q.get("q")),100,true);
        if(search!=null){String needle=search.toLowerCase(Locale.ROOT);all.removeIf(r->!(r.get("name")+" "+r.get("code")).toLowerCase(Locale.ROOT).contains(needle));}
        if(active!=null)all.removeIf(r->!Boolean.valueOf(active).equals(r.get("active")));
        switch(t){
            case "place"->{
                if(region!=null)all.removeIf(r->!region.equals(r.get("regionId")));
                if(review!=null)all.removeIf(r->!review.equals(r.get("reviewStatus")));
            }
            case "benefit"->{
                if(product!=null)all.removeIf(r->!product.equals(r.get("productId")));
                if(merchant!=null)all.removeIf(r->!merchant.equals(r.get("merchantId")));
            }
        }
        all.forEach(CatalogService::demo);return all;
    }
}
