package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class CatalogService {
    public static final String NOTICE="시연용·실제 사용 불가";
    public static final String BENEFITS="SELECT b.*,m.name merchant_name,m.place_id,m.location_description,p.name place_name,r.name region_name FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id";
    public static Map<String,Object> demo(Map<String,Object> m){if(m!=null&&(Boolean.TRUE.equals(m.get("demoOnly"))||"DEMO".equals(m.get("materialKind"))))m.put("notice",NOTICE);return m;}
    public List<Map<String,Object>> list(Connection c,String collection,String region) throws SQLException {
        List<Map<String,Object>> all;
        if(collection.equals("products"))all=rows(c,"SELECT * FROM product WHERE active=TRUE ORDER BY id");
        else {concept(c,"region",region);all=rows(c,"SELECT m.*,p.region_id,r.name region_name FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.active=TRUE"+(region==null?"":" AND p.region_id=?")+" ORDER BY m.id",region==null?new Object[]{}:new Object[]{region});}
        all.forEach(CatalogService::demo);return all;
    }
    public Map<String,Object> product(Connection c,String id) throws SQLException {
        var p=one(c,"SELECT * FROM product WHERE id=? AND active=TRUE",id);require(p!=null,404,"NOT_FOUND");
        var bs=rows(c,BENEFITS+" WHERE b.product_id=? AND b.active=TRUE AND m.active=TRUE ORDER BY b.id",id);bs.forEach(CatalogService::demo);p.put("benefits",bs);return demo(p);
    }
    public Map<String,Object> merchant(Connection c,String id) throws SQLException {
        var m=one(c,"SELECT m.*,p.region_id,r.name region_name FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.id=? AND m.active=TRUE",id);require(m!=null,404,"NOT_FOUND");
        var bs=rows(c,"SELECT b.* FROM benefit b JOIN product p ON p.id=b.product_id WHERE b.merchant_id=? AND b.active=TRUE AND p.active=TRUE ORDER BY b.id",id);bs.forEach(CatalogService::demo);m.put("benefits",bs);return demo(m);
    }
    public List<Map<String,Object>> merchantCards(Connection c) throws SQLException {
        var merchants=list(c,"merchants",null);
        var benefits=rows(c,"SELECT b.* FROM benefit b JOIN product p ON p.id=b.product_id JOIN merchant m ON m.id=b.merchant_id WHERE b.active=TRUE AND p.active=TRUE AND m.active=TRUE ORDER BY b.id");
        var byMerchant=new HashMap<String,List<Map<String,Object>>>();
        for(var benefit:benefits)byMerchant.computeIfAbsent((String)benefit.get("merchantId"),key->new ArrayList<>()).add(demo(benefit));
        for(var merchant:merchants)merchant.put("benefits",byMerchant.getOrDefault(merchant.get("id"),List.of()));
        return merchants;
    }
    public static Map<String,Object> page(List<Map<String,Object>> all,Map<String,List<String>> query) {
        int page=number(query,"page",1,1,Integer.MAX_VALUE),size=number(query,"size",20,1,100);long start=(long)(page-1)*size;
        List<Map<String,Object>> items=start>=all.size()?List.of():all.subList((int)start,(int)Math.min(start+size,all.size()));return Map.of("items",items,"page",page,"size",size,"total",all.size());
    }
    private static int number(Map<String,List<String>> q,String key,int fallback,int min,int max){String v=Input.param(q.get(key));if(v==null)return fallback;int n=Input.parseInteger(v);require(n>=min&&n<=max,400,"INVALID_INPUT");return n;}
    public static void concept(Connection c,String table,String id) throws SQLException {if(id!=null)require(one(c,"SELECT id FROM "+table+" WHERE id=? AND active=TRUE",Input.id(id))!=null,400,"INVALID_CONCEPT");}
}
