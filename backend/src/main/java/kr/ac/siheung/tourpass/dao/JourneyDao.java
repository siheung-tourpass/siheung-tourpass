package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class JourneyDao {
    private JourneyDao() {}

    public static List<Map<String,Object>> list(Connection c, String user, String status) throws SQLException {
        return rows(c,"SELECT j.*,(SELECT COUNT(*) FROM journey_place p WHERE p.journey_id=j.id) place_count FROM journey j WHERE owner_id=?"+(status==null?"":" AND status=?")+" ORDER BY j.id DESC",status==null?new Object[]{user}:new Object[]{user,status});
    }
    public static Map<String,Object> findOwned(Connection c, boolean lock, Object id, Object user) throws SQLException {
        return one(c,"SELECT * FROM journey WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),id,user);
    }
    public static List<Map<String,Object>> cards(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT jp.*,EXISTS(SELECT 1 FROM journey_photo photo WHERE photo.item_id=jp.id) has_photo FROM journey_place jp WHERE journey_id=? ORDER BY position,id",id);
    }
    public static List<Map<String,Object>> passHistory(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT h.*,p.product_name,p.starts_at,p.expires_at,p.cancelled_at,p.demo_only FROM journey_pass_history h JOIN issued_pass p ON p.id=h.issued_pass_id WHERE h.journey_id=? ORDER BY h.id DESC",id);
    }
    public static String create(Connection c, Object user, Object name, Object date) throws SQLException {
        return insert(c,"INSERT INTO journey(owner_id,name,travel_date) VALUES(?,?,?)",user,name,date);
    }
    public static Map<String,Object> firstCard(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id FROM journey_place WHERE journey_id=? LIMIT 1",id);
    }
    public static int disconnectPass(Connection c, java.time.Instant now, Object id) throws SQLException {
        return update(c,"UPDATE journey_pass_history SET disconnected_at=? WHERE journey_id=? AND disconnected_at IS NULL",now,id);
    }
    public static String connectPass(Connection c, Object id, Object pass, java.time.Instant now) throws SQLException {
        return insert(c,"INSERT INTO journey_pass_history(journey_id,issued_pass_id,linked_at) VALUES(?,?,?)",id,pass,now);
    }
    public static void save(Connection c, Object id, Map<String,Object> values) throws SQLException {
        updateValues(c,"journey","id=?",new Object[]{id},values);
    }
    public static List<Map<String,Object>> cardKeys(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT id,card_key FROM journey_place WHERE journey_id=?",id);
    }
    public static int deleteCard(Connection c, Object id) throws SQLException {
        return update(c,"DELETE FROM journey_place WHERE id=?",id);
    }
    public static int delete(Connection c, Object id) throws SQLException {
        return update(c,"DELETE FROM journey WHERE id=?",id);
    }
    public static Map<String,Object> findCardKey(Connection c, Object id, Object key) throws SQLException {
        return one(c,"SELECT id FROM journey_place WHERE journey_id=? AND card_key=?",id,key);
    }
    public static int nextPosition(Connection c, Object id) throws SQLException {
        return ((Number)one(c,"SELECT COALESCE(MAX(position),0)+1 next_position FROM journey_place WHERE journey_id=?",id).get("nextPosition")).intValue();
    }
    public static Map<String,Object> selectablePlace(Connection c, Object source) throws SQLException {
        return one(c,OntologyDao.SELECTABLE_PLACES+" AND p.id=?",source);
    }
    public static Map<String,Object> selectableMerchant(Connection c, Object source) throws SQLException {
        return one(c,"SELECT m.name,r.name region_name,m.location_description,'DEMO' material_kind FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.id=? AND m.active=TRUE AND r.active=TRUE",source);
    }
    public static String addCard(Connection c, Object id, Object placeId, Object kind, Object key, Object position, Object name, Object region, Object location, Object material) throws SQLException {
        return insert(c,"INSERT INTO journey_place(journey_id,place_id,card_kind,card_key,position,place_name,region_name,location_description,material_kind) VALUES(?,?,?,?,?,?,?,?,?)",id,placeId,kind,key,position,name,region,location,material);
    }
    public static Map<String,Object> findCard(Connection c, Object item, Object id) throws SQLException {
        return one(c,"SELECT * FROM journey_place WHERE id=? AND journey_id=?",item,id);
    }
    public static int record(Connection c, Object date, Object note, Object rating, Object item) throws SQLException {
        return update(c,"UPDATE journey_place SET visited_on=?,note=?,rating=? WHERE id=?",date,note,rating,item);
    }
    public static List<Map<String,Object>> positions(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT id,position FROM journey_place WHERE journey_id=? ORDER BY position,id",id);
    }
    public static int positionItem(Connection c, Object position, Object item) throws SQLException {
        return update(c,"UPDATE journey_place SET position=? WHERE id=?",position,item);
    }
    public static List<Map<String,Object>> coverCards(Connection c, List<String> ids) throws SQLException {
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return rows(c,"SELECT jp.journey_id,jp.card_kind,jp.card_key,p.code,p.material_kind FROM journey_place jp LEFT JOIN place p ON p.id=jp.place_id WHERE jp.journey_id IN ("+placeholders+") ORDER BY jp.journey_id,jp.position,jp.id",ids.toArray());
    }
}
