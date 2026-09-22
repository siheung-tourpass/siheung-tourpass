package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;

/** Source-backed representative points; never used as walking routes or visit evidence. */
public final class CardCoordinates {
    private static final Map<String,Map<String,Object>> POINTS=load();
    private CardCoordinates() {}

    private static Map<String,Map<String,Object>> load(){
        var points=new HashMap<String,Map<String,Object>>();
        var mapper=new ObjectMapper();
        for(String kind:List.of("tourism","events","dining")){
            var data=CatalogResources.load("siheung-"+kind+".json");
            for(var card:data.path(kind.equals("events")?"events":"places")){
                var point=(kind.equals("tourism")?card.path("operational"):card).path("coordinates");
                if(point.isMissingNode()||point.isNull())continue;
                double lat=point.path("latitude").asDouble(Double.NaN),lon=point.path("longitude").asDouble(Double.NaN);
                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||!point.path("crs").asText().equals("WGS84"))
                    throw new IllegalStateException("Invalid coordinates: "+card.path("code").asText());
                Input.url(point.path("sourceUrl").asText(),false);
                LocalDate.parse(point.path("checkedOn").asText());
                Map<String,Object> value=mapper.convertValue(point,new TypeReference<>(){});
                points.put(card.path("code").asText(),Map.copyOf(value));
            }
        }
        return Map.copyOf(points);
    }

    public static Map<String,Object> attach(Map<String,Object> card){
        var point=POINTS.get(card.get("code"));
        if(point!=null&&!Boolean.TRUE.equals(card.get("savedOnly"))&&!"DEMO".equals(card.get("materialKind")))card.put("coordinates",point);
        return card;
    }
}
