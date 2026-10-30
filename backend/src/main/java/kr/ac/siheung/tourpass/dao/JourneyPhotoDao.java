package kr.ac.siheung.tourpass.dao;

import java.sql.*;
import static kr.ac.siheung.tourpass.dao.Sql.*;

public final class JourneyPhotoDao {
    private JourneyPhotoDao() {}
    public static void save(Connection c,String item,byte[] image) throws SQLException {
        update(c,"INSERT INTO journey_photo(item_id,image) VALUES(?,?) ON CONFLICT(item_id) DO UPDATE SET image=EXCLUDED.image",item,image);
    }
    public static void delete(Connection c,String item) throws SQLException {
        update(c,"DELETE FROM journey_photo WHERE item_id=?",item);
    }
    public static byte[] image(Connection c,String item) throws SQLException {
        var row=one(c,"SELECT image FROM journey_photo WHERE item_id=?",item);
        return row==null?null:(byte[])row.get("image");
    }
}
