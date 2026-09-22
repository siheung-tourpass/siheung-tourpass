package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class PassDao {
    private PassDao() {}
    static final String BENEFITS="SELECT ib.*,m.active merchant_active,b.active benefit_active,r.used_at FROM issued_benefit ib JOIN merchant m ON m.id=ib.merchant_id JOIN benefit b ON b.id=ib.benefit_id LEFT JOIN redemption r ON r.issued_benefit_id=ib.id";

    public static List<Map<String,Object>> benefits(Connection c, Object passId) throws SQLException {
        return rows(c,BENEFITS+" WHERE ib.pass_id=? ORDER BY ib.id",passId);
    }
    public static Map<String,Object> benefit(Connection c, Object passId, Object id) throws SQLException {
        return one(c,BENEFITS+" WHERE ib.pass_id=? AND ib.id=?",passId,id);
    }
    public static Map<String,Object> find(Connection c, String id, String owner, boolean admin) throws SQLException {
        return one(c,"SELECT * FROM issued_pass WHERE id=?"+(admin?"":" AND owner_id=?"),admin?new Object[]{id}:new Object[]{id,owner});
    }
    public static List<Map<String,Object>> history(Connection c, Object id) throws SQLException {
        return rows(c,RedemptionDao.HISTORY+" WHERE p.id=? ORDER BY r.used_at DESC,r.id DESC",id);
    }
    public static List<Map<String,Object>> list(Connection c, String owner) throws SQLException {
        return rows(c,"SELECT id,owner_id,product_id,product_name,starts_at,expires_at,cancelled_at,demo_only FROM issued_pass"+(owner==null?"":" WHERE owner_id=?")+" ORDER BY starts_at DESC,id DESC",owner==null?new Object[]{}:new Object[]{owner});
    }
    public static Map<String,Object> lockProduct(Connection c, Object productId) throws SQLException {
        return one(c,"SELECT * FROM product WHERE id=? FOR UPDATE",productId);
    }
    public static List<Map<String,Object>> issuableBenefits(Connection c, Object productId) throws SQLException {
        return rows(c,CatalogDao.BENEFITS+" WHERE b.product_id=? AND b.active=TRUE AND m.active=TRUE AND p.active=TRUE AND p.review_status='APPROVED' AND p.material_kind='DEMO' AND r.active=TRUE ORDER BY b.id FOR UPDATE",productId);
    }
    public static Map<String,Object> activePass(Connection c, Object user, java.time.Instant now) throws SQLException {
        return one(c,"SELECT id FROM issued_pass WHERE owner_id=? AND cancelled_at IS NULL AND starts_at<=? AND expires_at>? FOR UPDATE",user,now,now);
    }
    public static String create(Connection c, Object user, Object productId, Object code, Object name, java.time.Instant startsAt, java.time.Instant expiresAt) throws SQLException {
        return insert(c,"INSERT INTO issued_pass(owner_id,product_id,code,product_name,starts_at,expires_at) VALUES(?,?,?,?,?,?) ON CONFLICT(code) DO NOTHING",user,productId,code,name,startsAt,expiresAt);
    }
    public static int snapshotBenefits(Connection c, String id, List<Map<String,Object>> benefits) throws SQLException {
        String columns="pass_id,benefit_id,merchant_id,place_id,merchant_name,place_name,region_name,location_description,benefit_name,description,benefit_type,use_limit,base_price_won,discount_won,payable_won,reservation_required,reservation_note,demo_only";
        return update(c,"INSERT INTO issued_benefit("+columns+") SELECT ?,b.id,b.merchant_id,m.place_id,m.name,p.name,r.name,m.location_description,b.name,b.description,b.benefit_type,b.use_limit,b.base_price_won,b.discount_won,b.payable_won,b.reservation_required,b.reservation_note,TRUE FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE b.id IN ("+String.join(",",Collections.nCopies(benefits.size(),"?"))+") ORDER BY b.id",java.util.stream.Stream.concat(java.util.stream.Stream.of(id),benefits.stream().map(b->b.get("id"))).toArray());
    }
    public static Map<String,Object> lock(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM issued_pass WHERE id=? FOR UPDATE",id);
    }
    public static int cancel(Connection c, java.time.Instant now, Object admin, Object reason, Object id) throws SQLException {
        return update(c,"UPDATE issued_pass SET cancelled_at=?,cancelled_by=?,cancel_reason=? WHERE id=?",now,admin,reason,id);
    }
    public static Map<String,Object> get(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM issued_pass WHERE id=?",id);
    }
}
