import java.util.*;
import kr.ac.siheung.tourpass.service.JourneyTemplates;
import kr.ac.siheung.tourpass.service.Problem;

public final class JourneyTemplatesCheck {
    public static void main(String[] args) {
        var empty=JourneyTemplates.list(List.of());
        assert empty.stream().noneMatch(item->Boolean.TRUE.equals(item.get("available")));
        assert empty.stream().noneMatch(item->item.containsKey("coverImage"));
        var template=empty.get(0);
        String code=(String)template.get("code");
        var places=new ArrayList<Map<String,Object>>();
        for(Object place:(List<?>)template.get("placeCodes"))places.add(Map.of("code",place));
        assert Boolean.TRUE.equals(JourneyTemplates.find(code,places).get("available"));
        assert template.get("cardCount").equals(places.size());
        var image=Map.of("path","/assets/places/template.jpg");
        places.set(1,Map.of("code",places.get(1).get("code"),"image",image));
        assert JourneyTemplates.find(code,places).get("coverImage").equals(image);
        places.add(Map.of("code","UNRELATED","image",Map.of("path","/assets/places/unrelated.jpg")));
        assert JourneyTemplates.find(code,places).get("coverImage").equals(image);
        places.remove(0);
        try { JourneyTemplates.find(code,places);throw new AssertionError("partial template accepted"); }
        catch(Problem expected) { assert expected.status==409; }
        try { JourneyTemplates.find("unknown",places);throw new AssertionError("unknown template accepted"); }
        catch(Problem expected) { assert expected.status==400; }
        System.out.println("PASS: template availability requires the complete set; partial and unknown templates rejected");
    }
}
