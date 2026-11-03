import java.time.LocalDate;
import java.util.*;
import kr.ac.siheung.tourpass.service.EventCatalog;
import kr.ac.siheung.tourpass.service.Problem;

public final class EventCatalogCheck {
    public static void main(String[] args){
        var all=EventCatalog.list(LocalDate.of(2026,10,14),null);
        assert !all.cards().isEmpty();
        for(var card:all.cards()){
            String code=(String)card.get("code");
            var start=LocalDate.parse((String)card.get("startDate"));
            var end=LocalDate.parse((String)card.get("endDate"));
            assert !end.isBefore(start):code;
            assert EventCatalog.list(start.minusDays(1),start.toString()).current().stream()
                .anyMatch(item->item.get("code").equals(code)
                    && item.get("scheduleLabel").equals("예정")):code;
            // Selected dates include both endpoints; the event expires the next day.
            for(var date:List.of(start,end)){
                var current=EventCatalog.list(date,date.toString()).current();
                assert current.stream().anyMatch(item->item.get("code").equals(code)
                    && item.get("scheduleLabel").equals("기간 중")):code;
            }
            assert EventCatalog.list(end.plusDays(1),end.toString()).past().stream()
                .anyMatch(item->item.get("code").equals(code)
                    && item.get("scheduleLabel").equals("일정 종료")):code;
            for(var date:List.of(start.minusDays(1),end.plusDays(1)))
                assert EventCatalog.list(start,date.toString()).cards().stream()
                    .noneMatch(item->item.get("code").equals(code)):code;
            assert card.get("materialKind").equals("REAL");
            assert card.get("regionName").equals("시흥시");
            for(String key:List.of("locationDescription","checkedOn","dailyHours","admissionFee","reservationNote"))
                assert card.get(key) instanceof String:key;
            assert !((List<?>)card.get("sources")).isEmpty():code;
            if(card.get("image") instanceof Map<?,?> image){
                assert image.get("path").toString().startsWith("/assets/events/");
                for(String key:List.of("alt","attribution","sourceUrl","licenseUrl"))
                    assert !image.get(key).toString().isBlank():code+" "+key;
                assert ((Number)image.get("width")).intValue()>0;
                assert ((Number)image.get("height")).intValue()>0;
            }
            var found=EventCatalog.find(code);
            assert found!=null && found.get("sources").equals(card.get("sources"));
        }
        assert EventCatalog.find("unknown-event")==null;
        for(String invalid:List.of("2026-02-30","2026-1-01","<script>")){
            try{EventCatalog.list(LocalDate.of(2026,10,14),invalid);throw new AssertionError(invalid);}
            catch(Problem expected){assert expected.status==400;}
        }
        System.out.println("PASS: inclusive event dates, expiry, filtering and sources");
    }
}
