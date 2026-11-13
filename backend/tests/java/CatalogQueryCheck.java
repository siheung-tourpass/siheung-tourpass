import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Map;
import kr.ac.siheung.tourpass.dao.Sql;
import kr.ac.siheung.tourpass.service.CatalogService;

/** Run against a seeded test database; every mutation is rolled back. */
public final class CatalogQueryCheck {
    private static void catalog(Connection connection) throws Exception {
        var queries=new int[1];
        var counted=(Connection)Proxy.newProxyInstance(CatalogQueryCheck.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement"))queries[0]++;
            try{return method.invoke(connection,args);}
            catch(InvocationTargetException error){throw error.getCause();}
        });
        var service=new CatalogService();
        for(String collection:new String[]{"products","merchants"}){
            queries[0]=0;
            var items=service.withBenefits(counted,collection,null);
            assert queries[0]==(items.isEmpty()?1:2):"Benefit queries must not grow with the catalog";
            for(var item:items){
                var detail=service.detail(connection,collection,(String)item.get("id"));
                assert item.equals(detail):collection+" list/detail mismatch: "+item.get("id");
            }
        }
        for(var region:Sql.rows(connection,"SELECT id FROM region WHERE active=TRUE")){
            for(var item:service.withBenefits(connection,"merchants",(String)region.get("id")))
                assert item.get("regionId").equals(region.get("id"));
        }
    }
    public static void main(String[] args) throws Exception {
        try(var connection=DriverManager.getConnection(args[0],System.getenv("TOURPASS_DB_USER"),System.getenv("TOURPASS_DB_PASSWORD"))){
            connection.setAutoCommit(false);
            try{
                Sql.update(connection,"INSERT INTO product(code,name,description) VALUES('catalog_query_check','혜택 없는 상품','목록 조회 회귀 검사')");
                catalog(connection);
                for(String table:new String[]{"benefit","merchant","product"}){
                    var savepoint=connection.setSavepoint();
                    Sql.update(connection,"UPDATE "+table+" SET active=FALSE");
                    catalog(connection);
                    connection.rollback(savepoint);
                }
                assert Sql.one(connection,"SELECT 1 WHERE FALSE")==null;
                var first=Sql.one(connection,"SELECT n AS id, DATE '2026-11-13' AS checked_on, TIMESTAMPTZ '2026-11-13 00:00:00+00' AS created_at FROM generate_series(1,3) n");
                assert first.equals(Map.of("id","1","checkedOn","2026-11-13","createdAt","2026-11-13T00:00:00Z")):first;
            }finally{connection.rollback();}
        }
        System.out.println("PASS: bounded catalog queries, list/detail parity, inactive filters and single-row conversion");
    }
}
