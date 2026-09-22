package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class PlaceFeedbackDao {
    private PlaceFeedbackDao() {}
    public static boolean available(Connection c,String place) throws SQLException {
        return one(c,OntologyDao.SELECTABLE_PLACES+" AND p.id=?",place)!=null;
    }
    public static Map<String,Object> find(Connection c,String user,String place) throws SQLException {
        return one(c,"SELECT place_id,rating,comment,updated_at FROM place_feedback WHERE visitor_id=? AND place_id=?",user,place);
    }
    public static void save(Connection c,String user,String place,int rating,String comment) throws SQLException {
        update(c,"INSERT INTO place_feedback(visitor_id,place_id,rating,comment) VALUES(?,?,?,?) ON CONFLICT(visitor_id,place_id) DO UPDATE SET rating=EXCLUDED.rating,comment=EXCLUDED.comment",user,place,rating,comment);
    }
    public static void delete(Connection c,String user,String place) throws SQLException {
        update(c,"DELETE FROM place_feedback WHERE visitor_id=? AND place_id=?",user,place);
    }
    public static List<Map<String,Object>> statistics(Connection c) throws SQLException {
        return rows(c,"SELECT p.id place_id,p.name place_name,r.name region_name,COUNT(*) response_count,ROUND(AVG(f.rating),1) average_rating,COUNT(*) FILTER(WHERE f.rating=1) rating1,COUNT(*) FILTER(WHERE f.rating=2) rating2,COUNT(*) FILTER(WHERE f.rating=3) rating3,COUNT(*) FILTER(WHERE f.rating=4) rating4,COUNT(*) FILTER(WHERE f.rating=5) rating5 FROM place_feedback f JOIN place p ON p.id=f.place_id JOIN region r ON r.id=p.region_id GROUP BY p.id,p.name,r.name ORDER BY COUNT(*) DESC,p.name,p.id");
    }
}
