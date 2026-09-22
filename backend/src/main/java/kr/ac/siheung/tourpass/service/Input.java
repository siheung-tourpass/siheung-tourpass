package kr.ac.siheung.tourpass.service;
import java.util.*;
import java.net.URI;
import java.time.LocalDate;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class Input {
    private Input(){}
    public static void fields(Map<String,Object> b,String required,String allowed) {
        Set<String> a=new HashSet<>(Arrays.asList(allowed.split(",")));
        require(a.containsAll(b.keySet()),400,"INVALID_INPUT");
        for(String k:required.split(",")) if(!k.isEmpty())require(b.containsKey(k),400,"INVALID_INPUT");
    }
    public static String text(Object v,int max,boolean nullable) {
        if(v==null){require(nullable,400,"INVALID_INPUT");return null;}
        require(v instanceof String,400,"INVALID_INPUT");String s=((String)v).strip();require(s.length()<=max&&(nullable||!s.isEmpty()),400,"INVALID_INPUT");return s.isEmpty()?null:s;
    }
    public static String id(Object v){require(v instanceof String&&((String)v).matches("[1-9][0-9]{0,18}"),400,"INVALID_INPUT");try{require(Long.parseLong((String)v)>0,400,"INVALID_INPUT");}catch(NumberFormatException e){throw new Problem(400,"INVALID_INPUT");}return (String)v;}
    public static int integer(Object v,int min,int max){require(v instanceof Integer||v instanceof Long,400,"INVALID_INPUT");long n=((Number)v).longValue();require(n>=min&&n<=max,400,"INVALID_INPUT");return (int)n;}
    public static boolean bool(Object v){require(v instanceof Boolean,400,"INVALID_INPUT");return (Boolean)v;}
    public static String choice(Object v,String... choices){require(v instanceof String&&Arrays.asList(choices).contains(v),400,"INVALID_INPUT");return (String)v;}
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object v){require(v instanceof Map,400,"INVALID_INPUT");return (Map<String,Object>)v;}
    public static List<?> array(Object v){require(v instanceof List,400,"INVALID_INPUT");return (List<?>)v;}
    public static String url(Object v,boolean nullable){String s=text(v,2048,nullable);if(s!=null)try{URI u=URI.create(s);require(Set.of("http","https").contains(u.getScheme())&&u.getHost()!=null,400,"INVALID_INPUT");}catch(IllegalArgumentException e){throw new Problem(400,"INVALID_INPUT");}return s;}
    public static String date(Object v,boolean nullable){String s=text(v,10,nullable);if(s!=null)try{require(LocalDate.parse(s).toString().equals(s),400,"INVALID_INPUT");}catch(java.time.DateTimeException e){throw new Problem(400,"INVALID_INPUT");}return s;}
}
