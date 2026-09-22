package kr.ac.siheung.tourpass.service;

import java.sql.*;
import java.util.*;
import kr.ac.siheung.tourpass.dao.PlaceFeedbackDao;
import static kr.ac.siheung.tourpass.service.Problem.require;

public final class PlaceFeedbackService {
    public Map<String,Object> get(Connection c,String user,String place) throws SQLException {
        var feedback=PlaceFeedbackDao.find(c,user,place);
        require(feedback!=null,404,"NOT_FOUND");return feedback;
    }
    public Map<String,Object> save(Connection c,String user,String place,Map<String,Object> body) throws SQLException {
        Input.fields(body,"rating","rating,comment");
        int rating=Input.integer(body.get("rating"),1,5);
        String comment=Input.text(body.get("comment"),500,true);
        require(PlaceFeedbackDao.available(c,place),404,"NOT_FOUND");
        PlaceFeedbackDao.save(c,user,place,rating,comment);return get(c,user,place);
    }
    public void delete(Connection c,String user,String place) throws SQLException {
        PlaceFeedbackDao.delete(c,user,place);
    }
    public Map<String,Object> current(Connection c,String user,String place) throws SQLException {
        var feedback=PlaceFeedbackDao.find(c,user,place);return feedback==null?Map.of():feedback;
    }
    public List<Map<String,Object>> statistics(Connection c) throws SQLException {
        return PlaceFeedbackDao.statistics(c);
    }
}
