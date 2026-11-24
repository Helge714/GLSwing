package com.vingame.glswing.rendering.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.rendering.*;

class UIQuadZeroBorderTest {
    @Test void zeroBorderMatchesPlainFillInBothWidthSpaces(){
        var transform=new GLTransform();
        for(boolean screen:new boolean[]{false,true})
            assertArrayEquals(UIQuadInstanceFormat.roundedRectangle(2,3,100,40,3,0,false,false,Color.GRAY,transform),
                UIQuadInstanceFormat.roundedRectangleWithBorder(2,3,100,40,3,0,screen,Color.GRAY,Color.RED,transform));
    }
    @Test void zeroBorderPreservesAsymmetricPopupCorners(){
        var transform=new GLTransform();var radii=new GLCornerRadii(0,0,4,4);
        for(boolean screen:new boolean[]{false,true})
            assertArrayEquals(UIQuadInstanceFormat.roundedRectangle(2,3,100,40,radii,Color.GRAY,transform),
                UIQuadInstanceFormat.roundedRectangleWithBorder(2,3,100,40,radii,0,screen,Color.GRAY,Color.RED,transform));
    }
    @Test void positiveBordersRetainTheirRenderingMode(){
        var transform=new GLTransform();
        assertEquals(UIQuadInstanceFormat.MODE_ROUNDED_FILL_BORDER,
            UIQuadInstanceFormat.roundedRectangleWithBorder(0,0,100,40,3,1,true,Color.GRAY,Color.RED,transform)[14]);
        assertEquals(UIQuadInstanceFormat.MODE_ASYMMETRIC_ROUNDED_FILL_BORDER,
            UIQuadInstanceFormat.roundedRectangleWithBorder(0,0,100,40,new GLCornerRadii(0,0,4,4),1,true,Color.GRAY,Color.RED,transform)[14]);
    }
    @Test void invalidBorderWidthsStillFail(){
        for(float width:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY}){
            assertThrows(IllegalArgumentException.class,()->UIQuadInstanceFormat.roundedRectangleWithBorder(0,0,100,40,3,width,true,Color.GRAY,Color.RED,new GLTransform()));
            assertThrows(IllegalArgumentException.class,()->UIQuadInstanceFormat.roundedRectangleWithBorder(0,0,100,40,new GLCornerRadii(0,0,4,4),width,true,Color.GRAY,Color.RED,new GLTransform()));
        }
    }
}
