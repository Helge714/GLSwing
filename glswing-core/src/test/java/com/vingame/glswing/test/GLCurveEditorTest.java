package com.vingame.glswing.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.ui.*;
import java.util.List;

class GLCurveEditorTest {
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
