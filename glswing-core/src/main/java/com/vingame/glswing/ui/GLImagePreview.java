package com.vingame.glswing.ui;

import java.awt.image.BufferedImage;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.rendering.*;

/** One reusable preview texture; never allocates at the requested export resolution. */
public class GLImagePreview extends GLContainer {
    private GLImage image;
    private BufferedImage source;
    @Override protected void themeChanged(){super.themeChanged();if(source!=null)setImage(source);}

    public void setImage(BufferedImage source){
        this.source=java.util.Objects.requireNonNull(source);
        var canvas=new BufferedImage(640,480,BufferedImage.TYPE_INT_ARGB);
        var g=canvas.createGraphics();
        try{
            var style=getGLTheme().textEditorStyle();
            g.setColor(style.backgroundColor());g.fillRect(0,0,640,480);
            double scale=Math.min(620.0/source.getWidth(),460.0/source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale)),x=(640-w)/2,y=(480-h)/2;
            for(int cy=0;cy<h;cy+=10)for(int cx=0;cx<w;cx+=10){g.setColor(((cx/10+cy/10)&1)==0?style.backgroundColor():style.borderColor());g.fillRect(x+cx,y+cy,Math.min(10,w-cx),Math.min(10,h-cy));}
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source,x,y,w,h,null);g.setColor(style.borderColor());g.drawRect(x,y,w,h);
        }finally{g.dispose();}
        if(image==null)image=GLImage.of(canvas,GLImageOptions.dynamic());else image.update(canvas);
        invalidateRecording();
    }
    @Override protected void record(GLContext2D c){
        c.fillRect(0,0,getWidth(),getHeight(),getGLTheme().textEditorStyle().backgroundColor());
        if(image!=null){float factor=Math.min(getWidth()/640,getHeight()/480);float w=640*factor,h=480*factor;c.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h);}
    }
}
