package kr.ac.siheung.tourpass.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Bundled official notices are information, not approved recommendation candidates. */
public final class EventCatalog {
    public record Listing(List<Map<String,Object>> current,List<Map<String,Object>> past,String date){
        public List<Map<String,Object>> cards(){
            return java.util.stream.Stream.concat(current.stream(),past.stream()).toList();
        }
    }
    private record Event(JsonNode data,LocalDate start,LocalDate end){
        Event(JsonNode data){this(data,LocalDate.parse(data.path("start_date").asText()),LocalDate.parse(data.path("end_date").asText()));}
    }
    private static final JsonNode DATA=CatalogResources.load("siheung-events.json");
    private static final List<Event> EVENTS=events();
    private static List<Event> events(){
        var events=new ArrayList<Event>();DATA.path("events").forEach(event->events.add(new Event(event)));return List.copyOf(events);
    }

    public static Listing list(String date){
        return list(LocalDate.now(ZoneId.of("Asia/Seoul")),date);
    }
    public static Listing list(LocalDate today,String date){
        String selected=Input.date(date,true);
        LocalDate filter=selected==null?null:LocalDate.parse(selected);
        var current=new ArrayList<Map<String,Object>>();
        var past=new ArrayList<Map<String,Object>>();
        for(var event:EVENTS){
            if(filter!=null&&(filter.isBefore(event.start())||filter.isAfter(event.end())))continue;
            (today.isAfter(event.end())?past:current).add(card(event,today));
        }
        Comparator<Map<String,Object>> byDate=Comparator.comparing(card->(String)card.get("startDate"));
        current.sort(byDate);past.sort(byDate.reversed());
        return new Listing(current,past,selected==null?"":selected);
    }
    public static Map<String,Object> find(String code){
        for(var event:EVENTS)if(code.equals(event.data().path("code").asText()))return card(event,LocalDate.now(ZoneId.of("Asia/Seoul")));
        return null;
    }
    private static Map<String,Object> card(Event definition,LocalDate today){
        var event=definition.data();
        var card=new LinkedHashMap<String,Object>();
        for(String key:List.of("code","name","description"))card.put(key,event.path(key).asText());
        card.put("address",event.path("address").asText("미확인"));
        var fields=Map.of("start_date","startDate","end_date","endDate","venue_name","locationDescription",
            "checked_on","checkedOn","daily_hours","dailyHours","admission_fee","admissionFee","reservation_note","reservationNote");
        fields.forEach((source,target)->card.put(target,event.path(source).asText("미확인")));
        card.put("regionName","시흥시");card.put("materialKind","REAL");
        card.put("reviewStatus",event.path("review_status").asText());
        card.put("scheduleLabel",today.isAfter(definition.end())?"일정 종료":today.isBefore(definition.start())?"예정":"기간 중");
        var sources=new ArrayList<Map<String,String>>();
        var sourceIds=new HashSet<String>();event.path("source_ids").forEach(id->sourceIds.add(id.asText()));
        for(var source:DATA.path("sources"))if(sourceIds.contains(source.path("code").asText())){
            sources.add(Map.of("url",Input.url(source.path("url").asText(),false),"publisher",source.path("publisher").asText()));
        }
        card.put("sources",sources);
        var placeCodes=new ArrayList<String>();
        for(var link:event.path("place_links"))placeCodes.add(link.path("place_code").asText());
        card.put("placeCodes",placeCodes);
        var notes=new ArrayList<String>();event.path("notes").forEach(note->notes.add(note.asText()));card.put("notes",notes);
        for(var image:DATA.path("images")){
            if(!image.path("event_code").asText().equals(event.path("code").asText())||!image.path("reuse_status").asText().equals("PAGE_LICENSE_CHECKED"))continue;
            String path=image.path("path").asText("");
            if(!path.matches("backend/data/event-images/[a-z0-9-]+\\.(png|jpg)"))continue;
            card.put("image",CatalogResources.image(image,path.replace("backend/data/event-images/","/assets/events/"),"source_page_url","license_url"));
        }
        return CardCoordinates.attach(card);
    }

}
