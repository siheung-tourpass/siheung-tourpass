package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Official designation facts are separate from tourism ranking and pass benefits. */
public final class DiningCatalog {
    private static final JsonNode DATA=CatalogResources.load("siheung-dining.json");

    public static List<Map<String,Object>> list(){
        var cards=new ArrayList<Map<String,Object>>();
        for(var place:DATA.path("places"))cards.add(card(place));
        return cards;
    }
    public static Map<String,Object> find(String code){
        for(var place:DATA.path("places"))if(code.equals(place.path("code").asText()))return card(place);
        return null;
    }
    private static Map<String,Object> card(JsonNode place){
        var card=new LinkedHashMap<String,Object>();
        for(String key:List.of("code","name","category","regionCode","regionName","checkedOn","reviewStatus","cuisine","designationNumber","designatedOn"))
            card.put(key,place.path(key).asText("미확인"));
        card.put("locationDescription",place.path("address").asText());
        card.put("materialKind","REAL");
        var image=place.path("image");
        if(image.path("path").asText().matches("/assets/dining/[a-z0-9-]+\\.jpg"))
            card.put("image",CatalogResources.image(image,image.path("path").asText(),"sourceUrl","licenseUrl"));
        for(var source:DATA.path("sources"))if(source.path("code").asText().equals(place.path("sourceCode").asText())){
            card.put("sourceUrl",Input.url(source.path("url").asText(),false));
            card.put("sourceTitle",source.path("title").asText());
            card.put("designation",source.path("designation").asText());
            card.put("contentDate",source.path("contentDate").asText("미확인"));
        }
        return card;
    }
}
