package com.vingame.glswing.rendering;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.vingame.glswing.component.GLContainer;
import java.awt.Color;
import javax.swing.SwingUtilities;
@EnabledIfSystemProperty(named="glswing.test.gl",matches="true")
class GLComponentImageCaptureTest {
    @Test void failuresReleaseContextAndAlphaSurvives() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var broken=new GLContainer(){protected void record(GLContext2D c){throw new IllegalStateException("test render failure");}};
            broken.setBounds(0,0,32,16);
            assertThrows(RuntimeException.class,()->GLComponentImageCapture.capture(broken,32,16,64,32,true));
            var good=new GLContainer(){protected void record(GLContext2D c){c.fillRect(4,4,8,8,Color.RED);}};
            good.setBounds(0,0,32,16);
            var image=GLComponentImageCapture.capture(good,32,16,64,32,true);
            assertEquals(0,image.getRGB(0,0)>>>24);assertEquals(0xFFFF0000,image.getRGB(12,12));
            assertEquals(32,good.getWidth());assertEquals(16,good.getHeight());
            assertThrows(RuntimeException.class,()->GLComponentImageCapture.capture(good,32,16,1000000,1,true));
            assertNotNull(GLComponentImageCapture.capture(good,32,16,32,16,false));
        });
    }
}
