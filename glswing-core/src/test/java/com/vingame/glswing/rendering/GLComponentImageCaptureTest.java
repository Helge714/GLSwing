package com.vingame.glswing.rendering;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.SwingUtilities;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.ui.GLLabel;

@EnabledIfSystemProperty(named="glswing.test.gl",matches="true")
class GLComponentImageCaptureTest {
    @Test void repeatedCapturesKeepTheirFontsAndPreserveTheSourceSurface()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            for(boolean attached:new boolean[]{false,true}){
                var root=new GLContainer(){protected void record(GLContext2D context){}};
                root.setBounds(0,0,300,90);
                var label=new GLLabel("Repeated capture font");root.addGL(label);label.setBounds(10,10,270,50);
                var surface=new GLSwingPanel(){};
                if(attached)surface.addGL(root);
                var parent=root.getGLParent();
                var sourceFont=label.getGLFont(label.getGLStyle().font());
                boolean previouslyInitialized=sourceFont.isInitialized();
                var first=GLComponentImageCapture.capture(root,300,90,300,90,false);
                var second=GLComponentImageCapture.capture(root,300,90,300,90,false);
                int[] a=first.getRGB(0,0,300,90,null,0,300),b=second.getRGB(0,0,300,90,null,0,300);
                assertTrue(java.util.Arrays.stream(a).anyMatch(pixel->(pixel&0xffffff)!=0),"Capture must contain visible text");
                assertArrayEquals(a,b,"Repeated captures must retain identical text pixels");
                assertEquals(previouslyInitialized,sourceFont.isInitialized(),"Capture must not upload the source surface font");
                assertFalse(sourceFont.isDisposed());
                assertSame(parent,root.getGLParent());assertSame(root,label.getGLParent());
                assertEquals(300,root.getWidth());assertEquals(90,root.getHeight());
                assertNull(GLComponentImageCapture.resolveCaptureFont(label.getGLStyle().font()),"Capture font scope must be restored");
                surface.removeAllGL();
            }
        });
    }
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
