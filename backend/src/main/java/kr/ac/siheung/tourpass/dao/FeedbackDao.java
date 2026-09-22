package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class FeedbackDao {
    private FeedbackDao() {}

    public static Map<String,Object> lockOwnedRedemption(Connection c, Object redemption, Object user) throws SQLException {
        return one(c,"SELECT r.id,ib.place_id FROM redemption r JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass p ON p.id=ib.pass_id WHERE r.id=? AND p.owner_id=? FOR UPDATE",redemption,user);
    }
    public static Map<String,Object> find(Connection c, Object redemption) throws SQLException {
        return one(c,"SELECT * FROM feedback WHERE redemption_id=?",redemption);
    }
    public static List<Map<String,Object>> reasons(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT reason_code FROM feedback_reason WHERE feedback_id=? ORDER BY reason_code",id);
    }
    public static List<Map<String,Object>> themes(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT t.id,t.code,t.name FROM feedback_theme ft JOIN theme t ON t.id=ft.theme_id WHERE ft.feedback_id=? ORDER BY t.id",id);
    }
    public static Map<String,Object> lock(Connection c, Object redemption) throws SQLException {
        return one(c,"SELECT id FROM feedback WHERE redemption_id=? FOR UPDATE",redemption);
    }
    public static String create(Connection c, Object redemption, Object rating, Object comment) throws SQLException {
        return insert(c,"INSERT INTO feedback(redemption_id,rating,comment) VALUES(?,?,?)",redemption,rating,comment);
    }
    public static int preserveTheme(Connection c, Object id, Object theme) throws SQLException {
        return update(c,"INSERT INTO feedback_theme(feedback_id,theme_id) VALUES(?,?)",id,theme);
    }
    public static int save(Connection c, Object rating, Object comment, Object id) throws SQLException {
        return update(c,"UPDATE feedback SET rating=?,comment=? WHERE id=?",rating,comment,id);
    }
    public static int deleteReasons(Connection c, Object id) throws SQLException {
        return update(c,"DELETE FROM feedback_reason WHERE feedback_id=?",id);
    }
    public static int addReason(Connection c, Object id, Object reason) throws SQLException {
        return update(c,"INSERT INTO feedback_reason(feedback_id,reason_code) VALUES(?,?)",id,reason);
    }
    public static int delete(Connection c, Object redemption) throws SQLException {
        return update(c,"DELETE FROM feedback WHERE redemption_id=?",redemption);
    }
}
