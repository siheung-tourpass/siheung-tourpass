import java.time.LocalDate;
import java.util.*;
import kr.ac.siheung.tourpass.service.EventCatalog;
import kr.ac.siheung.tourpass.service.Problem;

public final class EventCatalogCheck {
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> cards(Map<String,Object> data,String key){return (List<Map<String,Object>>)data.get(key);}
    public static void main(String[] args){
        var today=LocalDate.of(2026,10,14);
        var all=EventCatalog.list(today,null);
        assert cards(all,"current").size()==8 && cards(all,"past").size()==3;
        var added=cards(EventCatalog.list(today,"2026-10-24"),"current");
        assert added.size()==4;
        assert added.stream().anyMatch(card->card.get("code").equals("EVENT_2026_007"));
        assert cards(EventCatalog.list(LocalDate.of(2026,10,25),"2026-10-24"),"past").size()==4;
        for(int number=7;number<=11;number++){
            var card=EventCatalog.find(String.format("EVENT_2026_%03d",number));
            assert card!=null && card.get("checked_on").equals("2026-10-18");
            assert card.containsKey("image");
            var image=(Map<?,?>)card.get("image");
            assert ((Number)image.get("width")).intValue()>0 && ((Number)image.get("height")).intValue()>0;
            assert image.get("licenseUrl").equals("https://creativecommons.org/licenses/by-nc-nd/2.0/kr/");
        }
        assert cards(EventCatalog.list(today,"2026-10-17"),"current").size()==2;
        var end=cards(EventCatalog.list(LocalDate.of(2026,11,1),"2026-11-01"),"current");
        assert end.size()==1 && end.get(0).get("scheduleLabel").equals("기간 중");
        assert cards(EventCatalog.list(LocalDate.of(2026,11,2),"2026-11-01"),"past").size()==1;
        assert cards(EventCatalog.list(today,"2026-12-01"),"current").isEmpty();
        assert cards(EventCatalog.list(today,"2026-12-01"),"past").isEmpty();
        for(String group:List.of("current","past"))for(var card:cards(all,group)){
            var found=EventCatalog.find((String)card.get("code"));
            assert found!=null && found.get("name").equals(card.get("name"));
            assert found.get("sources").equals(card.get("sources"));
            assert Objects.equals(found.get("image"),card.get("image"));
            assert card.get("reviewStatus").equals("DRAFT");
            assert !((List<?>)card.get("sources")).isEmpty();
            if(card.containsKey("image"))assert ((Map<?,?>)card.get("image")).get("path").toString().startsWith("/assets/events/");
            assert card.containsKey("image") : card.get("name");
        }
        assert EventCatalog.find("unknown-event")==null;
        for(String invalid:List.of("2026-02-30","2026-1-01","<script>")){
            try{EventCatalog.list(today,invalid);throw new AssertionError(invalid);}catch(Problem expected){assert expected.status==400;}
        }
        System.out.println("PASS: event date boundaries, current/past separation, draft labels, sources and complete event image coverage");
    }
}
