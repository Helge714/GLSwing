package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import com.jogamp.opengl.*;
import com.vingame.glswing.component.GLContainer;

/** Offscreen capture of an already prepared component. Call on its owning UI thread.
 * Does not resize, clone or otherwise configure the component tree. */
public final class GLComponentImageCapture {
    private GLComponentImageCapture() { }
    public static final class SizeLimitException extends IllegalArgumentException {
        private final int maximumSide;
        public SizeLimitException(int maximumSide) { super("GPU image size limit: " + maximumSide); this.maximumSide = maximumSide; }
        public int maximumSide() { return maximumSide; }
    }
    private static void invalidateTree(GLContainer component) {
        component.invalidateRecording();
        for (var child : component.getGLChildren()) invalidateTree(child);
    }
    public static BufferedImage capture(GLContainer view,float width,float height,int w,int h,boolean transparent){
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0||!GLImageExportDimensions.valid(w,h))throw new IllegalArgumentException("Invalid image dimensions");
        java.util.Objects.requireNonNull(view);
        invalidateTree(view);
        var profile=GLProfile.get(GLProfile.GL3);var caps=new GLCapabilities(profile);caps.setDoubleBuffered(false);caps.setAlphaBits(8);
        var drawable=GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null,caps,null,1,1);
        try{
            drawable.display();
            drawable.invoke(true,d->{
                int[] texture=new int[1],buffer=new int[1];
                d.getGL().glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE,texture,0);d.getGL().glGetIntegerv(GL.GL_MAX_RENDERBUFFER_SIZE,buffer,0);
                int limit=Math.min(texture[0],buffer[0]);
                if(w>limit||h>limit)throw new SizeLimitException(limit);
                return true;
            });
            drawable.setSurfaceSize(w,h);
        }catch(RuntimeException|Error failure){drawable.destroy();throw failure;}
        var result=new BufferedImage[1];
        drawable.addGLEventListener(new GLEventListener(){
            GLRenderer2D renderer;
            public void init(GLAutoDrawable d){renderer=GLRenderer2D.create(d.getGL().getGL3());}
            public void reshape(GLAutoDrawable d,int x,int y,int width,int height){}
            public void dispose(GLAutoDrawable d){if(renderer!=null)renderer.dispose(d.getGL().getGL3());}
            public void display(GLAutoDrawable d){
                var gl=d.getGL().getGL3();gl.glViewport(0,0,w,h);gl.glClearColor(0,0,0,transparent?0:1);gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                var context=renderer.beginFrame(gl,width,height,Projection2D.TOP_LEFT_PIXELS.create(width,height));
                try { view.renderComponent(context); } finally { renderer.endFrame(); }
                var pixels=ByteBuffer.allocateDirect(w*h*4);gl.glPixelStorei(GL.GL_PACK_ALIGNMENT,1);gl.glReadPixels(0,0,w,h,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,pixels);
                var image=new BufferedImage(w,h,transparent?BufferedImage.TYPE_INT_ARGB_PRE:BufferedImage.TYPE_INT_RGB);int[] data=new int[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-y-1)*w+x)*4;data[y*w+x]=((pixels.get(i+3)&255)<<24)|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255);}
                image.getRaster().setDataElements(0,0,w,h,data);result[0]=image;
            }
        });try{drawable.display();return result[0];}finally{try{drawable.destroy();}finally{invalidateTree(view);}}
    }
}
