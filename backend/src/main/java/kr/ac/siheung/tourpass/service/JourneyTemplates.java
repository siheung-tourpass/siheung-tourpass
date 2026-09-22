package kr.ac.siheung.tourpass.service;

import java.util.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Fixed starting sets, resolved against currently selectable catalog cards. */
public final class JourneyTemplates {
    private record Template(String code,String name,String description,List<String> placeCodes){}
    private static final List<Template> TEMPLATES=List.of(
        new Template("oido","오이도 둘러보기","유적공원 · 박물관 · 빨강등대",List.of("PLACE_REAL_002","PLACE_REAL_003","PLACE_REAL_016")),
        new Template("gardens","공원과 연꽃","갯골생태공원 · 연꽃테마파크 · 관곡지",List.of("PLACE_REAL_001","PLACE_REAL_004","PLACE_REAL_005")),
        new Template("baegot","배곧 산책","배곧한울공원 · 시흥에코센터 초록배곧",List.of("PLACE_REAL_007","PLACE_REAL_015"))
    );
    public static Map<Object,Map<String,Object>> index(List<Map<String,Object>> places) {
        var byCode=new LinkedHashMap<Object,Map<String,Object>>();places.forEach(place->byCode.put(place.get("code"),place));return byCode;
    }
    public static List<Map<String,Object>> list(List<Map<String,Object>> places) {
        var byCode=index(places);
        return TEMPLATES.stream().map(template->resolve(template,byCode)).toList();
    }
    private static Map<String,Object> resolve(Template template,Map<Object,Map<String,Object>> byCode) {
        var item=new LinkedHashMap<String,Object>(Map.of("code",template.code(),"name",template.name(),"description",template.description(),"placeCodes",template.placeCodes()));
        var codes=template.placeCodes();
        boolean available=byCode.keySet().containsAll(codes);
        item.put("available",available);item.put("cardCount",codes.size());
        if(available)for(Object code:codes){
            var place=byCode.get(code);
            if(place.get("image")!=null){item.put("coverImage",place.get("image"));item.put("coverKind","PLACE");break;}
        }
        return item;
    }
    public static Map<String,Object> find(String code,List<Map<String,Object>> places) {
        var template=TEMPLATES.stream().filter(item->code.equals(item.code())).findFirst().orElse(null);
        require(template!=null,400,"INVALID_INPUT");
        var item=resolve(template,index(places));
        require(Boolean.TRUE.equals(item.get("available")),409,"TEMPLATE_UNAVAILABLE");
        return item;
    }
}
