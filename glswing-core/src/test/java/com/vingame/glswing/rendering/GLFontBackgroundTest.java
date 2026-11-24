package com.vingame.glswing.rendering;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.Font;
import java.awt.font.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.jogamp.opengl.GL3;

class GLFontBackgroundTest {
    @Test void rasterizationDoesNotHoldTheSharedCacheMonitor() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var slow=new Font(Font.SANS_SERIF,Font.PLAIN,19){
            @Override public GlyphVector createGlyphVector(FontRenderContext context,int[] codes){
                entered.countDown();
                try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
                throw new IllegalStateException("test raster stopped");
            }
        };
        var font=new GLFont(slow,"background-test");
        var gl=(GL3)Proxy.newProxyInstance(GL3.class.getClassLoader(),new Class<?>[]{GL3.class},(proxy,method,args)->{
            if(method.getName().equals("glGetIntegerv")){((int[])args[1])[0]=4096;return null;}
            throw new AssertionError("No GPU upload expected: "+method.getName());
        });
        var field=GLFont.class.getDeclaredField("PREPARED_ATLASES");field.setAccessible(true);var cache=field.get(null);
        var worker=Executors.newFixedThreadPool(2);
        try{
            var warming=worker.submit(()->assertThrows(IllegalStateException.class,()->font.initialize(gl)));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            worker.submit(()->{synchronized(cache){return true;}}).get(1,TimeUnit.SECONDS);
            release.countDown();warming.get(5,TimeUnit.SECONDS);
        }finally{release.countDown();worker.shutdownNow();font.dispose(null);}
    }
}
