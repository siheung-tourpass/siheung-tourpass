package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class FeedbackService {
    private Map<String,Object> owned(Connection c,String user,String redemption) throws SQLException {var r=one(c,"SELECT r.id,ib.place_id FROM redemption r JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass p ON p.id=ib.pass_id WHERE r.id=? AND p.owner_id=? FOR UPDATE",redemption,user);require(r!=null,404,"NOT_FOUND");return r;}
    public Map<String,Object> get(Connection c,String user,String redemption) throws SQLException {owned(c,user,redemption);var f=one(c,"SELECT * FROM feedback WHERE redemption_id=?",redemption);require(f!=null,404,"NOT_FOUND");String id=(String)f.get("id");f.put("reasons",rows(c,"SELECT reason_code FROM feedback_reason WHERE feedback_id=? ORDER BY reason_code",id).stream().map(x->x.get("reasonCode")).toList());f.put("preservedThemes",rows(c,"SELECT t.id,t.code,t.name FROM feedback_theme ft JOIN theme t ON t.id=ft.theme_id WHERE ft.feedback_id=? ORDER BY t.id",id));return f;}
    public Map<String,Object> save(Connection c,String user,String redemption,Map<String,Object> body,boolean create) throws SQLException {
        Input.fields(body,"rating,reasons,comment","rating,reasons,comment");int rating=Input.integer(body.get("rating"),1,5);String comment=Input.text(body.get("comment"),500,true);var reasons=new TreeSet<String>();for(Object reason:Input.array(body.get("reasons")))reasons.add(Input.choice(reason,"INTEREST","COMPANION_FIT","COST","GUIDANCE"));
        var r=owned(c,user,redemption);var existing=one(c,"SELECT id FROM feedback WHERE redemption_id=? FOR UPDATE",redemption);require(create?existing==null:existing!=null,create?409:404,create?"DUPLICATE_FEEDBACK":"NOT_FOUND");String id;
        if(create){id=insert(c,"INSERT INTO feedback(redemption_id,rating,comment) VALUES(?,?,?)",redemption,rating,comment);for(String theme:new OntologyService().paths(c,(String)r.get("placeId")).keySet())update(c,"INSERT INTO feedback_theme(feedback_id,theme_id) VALUES(?,?)",id,theme);}
        else{id=(String)existing.get("id");update(c,"UPDATE feedback SET rating=?,comment=? WHERE id=?",rating,comment,id);update(c,"DELETE FROM feedback_reason WHERE feedback_id=?",id);}
        for(String reason:reasons)update(c,"INSERT INTO feedback_reason(feedback_id,reason_code) VALUES(?,?)",id,reason);return get(c,user,redemption);
    }
    public void delete(Connection c,String user,String redemption) throws SQLException {owned(c,user,redemption);update(c,"DELETE FROM feedback WHERE redemption_id=?",redemption);}
}
