import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.net.URI;
import java.time.LocalDate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.siheung.tourpass.service.PlaceImages;

public final class PlaceImagesCheck {
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper();
        Map<String,Map<String,String>> images=mapper.readValue(Path.of("src/main/resources/place-images.json").toFile(),new TypeReference<>(){});
        var tourism=mapper.readTree(Path.of("data/siheung-tourism.json").toFile());
        var codes=new HashSet<String>();tourism.get("places").forEach(place->codes.add(place.get("code").asText()));
        assert !images.isEmpty();
        for(int i=0;i<100;i++)assert images.containsValue(PlaceImages.random());
        for(var entry:images.entrySet()){
            String code=entry.getKey();var image=entry.getValue();
            assert codes.contains(code):code;
            for(String key:List.of("path","alt","author","sourceUrl","license","licenseUrl","checkedOn","width","height"))assert !image.get(key).isBlank():key;
            assert image.get("path").matches("/assets/places/[a-z0-9_]+\\.(jpg|png)");
            var source=URI.create(image.get("sourceUrl"));
            assert source.getScheme().equals("https");
            assert Set.of("commons.wikimedia.org","www.kogl.or.kr","gnews.gg.go.kr").contains(source.getHost());
            if(!source.getHost().equals("commons.wikimedia.org")){
                assert image.get("license").equals("KOGL Type 1");
                for(String key:List.of("sourceTitle","publisher","imageUrl","provenanceNote"))assert !image.get(key).isBlank():key;
                assert URI.create(image.get("imageUrl")).getHost().equals(source.getHost());
            }
            LocalDate.parse(image.get("checkedOn"));
            if(image.containsKey("capturedYear"))assert image.get("capturedYear").matches("\\d{4}");
            if(image.containsKey("publishedOn"))LocalDate.parse(image.get("publishedOn"));
            assert Set.of("CC BY 3.0","CC BY 4.0","CC BY-SA 4.0","KOGL Type 1").contains(image.get("license"));
            var decoded=ImageIO.read(Path.of("../frontend"+image.get("path")).toFile());
            assert decoded!=null;
            assert decoded.getWidth()==Integer.parseInt(image.get("width"));
            assert decoded.getHeight()==Integer.parseInt(image.get("height"));
            var place=new HashMap<String,Object>(Map.of("code",code,"materialKind","REAL"));
            assert PlaceImages.attach(place).get("image").equals(image);
            var demo=new HashMap<String,Object>(Map.of("code",code,"materialKind","DEMO"));
            assert !PlaceImages.attach(demo).containsKey("image");
        }
        assert !PlaceImages.attach(new HashMap<>(Map.of("code","UNKNOWN","materialKind","REAL"))).containsKey("image");
        System.out.println("PASS: place image mapping, attribution, licenses, dimensions and real-photo isolation");
    }
}
