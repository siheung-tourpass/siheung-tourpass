package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import java.util.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class AuthDao {
    private AuthDao() {}

    public static Map<String,Object> lockCurrentUser(Connection c, Object id) throws SQLException {
        return one(c,"SELECT id,display_name,role,active FROM app_user WHERE id=? FOR UPDATE",id);
    }
    public static Map<String,Object> kakaoUser(Connection c,long kakaoId,String loginId) throws SQLException {
        return one(c,"INSERT INTO app_user(login_id,password_hash,display_name,role,kakao_id) VALUES(?,'','카카오 방문객','VISITOR',?) ON CONFLICT(kakao_id) DO UPDATE SET kakao_id=EXCLUDED.kakao_id RETURNING id,display_name,role,active",loginId,kakaoId);
    }
    public static Map<String,Object> findByLogin(Connection c, Object login) throws SQLException {
        return one(c,"SELECT * FROM app_user WHERE login_id=?",login);
    }
}
