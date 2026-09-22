package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class AdminDao {
    private AdminDao() {}

    public static Map<String,Object> find(Connection c, String table, Object id) throws SQLException {
        return one(c,"SELECT * FROM "+table+" WHERE id=?",id);
    }
    public static Map<String,Object> lock(Connection c, String table, Object id) throws SQLException {
        return one(c,"SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);
    }
    public static Map<String,Object> approvedDemoPlace(Connection c, Object placeId) throws SQLException {
        return one(c,"SELECT p.id FROM place p JOIN region r ON r.id=p.region_id WHERE p.id=? AND p.material_kind='DEMO' AND p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE",placeId);
    }
    public static String create(Connection c, String table, Map<String,Object> values) throws SQLException {
        return insertValues(c,table,values);
    }
    public static void save(Connection c, String table, Object id, Map<String,Object> values) throws SQLException {
        updateValues(c,table,"id=?",new Object[]{id},values);
    }
    public static int deleteSources(Connection c, Object id) throws SQLException {
        return update(c,"DELETE FROM place_source WHERE place_id=?",id);
    }
    public static int addSource(Connection c, Object id, Object url, Object date, Object note) throws SQLException {
        return update(c,"INSERT INTO place_source(place_id,source_url,checked_on,evidence_note) VALUES(?,?,?,?)",id,url,date,note);
    }
    public static List<Map<String,Object>> list(Connection c, String table) throws SQLException {
        return rows(c,"SELECT * FROM "+table+" ORDER BY id");
    }
}
