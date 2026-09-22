package kr.ac.siheung.tourpass.dao;
import java.sql.*;
import java.time.*;
import java.util.*;
public final class Sql {
    private Sql() {}
    public static List<Map<String,Object>> rows(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) { bind(s,args); try(var r=s.executeQuery()) {
            var out=new ArrayList<Map<String,Object>>(); var md=r.getMetaData();
            while(r.next()) { var row=new LinkedHashMap<String,Object>(); for(int i=1;i<=md.getColumnCount();i++) {
                String key=camel(md.getColumnLabel(i)); Object value=r.getObject(i);
                if(value instanceof Timestamp t) value=t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
                else if(value instanceof LocalDateTime t) value=t.toInstant(ZoneOffset.UTC).toString();
                else if(value instanceof java.sql.Date d) value=d.toString();
                else if((key.equals("id")||key.endsWith("Id")||key.equals("reviewedBy")||key.equals("cancelledBy")||key.equals("revision"))&&value!=null) value=value.toString();
                else if(Set.of("active","demoOnly","reservationRequired","hasFeedback","merchantActive","benefitActive").contains(key)&&value instanceof Number n) value=n.intValue()!=0;
                row.put(key,value);
            } out.add(row); } return out;
        }}
    }
    public static Map<String,Object> one(Connection c,String sql,Object... args) throws SQLException {
        var r=rows(c,sql,args); return r.isEmpty()?null:r.get(0);
    }
    public static int update(Connection c,String sql,Object... args) throws SQLException { try(var s=c.prepareStatement(sql)) {bind(s,args);return s.executeUpdate();} }
    public static String insert(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {bind(s,args);s.executeUpdate();try(var r=s.getGeneratedKeys()){return r.next()?r.getString(1):null;}}
    }
    // Table, column and WHERE fragments must come from trusted application code.
    public static String insertValues(Connection c,String table,Map<String,Object> values) throws SQLException {
        String columns=String.join(",",values.keySet().stream().map(Sql::snake).toList());
        return insert(c,"INSERT INTO "+table+"("+columns+") VALUES("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray());
    }
    public static void updateValues(Connection c,String table,String where,Object[] keys,Map<String,Object> values) throws SQLException {
        if(values.isEmpty())return;
        var args=new ArrayList<>(values.values());args.addAll(Arrays.asList(keys));
        update(c,"UPDATE "+table+" SET "+String.join(",",values.keySet().stream().map(k->snake(k)+"=?").toList())+" WHERE "+where,args.toArray());
    }
    private static void bind(PreparedStatement s,Object[] args) throws SQLException {
        for(int i=0;i<args.length;i++) {Object v=args[i]; if(v instanceof Instant t)v=Timestamp.valueOf(LocalDateTime.ofInstant(t,ZoneOffset.UTC));s.setObject(i+1,v);}
    }
    public static String camel(String value) {var b=new StringBuilder();boolean upper=false;for(char ch:value.toCharArray()) {if(ch=='_')upper=true;else {b.append(upper?Character.toUpperCase(ch):ch);upper=false;}}return b.toString();}
    public static String snake(String value) {return value.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
}
