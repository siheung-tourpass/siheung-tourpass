package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Reviewed photographs are bundled with the app; requests make no external calls. */
public final class PlaceImages {
    private static final Map<String,Map<String,String>> IMAGES=load();
    private static final List<Map<String,String>> PHOTOS=List.copyOf(IMAGES.values());
    public static Map<String,String> random(){
        return PHOTOS.get(ThreadLocalRandom.current().nextInt(PHOTOS.size()));
    }
    private static Map<String,Map<String,String>> load(){
        Map<String,Map<String,String>> images=CatalogResources.load("place-images.json",new TypeReference<>(){});
        images.replaceAll((code,image)->Map.copyOf(image));
        return Map.copyOf(images);
    }
    public static Map<String,Object> attach(Map<String,Object> place){
        if("REAL".equals(place.get("materialKind"))){
            var image=IMAGES.get(place.get("code"));
            if(image!=null)place.put("image",image);
        }
        return place;
    }
}
