package kr.ac.siheung.tourpass.controller;

import java.util.*;
import kr.ac.siheung.tourpass.service.OntologyService;

/** Hypermedia stays in the API representation; services return domain data. */
public final class ApiLinks {
    private ApiLinks() {}
    private static Map<String,Object> link(String base,String path,String method){return Map.of("href",base+path,"method",method);}
    private static void add(Map<String,Object> links,String rel,String base,String path,String method){links.put(rel,link(base,path,method));}
    private static LinkedHashMap<String,Object> copy(Map<?,?> map){
        var copy=new LinkedHashMap<String,Object>();map.forEach((key,value)->copy.put(key.toString(),value));return copy;
    }
    public static Object represent(String base,String path,ApiHandler.Reply reply,Set<String> allowed,Object user,Map<String,String[]> query){
        Object body=reply.body();
        var result=body instanceof Map<?,?> map?copy(map):new LinkedHashMap<String,Object>();
        if(!(body instanceof Map<?,?>))result.put("items",body);
        String self=reply.location()==null?path:reply.location();
        String role=user instanceof Map<?,?> map?Objects.toString(map.get("role"),""):"";
        var links=new LinkedHashMap<String,Object>();
        add(links,"index",base,"","GET");
        // Action responses may contain the updated parent representation.
        if(self.matches("/me/journeys/[0-9]+/places(?:/.*)?"))self=self.substring(0,self.indexOf("/places"));
        add(links,"self",base,self.equals(path)?queryPath(self,query):self,reply.location()!=null||allowed.contains("GET")||self.matches("/me/journeys/[0-9]+")?"GET":allowed.iterator().next());
        if(path.isEmpty()){
            for(String collection:List.of("products","merchants","recommendation-options","recommendations"))add(links,collection,base,"/"+collection,"GET");
            if(role.equals("VISITOR"))for(String collection:List.of("journeys","passes","redemptions"))add(links,collection,base,"/me/"+collection,"GET");
            if(role.equals("STAFF")){
                add(links,"assignments",base,"/merchant/assignments","GET");
                add(links,"pass-lookups",base,"/merchant/pass-lookups","POST");
            }
            if(role.equals("ADMIN")){
                for(String collection:List.of("products","merchants","benefits","passes","place-feedback"))add(links,collection,base,"/admin/"+collection,"GET");
                for(String collection:List.of("places","regions","themes","companion-types"))add(links,collection,base,"/admin/ontology/"+collection,"GET");
            }
        }
        if(path.isEmpty()&&role.equals("ADMIN"))for(String type:List.of("has-theme","suitable-for","sub-theme-of"))
            add(links,type,base,"/admin/ontology/relations?type="+type,"GET");
        if(reply.location()==null||reply.location().equals(path))for(String method:allowed)if(!Set.of("GET","HEAD","OPTIONS").contains(method))add(links,method.toLowerCase(Locale.ROOT),base,path,method);
        decorate(base,self,result,links,role,query);
        if(result.get("page") instanceof Number page&&result.get("size") instanceof Number size&&result.get("total") instanceof Number total){
            int current=page.intValue();
            if(current>1)add(links,"prev",base,pagePath(path,query,current-1),"GET");
            if((long)current*size.intValue()<total.longValue())add(links,"next",base,pagePath(path,query,current+1),"GET");
        }
        result.put("_links",links);
        return result;
    }
    private static String pagePath(String path,Map<String,String[]> query,int page){
        var next=new LinkedHashMap<>(query);next.put("page",new String[]{Integer.toString(page)});return queryPath(path,next);
    }
    private static String queryPath(String path,Map<String,String[]> query){
        var parts=new ArrayList<String>();
        query.forEach((key,values)->{for(String value:values)parts.add(java.net.URLEncoder.encode(key,java.nio.charset.StandardCharsets.UTF_8)+"="+java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8));});
        return path+(parts.isEmpty()?"":"?"+String.join("&",parts));
    }
    private static void decorate(String base,String path,Map<String,Object> result,Map<String,Object> links,String role,Map<String,String[]> query){
        if(result.get("items") instanceof List<?> items){
            var copies=new ArrayList<Object>();
            for(Object item:items){
                if(!(item instanceof Map<?,?> map)){copies.add(item);continue;}
                var copy=copy(map);
                var itemLinks=copy.get("_links") instanceof Map<?,?> existing?copy(existing):new LinkedHashMap<String,Object>();
                if(path.equals("/admin/ontology/relations")&&query.get("type")!=null){
                    String type=query.get("type")[0];var fields=OntologyService.relationFields(type);
                    add(itemLinks,"self",base,path+"/"+type+"/"+copy.get(fields.get("fromKey"))+"/"+copy.get(fields.get("toKey")),"GET");
                }
                if(copy.get("id")!=null&&!path.equals("/recommendations")&&!path.endsWith("place-feedback")){
                    String itemPath=path+"/"+copy.get("id");
                    add(itemLinks,"self",base,itemPath,"GET");
                }
                if(copy.get("place") instanceof Map<?,?> place)add(itemLinks,"place",base,"/places/"+place.get("id"),"GET");
                if(path.equals("/merchant/assignments"))add(itemLinks,"redemptions",base,"/merchant/redemptions?merchantId="+copy.get("merchantId"),"GET");
                if(!itemLinks.isEmpty())copy.put("_links",itemLinks);copies.add(copy);
            }
            result.put("items",copies);
        }
        if(path.matches("/me/journeys/[0-9]+")){
            add(links,"update",base,path,"PATCH");
            boolean planning="PLANNING".equals(result.get("status"));
            if(!planning)links.remove("delete");
            if(planning){add(links,"delete",base,path,"DELETE");add(links,"add-place",base,path+"/places","POST");}
            if(result.get("places") instanceof List<?> places){
                var copies=new ArrayList<Object>();
                for(Object item:places){
                    var place=copy((Map<?,?>)item);
                    var actions=new LinkedHashMap<String,Object>();String itemPath=path+"/places/"+place.get("id");
                    add(actions,"record",base,itemPath+"/record","PUT");
                    if(planning){add(actions,"move",base,itemPath+"/move","POST");add(actions,"delete",base,itemPath,"DELETE");}
                    place.put("_links",actions);copies.add(place);
                }
                result.put("places",copies);
            }
        }
        if(path.matches("/me/redemptions/[0-9]+")){
            String feedback=path+"/feedback";
            add(links,"feedback",base,feedback,"GET");
            add(links,"save-feedback",base,feedback,Boolean.TRUE.equals(result.get("hasFeedback"))?"PUT":"POST");
        }
        if(path.matches("/places/[0-9]+")&&role.equals("VISITOR")){
            String feedback="/me"+path+"/feedback";
            add(links,"feedback",base,feedback,"GET");add(links,"save-feedback",base,feedback,"PUT");
        }
        if(path.matches("/admin/passes/[0-9]+"))add(links,"cancellation",base,path+"/cancellation","PUT");
        if(path.matches("/admin/ontology/places/[0-9]+")||path.matches("/admin/ontology/relations/[^/]+/[0-9]+/[0-9]+"))add(links,"review",base,path+"/review","PUT");
        if(path.matches("/merchant/pass-lookups/[^/]+")&&!Boolean.TRUE.equals(result.get("used"))&&java.time.Instant.now().isBefore(java.time.Instant.parse(result.get("confirmationExpiresAt").toString())))
            add(links,"redeem",base,"/merchant/redemptions","POST");
        if(path.matches("/products/[0-9]+")&&role.equals("VISITOR"))add(links,"issue",base,"/me/passes","POST");
    }
}
