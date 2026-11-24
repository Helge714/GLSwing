package com.vingame.glswing.test;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.event.MouseEvent;
import com.vingame.glswing.ui.*;
import com.vingame.glswing.input.*;
class GLHoldRepeatTest {
    private static GLMouseEvent event(GLControl control,int kind,float x,float y){
        var pick=new GLPickResult(1,control,x,y,x,y,1,0,0,1,0,0);
        return new GLMouseEvent(new MouseEvent(new Canvas(),kind,System.currentTimeMillis(),0,(int)x,(int)y,1,false,MouseEvent.BUTTON1),pick,0,0,null);
    }
    @Test void heldArrowsRepeatAndReleaseStops() throws Exception {
        var slider=GLSlider.free("",0);slider.setPresentation(GLSlider.Presentation.BUTTONS);slider.setBounds(0,0,120,24);
        var spinner=new GLNumberSpinner(0);var button=spinner.getIncrementButton();button.setBounds(0,0,20,20);
        SwingUtilities.invokeAndWait(()->{slider.mousePressed(event(slider,MouseEvent.MOUSE_PRESSED,115,12));button.mousePressed(event(button,MouseEvent.MOUSE_PRESSED,10,10));});
        Thread.sleep(650);
        double[] values=new double[2];
        SwingUtilities.invokeAndWait(()->{slider.mouseReleased(event(slider,MouseEvent.MOUSE_RELEASED,115,12));button.mouseReleased(event(button,MouseEvent.MOUSE_RELEASED,10,10));values[0]=slider.getValue();values[1]=spinner.getValue();});
        assertTrue(values[0]>=2);assertTrue(values[1]>=2);
        Thread.sleep(180);
        SwingUtilities.invokeAndWait(()->{assertEquals(values[0],slider.getValue());assertEquals(values[1],spinner.getValue());});
    }
}

