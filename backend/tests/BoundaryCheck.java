import java.time.Instant;
import java.util.*;
import kr.ac.siheung.tourpass.service.PassService;
import kr.ac.siheung.tourpass.service.AuthService;
public class BoundaryCheck {
  public static void main(String[] args){
    var p=new HashMap<String,Object>();p.put("startsAt","2026-10-03T00:00:00Z");p.put("expiresAt","2026-10-04T00:00:00Z");
    var b=new HashMap<String,Object>();b.put("merchantActive",true);b.put("benefitActive",true);b.put("remainingCount",1);
    assert PassService.status(p,Instant.parse("2026-10-02T23:59:59.999999Z")).equals("NOT_STARTED");
    assert PassService.availability(p,b,Instant.parse("2026-10-03T00:00:00Z")).equals("AVAILABLE");
    assert PassService.availability(p,b,Instant.parse("2026-10-03T23:59:59.999999Z")).equals("AVAILABLE");
    assert PassService.availability(p,b,Instant.parse("2026-10-04T00:00:00Z")).equals("PASS_EXPIRED");
    p.put("cancelledAt","2026-10-03T01:00:00Z");assert PassService.status(p,Instant.parse("2026-10-05T00:00:00Z")).equals("CANCELLED");
    b.put("merchantActive",false);assert PassService.availability(p,b,Instant.now()).equals("MERCHANT_INACTIVE");
    for(String path:List.of("/journeys","/journeys/detail","/passes","/passes/issue","/feedback/edit","/api/v1/me/passes","/api/v1/me/journeys"))assert "VISITOR".equals(AuthService.requiredRole(path)):path;
    for(String path:List.of("/merchant/redeem","/api/v1/merchant/redemptions"))assert "STAFF".equals(AuthService.requiredRole(path)):path;
    for(String path:List.of("/admin","/admin/catalog","/api/v1/admin/ontology/places"))assert "ADMIN".equals(AuthService.requiredRole(path)):path;
    for(String path:List.of("/","/login","/places/detail","/recommendations","/journeys-extra","/admin-extra","/api/v1","/api/v1/session","/api/v1/recommendations","/api/v1/admin","/api/v1/me","/api/v1/me-extra/passes","/api/v1/journeys"))assert AuthService.requiredRole(path)==null:path;
    System.out.println("PASS: start/end boundaries and cancellation/merchant precedence");
    System.out.println("PASS: shared form/API roles and public path boundaries");
  }
}
