package kr.ac.siheung.tourpass.service;

import java.util.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Fixed starting sets, resolved against currently selectable catalog cards. */
public final class JourneyTemplates {
    private static final List<Map<String,Object>> TEMPLATES=List.of(
        Map.of("code","oido","name","오이도 둘러보기","description","유적공원 · 박물관 · 빨강등대","placeCodes",List.of("PLACE_REAL_002","PLACE_REAL_003","PLACE_REAL_016")),
        Map.of("code","gardens","name","공원과 연꽃","description","갯골생태공원 · 연꽃테마파크 · 관곡지","placeCodes",List.of("PLACE_REAL_001","PLACE_REAL_004","PLACE_REAL_005")),
        Map.of("code","baegot","name","배곧 산책","description","배곧한울공원 · 시흥에코센터 초록배곧","placeCodes",List.of("PLACE_REAL_007","PLACE_REAL_015"))
    );
    public static List<Map<String,Object>> list(List<Map<String,Object>> places) {
        var available=new HashSet<Object>();places.forEach(place->available.add(place.get("code")));
        var result=new ArrayList<Map<String,Object>>();
        for(var template:TEMPLATES){
            var item=new LinkedHashMap<>(template);
            var codes=(List<?>)template.get("placeCodes");
            item.put("available",available.containsAll(codes));item.put("cardCount",codes.size());result.add(item);
        }
        return result;
    }
    public static Map<String,Object> find(String code,List<Map<String,Object>> places) {
        var template=list(places).stream().filter(item->code.equals(item.get("code"))).findFirst().orElse(null);
        require(template!=null,400,"INVALID_INPUT");
        require(Boolean.TRUE.equals(template.get("available")),409,"TEMPLATE_UNAVAILABLE");
        return template;
    }
}
