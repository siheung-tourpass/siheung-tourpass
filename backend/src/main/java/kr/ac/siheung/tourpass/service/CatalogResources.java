package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/** Bundled catalog data; callers keep their own image eligibility checks. */
final class CatalogResources {
    private CatalogResources() {}

    static JsonNode load(String name) {
        try(var stream=CatalogResources.class.getResourceAsStream("/"+name)) {
            if(stream==null)throw new IOException("Missing "+name);
            return new ObjectMapper().readTree(stream);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }

    static Map<String,Object> image(JsonNode image,String path,String sourceKey,String licenseKey) {
        return Map.of("path",path,"alt",image.path("alt").asText(),"attribution",image.path("attribution").asText(),
            "sourceUrl",Input.url(image.path(sourceKey).asText(),false),"licenseUrl",Input.url(image.path(licenseKey).asText(),false),
            "width",image.path("width").asInt(),"height",image.path("height").asInt());
    }
}
