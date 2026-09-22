package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static kr.ac.siheung.tourpass.dao.Sql.*;
public final class RecommendationService {
    private record Feedback(String placeId,int rating,Set<String> themes,Set<String> reasons){}
    private record Ranked(Map<String,Object> result,int base,int sum,int count,long id){}
    public Map<String,Object> recommend(Connection c,String user,String role,Map<String,List<String>> q) throws SQLException {
        String region=Input.param(q.get("regionId")),companion=Input.param(q.get("companionId"));CatalogService.concept(c,"region",region);CatalogService.concept(c,"companion_type",companion);
        var selected=new LinkedHashSet<>(q.getOrDefault("themeId",List.of()));for(String id:selected)CatalogService.concept(c,"theme",id);
        var themes=new HashMap<String,String>();for(var t:rows(c,"SELECT id,name FROM theme"))themes.put((String)t.get("id"),(String)t.get("name"));
        boolean visitor="VISITOR".equals(role);var feedback=visitor?rows(c,"SELECT f.id,f.rating,ib.place_id,ft.theme_id,fr.reason_code FROM feedback f JOIN redemption r ON r.id=f.redemption_id JOIN issued_benefit ib ON ib.id=r.issued_benefit_id JOIN issued_pass ip ON ip.id=ib.pass_id LEFT JOIN feedback_theme ft ON ft.feedback_id=f.id LEFT JOIN feedback_reason fr ON fr.feedback_id=f.id WHERE ip.owner_id=?",user):List.<Map<String,Object>>of();
        var evaluations=new LinkedHashMap<String,Feedback>();
        for(var row:feedback){
            var evaluation=evaluations.computeIfAbsent((String)row.get("id"),key->new Feedback((String)row.get("placeId"),((Number)row.get("rating")).intValue(),new HashSet<>(),new TreeSet<>()));
            if(row.get("themeId")!=null)evaluation.themes().add((String)row.get("themeId"));
            if(row.get("reasonCode")!=null)evaluation.reasons().add((String)row.get("reasonCode"));
        }
        var candidates=rows(c,"SELECT p.* FROM place p JOIN region r ON r.id=p.region_id WHERE p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE"+(region==null?"":" AND p.region_id=?")+" AND (NOT EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id) OR EXISTS(SELECT 1 FROM merchant m WHERE m.place_id=p.id AND m.active=TRUE)) ORDER BY p.id",region==null?new Object[]{}:new Object[]{region});
        var availability=visitor&&!candidates.isEmpty()?passAvailability(c,user):Map.<String,List<Map<String,Object>>>of();
        var ranked=new ArrayList<Ranked>();var graph=new OntologyService().graph(c);
        for(var p:candidates){String id=(String)p.get("id");var paths=graph.paths(c,id);var matches=new ArrayList<Map<String,Object>>();var explanations=new ArrayList<String>();
            for(String theme:selected)if(paths.containsKey(theme)){var path=paths.get(theme);matches.add(Map.of("selectedThemeId",theme,"matchType",path.size()==1?"DIRECT":"INFERRED","path",path));explanations.add(themes.get(theme)+" +2("+String.join(" → ",path)+")");}
            boolean companionMatched=companion!=null&&one(c,"SELECT place_id FROM place_companion WHERE place_id=? AND companion_type_id=? AND active=TRUE AND review_status='APPROVED'",id,companion)!=null;
            if(companionMatched)explanations.add("동반 유형 +1");int base=2*matches.size()+(companionMatched?1:0);int count=0,sum=0;
            for(var evaluation:evaluations.values()){
                var selectedReasons=evaluation.reasons();boolean samePlace=id.equals(evaluation.placeId());
                boolean themeMatch=(selectedReasons.isEmpty()||selectedReasons.contains("INTEREST"))&&evaluation.themes().stream().anyMatch(paths::containsKey);
                if(samePlace||themeMatch){
                    int adjustment=evaluation.rating()-3;sum+=adjustment;count++;
                    String labels=selectedReasons.isEmpty()?"사유 미선택":String.join("·",selectedReasons.stream().map(RecommendationService::reasonLabel).toList());
                    explanations.add("본인 평가 적용: "+(samePlace?"같은 장소":"공통 테마")+" / "+labels+" / "+adjustment);
                }
            }
            double personal=count==0?0:(double)sum/count;
            var result=new LinkedHashMap<String,Object>();result.put("place",PlaceImages.attach(CatalogService.demo(p)));result.put("matchedThemes",matches);result.put("companionMatched",companionMatched);result.put("score",Map.of("base",base,"personal",rounded(personal),"total",rounded(base+personal),"feedbackCount",count));
            String reason=user==null?"ANONYMOUS":!visitor?"NOT_VISITOR":feedback.isEmpty()?"NO_FEEDBACK":count==0?"NO_THEME_OVERLAP":"APPLIED";result.put("personalizationReason",reason);if(count>0)explanations.add("본인 평가 "+count+"건 "+rounded(personal));explanations.add("합계 "+rounded(base+personal));result.put("explanation",String.join(", ",explanations));
            ranked.add(new Ranked(result,base,sum,count==0?1:count,Long.parseLong(id)));
        }
        ranked.sort((a,b)->{long an=(long)a.base*a.count+a.sum,bn=(long)b.base*b.count+b.sum;int cmp=Long.compare(bn*a.count,an*b.count);return cmp!=0?cmp:Long.compare(a.id,b.id);});
        var results=new ArrayList<Map<String,Object>>();
        for(var entry:ranked.stream().limit(3).toList()){
            var result=entry.result();var p=Input.object(result.get("place"));String id=(String)p.get("id");
            p.put("region",one(c,"SELECT id,name FROM region WHERE id=?",p.get("regionId")));
            result.put("passBenefits",rows(c,"SELECT b.id benefit_id,b.demo_only FROM benefit b JOIN merchant m ON m.id=b.merchant_id JOIN product pr ON pr.id=b.product_id WHERE m.place_id=? AND m.active=TRUE AND b.active=TRUE AND pr.active=TRUE ORDER BY b.id",id).stream().map(CatalogService::demo).toList());
            result.put("myPassAvailability",visitor?availability.getOrDefault(id,List.of()):null);
            results.add(result);
        }
        var out=new LinkedHashMap<String,Object>();out.put("modelRevision",one(c,"SELECT revision FROM ontology_revision WHERE id=1").get("revision"));out.put("items",results);out.put("emptyReason",ranked.isEmpty()?"NO_MATCHING_PLACE":null);if(ranked.isEmpty())out.put("message","조건을 변경해 주세요.");out.put("notice","추천은 영업·예약·이동 가능성을 보장하지 않습니다.");return out;
    }
    private static Map<String,List<Map<String,Object>>> passAvailability(Connection c,String user) throws SQLException {
        var byPlace=new HashMap<String,List<Map<String,Object>>>();
        for(var pass:rows(c,"SELECT * FROM issued_pass WHERE owner_id=? ORDER BY id",user)){
            for(var benefit:PassService.benefits(c,(String)pass.get("id"))){
                Instant now=Instant.now();
                String reason=PassService.availability(pass,benefit,now);
                byPlace.computeIfAbsent((String)benefit.get("placeId"),key->new ArrayList<>()).add(Map.of(
                    "passId",pass.get("id"),"issuedBenefitId",benefit.get("id"),"usable",reason.equals("AVAILABLE"),"reason",reason,"checkedAt",now.toString()));
            }
        }
        return byPlace;
    }
    private static String reasonLabel(String reason){return switch(reason){case "INTEREST"->"흥미";case "COMPANION_FIT"->"동반 적합";case "COST"->"비용";case "GUIDANCE"->"안내";default->throw new IllegalArgumentException(reason);};}
    private static BigDecimal rounded(double n){return BigDecimal.valueOf(n).setScale(2,RoundingMode.HALF_UP);}
}
