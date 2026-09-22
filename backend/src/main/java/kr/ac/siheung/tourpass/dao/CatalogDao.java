package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class CatalogDao {
    private CatalogDao() {}
    static final String BENEFITS="SELECT b.*,m.name merchant_name,m.place_id,m.location_description,p.name place_name,r.name region_name FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id";

    public static List<Map<String,Object>> products(Connection c) throws SQLException {
        return rows(c,"SELECT * FROM product WHERE active=TRUE ORDER BY id");
    }
    public static List<Map<String,Object>> merchants(Connection c, String region) throws SQLException {
        return rows(c,"SELECT m.*,p.region_id,r.name region_name FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.active=TRUE"+(region==null?"":" AND p.region_id=?")+" ORDER BY m.id",region==null?new Object[]{}:new Object[]{region});
    }
    public static Map<String,Object> product(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM product WHERE id=? AND active=TRUE",id);
    }
    public static List<Map<String,Object>> productBenefits(Connection c, Object id) throws SQLException {
        return rows(c,BENEFITS+" WHERE b.product_id=? AND b.active=TRUE AND m.active=TRUE ORDER BY b.id",id);
    }
    public static Map<String,Object> merchant(Connection c, Object id) throws SQLException {
        return one(c,"SELECT m.*,p.region_id,r.name region_name FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.id=? AND m.active=TRUE",id);
    }
    public static List<Map<String,Object>> merchantBenefits(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT b.* FROM benefit b JOIN product p ON p.id=b.product_id WHERE b.merchant_id=? AND b.active=TRUE AND p.active=TRUE ORDER BY b.id",id);
    }
    public static Map<String,Object> activeConcept(Connection c, String table, Object id) throws SQLException {
        return one(c,"SELECT id FROM "+table+" WHERE id=? AND active=TRUE",id);
    }
    public static List<Map<String,Object>> activeRegions(Connection c) throws SQLException {
        return rows(c,"SELECT id,code FROM region WHERE active=TRUE");
    }
}
