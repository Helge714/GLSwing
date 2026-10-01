package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLSliderProgressTest {
    @Test void extremeRangesRemainFiniteAndMonotonic(){
        double max=Double.MAX_VALUE;
        assertEquals(0,GLSlider.barProgress(-max,-max,max));
        assertEquals(.25f,GLSlider.barProgress(-max/2,-max,max));
        assertEquals(.5f,GLSlider.barProgress(0,-max,max));
        assertEquals(.75f,GLSlider.barProgress(max/2,-max,max));
        assertEquals(1,GLSlider.barProgress(max,-max,max));
        assertEquals(.5f,GLSlider.barProgress(5,0,10));
        assertEquals(0,GLSlider.barProgress(-1,0,10));
        assertEquals(1,GLSlider.barProgress(11,0,10));
        assertEquals(0,GLSlider.barProgress(0,0,Double.MIN_VALUE));
        assertEquals(1,GLSlider.barProgress(Double.MIN_VALUE,0,Double.MIN_VALUE));
    }
}
