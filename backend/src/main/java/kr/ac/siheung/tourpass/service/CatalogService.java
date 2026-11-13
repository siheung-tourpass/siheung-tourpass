package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.CatalogDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class CatalogService {
    public List<Map<String,Object>> cards(Connection c) throws SQLException {
        var cards=new ArrayList<Map<String,Object>>();
        for(var place:new OntologyService().browsePlaces(c))cards.add(card("place",place));
        var placeRegions=new HashMap<Object,Object>();
        for(var place:cards)if(place.get("regionId")!=null)placeRegions.put(place.get("code"),place.get("regionId"));
        for(var event:EventCatalog.list(null).cards()){
            var card=card("event",event);
            var regions=new LinkedHashSet<Object>();
            for(var code:(List<?>)event.get("placeCodes"))if(placeRegions.containsKey(code))regions.add(placeRegions.get(code));
            if(regions.size()==1)card.put("regionId",regions.iterator().next());
            cards.add(card);
        }
        for(var dining:diningCards(c))cards.add(card("dining",dining));
        var benefits=benefitsByCard(c);
        for(var card:cards){var list=benefits.get(card.get("key"));if(list!=null)card.put("tourpassBenefits",list);}
        return cards;
    }
    /** Journey card key of the tourism place or dining card a virtual merchant stands for. */
    public static String cardKey(Map<String,Object> benefit){return benefit.get("placeId")!=null?"place:"+benefit.get("placeId"):"dining:"+benefit.get("diningCode");}
    /** Complete, distinct benefit locations in benefit order; empty when any card is unavailable. */
    public static List<String> journeyCardKeys(Map<String,Object> product,List<Map<String,Object>> cards) {
        var available=cards.stream().map(card->card.get("key")).collect(java.util.stream.Collectors.toSet());
        var keys=new LinkedHashSet<String>();
        for(Object value:(List<?>)product.get("benefits")){
            String key=cardKey(Input.object(value));
            if(!available.contains(key))return List.of();
            keys.add(key);
        }
        return List.copyOf(keys);
    }
    @SuppressWarnings("unchecked")
    public Map<String,List<Map<String,Object>>> benefitsByCard(Connection c) throws SQLException {
        var benefits=new ArrayList<Map<String,Object>>();
        for(var product:withBenefits(c,"products",null))
            for(var benefit:(List<Map<String,Object>>)product.get("benefits")){benefit.put("productName",product.get("name"));benefits.add(benefit);}
        return groupBenefits(benefits);
    }
    public static Map<String,List<Map<String,Object>>> groupBenefits(List<Map<String,Object>> benefits) {
        return benefits.stream().collect(java.util.stream.Collectors.groupingBy(CatalogService::cardKey));
    }
    /** The product whose benefits cover the most of the given cards, the cheaper one on ties; null when none covers any. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> productFor(Connection c,Collection<String> keys) throws SQLException {
        Map<String,Object> best=null;long bestCount=0;
        for(var product:withBenefits(c,"products",null)){
            if(!onSale(product))continue;
            var matched=((List<Map<String,Object>>)product.get("benefits")).stream().filter(b->keys.contains(cardKey(b))).toList();
            long count=matched.stream().map(CatalogService::cardKey).distinct().count();
            if(count>0&&(best==null||count>bestCount||count==bestCount&&price(product)<price(best))){
                best=product;bestCount=count;
                product.put("matchedBenefits",matched);product.put("matchedCount",count);
            }
        }
        return best;
    }
    private static int price(Map<String,Object> product){return ((Number)product.get("priceWon")).intValue();}
    /** A product without a price (0 won) is not on sale yet. */
    public static boolean onSale(Map<String,Object> product){return price(product)>0;}
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
        if(collection.equals("products")){all=CatalogDao.products(c);}
        else {concept(c,"region",region);all=CatalogDao.merchants(c,region);}
        all.forEach(CatalogService::demo);return all;
    }
    public List<Map<String,Object>> withBenefits(Connection c,String collection,String region) throws SQLException {
        var items=list(c,collection,region);
        if(items.isEmpty())return items;
        var benefits=CatalogDao.benefits(c,collection);
        benefits.forEach(CatalogService::demo);
        String owner=collection.equals("products")?"productId":"merchantId";
        var grouped=benefits.stream().collect(java.util.stream.Collectors.groupingBy(b->b.get(owner)));
        for(var item:items)item.put("benefits",grouped.getOrDefault(item.get("id"),List.of()));
        return items;
    }
    public Map<String,Object> detail(Connection c,String collection,String id) throws SQLException {
        var item=collection.equals("products")?CatalogDao.product(c,id):CatalogDao.merchant(c,id);
        require(item!=null,404,"NOT_FOUND");
        return attachBenefits(c,collection,item);
    }
    private Map<String,Object> attachBenefits(Connection c,String collection,Map<String,Object> item) throws SQLException {
        var benefits=collection.equals("products")?CatalogDao.productBenefits(c,item.get("id")):CatalogDao.merchantBenefits(c,item.get("id"));
        benefits.forEach(CatalogService::demo);item.put("benefits",benefits);
        return demo(item);
    }
    public static Map<String,Object> page(List<Map<String,Object>> all,Map<String,List<String>> query) {
        int page=number(query,"page",1,1,Integer.MAX_VALUE),size=number(query,"size",20,1,100);long start=(long)(page-1)*size;
        List<Map<String,Object>> items=start>=all.size()?List.of():all.subList((int)start,(int)Math.min(start+size,all.size()));return Map.of("items",items,"page",page,"size",size,"total",all.size());
    }
    private static int number(Map<String,List<String>> q,String key,int fallback,int min,int max){String v=Input.param(q.get(key));return v==null?fallback:Input.integer(Input.parseInteger(v),min,max);}
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
