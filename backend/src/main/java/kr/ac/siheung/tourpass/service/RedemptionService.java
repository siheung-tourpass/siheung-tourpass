package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.RedemptionDao;
import kr.ac.siheung.tourpass.dao.PassDao;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class RedemptionService {
    public record Confirmation(String token,String staffId,String merchantId,String passId,Instant expiresAt,boolean used) {
        public Confirmation consume(){return new Confirmation(token,staffId,merchantId,passId,expiresAt,true);}
    }
    public record Lookup(Map<String,Object> response,Confirmation confirmation){}
    public List<Map<String,Object>> assignments(Connection c,String user) throws SQLException {return RedemptionDao.assignments(c,user);}
    public void assigned(Connection c,String user,String merchant) throws SQLException {require(RedemptionDao.lockAssignment(c,user,merchant)!=null,403,"FORBIDDEN");}
    public Lookup lookup(Connection c,String user,Map<String,Object> body) throws SQLException {
        Input.fields(body,"merchantId,code");String merchant=Input.id(body.get("merchantId")),code=Input.text(body.get("code"),32,false);assigned(c,user,merchant);
        var p=RedemptionDao.lookup(c,code,merchant);require(p!=null,404,"PASS_CODE_NOT_FOUND");
        Instant now=Instant.now();var bs=new ArrayList<Map<String,Object>>();for(var b:PassDao.benefits(c,(String)p.get("id")))if(merchant.equals(b.get("merchantId"))){String reason=PassService.availability(p,b,now);bs.add(Map.of("issuedBenefitId",b.get("id"),"benefitName",b.get("benefitName"),"remainingCount",b.get("remainingCount"),"usable",reason.equals("AVAILABLE"),"reason",reason));}
        var token=new Confirmation(AuthService.token(),user,merchant,(String)p.get("id"),now.plusSeconds(300),false);
        return new Lookup(Map.of("confirmationToken",token.token(),"confirmationExpiresAt",token.expiresAt().toString(),"benefits",bs),token);
    }
    public Map<String,Object> createLookup(Connection c,String user,Map<String,Object> body) throws SQLException {
        var result=lookup(c,user,body);var token=result.confirmation();
        RedemptionDao.createLookup(c,token.token(),user,token.merchantId(),token.passId(),token.expiresAt());
        return getLookup(c,user,token.token());
    }
    public Map<String,Object> getLookup(Connection c,String user,String id) throws SQLException {
        var token=storedLookup(c,user,id,false);assigned(c,user,token.merchantId());
        var pass=PassDao.get(c,token.passId());var benefits=new ArrayList<Map<String,Object>>();
        for(var benefit:PassDao.benefits(c,token.passId()))if(token.merchantId().equals(benefit.get("merchantId"))){
            String reason=PassService.availability(pass,benefit,Instant.now());
            benefits.add(Map.of("issuedBenefitId",benefit.get("id"),"benefitName",benefit.get("benefitName"),"remainingCount",benefit.get("remainingCount"),"usable",reason.equals("AVAILABLE"),"reason",reason));
        }
        return new LinkedHashMap<>(Map.of("id",id,"confirmationToken",id,"confirmationExpiresAt",token.expiresAt().toString(),"used",token.used(),"benefits",benefits));
    }
    private Confirmation storedLookup(Connection c,String user,String id,boolean lock) throws SQLException {
        require(id!=null&&id.matches("[A-Za-z0-9_-]{22}"),404,"NOT_FOUND");
        var row=RedemptionDao.passLookup(c,id,user,lock);require(row!=null,404,"NOT_FOUND");
        return new Confirmation(id,user,(String)row.get("merchantId"),(String)row.get("passId"),Instant.parse((String)row.get("expiresAt")),Boolean.TRUE.equals(row.get("used")));
    }
    public Map<String,Object> confirmLookup(Connection c,String user,Map<String,Object> body) throws SQLException {
        String id=Input.text(body.get("confirmationToken"),32,false);
        var token=storedLookup(c,user,id,true);var result=confirm(c,user,token,body);
        RedemptionDao.consumeLookup(c,id);return result;
    }
    public Map<String,Object> confirm(Connection c,String user,Confirmation token,Map<String,Object> body) throws SQLException {
        Input.fields(body,"confirmationToken,issuedBenefitId");String supplied=Input.text(body.get("confirmationToken"),32,false),id=Input.id(body.get("issuedBenefitId"));
        require(token!=null&&token.token().equals(supplied)&&token.staffId().equals(user),409,"CONFIRMATION_INVALID");require(!token.used(),409,"CONFIRMATION_USED");require(Instant.now().isBefore(token.expiresAt()),409,"CONFIRMATION_EXPIRED");assigned(c,user,token.merchantId());
        var ib=RedemptionDao.issuedBenefit(c,id);require(ib!=null&&token.merchantId().equals(ib.get("merchantId"))&&token.passId().equals(ib.get("passId")),403,"FORBIDDEN");
        RedemptionDao.lockMerchant(c,token.merchantId());RedemptionDao.lockBenefit(c,ib.get("benefitId"));
        var p=PassDao.lock(c,token.passId());RedemptionDao.lockIssuedBenefit(c,id);
        var b=PassDao.benefit(c,token.passId(),id);Instant now=Instant.now();String reason=PassService.availability(p,b,now);require(reason.equals("AVAILABLE"),409,reason);
        require(RedemptionDao.decrementBenefit(c,id)==1,409,"BENEFIT_ALREADY_USED");
        String rid=RedemptionDao.create(c,id,user,now);return detail(c,user,rid,true);
    }
    public List<Map<String,Object>> history(Connection c,String user,boolean staff,String filter) throws SQLException {
        if(staff){require(filter!=null,400,"INVALID_INPUT");assigned(c,user,Input.id(filter));}
        else if(filter!=null)require(PassDao.owned(c,Input.id(filter),user)!=null,404,"NOT_FOUND");
        var r=RedemptionDao.history(c,user,staff,filter);r.forEach(x->{x.put("result","SUCCESS");if(staff)x.remove("hasFeedback");});return r;
    }
    public Map<String,Object> detail(Connection c,String user,String id,boolean staff) throws SQLException {
        var r=RedemptionDao.detail(c,id,user,staff);require(r!=null,404,"NOT_FOUND");
        if(staff){require(RedemptionDao.assignment(c,user,r.get("merchantId"))!=null,404,"NOT_FOUND");r.remove("hasFeedback");}r.put("result","SUCCESS");return r;
    }
}
