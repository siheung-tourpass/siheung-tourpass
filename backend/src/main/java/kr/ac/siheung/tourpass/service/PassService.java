package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.AuthDao;
import kr.ac.siheung.tourpass.dao.JourneyDao;
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
    public Map<String,Object> detail(Connection c,String owner,String id,boolean admin) throws SQLException {
        var p=PassDao.find(c,id,owner,admin);require(p!=null,404,"NOT_FOUND");
        Instant now=Instant.now();p.put("status",status(p,now));var bs=PassDao.benefits(c,id);
        for(var b:bs){String reason=availability(p,b,now);b.put("usable",reason.equals("AVAILABLE"));b.put("reason",reason);b.remove("merchantActive");b.remove("benefitActive");CatalogService.demo(b);}
        p.put("benefits",bs);if(admin){p.remove("code");p.put("redemptions",PassDao.history(c,id));}
        else p.put("refundable",refundBlock(c,p,bs,now,false)==null);
        return CatalogService.demo(p);
    }
    public List<Map<String,Object>> list(Connection c,String owner,Map<String,List<String>> q) throws SQLException {
        String filter=Input.param(q.get("status"));if(filter!=null)Input.choice(filter,"ACTIVE","EXPIRED","CANCELLED","NOT_STARTED");
        var all=PassDao.list(c,owner);Instant now=Instant.now();
        for(var p:all){p.put("status",status(p,now));CatalogService.demo(p);}
        if(filter!=null)all.removeIf(p->!filter.equals(p.get("status")));return all;
    }
    public Map<String,Object> issue(Connection c,String user,Map<String,Object> body) throws SQLException {
        Input.fields(body,"productId","productId,journeyId");
        return issue(c,user,Input.id(body.get("productId")),body.get("journeyId")==null?null:Input.id(body.get("journeyId")));
    }
    /** Demo purchase at the product price: from a trip dated after today the pass starts at that date's KST midnight and is linked to the trip. */
    public Map<String,Object> issue(Connection c,String user,String productId,String journeyId) throws SQLException {
        AuthDao.lockCurrentUser(c,user);
        var p=PassDao.lockProduct(c,productId);require(p!=null,404,"NOT_FOUND");require(Boolean.TRUE.equals(p.get("active")),409,"PRODUCT_INACTIVE");
        require(CatalogService.onSale(p),409,"PRODUCT_INACTIVE");
        var bs=PassDao.issuableBenefits(c,productId);require(!bs.isEmpty(),409,"NO_ISSUABLE_BENEFITS");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Map<String,Object> journey=null;
        if(journeyId!=null){journey=JourneyDao.findOwned(c,true,journeyId,user);require(journey!=null,404,"NOT_FOUND");}
        Instant start=start(journey,now),end=start.plus(Duration.ofHours(24));
        require(PassDao.overlappingPass(c,user,start,end)==null,409,"ACTIVE_PASS_EXISTS");
        String id=null;for(int retry=0;retry<3&&id==null;retry++){id=PassDao.create(c,user,productId,AuthService.token(),p.get("name"),p.get("priceWon"),start,end);}
        require(id!=null,503,"RETRY_LATER");
        PassDao.snapshotBenefits(c,id,bs);
        if(journey!=null){JourneyDao.disconnectPass(c,now,journeyId);JourneyDao.connectPass(c,journeyId,id,now);JourneyDao.save(c,journeyId,Map.of("issuedPassId",id));}
        return detail(c,user,id,false);
    }
    /** A trip dated after today starts at that date's KST midnight; anything else starts now. */
    public static Instant start(Map<String,Object> journey,Instant now){
        if(journey==null||journey.get("travelDate")==null)return now;
        Instant day=LocalDate.parse((String)journey.get("travelDate")).atStartOfDay(KST).toInstant();return day.isAfter(now)?day:now;
    }
    /** Whether the owner already holds an uncancelled pass for any part of the period. */
    public boolean overlaps(Connection c,String user,Instant start,Instant end) throws SQLException {return PassDao.overlappingPass(c,user,start,end)!=null;}
    private static final ZoneId KST=ZoneId.of("Asia/Seoul");
    public static final String REFUND_REASON="방문객 환불";
    /** The owner may refund a pass that is unexpired and wholly unused; the pass row lock serializes this against redemption. */
    public Map<String,Object> refund(Connection c,String owner,String id) throws SQLException {
        var p=PassDao.lock(c,id);require(p!=null&&owner.equals(String.valueOf(p.get("ownerId"))),404,"NOT_FOUND");
        Instant now=Instant.now();String block=refundBlock(c,p,PassDao.benefits(c,id),now,true);require(block==null,409,block);
        PassDao.cancel(c,now,owner,REFUND_REASON,id);JourneyDao.detachPass(c,now,id);p=PassDao.get(c,id);
        return Map.of("passId",id,"cancelledAt",p.get("cancelledAt"),"cancelledBy",p.get("cancelledBy"),"reason",p.get("cancelReason"));
    }
    /** Why the pass cannot be refunded now, or null; locks the finished-trip check when refunding. */
    private static String refundBlock(Connection c,Map<String,Object> p,List<Map<String,Object>> benefits,Instant now,boolean lock) throws SQLException {
        if(p.get("cancelledAt")!=null)return "PASS_CANCELLED";
        if(!now.isBefore(Instant.parse((String)p.get("expiresAt"))))return "PASS_EXPIRED";
        if(benefits.stream().anyMatch(b->b.get("usedAt")!=null||!java.util.Objects.equals(b.get("remainingCount"),b.get("useLimit"))))return "PASS_USED";
        if(JourneyDao.completedWithPass(c,p.get("id"),lock)!=null)return "PASS_TRIP_COMPLETED";
        return null;
    }
    public Map<String,Object> cancel(Connection c,String admin,String id,Map<String,Object> body) throws SQLException {
        Input.fields(body,"reason");String reason=Input.text(body.get("reason"),500,false);var p=PassDao.lock(c,id);require(p!=null,404,"NOT_FOUND");
        if(p.get("cancelledAt")==null){Instant now=Instant.now();require(now.isBefore(Instant.parse((String)p.get("expiresAt"))),409,"PASS_EXPIRED");PassDao.cancel(c,now,admin,reason,id);p=PassDao.get(c,id);}
        return Map.of("passId",id,"cancelledAt",p.get("cancelledAt"),"cancelledBy",p.get("cancelledBy"),"reason",p.get("cancelReason"));
    }
}
