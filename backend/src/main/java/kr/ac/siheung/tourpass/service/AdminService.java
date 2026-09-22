package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class AdminService {
    public static String table(String collection){return switch(collection){case "products"->"product";case "merchants"->"merchant";case "benefits"->"benefit";case "places"->"place";case "regions"->"region";case "themes"->"theme";case "companion-types"->"companion_type";default->throw new Problem(404,"NOT_FOUND");};}
    public static List<String> fields(String collection,boolean create){
        String names=switch(table(collection)){case "product"->"name,description";case "merchant"->"placeId,name,locationDescription";case "benefit"->"name,description,benefitType,basePriceWon,discountWon,payableWon,reservationNote";case "place"->"name,regionId,description,locationDescription,annotation,sources";default->"name";};
        if(create)names="code,"+names+(collection.equals("benefits")?",productId,merchantId":"");return Arrays.asList(names.split(","));
    }
    public Map<String,Object> get(Connection c,String collection,String id) throws SQLException {
        var m=one(c,"SELECT * FROM "+table(collection)+" WHERE id=?",id);require(m!=null,404,"NOT_FOUND");
        if(collection.equals("places"))m.put("sources",rows(c,"SELECT source_url,checked_on,evidence_note FROM place_source WHERE place_id=? ORDER BY id",id));return CatalogService.demo(m);
    }
    public Map<String,Object> save(Connection c,String collection,String id,Map<String,Object> body) throws SQLException {
        String t=table(collection);boolean create=id==null;Map<String,Object> old=create?null:one(c,"SELECT * FROM "+t+" WHERE id=? FOR UPDATE",id);require(create||old!=null,404,"NOT_FOUND");
        String fields=String.join(",",fields(collection,create));
        Input.fields(body,create?fields:"",create?fields:fields+",active");var values=new LinkedHashMap<String,Object>();
        for(var e:body.entrySet()){String key=e.getKey();Object v=e.getValue();switch(key){
            case "code"->values.put(key,Input.text(v,64,false));case "name"->values.put(key,Input.text(v,100,false));
            case "description"->values.put(key,Input.text(v,t.equals("place")?2000:1000,false));case "locationDescription","reservationNote"->values.put(key,Input.text(v,500,false));
            case "placeId","regionId","productId","merchantId"->values.put(key,Input.id(v));case "active"->values.put(key,Input.bool(v));
            case "benefitType"->values.put(key,Input.choice(v,"FREE_ONCE","DISCOUNT"));case "basePriceWon","discountWon"->values.put(key,v==null?null:Input.integer(v,0,Integer.MAX_VALUE));case "payableWon"->values.put(key,Input.integer(v,0,Integer.MAX_VALUE));
            case "annotation"->{var a=OntologyService.annotation(v);require(create||old.get("materialKind").equals(a.get("materialKind")),400,"INVALID_INPUT");values.putAll(a);}case "sources"->Input.array(v);default->throw new Problem(400,"INVALID_INPUT");}}
        var merged=new LinkedHashMap<String,Object>();if(old!=null)merged.putAll(old);merged.putAll(values);
        if(t.equals("merchant")){if(create||body.containsKey("placeId")){var place=one(c,"SELECT p.id FROM place p JOIN region r ON r.id=p.region_id WHERE p.id=? AND p.material_kind='DEMO' AND p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE",merged.get("placeId"));require(place!=null,409,"REVIEW_REQUIRED");}require(merged.get("locationDescription").toString().contains("가상"),400,"INVALID_INPUT");}
        if(t.equals("benefit")){if(create){CatalogService.concept(c,"product",(String)merged.get("productId"));CatalogService.concept(c,"merchant",(String)merged.get("merchantId"));}price(merged);}
        if(t.equals("place")){
            if(create||body.containsKey("regionId"))CatalogService.concept(c,"region",(String)merged.get("regionId"));
            if(create||body.keySet().stream().anyMatch(k->!k.equals("active"))){values.put("reviewStatus","DRAFT");values.put("reviewedBy",null);values.put("reviewedAt",null);}
            if(body.containsKey("sources")||body.containsKey("annotation")){var sources=body.containsKey("sources")?Input.array(body.get("sources")):rows(c,"SELECT source_url,checked_on,evidence_note FROM place_source WHERE place_id=?",id);require(!merged.get("materialKind").equals("DEMO")||sources.isEmpty(),400,"INVALID_INPUT");var urls=new HashSet<String>();if(merged.get("sourceUrl")!=null)urls.add((String)merged.get("sourceUrl"));for(Object item:sources){var s=Input.object(item);Input.fields(s,"sourceUrl,checkedOn,evidenceNote","sourceUrl,checkedOn,evidenceNote");require(urls.add(Input.url(s.get("sourceUrl"),false)),400,"INVALID_INPUT");Input.date(s.get("checkedOn"),false);Input.text(s.get("evidenceNote"),1000,false);}}
        }
        if(create)id=insertValues(c,t,values);
        else updateValues(c,t,"id=?",new Object[]{id},values);
        if(t.equals("place")&&body.containsKey("sources")){update(c,"DELETE FROM place_source WHERE place_id=?",id);for(Object item:Input.array(body.get("sources"))){var s=Input.object(item);update(c,"INSERT INTO place_source(place_id,source_url,checked_on,evidence_note) VALUES(?,?,?,?)",id,s.get("sourceUrl"),s.get("checkedOn"),s.get("evidenceNote"));}}
        var result=get(c,collection,id);return Set.of("place","region","theme","companion_type").contains(t)?OntologyService.revision(c,result,true):result;
    }
    private static void price(Map<String,Object> m){Object base=m.get("basePriceWon"),discount=m.get("discountWon");int payable=((Number)m.get("payableWon")).intValue();
        if(m.get("benefitType").equals("FREE_ONCE"))require(base==null&&discount==null&&payable==0,400,"INVALID_INPUT");
        else require(base instanceof Number&&discount instanceof Number&&((Number)discount).longValue()<=((Number)base).longValue()&&payable==((Number)base).longValue()-((Number)discount).longValue(),400,"INVALID_INPUT");}
    public List<Map<String,Object>> list(Connection c,String collection,Map<String,List<String>> q) throws SQLException {
        String t=table(collection);var all=rows(c,"SELECT * FROM "+t+" ORDER BY id");String active=CatalogService.param(q,"active"),region=CatalogService.param(q,"regionId"),review=CatalogService.param(q,"reviewStatus"),product=CatalogService.param(q,"productId"),merchant=CatalogService.param(q,"merchantId");
        if(active!=null)Input.choice(active,"true","false");if(region!=null)Input.id(region);if(review!=null)Input.choice(review,"DRAFT","APPROVED","REJECTED");if(product!=null)Input.id(product);if(merchant!=null)Input.id(merchant);
        all.removeIf(r->active!=null&&!Boolean.valueOf(active).equals(r.get("active"))||t.equals("place")&&(region!=null&&!region.equals(r.get("regionId"))||review!=null&&!review.equals(r.get("reviewStatus")))||t.equals("benefit")&&(product!=null&&!product.equals(r.get("productId"))||merchant!=null&&!merchant.equals(r.get("merchantId"))));all.forEach(CatalogService::demo);return all;
    }
}
