import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import kr.ac.siheung.tourpass.service.CardCoordinates;
import kr.ac.siheung.tourpass.service.DiningCatalog;
import kr.ac.siheung.tourpass.service.EventCatalog;

public final class CardCoordinatesCheck {
    static void check(Map<String,Object> card){
        var point=(Map<?,?>)card.get("coordinates");
        assert point!=null:card.get("code");
        double lat=((Number)point.get("latitude")).doubleValue(),lon=((Number)point.get("longitude")).doubleValue();
        assert lat>37.2&&lat<37.6&&lon>126.5&&lon<127:card.get("code");
        assert point.get("crs").equals("WGS84");
        assert point.get("sourceUrl").toString().startsWith("https://");
        assert !LocalDate.parse(point.get("checkedOn").toString()).isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))):card.get("code");
        assert !point.get("basis").toString().isBlank();
    }
    public static void main(String[] args) throws Exception {
        var mapper=new ObjectMapper();
        var source=mapper.readTree(Files.readString(Path.of("data/siheung-tourism.json")));
        // Real cards without a sourced point must say why instead of carrying a guessed one.
        var unlocated=new HashSet<String>();
        for(var kind:List.of("tourism","events")){
            var data=mapper.readTree(Files.readString(Path.of("data/siheung-"+kind+".json")));
            for(var card:data.path(kind.equals("events")?"events":"places")){
                var holder=kind.equals("tourism")?card.path("operational"):card;
                if(!holder.path("coordinates").isNull())continue;
                assert !holder.path("coordinates_note").asText().isBlank():card.path("code").asText();
                unlocated.add(card.path("code").asText());
            }
        }
        for(var place:source.path("places")){
            var card=new HashMap<String,Object>(Map.of("code",place.path("code").asText(),"materialKind","REAL"));
            CardCoordinates.attach(card);
            if(unlocated.contains(place.path("code").asText()))assert !card.containsKey("coordinates");else check(card);
            for(var exclusion:List.of(Map.of("materialKind","DEMO"),Map.of("savedOnly",true))){
                var excluded=new HashMap<String,Object>(Map.of("code",place.path("code").asText()));
                excluded.putAll(exclusion);CardCoordinates.attach(excluded);assert !excluded.containsKey("coordinates");
            }
        }
        for(var card:EventCatalog.list(null).cards())if(unlocated.contains(card.get("code")))assert !card.containsKey("coordinates");else check(card);
        for(var card:DiningCatalog.list())check(card);
        var unknown=new HashMap<String,Object>(Map.of("code","custom-place"));
        CardCoordinates.attach(unknown);assert !unknown.containsKey("coordinates");
        System.out.println("PASS: real catalog cards have sourced WGS84 points or a recorded gap; fictional, saved-only and unknown cards have no inferred coordinates");
    }
}
