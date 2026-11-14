package com.vingame.glswing.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.ui.*;
import java.util.List;

class GLCurveEditorTest {
    private static com.vingame.glswing.input.GLMouseEvent mouse(GLCurveEditor editor,int id,float x,float y,int clicks){
        var event=new java.awt.event.MouseEvent(new java.awt.Canvas(),id,0,0,(int)x,(int)y,clicks,false,java.awt.event.MouseEvent.BUTTON1);
        return new com.vingame.glswing.input.GLMouseEvent(event,new com.vingame.glswing.input.GLPickResult(1,editor,x,y,x,y,1,0,0,1,0,0),0,0);
    }
    @Test void editingControlsFollowHoverAndFocusAndDisappearOutside()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var host=new com.vingame.glswing.component.GLSwingPanel(){};
            try{
                var editor=new GLCurveEditor();var outside=new GLButton("Outside");host.addGL(editor);host.addGL(outside);
                var event=mouse(editor,java.awt.event.MouseEvent.MOUSE_ENTERED,10,10,0);
                assertFalse(editor.isEditingControlsVisible());editor.mouseEntered(event);assertTrue(editor.isEditingControlsVisible());
                editor.mouseExited(event);assertFalse(editor.isEditingControlsVisible());
                host.requestGLFocus(editor);assertTrue(editor.isEditingControlsVisible());
                host.requestGLFocus(outside);assertFalse(editor.isEditingControlsVisible());
                editor.mouseEntered(event);editor.setPreview(true);assertFalse(editor.isEditingControlsVisible());
            }finally{host.destroy();}
        });
    }
    @Test void fixedPointsOnlyMoveVerticallyAndCannotBeAddedOrDeleted()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var editor=new GLCurveEditor();editor.setBounds(0,0,200,120);editor.setCurve(GLFunctionCurve.identity().insert(.5,.75));editor.setFixedPointPositions(true);
            editor.mousePressed(mouse(editor,java.awt.event.MouseEvent.MOUSE_PRESSED,100,34.5f,1));
            editor.mouseDragged(mouse(editor,java.awt.event.MouseEvent.MOUSE_DRAGGED,160,70,1));
            editor.mouseReleased(mouse(editor,java.awt.event.MouseEvent.MOUSE_RELEASED,160,70,1));
            assertEquals(.5,editor.getCurve().points().get(1).x());assertNotEquals(.75,editor.getCurve().points().get(1).y());
            editor.mouseClicked(mouse(editor,java.awt.event.MouseEvent.MOUSE_CLICKED,150,90,2));assertEquals(3,editor.getCurve().points().size());
            editor.keyPressed(new com.vingame.glswing.input.GLKeyEvent(new java.awt.event.KeyEvent(new java.awt.Canvas(),java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_DELETE,'\u007f')));assertEquals(3,editor.getCurve().points().size());
        });
    }
    @Test void stepSegmentsAndMonotoneBezierHandles(){
        var curve=GLFunctionCurve.identity().insert(.5,.75).mode(0,GLFunctionCurve.Mode.STEP);
        assertEquals(0,curve.evaluate(.499999));assertEquals(.75,curve.evaluate(.5));
        var bezier=GLFunctionCurve.identity().mode(0,GLFunctionCurve.Mode.BEZIER)
                .handle(0,false,1.0/3,0).handle(1,true,2.0/3,0);
        assertEquals(.125,bezier.evaluate(.5),1e-8);
        assertThrows(IllegalArgumentException.class,()->new GLFunctionCurve(List.of(
                new GLFunctionCurve.Point(0,0,0,0,.9,0,GLFunctionCurve.Mode.BEZIER,false),
                new GLFunctionCurve.Point(1,1,.1,1,1,1,GLFunctionCurve.Mode.LINEAR,false))));
    }
    @Test void mirroredBezierHasPointSymmetryAndPreviewUsesThemeHeight(){
        var curve=GLFunctionCurve.identity().insert(.5,.5).mode(0,GLFunctionCurve.Mode.BEZIER).handle(0,false,.2,0).symmetric();
        for(int i=0;i<=100;i++)assertEquals(1,curve.evaluate(i/100.0)+curve.evaluate(1-i/100.0),1e-8);
        var editor=new GLCurveEditor();float expanded=editor.getPreferredGLSize().height();
        editor.setCurve(curve);editor.setPreview(true);
        assertFalse(editor.isFocusable());assertTrue(editor.getPreferredGLSize().height()<expanded);assertEquals(curve,editor.getCurve());
        editor.setPreview(false);assertTrue(editor.isFocusable());assertEquals(expanded,editor.getPreferredGLSize().height());
    }
}
