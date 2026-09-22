import java.util.*;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import kr.ac.siheung.tourpass.service.DiningCatalog;

public final class DiningCatalogCheck {
    public static void main(String[] args) throws Exception {
        var cards=DiningCatalog.list();
        assert !cards.isEmpty();
        var codes=new HashSet<Object>();
        for(var card:cards){
            assert codes.add(card.get("code"));
            assert card.equals(DiningCatalog.find((String)card.get("code")));
            assert card.get("materialKind").equals("REAL");
            assert card.get("reviewStatus").equals("SOURCE_CHECKED");
            assert card.get("sourceUrl").toString().startsWith("https://www.siheung.go.kr/");
            LocalDate.parse((String)card.get("checkedOn"));
            assert !card.get("locationDescription").toString().isBlank();
            assert !card.containsKey("benefits");
            if(card.get("image") instanceof Map<?,?> image){
                String path=(String)image.get("path");
                assert path.matches("/assets/dining/[a-z0-9-]+\\.jpg");
                assert !image.get("attribution").toString().isBlank();
                assert image.get("licenseUrl").toString().startsWith("https://");
                var photo=ImageIO.read(Path.of("data/dining-images",Path.of(path).getFileName().toString()).toFile());
                assert photo!=null;
                assert photo.getWidth()==((Number)image.get("width")).intValue();
                assert photo.getHeight()==((Number)image.get("height")).intValue();
            }
        }
        cards.get(0).put("name","changed");
        assert !DiningCatalog.list().get(0).get("name").equals("changed");
        assert DiningCatalog.find("missing")==null;
        System.out.println("PASS: dining provenance, image attribution/files and isolated card state");
    }
}
