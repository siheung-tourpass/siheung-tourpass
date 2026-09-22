package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class AuthDao {
    private AuthDao() {}

    public static Map<String,Object> lockCurrentUser(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id,display_name,role,active FROM app_user WHERE id=? FOR UPDATE",id);
    }
    public static Map<String,Object> findByLogin(Connection c, Object login) throws SQLException {
        return one(c,"SELECT * FROM app_user WHERE login_id=?",login);
    }
}
