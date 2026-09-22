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
        var source=new ObjectMapper().readTree(Files.readString(Path.of("data/siheung-tourism.json")));
        for(var place:source.path("places")){
            var card=new HashMap<String,Object>(Map.of("code",place.path("code").asText(),"materialKind","REAL"));
            check(CardCoordinates.attach(card));
            for(var exclusion:List.of(Map.of("materialKind","DEMO"),Map.of("savedOnly",true))){
                var excluded=new HashMap<String,Object>(Map.of("code",place.path("code").asText()));
                excluded.putAll(exclusion);CardCoordinates.attach(excluded);assert !excluded.containsKey("coordinates");
            }
        }
        for(var card:EventCatalog.list(null).cards())check(card);
        for(var card:DiningCatalog.list())check(card);
        var unknown=new HashMap<String,Object>(Map.of("code","custom-place"));
        CardCoordinates.attach(unknown);assert !unknown.containsKey("coordinates");
        System.out.println("PASS: all real catalog cards have sourced WGS84 points; fictional, saved-only and unknown cards have no inferred coordinates");
    }
}
