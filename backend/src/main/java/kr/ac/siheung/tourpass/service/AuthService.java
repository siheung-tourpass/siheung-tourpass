package kr.ac.siheung.tourpass.service;
import java.sql.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static kr.ac.siheung.tourpass.dao.Sql.*;
import static kr.ac.siheung.tourpass.service.Problem.require;
public final class AuthService {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static String token(){byte[] b=new byte[16];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String requiredRole(String path){
        if(path.equals("/api/v1")||path.startsWith("/api/v1/")){
            path=path.substring("/api/v1".length());
            return path.startsWith("/me/")?"VISITOR":path.startsWith("/merchant/")?"STAFF":path.startsWith("/admin/")?"ADMIN":null;
        }
        if(path.equals("/journeys")||path.startsWith("/journeys/")||path.equals("/passes")||path.startsWith("/passes/")||path.startsWith("/feedback/"))return "VISITOR";
        if(path.startsWith("/merchant/"))return "STAFF";
        if(path.equals("/admin")||path.startsWith("/admin/"))return "ADMIN";
        return null;
    }
    public Map<String,Object> current(Connection c,String id,String role) throws SQLException {
        if(id==null){require(role==null,401,"AUTH_REQUIRED");return null;}
        var u=one(c,"SELECT id,display_name,role,active FROM app_user WHERE id=? FOR UPDATE",id);
        require(u!=null&&Boolean.TRUE.equals(u.get("active")),401,"AUTH_REQUIRED");
        require(role==null||role.equals(u.get("role")),403,"FORBIDDEN");u.remove("active");return u;
    }
    public Map<String,Object> login(Connection c,Map<String,Object> body) throws Exception {
        Input.fields(body,"loginId,password","loginId,password");String login=Input.text(body.get("loginId"),100,false),password=Input.text(body.get("password"),500,false);
        var u=one(c,"SELECT * FROM app_user WHERE login_id=?",login);
        // A missing login performs the same expensive hash operation.
        String hash=u==null?"pbkdf2-sha256$210000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=":(String)u.get("passwordHash");
        boolean valid=verify(password,hash);require(valid&&u!=null&&Boolean.TRUE.equals(u.get("active")),401,"INVALID_CREDENTIALS");
        return new LinkedHashMap<>(Map.of("id",u.get("id"),"displayName",u.get("displayName"),"role",u.get("role")));
    }
    public static boolean verify(String password,String encoded) throws Exception {
        String[] p=encoded.split("\\$");if(p.length!=4||!p[0].equals("pbkdf2-sha256"))return false;
        byte[] salt=Base64.getDecoder().decode(p[2]),expected=Base64.getDecoder().decode(p[3]);
        var spec=new PBEKeySpec(password.toCharArray(),salt,Integer.parseInt(p[1]),256);
        try{return MessageDigest.isEqual(expected,SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}
    }
}
