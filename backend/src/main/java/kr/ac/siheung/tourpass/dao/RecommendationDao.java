package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class RecommendationDao {
    private RecommendationDao() {}

    public static List<Map<String,Object>> themeNames(Connection c) throws SQLException {
        return rows(c,"SELECT id,name FROM theme");
    }
    public static List<Map<String,Object>> feedback(Connection c, Object user) throws SQLException {
        return rows(c,"SELECT f.id,f.rating,ib.place_id,ft.theme_id,fr.reason_code FROM feedback f JOIN redemption r ON r.id=f.redemption_id JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass ip ON ip.id=ib.pass_id LEFT JOIN feedback_theme ft ON ft.feedback_id=f.id LEFT JOIN feedback_reason fr ON fr.feedback_id=f.id WHERE ip.owner_id=?",user);
    }
    public static List<Map<String,Object>> candidates(Connection c, String region) throws SQLException {
        return rows(c,"SELECT p.* FROM place p JOIN region r ON r.id=p.region_id WHERE p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE"+(region==null?"":" AND p.region_id=?")+" AND (NOT EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id) OR EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id AND m.active=TRUE)) ORDER BY p.id",region==null?new Object[]{}:new Object[]{region});
    }
    public static Map<String,Object> companionMatch(Connection c, Object id, Object companion) throws SQLException {
        return one(c,"SELECT place_id FROM place_companion WHERE place_id=? AND companion_type_id=? AND active=TRUE AND review_status='APPROVED'",id,companion);
    }
    public static Map<String,Object> region(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id,name FROM region WHERE id=?",id);
    }
    public static List<Map<String,Object>> benefits(Connection c, Object id) throws SQLException {
        return rows(c,"SELECT b.id benefit_id,b.demo_only FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN product pr ON pr.id=b.product_id WHERE m.place_id=? AND m.active=TRUE AND b.active=TRUE AND pr.active=TRUE ORDER BY b.id",id);
    }
    public static List<Map<String,Object>> ownedBenefits(Connection c, Object user) throws SQLException {
        return rows(c,"SELECT ib.id,ib.pass_id,ib.place_id,ib.remaining_count,p.starts_at,p.expires_at,p.cancelled_at,m.active merchant_active,b.active benefit_active FROM issued_benefit ib JOIN issued_pass p ON p.id=ib.pass_id JOIN merchant m ON m.id=ib.merchant_id JOIN benefit b ON b.id=ib.benefit_id WHERE p.owner_id=? ORDER BY p.id,ib.id",user);
    }
}
