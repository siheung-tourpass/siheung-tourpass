package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class CatalogDao {
    private CatalogDao() {}
    static final String BENEFITS="SELECT b.*,m.name merchant_name,m.place_id,m.dining_code,m.location_description,COALESCE(p.name,m.name) place_name,r.name region_name FROM benefit b JOIN merchant m ON m.id=b.merchant_id LEFT JOIN place p ON p.id=m.place_id JOIN region r ON r.id=m.region_id";

    private static final String PRODUCT_BENEFITS=BENEFITS+" JOIN product pr ON pr.id=b.product_id WHERE pr.active=TRUE AND b.active=TRUE AND m.active=TRUE";
    private static final String MERCHANT_BENEFITS="SELECT b.* FROM benefit b JOIN product p ON p.id=b.product_id JOIN merchant m ON m.id=b.merchant_id WHERE m.active=TRUE AND b.active=TRUE AND p.active=TRUE";

    public static List<Map<String,Object>> products(Connection c) throws SQLException {
        return rows(c,"SELECT * FROM product WHERE active=TRUE ORDER BY id");
    }
    public static List<Map<String,Object>> merchants(Connection c, String region) throws SQLException {
        return rows(c,"SELECT m.*,r.name region_name FROM merchant m JOIN region r ON r.id=m.region_id WHERE m.active=TRUE"+(region==null?"":" AND m.region_id=?")+" ORDER BY m.id",region==null?new Object[]{}:new Object[]{region});
    }
    public static Map<String,Object> product(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM product WHERE id=? AND active=TRUE",id);
    }
    public static List<Map<String,Object>> productBenefits(Connection c, Object id) throws SQLException {
        return rows(c,PRODUCT_BENEFITS+" AND b.product_id=? ORDER BY b.id",id);
    }
    public static Map<String,Object> merchant(Connection c, Object id) throws SQLException {
        return one(c,"SELECT m.*,r.name region_name FROM merchant m JOIN region r ON r.id=m.region_id WHERE m.id=? AND m.active=TRUE",id);
    }
    public static List<Map<String,Object>> merchantBenefits(Connection c, Object id) throws SQLException {
        return rows(c,MERCHANT_BENEFITS+" AND b.merchant_id=? ORDER BY b.id",id);
    }
    public static List<Map<String,Object>> benefits(Connection c,String collection) throws SQLException {
        return rows(c,(collection.equals("products")?PRODUCT_BENEFITS:MERCHANT_BENEFITS)+" ORDER BY b.id");
    }
    public static Map<String,Object> activeConcept(Connection c, String table, Object id) throws SQLException {
        return one(c,"SELECT id FROM "+table+" WHERE id=? AND active=TRUE",id);
    }
    public static List<Map<String,Object>> activeRegions(Connection c) throws SQLException {
        return rows(c,"SELECT id,code FROM region WHERE active=TRUE");
    }
}
