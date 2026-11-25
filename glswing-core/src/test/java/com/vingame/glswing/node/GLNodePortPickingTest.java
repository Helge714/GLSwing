package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.jogamp.opengl.*;
import com.vingame.glswing.rendering.*;
import com.vingame.glswing.input.GLMouseEvent;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.nio.ByteBuffer;

class GLNodePortPickingTest {
    @Test @EnabledIfSystemProperty(named="glswing.test.gl", matches="true")
    void horizontalHitAreaPreservesVerticalEdgesAndFocusUsesAccent() {
        var profile=GLProfile.get(GLProfile.GL3);var caps=new GLCapabilities(profile);caps.setOnscreen(false);
        var drawable=GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null,caps,null,200,160);
        drawable.display();drawable.getContext().makeCurrent();var gl=drawable.getGL().getGL3();
        var renderer=GLRenderer2D.create(gl);
        try {
            gl.glViewport(0,0,200,160);
            for(var shape:GLNodePort.Shape.values())for(float zoom:new float[]{.5f,1,2}) {
                var port=GLNodePort.input("in");port.setShape(shape);port.setBounds(0,0,10,10);
                for(boolean hover:new boolean[]{false,true}) {
                    if(hover)port.mouseEntered(new GLMouseEvent(new MouseEvent(new Canvas(),MouseEvent.MOUSE_ENTERED,0,0,80,60,0,false),renderer.readPickResult(gl,80,60),0,0));
                    var frame=renderer.beginFrame(gl,200,160,Projection2D.TOP_LEFT_PIXELS.create(200,160));
                    frame.clear(Color.BLACK);frame.setTransform(new GLTransform().translate(80,60).scale(zoom,zoom));
                    port.renderComponent(frame);assertTrue(frame.isColorRenderingEnabled());renderer.endFrame();
                    var pixels=ByteBuffer.allocateDirect(200*160*4);gl.glReadPixels(0,0,200,160,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,pixels);
                    assertSame(port,renderer.readPickResult(gl,Math.round(80-4*zoom),Math.round(60+5*zoom)).getTarget(),shape+" left");
                    assertSame(port,renderer.readPickResult(gl,Math.round(80+14*zoom),Math.round(60+5*zoom)).getTarget(),shape+" right");
                    assertNotSame(port,renderer.readPickResult(gl,Math.round(80-4*zoom),Math.round(60-3*zoom)).getTarget(),shape+" no vertical expansion");
                    int pixel=((159-Math.round(60+5*zoom))*200+Math.round(80-4*zoom))*4;
                    assertEquals(0,Byte.toUnsignedInt(pixels.get(pixel)),"Hit area must remain invisible");

                }
                assertEquals(10,port.getWidth());assertEquals(10,port.getHeight());
                port.setSocketVisible(false);
                var frame=renderer.beginFrame(gl,200,160,Projection2D.TOP_LEFT_PIXELS.create(200,160));frame.clear(Color.BLACK);port.renderComponent(frame);renderer.endFrame();
                assertNotSame(port,renderer.readPickResult(gl,0,0).getTarget());
            }
        } finally {renderer.dispose(gl);drawable.getContext().release();drawable.destroy();}
    }
    @Test @EnabledIfSystemProperty(named="glswing.test.gl", matches="true")
    void hoveredAndCandidateOutlinesUseThemeAccent() {
        for(var shape:GLNodePort.Shape.values())for(boolean candidate:new boolean[]{false,true}) {
            var port=GLNodePort.input("in");port.setShape(shape);port.setBounds(0,0,10,10);
            if(candidate)port.setConnectionCandidate(true);
            else port.mouseEntered(new GLMouseEvent(new MouseEvent(new Canvas(),MouseEvent.MOUSE_ENTERED,0,0,0,0,0,false),com.vingame.glswing.input.GLPickResult.none(),0,0));
            var image=GLComponentImageCapture.capture(port,10,10,80,80,true);
            var accent=port.getGLTheme().values().getColor("node.portFocusBorder",port.getGLTheme().buttonStyle().focusColor());
            int matches=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                var pixel=new Color(image.getRGB(x,y),true);
                if(pixel.getAlpha()>240&&Math.abs(pixel.getRed()-accent.getRed())<3
                    &&Math.abs(pixel.getGreen()-accent.getGreen())<3&&Math.abs(pixel.getBlue()-accent.getBlue())<3)matches++;
            }
            assertTrue(matches>0,shape+" accent outline, candidate="+candidate);
        }
    }

}
