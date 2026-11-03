import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import kr.ac.siheung.tourpass.service.JourneyPhotoService;
import kr.ac.siheung.tourpass.service.Problem;

public class JourneyPhotoCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless","true");
        for(String format:new String[]{"png","jpeg"}) {
            var output=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(40,30,BufferedImage.TYPE_INT_RGB),format,output);
            byte[] normalized=JourneyPhotoService.normalize(output.toByteArray());
            assert normalized[0]==(byte)0xff&&normalized[1]==(byte)0xd8;
            assert ImageIO.read(new ByteArrayInputStream(normalized)).getWidth()==40;
        }
        for(byte[] invalid:new byte[][]{new byte[0],new byte[JourneyPhotoService.MAX_BYTES+1],"<svg/>".getBytes(),new byte[]{(byte)0xff,(byte)0xd8}}) {
            try { JourneyPhotoService.normalize(invalid);throw new AssertionError("Invalid image accepted"); }
            catch(Problem expected){assert expected.code.equals("INVALID_PHOTO");}
        }
        var large=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4001,4000,BufferedImage.TYPE_BYTE_GRAY),"png",large);
        try { JourneyPhotoService.normalize(large.toByteArray());throw new AssertionError("Oversized dimensions accepted"); }
        catch(Problem expected){assert expected.code.equals("INVALID_PHOTO");}
        System.out.println("PASS: photo formats, invalid content, byte and pixel limits");
    }
}
