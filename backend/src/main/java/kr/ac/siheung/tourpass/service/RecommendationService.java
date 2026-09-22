package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static kr.ac.siheung.tourpass.dao.Sql.*;
public final class RecommendationService {
    private record Ranked(Map<String,Object> result,int base,int sum,int count,long id){}
    public Map<String,Object> recommend(Connection c,String user,String role,Map<String,List<String>> q) throws SQLException {
        String region=CatalogService.param(q,"regionId"),companion=CatalogService.param(q,"companionId");CatalogService.concept(c,"region",region);CatalogService.concept(c,"companion_type",companion);
        var selected=new LinkedHashSet<>(q.getOrDefault("themeId",List.of()));for(String id:selected)CatalogService.concept(c,"theme",id);
        var themes=new HashMap<String,String>();for(var t:rows(c,"SELECT id,name FROM theme"))themes.put((String)t.get("id"),(String)t.get("name"));
        boolean visitor="VISITOR".equals(role);var feedback=visitor?rows(c,"SELECT f.id,f.rating,ft.theme_id FROM feedback f JOIN redemption r ON r.id=f.redemption_id JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass ip ON ip.id=ib.pass_id LEFT JOIN feedback_theme ft ON ft.feedback_id=f.id WHERE ip.owner_id=?",user):List.<Map<String,Object>>of();
        var candidates=rows(c,"SELECT p.* FROM place p JOIN region r ON r.id=p.region_id WHERE p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE"+(region==null?"":" AND p.region_id=?")+" AND (NOT EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id) OR EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id AND m.active=TRUE)) ORDER BY p.id",region==null?new Object[]{}:new Object[]{region});
        var ranked=new ArrayList<Ranked>();var ontology=new OntologyService();
        for(var p:candidates){String id=(String)p.get("id");var paths=ontology.paths(c,id);var matches=new ArrayList<Map<String,Object>>();var explanations=new ArrayList<String>();
            for(String theme:selected)if(paths.containsKey(theme)){var path=paths.get(theme);matches.add(Map.of("selectedThemeId",theme,"matchType",path.size()==1?"DIRECT":"INFERRED","path",path));explanations.add(themes.get(theme)+" +2("+String.join(" → ",path)+")");}
            boolean companionMatched=companion!=null&&one(c,"SELECT place_id FROM place_companion WHERE place_id=? AND companion_type_id=? AND active=TRUE AND review_status='APPROVED'",id,companion)!=null;
            if(companionMatched)explanations.add("동반 유형 +1");int base=2*matches.size()+(companionMatched?1:0);var used=new HashMap<String,Integer>();for(var f:feedback)if(paths.containsKey(f.get("themeId")))used.put((String)f.get("id"),((Number)f.get("rating")).intValue()-3);
            int count=used.size(),sum=used.values().stream().mapToInt(Integer::intValue).sum();double personal=count==0?0:(double)sum/count;
            var result=new LinkedHashMap<String,Object>();p.put("region",one(c,"SELECT id,name FROM region WHERE id=?",p.get("regionId")));result.put("place",CatalogService.demo(p));result.put("matchedThemes",matches);result.put("companionMatched",companionMatched);result.put("score",Map.of("base",base,"personal",rounded(personal),"total",rounded(base+personal),"feedbackCount",count));
            String reason=user==null?"ANONYMOUS":!visitor?"NOT_VISITOR":feedback.isEmpty()?"NO_FEEDBACK":count==0?"NO_THEME_OVERLAP":"APPLIED";result.put("personalizationReason",reason);if(count>0)explanations.add("본인 평가 "+count+"건 "+rounded(personal));explanations.add("합계 "+rounded(base+personal));result.put("explanation",String.join(", ",explanations));
            result.put("passBenefits",rows(c,"SELECT b.id benefit_id,b.demo_only FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN product pr ON pr.id=b.product_id WHERE m.place_id=? AND m.active=TRUE AND b.active=TRUE AND pr.active=TRUE ORDER BY b.id",id).stream().map(CatalogService::demo).toList());
            if(visitor){var available=new ArrayList<Map<String,Object>>();for(var pass:rows(c,"SELECT * FROM issued_pass WHERE owner_id=? ORDER BY id",user)){for(var b:PassService.benefits(c,(String)pass.get("id")))if(id.equals(b.get("placeId"))){Instant now=Instant.now();String why=PassService.availability(pass,b,now);available.add(Map.of("passId",pass.get("id"),"issuedBenefitId",b.get("id"),"usable",why.equals("AVAILABLE"),"reason",why,"checkedAt",now.toString()));}}result.put("myPassAvailability",available);}else result.put("myPassAvailability",null);
            ranked.add(new Ranked(result,base,sum,count==0?1:count,Long.parseLong(id)));
        }
        ranked.sort((a,b)->{long an=(long)a.base*a.count+a.sum,bn=(long)b.base*b.count+b.sum;int cmp=Long.compare(bn*a.count,an*b.count);return cmp!=0?cmp:Long.compare(a.id,b.id);});
        var out=new LinkedHashMap<String,Object>();out.put("modelRevision",one(c,"SELECT revision FROM ontology_revision WHERE id=1").get("revision"));out.put("items",ranked.stream().limit(3).map(Ranked::result).toList());out.put("emptyReason",ranked.isEmpty()?"NO_MATCHING_PLACE":null);if(ranked.isEmpty())out.put("message","조건을 변경해 주세요.");out.put("notice","추천은 영업·예약·이동 가능성을 보장하지 않습니다.");return out;
    }
    private static BigDecimal rounded(double n){return BigDecimal.valueOf(n).setScale(2,RoundingMode.HALF_UP);}
}
