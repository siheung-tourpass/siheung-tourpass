package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.CatalogDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class CatalogService {
    public List<Map<String,Object>> cards(Connection c) throws SQLException {
        var cards=new ArrayList<Map<String,Object>>();
        for(var place:new OntologyService().browsePlaces(c))cards.add(card("place",place));
        for(var event:EventCatalog.list(null).cards()){
            var card=card("event",event);
            var regions=new LinkedHashSet<Object>();
            for(var place:cards)if(((List<?>)event.get("placeCodes")).contains(place.get("code"))&&place.get("regionId")!=null)regions.add(place.get("regionId"));
            if(regions.size()==1)card.put("regionId",regions.iterator().next());
            cards.add(card);
        }
        for(var dining:diningCards(c))cards.add(card("dining",dining));
        return cards;
    }
    private static Map<String,Object> card(String kind,Map<String,Object> card){
        card.put("kind",kind);card.put("kindLabel",switch(kind){case "place"->"관광지";case "event"->"행사";default->"음식점 / 카페";});
        card.put("key",kind+":"+card.get(kind.equals("place")?"id":"code"));
        if(kind.equals("dining"))card.put("description",card.get("designation")+" · "+card.get("cuisine"));
        if(card.get("image") instanceof Map<?,?> image)
            card.put("credit",image.containsKey("attribution")?image.get("attribution"):image.get("author")+" · "+image.get("license"));
        return card;
    }
    public static final String NOTICE="시연용·실제 사용 불가";
    public static Map<String,Object> demo(Map<String,Object> m){if(m!=null&&(Boolean.TRUE.equals(m.get("demoOnly"))||"DEMO".equals(m.get("materialKind"))))m.put("notice",NOTICE);return m;}
    public List<Map<String,Object>> list(Connection c,String collection,String region) throws SQLException {
        List<Map<String,Object>> all;
        if(collection.equals("products"))all=CatalogDao.products(c);
        else {concept(c,"region",region);all=CatalogDao.merchants(c,region);}
        all.forEach(CatalogService::demo);return all;
    }
    public Map<String,Object> product(Connection c,String id) throws SQLException {
        var p=CatalogDao.product(c,id);require(p!=null,404,"NOT_FOUND");
        var bs=CatalogDao.productBenefits(c,id);bs.forEach(CatalogService::demo);p.put("benefits",bs);return demo(p);
    }
    public Map<String,Object> merchant(Connection c,String id) throws SQLException {
        var m=CatalogDao.merchant(c,id);require(m!=null,404,"NOT_FOUND");
        var bs=CatalogDao.merchantBenefits(c,id);bs.forEach(CatalogService::demo);m.put("benefits",bs);return demo(m);
    }
    public static Map<String,Object> page(List<Map<String,Object>> all,Map<String,List<String>> query) {
        int page=number(query,"page",1,1,Integer.MAX_VALUE),size=number(query,"size",20,1,100);long start=(long)(page-1)*size;
        List<Map<String,Object>> items=start>=all.size()?List.of():all.subList((int)start,(int)Math.min(start+size,all.size()));return Map.of("items",items,"page",page,"size",size,"total",all.size());
    }
    private static int number(Map<String,List<String>> q,String key,int fallback,int min,int max){String v=Input.param(q.get(key));if(v==null)return fallback;int n=Input.parseInteger(v);require(n>=min&&n<=max,400,"INVALID_INPUT");return n;}
    public static void concept(Connection c,String table,String id) throws SQLException {if(id!=null)require(CatalogDao.activeConcept(c,table,Input.id(id))!=null,400,"INVALID_CONCEPT");}
    public List<Map<String,Object>> diningCards(Connection c) throws SQLException {
        var regions=new HashMap<Object,Object>();
        for(var region:CatalogDao.activeRegions(c))regions.put(region.get("code"),region.get("id"));
        var cards=DiningCatalog.list();
        for(var card:cards){
            var region=regions.get(card.get("regionCode"));
            if(region!=null)card.put("regionId",region);
        }
        return cards;
    }
}
