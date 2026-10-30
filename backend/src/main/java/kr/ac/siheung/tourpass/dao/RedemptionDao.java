package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class RedemptionDao {
    private RedemptionDao() {}
    static final String HISTORY="SELECT r.id,ib.pass_id,ib.id issued_benefit_id,ib.merchant_id,ib.merchant_name,ib.benefit_name,r.used_at,(f.id IS NOT NULL) has_feedback FROM redemption r JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass p ON p.id=ib.pass_id LEFT JOIN feedback f ON f.redemption_id=r.id";

    public static void createLookup(Connection c,String token,String staff,String merchant,String pass,java.time.Instant expires) throws SQLException {
        update(c,"INSERT INTO pass_lookup(token,staff_user_id,merchant_id,pass_id,expires_at) VALUES(?,?,?,?,?)",token,staff,merchant,pass,expires);
    }
    public static Map<String,Object> passLookup(Connection c,String token,String staff,boolean lock) throws SQLException {
        return one(c,"SELECT * FROM pass_lookup WHERE token=? AND staff_user_id=?"+(lock?" FOR UPDATE":""),token,staff);
    }
    public static void consumeLookup(Connection c,String token) throws SQLException {
        update(c,"UPDATE pass_lookup SET used=TRUE WHERE token=?",token);
    }
    public static List<Map<String,Object>> assignments(Connection c, Object user) throws SQLException {
        return rows(c,"SELECT m.id merchant_id,m.name,m.active FROM merchant_staff ms JOIN merchant m ON m.id=ms.merchant_id WHERE ms.user_id=? AND ms.active=TRUE ORDER BY m.id",user);
    }
    public static Map<String,Object> lockAssignment(Connection c, Object user, Object merchant) throws SQLException {
        return one(c,"SELECT user_id FROM merchant_staff WHERE user_id=? AND merchant_id=? AND active=TRUE FOR UPDATE",user,merchant);
    }
    public static Map<String,Object> lookup(Connection c, Object code, Object merchant) throws SQLException {
        return one(c,"SELECT p.* FROM issued_pass p WHERE p.code=? AND EXISTS(SELECT 1 FROM issued_benefit ib WHERE ib.pass_id=p.id AND ib.merchant_id=?)",code,merchant);
    }
    public static Map<String,Object> issuedBenefit(Connection c, Object id) throws SQLException {
        return one(c,"SELECT * FROM issued_benefit WHERE id=?",id);
    }
    public static Map<String,Object> lockMerchant(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id FROM merchant WHERE id=? FOR UPDATE",id);
    }
    public static Map<String,Object> lockBenefit(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id FROM benefit WHERE id=? FOR UPDATE",id);
    }
    public static Map<String,Object> lockIssuedBenefit(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id FROM issued_benefit WHERE id=? FOR UPDATE",id);
    }
    public static int decrementBenefit(Connection c, Object id) throws SQLException {
        return update(c,"UPDATE issued_benefit SET remaining_count=remaining_count-1 WHERE id=? AND remaining_count>0",id);
    }
    public static String create(Connection c, Object id, Object user, java.time.Instant now) throws SQLException {
        return insert(c,"INSERT INTO redemption(issued_benefit_id,staff_user_id,used_at) VALUES(?,?,?)",id,user,now);
    }
    public static List<Map<String,Object>> history(Connection c, String user, boolean staff, String filter) throws SQLException {
        String where=staff?" WHERE ib.merchant_id=?":" WHERE p.owner_id=?";
        var args=new ArrayList<Object>();args.add(staff?filter:user);
        if(!staff&&filter!=null){where+=" AND p.id=?";args.add(filter);}
        return rows(c,HISTORY+where+" ORDER BY r.used_at DESC,r.id DESC",args.toArray());
    }
    public static Map<String,Object> detail(Connection c, String id, String user, boolean staff) throws SQLException {
        return one(c,HISTORY+" WHERE r.id=?"+(!staff?" AND p.owner_id=?":""),staff?new Object[]{id}:new Object[]{id,user});
    }
    public static Map<String,Object> assignment(Connection c, Object user, Object merchant) throws SQLException {
        return one(c,"SELECT user_id FROM merchant_staff WHERE user_id=? AND merchant_id=? AND active=TRUE",user,merchant);
    }
}
