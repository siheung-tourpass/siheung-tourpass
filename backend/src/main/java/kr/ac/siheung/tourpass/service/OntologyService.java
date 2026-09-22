package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class OntologyService {
    public record Relation(String table,String from,String to,String fromTable,String toTable){}
    public static Relation relation(String type){return switch(type){case "has-theme"->new Relation("place_theme","place_id","theme_id","place","theme");case "suitable-for"->new Relation("place_companion","place_id","companion_type_id","place","companion_type");case "sub-theme-of"->new Relation("theme_parent","child_theme_id","parent_theme_id","theme","theme");default->throw new Problem(400,"INVALID_RELATION_TYPE");};}
    public Map<String,Object> options(Connection c) throws SQLException {return Map.of("regions",rows(c,"SELECT id,code,name FROM region WHERE active=TRUE ORDER BY id"),"themes",rows(c,"SELECT id,code,name FROM theme WHERE active=TRUE ORDER BY id"),"companionTypes",rows(c,"SELECT id,code,name FROM companion_type WHERE active=TRUE ORDER BY id"));}
    public List<Map<String,Object>> relations(Connection c,String type) throws SQLException {var rel=relation(type);return rows(c,"SELECT * FROM "+rel.table()+" ORDER BY "+rel.from()+","+rel.to());}
    public Map<String,Object> relationGet(Connection c,String type,String from,String to) throws SQLException {var rel=relation(type);var result=one(c,"SELECT * FROM "+rel.table()+" WHERE "+rel.from()+"=? AND "+rel.to()+"=?",from,to);require(result!=null,404,"NOT_FOUND");return result;}
    static Map<String,Object> revision(Connection c,Map<String,Object> result,boolean changed) throws SQLException {if(changed)update(c,"UPDATE ontology_revision SET revision=revision+1 WHERE id=1");result.put("modelRevision",one(c,"SELECT revision FROM ontology_revision WHERE id=1").get("revision"));return result;}
    public Map<String,List<String>> paths(Connection c,String place) throws SQLException {
        var themes=rows(c,"SELECT id,code FROM theme WHERE active=TRUE ORDER BY code");var codes=new HashMap<String,String>();themes.forEach(t->codes.put((String)t.get("id"),(String)t.get("code")));
        var edges=rows(c,"SELECT tp.child_theme_id,tp.parent_theme_id FROM theme_parent tp JOIN theme a ON a.id=tp.child_theme_id JOIN theme b ON b.id=tp.parent_theme_id WHERE tp.active=TRUE AND tp.review_status='APPROVED' AND a.active=TRUE AND b.active=TRUE ORDER BY b.code");
        var parents=new HashMap<String,List<String>>();for(var e:edges)parents.computeIfAbsent((String)e.get("childThemeId"),k->new ArrayList<>()).add((String)e.get("parentThemeId"));
        var direct=rows(c,"SELECT t.id FROM place_theme pt JOIN theme t ON t.id=pt.theme_id WHERE pt.place_id=? AND pt.active=TRUE AND pt.review_status='APPROVED' AND t.active=TRUE ORDER BY t.code",place);
        var result=new LinkedHashMap<String,List<String>>();var queue=new ArrayDeque<List<String>>();for(var t:direct)queue.add(List.of((String)t.get("id")));
        while(!queue.isEmpty()){var ids=queue.remove();String end=ids.get(ids.size()-1);var path=ids.stream().map(codes::get).toList();var old=result.get(end);if(old!=null&&(old.size()<path.size()||old.size()==path.size()&&String.join("/",old).compareTo(String.join("/",path))<=0))continue;result.put(end,path);
            for(String parent:parents.getOrDefault(end,List.of()))if(!ids.contains(parent)){var next=new ArrayList<>(ids);next.add(parent);queue.add(next);}}
        return result;
    }
    public Map<String,Object> place(Connection c,String id) throws SQLException {
        var p=one(c,"SELECT p.*,r.name region_name FROM place p JOIN region r ON r.id=p.region_id WHERE p.id=? AND p.active=TRUE AND p.review_status='APPROVED' AND r.active=TRUE",id);require(p!=null,404,"NOT_FOUND");p.put("themes",paths(c,id));p.put("companions",rows(c,"SELECT ct.id,ct.code,ct.name FROM place_companion pc JOIN companion_type ct ON ct.id=pc.companion_type_id WHERE pc.place_id=? AND pc.active=TRUE AND pc.review_status='APPROVED' AND ct.active=TRUE ORDER BY ct.id",id));p.put("sources",rows(c,"SELECT source_url,checked_on,evidence_note FROM place_source WHERE place_id=? ORDER BY id",id));return CatalogService.demo(p);
    }
    public void cycle(Connection c,String child,String parent) throws SQLException {
        require(!child.equals(parent),409,"THEME_CYCLE");var graph=new HashMap<String,List<String>>();for(var e:rows(c,"SELECT child_theme_id,parent_theme_id FROM theme_parent"))graph.computeIfAbsent((String)e.get("childThemeId"),k->new ArrayList<>()).add((String)e.get("parentThemeId"));
        var seen=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(parent);while(!queue.isEmpty()){String n=queue.remove();require(!n.equals(child),409,"THEME_CYCLE");if(seen.add(n))queue.addAll(graph.getOrDefault(n,List.of()));}
    }
    public static Map<String,Object> annotation(Object value){var a=Input.object(value);Input.fields(a,"materialKind,sourceUrl,checkedOn,evidenceNote","materialKind,sourceUrl,checkedOn,evidenceNote");var out=new LinkedHashMap<String,Object>();String kind=Input.choice(a.get("materialKind"),"REAL","DEMO");out.put("materialKind",kind);out.put("sourceUrl",Input.url(a.get("sourceUrl"),true));out.put("checkedOn",Input.date(a.get("checkedOn"),true));out.put("evidenceNote",Input.text(a.get("evidenceNote"),1000,false));require(!kind.equals("DEMO")||(out.get("sourceUrl")==null&&out.get("checkedOn")==null),400,"INVALID_INPUT");return out;}
    public Map<String,Object> relationSave(Connection c,String type,String from,String to,Map<String,Object> body,boolean create) throws SQLException {
        var rel=relation(type);require(one(c,"SELECT id FROM "+rel.fromTable()+" WHERE id=?",from)!=null&&one(c,"SELECT id FROM "+rel.toTable()+" WHERE id=?",to)!=null,400,"INVALID_CONCEPT");
        String where=rel.from()+"=? AND "+rel.to()+"=?";var old=one(c,"SELECT * FROM "+rel.table()+" WHERE "+where,from,to);
        require(create?old==null:old!=null,create?409:404,create?"DUPLICATE_RELATION":"NOT_FOUND");if(type.equals("sub-theme-of"))cycle(c,from,to);
        Input.fields(body,create?"annotation":"","annotation,active");require(!create||!body.containsKey("active"),400,"INVALID_INPUT");var values=new LinkedHashMap<String,Object>();
        if(body.containsKey("annotation")){values.putAll(annotation(body.get("annotation")));if(old!=null)require(old.get("materialKind").equals(values.get("materialKind")),400,"INVALID_INPUT");
            if(!rel.fromTable().equals("theme")){var p=one(c,"SELECT material_kind FROM place WHERE id=?",from);require(p.get("materialKind").equals(values.get("materialKind")),400,"INVALID_RELATION_TYPE");}
            values.put("reviewStatus","DRAFT");values.put("reviewedBy",null);values.put("reviewedAt",null);}
        if(body.containsKey("active"))values.put("active",Input.bool(body.get("active")));
        if(create){values.put(camel(rel.from()),from);values.put(camel(rel.to()),to);insertValues(c,rel.table(),values);}else updateValues(c,rel.table(),where,new Object[]{from,to},values);
        return revision(c,one(c,"SELECT * FROM "+rel.table()+" WHERE "+where,from,to),true);
    }
    public Map<String,Object> review(Connection c,String user,String table,String where,Object[] keys,Map<String,Object> body) throws SQLException {
        Input.fields(body,"status","status");String status=Input.choice(body.get("status"),"APPROVED","REJECTED");var p=one(c,"SELECT * FROM "+table+" WHERE "+where,keys);require(p!=null,404,"NOT_FOUND");
        if(status.equals(p.get("reviewStatus")))return revision(c,p,false);
        if(status.equals("APPROVED")){
            require(p.get("evidenceNote")!=null&&!p.get("evidenceNote").toString().isBlank(),409,"REVIEW_REQUIRED");if(p.get("materialKind").equals("REAL"))require(p.get("sourceUrl")!=null&&p.get("checkedOn")!=null,409,"REVIEW_REQUIRED");
            if(table.equals("place"))CatalogService.concept(c,"region",(String)p.get("regionId"));
            else if(table.equals("place_theme")||table.equals("place_companion")){var place=one(c,"SELECT * FROM place WHERE id=?",p.get("placeId"));require(place!=null&&Boolean.TRUE.equals(place.get("active"))&&place.get("reviewStatus").equals("APPROVED")&&place.get("materialKind").equals(p.get("materialKind")),409,"REVIEW_REQUIRED");CatalogService.concept(c,table.equals("place_theme")?"theme":"companion_type",(String)p.get(table.equals("place_theme")?"themeId":"companionTypeId"));}
            else{CatalogService.concept(c,"theme",(String)p.get("childThemeId"));CatalogService.concept(c,"theme",(String)p.get("parentThemeId"));cycle(c,(String)p.get("childThemeId"),(String)p.get("parentThemeId"));}
        }
        var v=new LinkedHashMap<String,Object>();v.put("reviewStatus",status);v.put("reviewedBy",user);v.put("reviewedAt",java.time.Instant.now());updateValues(c,table,where,keys,v);return revision(c,one(c,"SELECT * FROM "+table+" WHERE "+where,keys),true);
    }
}
