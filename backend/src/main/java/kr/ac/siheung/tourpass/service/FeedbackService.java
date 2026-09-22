package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.FeedbackDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class FeedbackService {
    public static final Map<String,String> REASONS;
    static {
        var reasons=new LinkedHashMap<String,String>();
        reasons.put("INTEREST","흥미");reasons.put("COMPANION_FIT","동반 적합");
        reasons.put("COST","비용");reasons.put("GUIDANCE","안내");
        REASONS=Collections.unmodifiableMap(reasons);
    }
    private Map<String,Object> owned(Connection c,String user,String redemption) throws SQLException {var r=FeedbackDao.lockOwnedRedemption(c,redemption,user);require(r!=null,404,"NOT_FOUND");return r;}
    public Map<String,Object> get(Connection c,String user,String redemption) throws SQLException {owned(c,user,redemption);return read(c,redemption);}
    private Map<String,Object> read(Connection c,String redemption) throws SQLException {var f=FeedbackDao.find(c,redemption);require(f!=null,404,"NOT_FOUND");String id=(String)f.get("id");f.put("reasons",FeedbackDao.reasons(c,id).stream().map(x->x.get("reasonCode")).toList());f.put("preservedThemes",FeedbackDao.themes(c,id));return f;}
    public Map<String,Object> save(Connection c,String user,String redemption,Map<String,Object> body,boolean create) throws SQLException {
        Input.fields(body,"rating,reasons,comment");int rating=Input.integer(body.get("rating"),1,5);String comment=Input.text(body.get("comment"),500,true);var reasons=new TreeSet<String>();for(Object reason:Input.array(body.get("reasons")))reasons.add(Input.choice(reason,REASONS.keySet().toArray(String[]::new)));
        var r=owned(c,user,redemption);var existing=FeedbackDao.lock(c,redemption);require(create?existing==null:existing!=null,create?409:404,create?"DUPLICATE_FEEDBACK":"NOT_FOUND");String id;
        if(create){id=FeedbackDao.create(c,redemption,rating,comment);for(String theme:new OntologyService().paths(c,(String)r.get("placeId")).keySet())FeedbackDao.preserveTheme(c,id,theme);}
        else{id=(String)existing.get("id");FeedbackDao.save(c,rating,comment,id);FeedbackDao.deleteReasons(c,id);}
        for(String reason:reasons)FeedbackDao.addReason(c,id,reason);return read(c,redemption);
    }
    public void delete(Connection c,String user,String redemption) throws SQLException {owned(c,user,redemption);FeedbackDao.delete(c,redemption);}
}
