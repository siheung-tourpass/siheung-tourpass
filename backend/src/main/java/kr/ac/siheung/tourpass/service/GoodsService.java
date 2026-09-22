package kr.ac.siheung.tourpass.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static kr.ac.siheung.tourpass.service.Problem.require;

/** Public catalog content only; printed QR codes never contain pass or account data. */
public final class GoodsService {
    public static Map<String,Object> find(List<Map<String,Object>> cards,String key) {
        return cards.stream().filter(card->card.get("key").equals(key)).findFirst().orElseThrow(()->new Problem(404,"NOT_FOUND"));
    }
    public static String baseUrl(String value){
        String text=Input.text(value,240,false);
        try{
            URI uri=URI.create(text);
            require(("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))&&uri.getHost()!=null&&uri.getRawUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null,400,"INVALID_INPUT");
            require(uri.getPort()>=-1&&uri.getPort()<=65535,400,"INVALID_INPUT");
            return uri.toASCIIString().replaceAll("/+$","");
        }catch(IllegalArgumentException e){throw new Problem(400,"INVALID_INPUT");}
    }
    public static Map<String,Object> preview(Map<String,Object> card,String base,String caption,String copies){
        String normalizedBase=baseUrl(base);
        String url=normalizedBase+"/cards/detail?key="+URLEncoder.encode((String)card.get("key"),StandardCharsets.UTF_8);
        int count=Integer.parseInt(Input.choice(copies,"1","2","3","4"));
        var result=new LinkedHashMap<String,Object>();result.put("card",card);result.put("url",url);result.put("baseUrl",normalizedBase);
        result.put("caption",Input.text(caption,40,true));result.put("copies",count);result.put("qr",qr(url));return result;
    }
    public static Map<String,Object> qr(String url){
        try{
            var matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,0,0,Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M));
            StringBuilder path=new StringBuilder();
            for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)if(matrix.get(x,y))path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            return Map.of("size",matrix.getWidth(),"path",path.toString());
        }catch(com.google.zxing.WriterException e){throw new Problem(400,"INVALID_INPUT");}
    }
}
