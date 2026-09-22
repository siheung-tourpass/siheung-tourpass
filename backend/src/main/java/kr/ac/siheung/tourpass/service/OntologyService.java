package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.OntologyDao;
import kr.ac.siheung.tourpass.dao.OntologyDao.Relation;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class OntologyService {
    static void resetReview(Map<String,Object> values){values.put("reviewStatus","DRAFT");values.put("reviewedBy",null);values.put("reviewedAt",null);}
    public static void validateRelation(String type){
        require(Set.of("has-theme","suitable-for","sub-theme-of").contains(type),400,"INVALID_RELATION_TYPE");
    }
    private static Relation relation(String type){
        validateRelation(type);
        return OntologyDao.relation(type);
    }
    public static Map<String,String> relationFields(String type){
        var rel=relation(type);return Map.of("fromKey",rel.fromKey(),"toKey",rel.toKey());
    }
    public Map<String,Object> options(Connection c) throws SQLException {return Map.of("regions",OntologyDao.regions(c),"themes",OntologyDao.themes(c),"companionTypes",OntologyDao.companions(c));}
    public List<Map<String,Object>> relations(Connection c,String type) throws SQLException {var rel=relation(type);return OntologyDao.relations(c,rel);}
    public Map<String,Object> relationGet(Connection c,String type,String from,String to) throws SQLException {var rel=relation(type);var result=OntologyDao.relation(c,rel,from,to);require(result!=null,404,"NOT_FOUND");return result;}
    static Map<String,Object> revision(Connection c,Map<String,Object> result,boolean changed) throws SQLException {if(changed)OntologyDao.incrementRevision(c);result.put("modelRevision",OntologyDao.revision(c).get("revision"));return result;}
    public Map<String,List<String>> paths(Connection c,String place) throws SQLException {
        return graph(c).paths(c,place);
    }
    public record ThemeGraph(Map<String,String> codes,Map<String,List<String>> parents) {
        public Map<String,List<String>> paths(Connection c,String place) throws SQLException {
            var direct=OntologyDao.directThemes(c,place);
            var result=new LinkedHashMap<String,List<String>>();var queue=new ArrayDeque<List<String>>();for(var t:direct)queue.add(List.of((String)t.get("id")));
            while(!queue.isEmpty()){var ids=queue.remove();String end=ids.get(ids.size()-1);var path=ids.stream().map(codes::get).toList();var old=result.get(end);if(old!=null&&(old.size()<path.size()||old.size()==path.size()&&String.join("/",old).compareTo(String.join("/",path))<=0))continue;result.put(end,path);
                for(String parent:parents.getOrDefault(end,List.of()))if(!ids.contains(parent)){var next=new ArrayList<>(ids);next.add(parent);queue.add(next);}}
            return result;
        }
    }
    public ThemeGraph graph(Connection c) throws SQLException {
        var themes=OntologyDao.themeCodes(c);var codes=new HashMap<String,String>();themes.forEach(t->codes.put((String)t.get("id"),(String)t.get("code")));
        var edges=OntologyDao.approvedEdges(c);
        var parents=new HashMap<String,List<String>>();for(var e:edges)parents.computeIfAbsent((String)e.get("childThemeId"),k->new ArrayList<>()).add((String)e.get("parentThemeId"));
        return new ThemeGraph(codes,parents);
    }
    public List<Map<String,Object>> browsePlaces(Connection c) throws SQLException {
        return OntologyDao.browsePlaces(c).stream().map(p->CardCoordinates.attach(PlaceImages.attach(CatalogService.demo(p)))).toList();
    }
    public Map<String,Object> place(Connection c,String id) throws SQLException {
        var p=OntologyDao.place(c,id);require(p!=null,404,"NOT_FOUND");p.put("themes",paths(c,id));p.put("companions",OntologyDao.placeCompanions(c,id));p.put("sources",OntologyDao.sources(c,id));return CardCoordinates.attach(PlaceImages.attach(CatalogService.demo(p)));
    }
    public void cycle(Connection c,String child,String parent) throws SQLException {
        require(!child.equals(parent),409,"THEME_CYCLE");var graph=new HashMap<String,List<String>>();for(var e:OntologyDao.allEdges(c))graph.computeIfAbsent((String)e.get("childThemeId"),k->new ArrayList<>()).add((String)e.get("parentThemeId"));
        var seen=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(parent);while(!queue.isEmpty()){String n=queue.remove();require(!n.equals(child),409,"THEME_CYCLE");if(seen.add(n))queue.addAll(graph.getOrDefault(n,List.of()));}
    }
    public static Map<String,Object> annotation(Object value){var a=Input.object(value);Input.fields(a,"materialKind,sourceUrl,checkedOn,evidenceNote");var out=new LinkedHashMap<String,Object>();String kind=Input.choice(a.get("materialKind"),"REAL","DEMO");out.put("materialKind",kind);out.put("sourceUrl",Input.url(a.get("sourceUrl"),true));out.put("checkedOn",Input.date(a.get("checkedOn"),true));out.put("evidenceNote",Input.text(a.get("evidenceNote"),1000,false));require(!kind.equals("DEMO")||(out.get("sourceUrl")==null&&out.get("checkedOn")==null),400,"INVALID_INPUT");return out;}
    public Map<String,Object> relationSave(Connection c,String type,String from,String to,Map<String,Object> body,boolean create) throws SQLException {
        var rel=relation(type);var source=OntologyDao.relationSource(c,rel,from);
        require(source!=null&&OntologyDao.relationTarget(c,rel,to)!=null,400,"INVALID_CONCEPT");
        var old=OntologyDao.relation(c,rel,from,to);
        require(create?old==null:old!=null,create?409:404,create?"DUPLICATE_RELATION":"NOT_FOUND");if(type.equals("sub-theme-of"))cycle(c,from,to);
        Input.fields(body,create?"annotation":"",create?"annotation":"annotation,active");var values=new LinkedHashMap<String,Object>();
        if(body.containsKey("annotation")){values.putAll(annotation(body.get("annotation")));if(old!=null)require(old.get("materialKind").equals(values.get("materialKind")),400,"INVALID_INPUT");
            if(!rel.fromTable().equals("theme"))require(source.get("materialKind").equals(values.get("materialKind")),400,"INVALID_RELATION_TYPE");
            resetReview(values);}
        if(body.containsKey("active"))values.put("active",Input.bool(body.get("active")));
        if(create){values.put(rel.fromKey(),from);values.put(rel.toKey(),to);OntologyDao.createRelation(c,rel,values);}else OntologyDao.saveRelation(c,rel,from,to,values);
        return revision(c,OntologyDao.relation(c,rel,from,to),true);
    }
    public Map<String,Object> reviewPlace(Connection c,String user,String id,Map<String,Object> body) throws SQLException {
        return review(c,user,"place",new Object[]{id},body);
    }
    public Map<String,Object> reviewRelation(Connection c,String user,String type,String from,String to,Map<String,Object> body) throws SQLException {
        return review(c,user,type,new Object[]{from,to},body);
    }
    private Map<String,Object> review(Connection c,String user,String type,Object[] keys,Map<String,Object> body) throws SQLException {
        String table=type.equals("place")?"place":relation(type).table();
        Input.fields(body,"status");String status=Input.choice(body.get("status"),"APPROVED","REJECTED");var p=OntologyDao.reviewTarget(c,type,keys);require(p!=null,404,"NOT_FOUND");
        if(status.equals(p.get("reviewStatus")))return revision(c,p,false);
        if(status.equals("APPROVED")){
            require(p.get("evidenceNote")!=null&&!p.get("evidenceNote").toString().isBlank(),409,"REVIEW_REQUIRED");if(p.get("materialKind").equals("REAL"))require(p.get("sourceUrl")!=null&&p.get("checkedOn")!=null,409,"REVIEW_REQUIRED");
            if(table.equals("place"))CatalogService.concept(c,"region",(String)p.get("regionId"));
            else if(table.equals("place_theme")||table.equals("place_companion")){var place=OntologyDao.reviewPlace(c,p.get("placeId"));require(place!=null&&Boolean.TRUE.equals(place.get("active"))&&place.get("reviewStatus").equals("APPROVED")&&place.get("materialKind").equals(p.get("materialKind")),409,"REVIEW_REQUIRED");CatalogService.concept(c,table.equals("place_theme")?"theme":"companion_type",(String)p.get(table.equals("place_theme")?"themeId":"companionTypeId"));}
            else{CatalogService.concept(c,"theme",(String)p.get("childThemeId"));CatalogService.concept(c,"theme",(String)p.get("parentThemeId"));cycle(c,(String)p.get("childThemeId"),(String)p.get("parentThemeId"));}
        }
        var v=new LinkedHashMap<String,Object>();v.put("reviewStatus",status);v.put("reviewedBy",user);v.put("reviewedAt",java.time.Instant.now());OntologyDao.saveReview(c,type,keys,v);return revision(c,OntologyDao.reviewTarget(c,type,keys),true);
    }
}
