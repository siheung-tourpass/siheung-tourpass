import java.util.*;
import kr.ac.siheung.tourpass.service.DiningCatalog;

public final class DiningCatalogCheck {
    public static void main(String[] args) throws Exception {
        var cards=DiningCatalog.list();
        assert cards.size()==7;
        var codes=new HashSet<Object>();
        for(var card:cards){
            assert codes.add(card.get("code"));
            assert card.equals(DiningCatalog.find((String)card.get("code")));
            assert card.get("materialKind").equals("REAL");
            assert card.get("reviewStatus").equals("SOURCE_CHECKED");
            assert card.get("sourceUrl").toString().startsWith("https://www.siheung.go.kr/");
            assert card.get("checkedOn").equals("2026-10-18");
            assert card.get("locationDescription").toString().startsWith("경기도 시흥시 ");
            assert !card.containsKey("benefits");
        }
        assert cards.stream().filter(card->card.get("category").equals("카페")).count()==2;
        var cafe=DiningCatalog.find("DINING_PAULBASSETT");
        assert cafe.get("designationNumber").equals("HG200007083");
        assert cafe.get("contentDate").equals("2026-10-02");
        assert DiningCatalog.find("DINING_BORIGULBI").get("contentDate").equals("미확인");
        assert !DiningCatalog.find("DINING_BORIGULBI").containsKey("image");
        var image=(Map<?,?>)DiningCatalog.find("DINING_BEIJING").get("image");
        assert image.get("path").equals("/assets/dining/beijing-exterior.jpg");
        assert image.get("width").equals(940) && image.get("height").equals(626);
        assert image.get("attribution").toString().contains("한국관광공사");
        assert image.get("licenseUrl").toString().endsWith("/licenseType3.do");
        var photo=javax.imageio.ImageIO.read(java.nio.file.Path.of("data/dining-images/beijing-exterior.jpg").toFile());
        assert photo.getWidth()==940 && photo.getHeight()==626;
        cards.get(0).put("name","changed");
        assert !DiningCatalog.list().get(0).get("name").equals("changed");
        assert DiningCatalog.find("missing")==null;
        System.out.println("PASS: official dining sources, designation metadata, categories and isolated card state");
    }
}
