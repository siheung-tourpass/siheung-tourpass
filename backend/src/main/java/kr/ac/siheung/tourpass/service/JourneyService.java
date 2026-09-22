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
        if(status!=null)Input.choice(status,"PLANNING","ARCHIVED");
        return rows(c,"SELECT j.*,(SELECT COUNT(*) FROM journey_place p WHERE p.journey_id=j.id) place_count FROM journey j WHERE owner_id=?"+(status==null?"":" AND status=?")+" ORDER BY j.id DESC",status==null?new Object[]{user}:new Object[]{user,status});
    }
    private Map<String,Object> owned(Connection c,String user,String id,boolean lock) throws SQLException {
        var j=one(c,"SELECT * FROM journey WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),id,user);
        require(j!=null,404,"NOT_FOUND");return j;
    }
    public Map<String,Object> detail(Connection c,String user,String id) throws SQLException {
        var j=owned(c,user,id,false);
        j.put("places",rows(c,"SELECT * FROM journey_place WHERE journey_id=? ORDER BY position,id",id));
        if(j.get("issuedPassId")!=null)j.put("benefitPass",new PassService().detail(c,user,(String)j.get("issuedPassId"),false));
        return j;
    }
    public Map<String,Object> create(Connection c,String user,Map<String,Object> b) throws SQLException {
        Input.fields(b,"name","name,travelDate");
        String id=insert(c,"INSERT INTO journey(owner_id,name,travel_date) VALUES(?,?,?)",user,Input.text(b.get("name"),100,false),date(b.get("travelDate")));
        return detail(c,user,id);
    }
    public Map<String,Object> save(Connection c,String user,String id,Map<String,Object> b) throws SQLException {
        owned(c,user,id,true);Input.fields(b,"","name,travelDate,status,issuedPassId");var v=new LinkedHashMap<String,Object>();
        if(b.containsKey("name"))v.put("name",Input.text(b.get("name"),100,false));
        if(b.containsKey("travelDate"))v.put("travelDate",date(b.get("travelDate")));
        if(b.containsKey("status"))v.put("status",Input.choice(b.get("status"),"PLANNING","ARCHIVED"));
        if(b.containsKey("issuedPassId")){
            String pass=b.get("issuedPassId")==null?null:Input.id(b.get("issuedPassId"));
            if(pass!=null)require(one(c,"SELECT id FROM issued_pass WHERE id=? AND owner_id=?",pass,user)!=null,404,"NOT_FOUND");
            v.put("issuedPassId",pass);
        }
        updateValues(c,"journey","id=?",new Object[]{id},v);return detail(c,user,id);
    }
    public Map<String,Object> add(Connection c,String user,String id,Map<String,Object> b) throws SQLException {
        var j=owned(c,user,id,true);require(j.get("status").equals("PLANNING"),409,"JOURNEY_ARCHIVED");
        Input.fields(b,"placeId","placeId");String place=Input.id(b.get("placeId"));
        if(one(c,"SELECT id FROM journey_place WHERE journey_id=? AND place_id=?",id,place)!=null)return detail(c,user,id);
        var p=new OntologyService().place(c,place);
        int pos=((Number)one(c,"SELECT COALESCE(MAX(position),0)+1 next_position FROM journey_place WHERE journey_id=?",id).get("nextPosition")).intValue();
        insert(c,"INSERT INTO journey_place(journey_id,place_id,position,place_name,region_name,location_description,material_kind) VALUES(?,?,?,?,?,?,?)",id,place,pos,p.get("name"),p.get("regionName"),p.get("locationDescription"),p.get("materialKind"));
        return detail(c,user,id);
    }
    public Map<String,Object> item(Connection c,String user,String id,String item,String action,Map<String,Object> b) throws SQLException {
        var j=owned(c,user,id,true);
        var p=one(c,"SELECT * FROM journey_place WHERE id=? AND journey_id=?",item,id);require(p!=null,404,"NOT_FOUND");
        if(action.equals("record")){
            Input.fields(b,"visitedOn,note,rating","visitedOn,note,rating");String date=date(b.get("visitedOn"));
            require(date==null||!LocalDate.parse(date).isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))),400,"INVALID_INPUT");
            Integer rating=b.get("rating")==null?null:Input.integer(b.get("rating"),1,5);require(rating==null||date!=null,400,"INVALID_INPUT");
            update(c,"UPDATE journey_place SET visited_on=?,note=?,rating=? WHERE id=?",date,Input.text(b.get("note"),500,true),rating,item);
        }else{
            require(j.get("status").equals("PLANNING"),409,"JOURNEY_ARCHIVED");
            if(action.equals("delete")){Input.fields(b,"","");update(c,"DELETE FROM journey_place WHERE id=?",item);}
            else{
                Input.fields(b,"direction","direction");String direction=Input.choice(b.get("direction"),"UP","DOWN");
                var places=rows(c,"SELECT id,position FROM journey_place WHERE journey_id=? ORDER BY position,id",id);
                for(int i=0;i<places.size();i++)if(item.equals(places.get(i).get("id"))){int to=i+(direction.equals("UP")?-1:1);
                    if(to>=0&&to<places.size()){
                        update(c,"UPDATE journey_place SET position=? WHERE id=?",places.get(to).get("position"),item);
                        update(c,"UPDATE journey_place SET position=? WHERE id=?",p.get("position"),places.get(to).get("id"));
                    }break;
                }
            }
        }
        return detail(c,user,id);
    }
}
