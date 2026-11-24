package com.vingame.glswing.rendering;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
class GLImageExportTest {
    @TempDir Path root;
    @Test void pngKeepsAlphaAndJpegAcceptsAlpha() throws Exception {
        var image=new BufferedImage(12,8,BufferedImage.TYPE_INT_ARGB);image.setRGB(3,4,0x80FF0000);
        var out=new ByteArrayOutputStream();GLImageExport.encode(image,"png",1,out);
        var png=ImageIO.read(new ByteArrayInputStream(out.toByteArray()));assertEquals(image.getRGB(3,4),png.getRGB(3,4));assertEquals(0,png.getRGB(0,0));
        out.reset();GLImageExport.encode(image,"jpg",.9f,out);assertNotNull(ImageIO.read(new ByteArrayInputStream(out.toByteArray())));
    }
    @Test void failedEncodingPreservesDestinationAndCleansTemp() throws Exception {
        var path=root.resolve("existing.png");Files.writeString(path,"keep");
        var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
        assertThrows(GLImageExport.EncoderUnavailableException.class,()->GLImageExport.write(image,"unknown-format",path,1));
        assertEquals("keep",Files.readString(path));try(var files=Files.list(root)){assertEquals(1,files.count());}
        assertThrows(IllegalArgumentException.class,()->GLImageExport.write(image,"jpg",path,Float.NaN));
        assertEquals("keep",Files.readString(path));
        GLImageExport.write(image,"png",path,1);assertNotNull(ImageIO.read(path.toFile()));
    }
}
