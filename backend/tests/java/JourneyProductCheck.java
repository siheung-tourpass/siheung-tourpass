import java.util.*;
import kr.ac.siheung.tourpass.service.CatalogService;

public final class JourneyProductCheck {
    public static void main(String[] args) {
        var place=Map.<String,Object>of("placeId","1");
        var dining=Map.<String,Object>of("diningCode","CAFE");
        var product=Map.<String,Object>of("benefits",List.of(dining,place,dining));
        var cards=List.of(Map.<String,Object>of("key","place:1"),Map.<String,Object>of("key","dining:CAFE"));
        assert CatalogService.journeyCardKeys(product,cards).equals(List.of("dining:CAFE","place:1"));
        assert CatalogService.journeyCardKeys(product,cards.subList(0,1)).isEmpty();
        assert CatalogService.journeyCardKeys(product,List.of()).isEmpty();
        assert CatalogService.journeyCardKeys(Map.of("benefits",List.of()),cards).isEmpty();
        assert CatalogService.journeyCardKeys(Map.of("benefits",List.of(Map.of("merchantId","1"))),cards).isEmpty();
        var anotherPlace=Map.<String,Object>of("placeId","1","name","second benefit");
        var grouped=CatalogService.groupBenefits(List.of(dining,place,anotherPlace));
        assert grouped.get("place:1").equals(List.of(place,anotherPlace));
        assert grouped.get("dining:CAFE").equals(List.of(dining));
        assert CatalogService.groupBenefits(List.of()).isEmpty();
        System.out.println("PASS: product starts preserve benefit order, deduplicate locations and reject incomplete sets");
    }
}
