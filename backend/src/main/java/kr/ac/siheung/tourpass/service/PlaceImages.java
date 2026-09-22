package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/** Reviewed photographs are bundled with the app; requests make no external calls. */
public final class PlaceImages {
    private static final Map<String,Map<String,String>> IMAGES=load();
    private static Map<String,Map<String,String>> load(){
        try(var stream=PlaceImages.class.getResourceAsStream("/place-images.json")){
            if(stream==null)throw new IOException("Missing place-images.json");
            Map<String,Map<String,String>> images=new ObjectMapper().readValue(stream,new TypeReference<>(){});
            images.replaceAll((code,image)->Map.copyOf(image));
            return Map.copyOf(images);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Map<String,Object> attach(Map<String,Object> place){
        if("REAL".equals(place.get("materialKind"))){
            var image=IMAGES.get(place.get("code"));
            if(image!=null)place.put("image",image);
        }
        return place;
    }
}
