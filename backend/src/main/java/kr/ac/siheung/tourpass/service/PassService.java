package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
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
        return rows(c,"SELECT ib.*,m.active merchant_active,b.active benefit_active,r.used_at FROM issued_benefit ib JOIN merchant m ON m.id=ib.merchant_id JOIN benefit b ON b.id=ib.benefit_id LEFT JOIN redemption r ON r.issued_benefit_id=ib.id WHERE ib.pass_id=? ORDER BY ib.id",passId);
    }
    public Map<String,Object> detail(Connection c,String owner,String id,boolean admin) throws SQLException {
        var p=one(c,"SELECT * FROM issued_pass WHERE id=?"+(admin?"":" AND owner_id=?"),admin?new Object[]{id}:new Object[]{id,owner});require(p!=null,404,"NOT_FOUND");
        Instant now=Instant.now();p.put("status",status(p,now));var bs=benefits(c,id);
        for(var b:bs){String reason=availability(p,b,now);b.put("usable",reason.equals("AVAILABLE"));b.put("reason",reason);b.remove("merchantActive");b.remove("benefitActive");CatalogService.demo(b);}
        p.put("benefits",bs);if(admin){p.remove("code");p.put("redemptions",rows(c,RedemptionService.HISTORY+" WHERE p.id=? ORDER BY r.used_at DESC,r.id DESC",id));}return CatalogService.demo(p);
    }
    public List<Map<String,Object>> list(Connection c,String owner,boolean admin,Map<String,List<String>> q) throws SQLException {
        String filter=CatalogService.param(q,"status");if(filter!=null)Input.choice(filter,"ACTIVE","EXPIRED","CANCELLED","NOT_STARTED");
        var all=rows(c,"SELECT id,owner_id,product_id,product_name,starts_at,expires_at,cancelled_at,demo_only FROM issued_pass"+(owner==null?"":" WHERE owner_id=?")+" ORDER BY starts_at DESC,id DESC",owner==null?new Object[]{}:new Object[]{owner});Instant now=Instant.now();
        all.removeIf(p->{p.put("status",status(p,now));CatalogService.demo(p);return filter!=null&&!filter.equals(p.get("status"));});return all;
    }
    public Map<String,Object> issue(Connection c,String user,Map<String,Object> body) throws SQLException {
        Input.fields(body,"productId","productId");String productId=Input.id(body.get("productId"));
        var p=one(c,"SELECT * FROM product WHERE id=? FOR UPDATE",productId);require(p!=null,404,"NOT_FOUND");require(Boolean.TRUE.equals(p.get("active")),409,"PRODUCT_INACTIVE");
        var bs=rows(c,CatalogService.BENEFITS+" WHERE b.product_id=? AND b.active=TRUE AND m.active=TRUE AND p.active=TRUE AND p.review_status='APPROVED' AND p.material_kind='DEMO' AND r.active=TRUE ORDER BY b.id FOR UPDATE",productId);require(!bs.isEmpty(),409,"NO_ISSUABLE_BENEFITS");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        require(one(c,"SELECT id FROM issued_pass WHERE owner_id=? AND cancelled_at IS NULL AND starts_at<=? AND expires_at>? FOR UPDATE",user,now,now)==null,409,"ACTIVE_PASS_EXISTS");
        String id=null;for(int retry=0;retry<3;retry++)try{id=insert(c,"INSERT INTO issued_pass(owner_id,product_id,code,product_name,starts_at,expires_at) VALUES(?,?,?,?,?,?)",user,productId,AuthService.token(),p.get("name"),now,now.plus(Duration.ofHours(24)));break;}catch(SQLException e){if(e.getErrorCode()!=1062)throw e;}
        require(id!=null,503,"RETRY_LATER");
        String columns="pass_id,benefit_id,merchant_id,place_id,merchant_name,place_name,region_name,location_description,benefit_name,description,benefit_type,use_limit,base_price_won,discount_won,payable_won,reservation_required,reservation_note,demo_only";
        for(var b:bs)update(c,"INSERT INTO issued_benefit("+columns+") VALUES("+String.join(",",Collections.nCopies(18,"?"))+")",id,b.get("id"),b.get("merchantId"),b.get("placeId"),b.get("merchantName"),b.get("placeName"),b.get("regionName"),b.get("locationDescription"),b.get("name"),b.get("description"),b.get("benefitType"),b.get("useLimit"),b.get("basePriceWon"),b.get("discountWon"),b.get("payableWon"),b.get("reservationRequired"),b.get("reservationNote"),true);
        return detail(c,user,id,false);
    }
    public Map<String,Object> cancel(Connection c,String admin,String id,Map<String,Object> body) throws SQLException {
        Input.fields(body,"reason","reason");String reason=Input.text(body.get("reason"),500,false);var p=one(c,"SELECT * FROM issued_pass WHERE id=? FOR UPDATE",id);require(p!=null,404,"NOT_FOUND");
        if(p.get("cancelledAt")==null){Instant now=Instant.now();require(now.isBefore(Instant.parse((String)p.get("expiresAt"))),409,"PASS_EXPIRED");update(c,"UPDATE issued_pass SET cancelled_at=?,cancelled_by=?,cancel_reason=? WHERE id=?",now,admin,reason,id);p=one(c,"SELECT * FROM issued_pass WHERE id=?",id);}
        return Map.of("passId",id,"cancelledAt",p.get("cancelledAt"),"cancelledBy",p.get("cancelledBy"),"reason",p.get("cancelReason"));
    }
}
