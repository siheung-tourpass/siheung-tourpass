package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.PassDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class PassService {
    public static String status(Map<String,Object> p,Instant now){if(p.get("cancelledAt")!=null)return "CANCELLED";if(now.isBefore(Instant.parse((String)p.get("startsAt"))))return "NOT_STARTED";if(!now.isBefore(Instant.parse((String)p.get("expiresAt"))))return "EXPIRED";return "ACTIVE";}
    public static String availability(Map<String,Object> p,Map<String,Object> b,Instant now){
        if(!Boolean.TRUE.equals(b.get("merchantActive")))return "MERCHANT_INACTIVE";
        if(!Boolean.TRUE.equals(b.get("benefitActive")))return "BENEFIT_INACTIVE";
        String s=status(p,now);if(!s.equals("ACTIVE"))return "PASS_"+s;
        if(((Number)b.get("remainingCount")).intValue()==0)return "BENEFIT_ALREADY_USED";return "AVAILABLE";
    }
    public static List<Map<String,Object>> benefits(Connection c,String passId) throws SQLException {
        return PassDao.benefits(c,passId);
    }
    public static Map<String,Object> benefit(Connection c,String passId,String id) throws SQLException {
        return PassDao.benefit(c,passId,id);
    }
    public Map<String,Object> detail(Connection c,String owner,String id,boolean admin) throws SQLException {
        var p=PassDao.find(c,id,owner,admin);require(p!=null,404,"NOT_FOUND");
        Instant now=Instant.now();p.put("status",status(p,now));var bs=benefits(c,id);
        for(var b:bs){String reason=availability(p,b,now);b.put("usable",reason.equals("AVAILABLE"));b.put("reason",reason);b.remove("merchantActive");b.remove("benefitActive");CatalogService.demo(b);}
        p.put("benefits",bs);if(admin){p.remove("code");p.put("redemptions",PassDao.history(c,id));}return CatalogService.demo(p);
    }
    public List<Map<String,Object>> list(Connection c,String owner,Map<String,List<String>> q) throws SQLException {
        String filter=Input.param(q.get("status"));if(filter!=null)Input.choice(filter,"ACTIVE","EXPIRED","CANCELLED","NOT_STARTED");
        var all=PassDao.list(c,owner);Instant now=Instant.now();
        all.removeIf(p->{p.put("status",status(p,now));CatalogService.demo(p);return filter!=null&&!filter.equals(p.get("status"));});return all;
    }
    public Map<String,Object> issue(Connection c,String user,Map<String,Object> body) throws SQLException {
        Input.fields(body,"productId");String productId=Input.id(body.get("productId"));
        var p=PassDao.lockProduct(c,productId);require(p!=null,404,"NOT_FOUND");require(Boolean.TRUE.equals(p.get("active")),409,"PRODUCT_INACTIVE");
        var bs=PassDao.issuableBenefits(c,productId);require(!bs.isEmpty(),409,"NO_ISSUABLE_BENEFITS");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        require(PassDao.activePass(c,user,now)==null,409,"ACTIVE_PASS_EXISTS");
        String id=null;for(int retry=0;retry<3&&id==null;retry++){id=PassDao.create(c,user,productId,AuthService.token(),p.get("name"),now,now.plus(Duration.ofHours(24)));}
        require(id!=null,503,"RETRY_LATER");
        PassDao.snapshotBenefits(c,id,bs);
        return detail(c,user,id,false);
    }
    public Map<String,Object> cancel(Connection c,String admin,String id,Map<String,Object> body) throws SQLException {
        Input.fields(body,"reason");String reason=Input.text(body.get("reason"),500,false);var p=PassDao.lock(c,id);require(p!=null,404,"NOT_FOUND");
        if(p.get("cancelledAt")==null){Instant now=Instant.now();require(now.isBefore(Instant.parse((String)p.get("expiresAt"))),409,"PASS_EXPIRED");PassDao.cancel(c,now,admin,reason,id);p=PassDao.get(c,id);}
        return Map.of("passId",id,"cancelledAt",p.get("cancelledAt"),"cancelledBy",p.get("cancelledBy"),"reason",p.get("cancelReason"));
    }
}
