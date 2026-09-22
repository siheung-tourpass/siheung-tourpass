package kr.ac.siheung.tourpass.service;

import java.sql.*;
import java.time.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.JourneyDao;
import kr.ac.siheung.tourpass.dao.PassDao;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Personal plans and self-reported memories, independent of benefit validity. */
public final class JourneyService {
    private static final Map<String,Set<String>> TRANSITIONS=Map.of(
        "PLANNING",Set.of("PLANNING","CONFIRMED"),
        "CONFIRMED",Set.of("CONFIRMED","PLANNING","ARCHIVED"),
        "ARCHIVED",Set.of("ARCHIVED","CONFIRMED"));
    private static String date(Object value) {
        String date=Input.date(value,true);
        require(date==null||LocalDate.parse(date).getYear()>=1000,400,"INVALID_INPUT");
        return date;
    }
    public List<Map<String,Object>> list(Connection c,String user,String status) throws SQLException {
        if(status!=null)Input.choice(status,"PLANNING","CONFIRMED","ARCHIVED");
        return JourneyDao.list(c,user,status);
    }
    private Map<String,Object> owned(Connection c,String user,String id,boolean lock) throws SQLException {
        var j=JourneyDao.findOwned(c,lock,id,user);
        require(j!=null,404,"NOT_FOUND");return j;
    }
    private void planning(Connection c,String user,String id) throws SQLException {
        require(owned(c,user,id,true).get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
    }
    public Map<String,Object> cards(Connection c,String user,String id) throws SQLException {
        var j=owned(c,user,id,false);
        j.put("places",JourneyDao.cards(c,id));
        return j;
    }
    public Map<String,Object> detail(Connection c,String user,String id) throws SQLException {
        var j=cards(c,user,id);
        if(j.get("issuedPassId")!=null)j.put("benefitPass",new PassService().detail(c,user,(String)j.get("issuedPassId"),false));
        var history=JourneyDao.passHistory(c,id);
        Instant now=Instant.now();for(var link:history){link.put("status",PassService.status(link,now));CatalogService.demo(link);}
        j.put("benefitPassHistory",history);
        return j;
    }
    public Map<String,Object> create(Connection c,String user,Map<String,Object> b) throws SQLException {
        return detail(c,user,createRow(c,user,b));
    }
    private String createRow(Connection c,String user,Map<String,Object> b) throws SQLException {
        Input.fields(b,"name","name,travelDate");
        return JourneyDao.create(c,user,Input.text(b.get("name"),100,false),date(b.get("travelDate")));
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
            require(TRANSITIONS.get(j.get("status")).contains(status),409,"INVALID_JOURNEY_TRANSITION");
            if(status.equals("CONFIRMED"))require(JourneyDao.firstCard(c,id)!=null,400,"JOURNEY_EMPTY");
            v.put("status",status);
        }
        if(b.containsKey("issuedPassId")){
            String pass=b.get("issuedPassId")==null?null:Input.id(b.get("issuedPassId"));
            if(pass!=null)require(PassDao.owned(c,pass,user)!=null,404,"NOT_FOUND");
            if(!Objects.equals(pass,j.get("issuedPassId"))){
                Instant now=Instant.now();
                JourneyDao.disconnectPass(c,now,id);
                if(pass!=null)JourneyDao.connectPass(c,id,pass,now);
            }
            v.put("issuedPassId",pass);
        }
        JourneyDao.save(c,id,v);
    }
    public Map<String,Object> saveDraft(Connection c,String user,String id,Map<String,Object> b,List<String> places,boolean confirm) throws SQLException {
        boolean create=id==null;
        if(create)id=createRow(c,user,b);
        var journey=owned(c,user,id,true);
        require(journey.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
        var ids=new LinkedHashMap<String,CardKey>();if(places!=null)for(String place:places){var card=CardKey.parse(place);ids.putIfAbsent(card.key(),card);}
        if(confirm)require(!ids.isEmpty(),400,"JOURNEY_EMPTY");
        if(!create)saveValues(c,user,id,journey,b);
        var existingIds=new HashMap<String,Object>();
        for(var existing:JourneyDao.cardKeys(c,id)){
            existingIds.put((String)existing.get("cardKey"),existing.get("id"));
            if(!ids.containsKey(existing.get("cardKey")))JourneyDao.deleteCard(c,existing.get("id"));
        }
        int position=1;
        for(var card:ids.values()){
            if(existingIds.containsKey(card.key()))JourneyDao.positionItem(c,position,existingIds.get(card.key()));
            else addCard(c,id,card,position);
            position++;
        }
        if(confirm)saveValues(c,user,id,journey,Map.of("status","CONFIRMED"));
        return detail(c,user,id);
    }
    public void delete(Connection c,String user,String id) throws SQLException {
        planning(c,user,id);
        JourneyDao.delete(c,id);
    }
    public record CardKey(String kind,String source) {
        public String key(){return kind+":"+source;}
        public static CardKey parse(Object value) {
            String key=Input.text(value,150,false);
            if(key.matches("[1-9][0-9]*"))key="place:"+key;
            require(key.matches("(place|merchant):[1-9][0-9]*|(event|dining):[A-Za-z0-9_-]+"),400,"INVALID_INPUT");
            String[] parts=key.split(":",2);
            if(parts[0].equals("place")||parts[0].equals("merchant"))Input.id(parts[1]);
            return new CardKey(parts[0],parts[1]);
        }
    }
    public Map<String,Object> add(Connection c,String user,String id,Map<String,Object> b) throws SQLException {
        planning(c,user,id);
        Input.fields(b,"","placeId,cardKey");
        require(b.containsKey("placeId")!=b.containsKey("cardKey"),400,"INVALID_INPUT");
        var card=CardKey.parse(b.containsKey("cardKey")?b.get("cardKey"):Input.id(b.get("placeId")));
        if(JourneyDao.findCardKey(c,id,card.key())==null)addCard(c,id,card,JourneyDao.nextPosition(c,id));
        return detail(c,user,id);
    }
    private void addCard(Connection c,String id,CardKey card,int position) throws SQLException {
        String kind=card.kind(),source=card.source();
        var p=switch(kind){
            case "place"->JourneyDao.selectablePlace(c,source);
            case "merchant"->JourneyDao.selectableMerchant(c,source);
            case "dining"->DiningCatalog.find(source);
            default->EventCatalog.find(source);
        };
        require(p!=null,404,"NOT_FOUND");
        JourneyDao.addCard(c,id,kind.equals("place")?source:null,kind.toUpperCase(Locale.ROOT),card.key(),position,p.get("name"),p.get("regionName"),p.get("locationDescription"),p.get("materialKind"));
    }
    public Map<String,Object> item(Connection c,String user,String id,String item,String action,Map<String,Object> b) throws SQLException {
        var j=owned(c,user,id,true);
        var p=JourneyDao.findCard(c,item,id);require(p!=null,404,"NOT_FOUND");
        if(!action.equals("record"))require(j.get("status").equals("PLANNING"),409,"JOURNEY_LOCKED");
        switch(action){
        case "record"->{
            Input.fields(b,"visitedOn,note,rating");String date=date(b.get("visitedOn"));
            require(date==null||!LocalDate.parse(date).isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))),400,"INVALID_INPUT");
            Integer rating=b.get("rating")==null?null:Input.integer(b.get("rating"),1,5);require(rating==null||date!=null,400,"INVALID_INPUT");
            JourneyDao.record(c,date,Input.text(b.get("note"),500,true),rating,item);
        }
        case "delete"->{
            Input.fields(b,"");
            JourneyDao.deleteCard(c,item);
        }
        case "move"->{
            Input.fields(b,"direction");
            String direction=Input.choice(b.get("direction"),"UP","DOWN");
            var places=JourneyDao.positions(c,id);
            var ids=places.stream().map(place->place.get("id")).toList();
            int to=ids.indexOf(item)+(direction.equals("UP")?-1:1);
            if(to>=0&&to<places.size()){
                var neighbor=places.get(to);
                JourneyDao.positionItem(c,neighbor.get("position"),item);
                JourneyDao.positionItem(c,p.get("position"),neighbor.get("id"));
            }
        }
        default->throw new Problem(400,"INVALID_INPUT");
        }
        return detail(c,user,id);
    }
    public List<Map<String,Object>> coverCards(Connection c,List<String> ids) throws SQLException {
        return ids.isEmpty()?List.of():JourneyDao.coverCards(c,ids);
    }
}
