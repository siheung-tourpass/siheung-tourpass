import java.util.*;
import java.util.regex.Pattern;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;
import kr.ac.siheung.tourpass.service.GoodsService;
import kr.ac.siheung.tourpass.service.Problem;
import kr.ac.siheung.tourpass.filter.SecurityFilter;

public final class GoodsCheck {
    public static void main(String[] args) throws Exception {
        assert "ADMIN".equals(SecurityFilter.requiredRole("/admin/goods"));
        assert SecurityFilter.requiredRole("/cards/detail")==null;
        var card=Map.<String,Object>of("key","event:행사&test");
        assert GoodsService.find(List.of(card),"event:행사&test")==card;
        for(String key:Arrays.asList(null,"event:missing")){
            try{GoodsService.find(List.of(card),key);throw new AssertionError("Expected missing card");}
            catch(Problem e){assert e.status==404;}
        }
        var preview=GoodsService.preview(card,"https://example.com/tourpass/","여행 기념", "4");
        assert preview.get("baseUrl").equals("https://example.com/tourpass");
        assert preview.get("url").equals("https://example.com/tourpass/cards/detail?key=event%3A%ED%96%89%EC%82%AC%26test");
        assert preview.get("copies").equals(4);
        var qr=(Map<?,?>)preview.get("qr");int size=(Integer)qr.get("size");
        var matrix=new BitMatrix(size);var cells=Pattern.compile("M(\\d+) (\\d+)h1v1h-1z").matcher((String)qr.get("path"));
        while(cells.find())matrix.set(Integer.parseInt(cells.group(1)),Integer.parseInt(cells.group(2)));
        // Decode the emitted SVG's actual cells, including the required quiet zone.
        var inner=new BitMatrix(size-8);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            if(x<4||y<4||x>=size-4||y>=size-4)assert !matrix.get(x,y);
            else if(matrix.get(x,y))inner.set(x-4,y-4);
        }
        assert new Decoder().decode(inner).getText().equals(preview.get("url"));
        for(String invalid:List.of("javascript:alert(1)","/relative","https://a.test/?x=1","https://a.test/#x","https://user:pass@a.test","https://a.test:99999"))reject(()->GoodsService.baseUrl(invalid));
        for(String copies:List.of("0","5","-1","1.5","abc"))reject(()->GoodsService.preview(card,"https://example.com",null,copies));
        reject(()->GoodsService.preview(card,"https://example.com","a".repeat(41),"1"));
        System.out.println("PASS: goods URL validation, QR round-trip, quiet zone, copies, caption and access policy");
    }
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Expected invalid input");}catch(Problem e){assert e.status==400;}}
}
