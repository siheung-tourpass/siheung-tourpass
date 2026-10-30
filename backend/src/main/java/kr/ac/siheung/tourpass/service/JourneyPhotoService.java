package kr.ac.siheung.tourpass.service;

import java.io.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.sql.*;
import kr.ac.siheung.tourpass.dao.JourneyDao;
import kr.ac.siheung.tourpass.dao.JourneyPhotoDao;
import static kr.ac.siheung.tourpass.service.Problem.require;

public final class JourneyPhotoService {
    public static final int MAX_BYTES=8*1024*1024;
    private void owned(Connection c,String user,String journey,String item,boolean lock) throws SQLException {
        require(JourneyDao.findOwned(c,lock,journey,user)!=null,404,"NOT_FOUND");
        require(JourneyDao.findCard(c,item,journey)!=null,404,"NOT_FOUND");
    }
    public byte[] image(Connection c,String user,String journey,String item) throws SQLException {
        owned(c,user,journey,item,false);
        byte[] image=JourneyPhotoDao.image(c,item);require(image!=null,404,"NOT_FOUND");return image;
    }
    public void save(Connection c,String user,String journey,String item,byte[] bytes) throws SQLException {
        owned(c,user,journey,item,true);
        JourneyPhotoDao.save(c,item,normalize(bytes));
    }
    public void delete(Connection c,String user,String journey,String item) throws SQLException {
        owned(c,user,journey,item,true);JourneyPhotoDao.delete(c,item);
    }
    public static byte[] normalize(byte[] bytes) {
        require(bytes!=null&&bytes.length>0&&bytes.length<=MAX_BYTES,400,"INVALID_PHOTO");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);require(readers.hasNext(),400,"INVALID_PHOTO");
            var reader=readers.next();
            try {
                require(java.util.Set.of("JPEG","PNG").contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT)),400,"INVALID_PHOTO");
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                require(w>0&&h>0&&(long)w*h<=16_000_000,400,"INVALID_PHOTO");
                var decoded=reader.read(0);
                var clean=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                var graphics=clean.createGraphics();
                try { graphics.setColor(Color.WHITE);graphics.fillRect(0,0,w,h);graphics.drawImage(decoded,0,0,null); }
                finally { graphics.dispose(); }
                var output=new ByteArrayOutputStream();ImageIO.write(clean,"jpeg",output);
                require(output.size()<=MAX_BYTES,400,"INVALID_PHOTO");return output.toByteArray();
            } finally { reader.dispose(); }
        }catch(IOException|IllegalArgumentException e){throw new Problem(400,"INVALID_PHOTO");}
    }
}
