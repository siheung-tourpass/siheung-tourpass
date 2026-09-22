package kr.ac.siheung.tourpass.service;

import java.sql.*;
import java.time.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Personal plans and self-reported memories, independent of benefit validity. */
public final class JourneyService {
    private static String date(Object value) {
        String date=Input.date(value,true);
        require(date==null||LocalDate.parse(date).getYear()>=1000,400,"INVALID_INPUT");
        return date;
    }
    public List<Map<String,Object>> list(Connection c,String user,String status) throws SQLException {
        if(status!=null)Input.choice(status,"PLANNING","CONFIRMED","ARCHIVED");
        return rows(c,"SELECT j.*,(SELECT COUNT(*) FROM journey_place p WHERE p.journey_id=j.id) place_count FROM journey j WHERE owner_id=?"+(status==null?"":" AND status=?")+" ORDER BY j.id DESC",status==null?new Object[]{user}:new Object[]{user,status});
    }
    private Map<String,Object> owned(Connection c,String user,String id,boolean lock) throws SQLException {
        var j=one(c,"SELECT * FROM journey WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),id,user);
        require(j!=null,404,"NOT_FOUND");return j;
    }
    private void planning(Connection c,String user,String id) throws SQLException {
        require(owned(c,user,id,true).get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
    }
    public Map<String,Object> detail(Connection c,String user,String id) throws SQLException {
        var j=owned(c,user,id,false);
        j.put("places",rows(c,"SELECT * FROM journey_place WHERE journey_id=? ORDER BY position,id",id));
        if(j.get("issuedPassId")!=null)j.put("benefitPass",new PassService().detail(c,user,(String)j.get("issuedPassId"),false));
        var history=rows(c,"SELECT h.*,p.product_name,p.starts_at,p.expires_at,p.cancelled_at,p.demo_only FROM journey_pass_history h JOIN issued_pass p ON p.id=h.issued_pass_id WHERE h.journey_id=? ORDER BY h.id DESC",id);
        Instant now=Instant.now();for(var link:history){link.put("status",PassService.status(link,now));CatalogService.demo(link);}
        j.put("benefitPassHistory",history);
        return j;
    }
    public Map<String,Object> create(Connection c,String user,Map<String,Object> b) throws SQLException {
        return detail(c,user,createRow(c,user,b));
    }
    private String createRow(Connection c,String user,Map<String,Object> b) throws SQLException {
        Input.fields(b,"name","name,travelDate");
        return insert(c,"INSERT INTO journey(owner_id,name,travel_date) VALUES(?,?,?)",user,Input.text(b.get("name"),100,false),date(b.get("travelDate")));
    }
    public Map<String,Object> save(Connection c,String user,String id,Map<String,Object> b) throws SQLException {
        saveValues(c,user,id,owned(c,user,id,true),b);
        return detail(c,user,id);
    }
    private void saveValues(Connection c,String user,String id,Map<String,Object> j,Map<String,Object> b) throws SQLException {
        Input.fields(b,"","name,travelDate,status,issuedPassId");var v=new LinkedHashMap<String,Object>();
        if(b.containsKey("name"))v.put("name",Input.text(b.get("name"),100,false));
        if(b.containsKey("travelDate"))v.put("travelDate",date(b.get("travelDate")));
        if(b.containsKey("status")){
            String status=Input.choice(b.get("status"),"PLANNING","CONFIRMED","ARCHIVED");
            String current=(String)j.get("status");
            require(status.equals(current)||(current.equals("PLANNING")&&status.equals("CONFIRMED"))||
                (current.equals("CONFIRMED")&&status.equals("ARCHIVED"))||(current.equals("ARCHIVED")&&status.equals("CONFIRMED")),409,"INVALID_JOURNEY_TRANSITION");
            if(status.equals("CONFIRMED"))require(one(c,"SELECT id FROM journey_place WHERE journey_id=? LIMIT 1",id)!=null,400,"JOURNEY_EMPTY");
            v.put("status",status);
        }
        if(b.containsKey("issuedPassId")){
            String pass=b.get("issuedPassId")==null?null:Input.id(b.get("issuedPassId"));
            if(pass!=null)require(one(c,"SELECT id FROM issued_pass WHERE id=? AND owner_id=?",pass,user)!=null,404,"NOT_FOUND");
            if(!Objects.equals(pass,j.get("issuedPassId"))){
                Instant now=Instant.now();
                update(c,"UPDATE journey_pass_history SET disconnected_at=? WHERE journey_id=? AND disconnected_at IS NULL",now,id);
                if(pass!=null)insert(c,"INSERT INTO journey_pass_history(journey_id,issued_pass_id,linked_at) VALUES(?,?,?)",id,pass,now);
            }
            v.put("issuedPassId",pass);
        }
        updateValues(c,"journey","id=?",new Object[]{id},v);
    }
    public Map<String,Object> saveDraft(Connection c,String user,String id,Map<String,Object> b,List<String> places,boolean confirm) throws SQLException {
        boolean create=id==null;
        if(create)id=createRow(c,user,b);
        var journey=owned(c,user,id,true);
        require(journey.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
        var ids=new LinkedHashSet<String>();if(places!=null)for(String place:places)ids.add(cardKey(place));
        if(confirm)require(!ids.isEmpty(),400,"JOURNEY_EMPTY");
        if(!create)saveValues(c,user,id,journey,b);
        var existingKeys=new HashSet<String>();
        for(var existing:rows(c,"SELECT id,card_key FROM journey_place WHERE journey_id=?",id)){
            existingKeys.add((String)existing.get("cardKey"));
            if(!ids.contains(existing.get("cardKey")))update(c,"DELETE FROM journey_place WHERE id=?",existing.get("id"));
        }
        int position=1;
        for(String place:ids){
            if(!existingKeys.contains(place))addCard(c,id,place);
            update(c,"UPDATE journey_place SET position=? WHERE journey_id=? AND card_key=?",position++,id,place);
        }
        if(confirm)saveValues(c,user,id,journey,Map.of("status","CONFIRMED"));
        return detail(c,user,id);
    }
    public void delete(Connection c,String user,String id) throws SQLException {
        planning(c,user,id);
        update(c,"DELETE FROM journey_pass_history WHERE journey_id=?",id);
        update(c,"DELETE FROM journey_place WHERE journey_id=?",id);
        update(c,"DELETE FROM journey WHERE id=?",id);
    }
    private static String cardKey(Object value) {
        String key=Input.text(value,150,false);
        if(key.matches("[1-9][0-9]*"))return "place:"+Input.id(key);
        require(key.matches("(place|merchant):[1-9][0-9]*|event:[A-Za-z0-9_-]+"),400,"INVALID_INPUT");
        if(!key.startsWith("event:"))Input.id(key.substring(key.indexOf(':')+1));
        return key;
    }
    public Map<String,Object> add(Connection c,String user,String id,Map<String,Object> b) throws SQLException {
        planning(c,user,id);
        Input.fields(b,"","placeId,cardKey");
        require(b.containsKey("placeId")!=b.containsKey("cardKey"),400,"INVALID_INPUT");
        String key=b.containsKey("cardKey")?cardKey(b.get("cardKey")):"place:"+Input.id(b.get("placeId"));
        if(one(c,"SELECT id FROM journey_place WHERE journey_id=? AND card_key=?",id,key)==null)addCard(c,id,key);
        return detail(c,user,id);
    }
    private void addCard(Connection c,String id,String key) throws SQLException {
        String kind=key.substring(0,key.indexOf(':')),source=key.substring(key.indexOf(':')+1);
        Map<String,Object> p;
        if(kind.equals("place")){
            p=one(c,OntologyService.SELECTABLE_PLACES+" AND p.id=?",source);
        }else if(kind.equals("merchant")){
            p=one(c,"SELECT m.name,r.name region_name,m.location_description,'DEMO' material_kind FROM merchant m JOIN place p ON p.id=m.place_id JOIN region r ON r.id=p.region_id WHERE m.id=? AND m.active=TRUE AND r.active=TRUE",source);
        }else{
            var event=EventCatalog.find(source);
            p=event==null?null:Map.of("name",event.get("name"),"regionName","시흥시","locationDescription",event.get("venue_name"),"materialKind","REAL");
        }
        require(p!=null,404,"NOT_FOUND");
        int pos=((Number)one(c,"SELECT COALESCE(MAX(position),0)+1 next_position FROM journey_place WHERE journey_id=?",id).get("nextPosition")).intValue();
        insert(c,"INSERT INTO journey_place(journey_id,place_id,card_kind,card_key,position,place_name,region_name,location_description,material_kind) VALUES(?,?,?,?,?,?,?,?,?)",id,kind.equals("place")?source:null,kind.toUpperCase(Locale.ROOT),key,pos,p.get("name"),p.get("regionName"),p.get("locationDescription"),p.get("materialKind"));
    }
    public Map<String,Object> item(Connection c,String user,String id,String item,String action,Map<String,Object> b) throws SQLException {
        var j=owned(c,user,id,true);
        var p=one(c,"SELECT * FROM journey_place WHERE id=? AND journey_id=?",item,id);require(p!=null,404,"NOT_FOUND");
        if(action.equals("record")){
            Input.fields(b,"visitedOn,note,rating","visitedOn,note,rating");String date=date(b.get("visitedOn"));
            require(date==null||!LocalDate.parse(date).isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))),400,"INVALID_INPUT");
            Integer rating=b.get("rating")==null?null:Input.integer(b.get("rating"),1,5);require(rating==null||date!=null,400,"INVALID_INPUT");
            update(c,"UPDATE journey_place SET visited_on=?,note=?,rating=? WHERE id=?",date,Input.text(b.get("note"),500,true),rating,item);
            return detail(c,user,id);
        }
        require(j.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
        if(action.equals("delete")){
            Input.fields(b,"","");
            update(c,"DELETE FROM journey_place WHERE id=?",item);
        }else{
            Input.fields(b,"direction","direction");
            String direction=Input.choice(b.get("direction"),"UP","DOWN");
            var places=rows(c,"SELECT id,position FROM journey_place WHERE journey_id=? ORDER BY position,id",id);
            var ids=places.stream().map(place->place.get("id")).toList();
            int to=ids.indexOf(item)+(direction.equals("UP")?-1:1);
            if(to>=0&&to<places.size()){
                var neighbor=places.get(to);
                update(c,"UPDATE journey_place SET position=? WHERE id=?",neighbor.get("position"),item);
                update(c,"UPDATE journey_place SET position=? WHERE id=?",p.get("position"),neighbor.get("id"));
            }
        }
        return detail(c,user,id);
    }
}
