package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class RedemptionService {
    public record Confirmation(String token,String staffId,String merchantId,String passId,Instant expiresAt,boolean used) {
        public Confirmation consume(){return new Confirmation(token,staffId,merchantId,passId,expiresAt,true);}
    }
    public record Lookup(Map<String,Object> response,Confirmation confirmation){}
    public List<Map<String,Object>> assignments(Connection c,String user) throws SQLException {return rows(c,"SELECT m.id merchant_id,m.name,m.active FROM merchant_staff ms JOIN merchant m ON m.id=ms.merchant_id WHERE ms.user_id=? AND ms.active=TRUE ORDER BY m.id",user);}
    public void assigned(Connection c,String user,String merchant) throws SQLException {require(one(c,"SELECT user_id FROM merchant_staff WHERE user_id=? AND merchant_id=? AND active=TRUE FOR UPDATE",user,merchant)!=null,403,"FORBIDDEN");}
    public Lookup lookup(Connection c,String user,Map<String,Object> body) throws SQLException {
        Input.fields(body,"merchantId,code","merchantId,code");String merchant=Input.id(body.get("merchantId")),code=Input.text(body.get("code"),32,false);assigned(c,user,merchant);
        var p=one(c,"SELECT p.* FROM issued_pass p WHERE p.code=? AND EXISTS(SELECT 1 FROM issued_benefit ib WHERE ib.pass_id=p.id AND ib.merchant_id=?)",code,merchant);require(p!=null,404,"PASS_CODE_NOT_FOUND");
        Instant now=Instant.now();var bs=new ArrayList<Map<String,Object>>();for(var b:PassService.benefits(c,(String)p.get("id")))if(merchant.equals(b.get("merchantId"))){String reason=PassService.availability(p,b,now);bs.add(Map.of("issuedBenefitId",b.get("id"),"benefitName",b.get("benefitName"),"remainingCount",b.get("remainingCount"),"usable",reason.equals("AVAILABLE"),"reason",reason));}
        var token=new Confirmation(AuthService.token(),user,merchant,(String)p.get("id"),now.plusSeconds(300),false);
        return new Lookup(Map.of("confirmationToken",token.token(),"confirmationExpiresAt",token.expiresAt().toString(),"benefits",bs),token);
    }
    public Map<String,Object> confirm(Connection c,String user,Confirmation token,Map<String,Object> body) throws SQLException {
        Input.fields(body,"confirmationToken,issuedBenefitId","confirmationToken,issuedBenefitId");String supplied=Input.text(body.get("confirmationToken"),32,false),id=Input.id(body.get("issuedBenefitId"));
        require(token!=null&&token.token().equals(supplied)&&token.staffId().equals(user),409,"CONFIRMATION_INVALID");require(!token.used(),409,"CONFIRMATION_USED");require(Instant.now().isBefore(token.expiresAt()),409,"CONFIRMATION_EXPIRED");assigned(c,user,token.merchantId());
        var ib=one(c,"SELECT * FROM issued_benefit WHERE id=?",id);require(ib!=null&&token.merchantId().equals(ib.get("merchantId"))&&token.passId().equals(ib.get("passId")),403,"FORBIDDEN");
        one(c,"SELECT id FROM merchant WHERE id=? FOR UPDATE",token.merchantId());one(c,"SELECT id FROM benefit WHERE id=? FOR UPDATE",ib.get("benefitId"));
        var p=one(c,"SELECT * FROM issued_pass WHERE id=? FOR UPDATE",token.passId());one(c,"SELECT id FROM issued_benefit WHERE id=? FOR UPDATE",id);
        var b=PassService.benefits(c,token.passId()).stream().filter(x->id.equals(x.get("id"))).findFirst().orElseThrow();Instant now=Instant.now();String reason=PassService.availability(p,b,now);require(reason.equals("AVAILABLE"),409,reason);
        require(update(c,"UPDATE issued_benefit SET remaining_count=remaining_count-1 WHERE id=? AND remaining_count>0",id)==1,409,"BENEFIT_ALREADY_USED");
        String rid=insert(c,"INSERT INTO redemption(issued_benefit_id,staff_user_id,used_at) VALUES(?,?,?)",id,user,now);return detail(c,user,rid,true);
    }
    public static final String HISTORY="SELECT r.id,ib.pass_id,ib.id issued_benefit_id,ib.merchant_id,ib.merchant_name,ib.benefit_name,r.used_at,(f.id IS NOT NULL) has_feedback FROM redemption r JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass p ON p.id=ib.pass_id LEFT JOIN feedback f ON f.redemption_id=r.id";
    public List<Map<String,Object>> history(Connection c,String user,boolean staff,String filter) throws SQLException {
        if(staff){require(filter!=null,400,"INVALID_INPUT");assigned(c,user,Input.id(filter));}
        else if(filter!=null)require(one(c,"SELECT id FROM issued_pass WHERE id=? AND owner_id=?",Input.id(filter),user)!=null,404,"NOT_FOUND");
        String where=staff?" WHERE ib.merchant_id=?":" WHERE p.owner_id=?";var args=new ArrayList<Object>();args.add(staff?filter:user);
        if(!staff&&filter!=null){where+=" AND p.id=?";args.add(filter);}var r=rows(c,HISTORY+where+" ORDER BY r.used_at DESC,r.id DESC",args.toArray());r.forEach(x->{x.put("result","SUCCESS");if(staff)x.remove("hasFeedback");});return r;
    }
    public Map<String,Object> detail(Connection c,String user,String id,boolean staff) throws SQLException {
        var r=one(c,HISTORY+" WHERE r.id=?"+(!staff?" AND p.owner_id=?":""),staff?new Object[]{id}:new Object[]{id,user});require(r!=null,404,"NOT_FOUND");
        if(staff){require(one(c,"SELECT user_id FROM merchant_staff WHERE user_id=? AND merchant_id=? AND active=TRUE",user,r.get("merchantId"))!=null,404,"NOT_FOUND");r.remove("hasFeedback");}r.put("result","SUCCESS");return r;
    }
}
