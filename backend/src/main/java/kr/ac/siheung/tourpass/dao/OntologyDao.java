package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class OntologyDao {
    private OntologyDao() {}
    static final String SELECTABLE_PLACES="SELECT p.*,r.name region_name FROM place p JOIN region r ON r.id=p.region_id WHERE p.active=TRUE AND r.active=TRUE AND (p.review_status='APPROVED' OR (p.material_kind='REAL' AND p.review_status='DRAFT'))";
    public record Relation(String table,String from,String to,String fromTable,String toTable) {
        public String fromKey(){return camel(from);}
        public String toKey(){return camel(to);}
    }
    public static Relation relation(String type){return switch(type){
        case "has-theme" -> new Relation("place_theme","place_id","theme_id","place","theme");
        case "suitable-for" -> new Relation("place_companion","place_id","companion_type_id","place","companion_type");
        case "sub-theme-of" -> new Relation("theme_parent","child_theme_id","parent_theme_id","theme","theme");
        default -> throw new IllegalArgumentException("Unknown relation type");
    };}
    private static String reviewTable(String type){return type.equals("place")?"place":relation(type).table();}
    private static String reviewWhere(String type){
        if(type.equals("place"))return "id=?";
        var rel=relation(type);return rel.from()+"=? AND "+rel.to()+"=?";
    }

    public static List<Map<String,Object>> regions(Connection c) throws SQLException {
        return rows(c,"SELECT id,code,name FROM region WHERE active=TRUE ORDER BY id");
    }
    public static List<Map<String,Object>> themes(Connection c) throws SQLException {
        return rows(c,"SELECT id,code,name FROM theme WHERE active=TRUE ORDER BY id");
    }
    public static List<Map<String,Object>> companions(Connection c) throws SQLException {
        return rows(c,"SELECT id,code,name FROM companion_type WHERE active=TRUE ORDER BY id");
    }
    public static List<Map<String,Object>> relations(Connection c, Relation rel) throws SQLException {
        return rows(c,"SELECT * FROM "+rel.table()+" ORDER BY "+rel.from()+","+rel.to());
    }
    public static Map<String,Object> relation(Connection c, Relation rel, Object from, Object to) throws SQLException {
        return one(c,"SELECT * FROM "+rel.table()+" WHERE "+rel.from()+"=? AND "+rel.to()+"=?",from,to);
    }
    public static int incrementRevision(Connection c) throws SQLException {
        return update(c,"UPDATE ontology_revision SET revision=revision+1 WHERE id=1");
    }
    public static Map<String,Object> revision(Connection c) throws SQLException {
        return one(c,"SELECT revision FROM ontology_revision WHERE id=1");
    }
    public static List<Map<String,Object>> directThemes(Connection c, Object place) throws SQLException {
        return rows(c,"SELECT t.id FROM place_theme pt JOIN theme t ON t.id=pt.theme_id WHERE pt.place_id=? AND pt.active=TRUE AND pt.review_status='APPROVED' AND t.active=TRUE ORDER BY t.code",place);
    }
    public static List<Map<String,Object>> themeCodes(Connection c) throws SQLException {
        return rows(c,"SELECT id,code FROM theme WHERE active=TRUE ORDER BY code");
    }
    public static List<Map<String,Object>> approvedEdges(Connection c) throws SQLException {
        return rows(c,"SELECT tp.child_theme_id,tp.parent_theme_id FROM theme_parent tp JOIN theme a ON a.id=tp.child_theme_id JOIN theme b ON b.id=tp.parent_theme_id WHERE tp.active=TRUE AND tp.review_status='APPROVED' AND a.active=TRUE AND b.active=TRUE ORDER BY b.code");
    }
    public static List<Map<String,Object>> browsePlaces(Connection c) throws SQLException {
        return rows(c,SELECTABLE_PLACES+" ORDER BY p.id");
    }
    public static Map<String,Object> place(Connection c, Object id) throws SQLException {
        return one(c,"SELECT p.*,r.name region_name FROM place p JOIN region r ON r.id=p.region_id WHERE p.id=? AND p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE",id);
    }
    public static List<Map<String,Object>> placeCompanions(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT ct.id,ct.code,ct.name FROM place_companion pc JOIN companion_type ct ON ct.id=pc.companion_type_id WHERE pc.place_id=? AND pc.active=TRUE AND pc.review_status='APPROVED' AND ct.active=TRUE ORDER BY ct.id",id);
    }
    public static List<Map<String,Object>> sources(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT source_url,checked_on,evidence_note FROM place_source WHERE place_id=? ORDER BY id",id);
    }
    public static List<Map<String,Object>> allEdges(Connection c) throws SQLException {
        return rows(c,"SELECT child_theme_id,parent_theme_id FROM theme_parent");
    }
    public static Map<String,Object> relationSource(Connection c, Relation rel, Object from) throws SQLException {
        return one(c,"SELECT * FROM "+rel.fromTable()+" WHERE id=?",from);
    }
    public static Map<String,Object> relationTarget(Connection c, Relation rel, Object to) throws SQLException {
        return one(c,"SELECT id FROM "+rel.toTable()+" WHERE id=?",to);
    }
    public static String createRelation(Connection c, Relation rel, Map<String,Object> values) throws SQLException {
        return insertValues(c,rel.table(),values);
    }
    public static void saveRelation(Connection c, Relation rel, Object from, Object to, Map<String,Object> values) throws SQLException {
        updateValues(c,rel.table(),rel.from()+"=? AND "+rel.to()+"=?",new Object[]{from,to},values);
    }
    public static Map<String,Object> reviewTarget(Connection c, String type, Object[] keys) throws SQLException {
        return one(c,"SELECT * FROM "+reviewTable(type)+" WHERE "+reviewWhere(type),keys);
    }
    public static Map<String,Object> reviewPlace(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM place WHERE id=?",id);
    }
    public static void saveReview(Connection c, String type, Object[] keys, Map<String,Object> values) throws SQLException {
        updateValues(c,reviewTable(type),reviewWhere(type),keys,values);
    }
}
