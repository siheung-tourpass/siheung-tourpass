package kr.ac.siheung.tourpass.config;
import java.sql.*;
import javax.sql.DataSource;
import javax.naming.InitialContext;
import kr.ac.siheung.tourpass.service.Problem;
public final class Database {
    @FunctionalInterface public interface Work<T> {T run(Connection c) throws Exception;}
    private static Connection open() throws Exception {
        String url=System.getenv("TOURPASS_DB_URL");
        Connection c;
        if(url==null) c=((DataSource)new InitialContext().lookup("java:comp/env/jdbc/TourpassDB")).getConnection();
        else {Class.forName("com.mysql.cj.jdbc.Driver");c=DriverManager.getConnection(url,System.getenv("TOURPASS_DB_USER"),System.getenv("TOURPASS_DB_PASSWORD"));}
        try(var s=c.createStatement()){s.execute("SET time_zone = '+00:00'");return c;}catch(SQLException e){c.close();throw e;}
    }
    public static <T>T transaction(Work<T> work) {return transaction(false,work);}
    public static <T>T transaction(boolean write,Work<T> work) {
        try(var c=open()) {c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try {
                // ponytail: serialize demo writes; use domain row locks if throughput requires it.
                if(write)kr.ac.siheung.tourpass.dao.Sql.one(c,"SELECT revision FROM ontology_revision WHERE id=1 FOR UPDATE");
                T result=work.run(c);c.commit();return result;} catch(Exception e){c.rollback();throw e;}
        } catch(Problem e){throw e;} catch(SQLException e){
            if(e.getErrorCode()==1213||e.getErrorCode()==1205)throw new Problem(503,"RETRY_LATER");
            if(e.getErrorCode()==1062)throw new Problem(409,"DUPLICATE_RESOURCE");
            throw new Problem(500,"INTERNAL_ERROR");
        } catch(Exception e){throw new Problem(500,"INTERNAL_ERROR");}
    }
}
