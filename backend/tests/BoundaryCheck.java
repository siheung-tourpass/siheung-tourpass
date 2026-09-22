import kr.ac.siheung.tourpass.filter.SecurityFilter;
import java.time.Instant;
import java.util.*;
import kr.ac.siheung.tourpass.service.PassService;
import kr.ac.siheung.tourpass.service.AuthService;
import kr.ac.siheung.tourpass.service.Input;
import kr.ac.siheung.tourpass.service.CatalogService;
import kr.ac.siheung.tourpass.service.Problem;
import kr.ac.siheung.tourpass.dao.Sql;
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
    for(String path:List.of("/journeys","/journeys/detail","/passes","/passes/issue","/feedback/edit","/api/v1/me/passes","/api/v1/me/journeys"))assert "VISITOR".equals(SecurityFilter.requiredRole(path)):path;
    for(String path:List.of("/merchant/redeem","/api/v1/merchant/redemptions"))assert "STAFF".equals(SecurityFilter.requiredRole(path)):path;
    for(String path:List.of("/admin","/admin/catalog","/api/v1/admin/ontology/places"))assert "ADMIN".equals(SecurityFilter.requiredRole(path)):path;
    for(String path:List.of("/","/login","/places/detail","/recommendations","/journeys-extra","/admin-extra","/api/v1","/api/v1/session","/api/v1/recommendations","/api/v1/admin","/api/v1/me","/api/v1/me-extra/passes","/api/v1/journeys"))assert SecurityFilter.requiredRole(path)==null:path;
    for(String path:List.of("/api/v1","/api/v1/","/api/v1/session"))assert SecurityFilter.isApiPath(path):path;
    for(String path:List.of("/api/v1-extra","/api/v10","/api/v","/"))assert !SecurityFilter.isApiPath(path):path;
    assert Input.id("1").equals("1");
    assert Input.id("9223372036854775807").equals("9223372036854775807");
    for(String value:List.of("0","-1","01","9223372036854775808","9999999999999999999"))invalid(()->Input.id(value));
    assert Input.parseInteger("-2147483648")==Integer.MIN_VALUE;
    assert Input.parseInteger("+5")==5;
    for(String value:List.of("", " 1", "1.0", "2147483648"))invalid(()->Input.parseInteger(value));
    invalid(()->Input.parseInteger(null));
    invalid(()->Input.integer("5",1,5));
    assert Input.param(null)==null;
    for(String value:List.of("", "  ", "1"))assert Input.param(List.of(value)).equals(value);
    for(List<String> values:List.of(List.<String>of(),List.of("1","1"),List.of("1","2")))invalid(()->Input.param(values));
    var items=List.<Map<String,Object>>of(Map.of("id","1"),Map.of("id","2"));
    assert CatalogService.page(items,Map.of("size",List.of("1"),"page",List.of("2"))).get("items").equals(List.of(items.get(1)));
    assert CatalogService.page(items,Map.of("page",List.of("2147483647"))).get("items").equals(List.of());
    for(String value:List.of("", "0", "2147483648"))invalid(()->CatalogService.page(items,Map.of("page",List.of(value))));
    invalid(()->CatalogService.page(items,Map.of("page",List.of("1","1"))));
    assert Sql.camel("companion_type_id").equals("companionTypeId");
    assert Sql.camel("child_theme_id").equals("childThemeId");
    System.out.println("PASS: start/end boundaries and cancellation/merchant precedence");
    System.out.println("PASS: shared form/API roles and public path boundaries");
    System.out.println("PASS: single-value input, pagination and relation column names");
  }
  private static void invalid(Runnable action){
    try{action.run();throw new AssertionError("Expected INVALID_INPUT");}
    catch(Problem e){assert e.status==400&&e.code.equals("INVALID_INPUT");}
  }
}
