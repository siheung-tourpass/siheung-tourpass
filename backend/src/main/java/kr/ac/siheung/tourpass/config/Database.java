package kr.ac.siheung.tourpass.config;
import java.sql.*;
import java.util.Set;
import javax.sql.DataSource;
import javax.naming.InitialContext;
import kr.ac.siheung.tourpass.service.Problem;
public final class Database {
    @FunctionalInterface public interface Work<T> {T run(Connection c) throws Exception;}
    private static Connection open() throws Exception {
        String url=System.getenv("TOURPASS_DB_URL");
        Connection c;
        if(url==null) c=((DataSource)new InitialContext().lookup("java:comp/env/jdbc/TourpassDB")).getConnection();
        else {c=DriverManager.getConnection(url,System.getenv("TOURPASS_DB_USER"),System.getenv("TOURPASS_DB_PASSWORD"));}
        try(var s=c.createStatement()){s.execute("SET TIME ZONE 'UTC'");return c;}catch(SQLException e){c.close();throw e;}
    }
    public static <T>T transaction(Work<T> work) {return transaction(false,work);}
    public static <T>T transaction(boolean write,Work<T> work) {
        try(var c=open()) {c.setTransactionIsolation(write?Connection.TRANSACTION_READ_COMMITTED:Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try {
                // shortcut: serialize demo writes; use domain row locks if throughput requires it.
                // READ COMMITTED sees the preceding writer after waiting for this lock.
                if(write)kr.ac.siheung.tourpass.dao.Sql.one(c,"SELECT revision FROM ontology_revision WHERE id=1 FOR UPDATE");
                T result=work.run(c);c.commit();return result;} catch(Exception e){c.rollback();throw e;}
        } catch(Problem e){throw e;} catch(SQLException e){
            if(Set.of("40001","40P01","55P03").contains(e.getSQLState()))throw new Problem(503,"RETRY_LATER");
            if("23505".equals(e.getSQLState()))throw new Problem(409,"DUPLICATE_RESOURCE");
            System.getLogger(Database.class.getName()).log(System.Logger.Level.ERROR,"Database transaction failed",e);
            throw new Problem(500,"INTERNAL_ERROR");
        } catch(Exception e){System.getLogger(Database.class.getName()).log(System.Logger.Level.ERROR,"Database transaction failed",e);
            throw new Problem(500,"INTERNAL_ERROR");}
    }
}
