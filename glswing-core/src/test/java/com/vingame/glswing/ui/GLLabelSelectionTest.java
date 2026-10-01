package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.input.GLKeyEvent;
class GLLabelSelectionTest {
    private static GLKeyEvent key(int code,int modifiers){return new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,modifiers,code,KeyEvent.CHAR_UNDEFINED));}
    @Test void selectionIsOptInAndReadOnly(){
        var label=new GLLabel("Connection reset by peer");label.setBounds(0,0,60,32);
        assertFalse(label.isSelectable());assertFalse(label.isMouseTargetEnabled());assertFalse(label.isFocusTraversable());
        label.selectAll();assertEquals("",label.getSelectedText());
        label.setSelectable(true);assertTrue(label.isMouseTargetEnabled());assertTrue(label.isFocusTraversable());
        label.keyPressed(key(KeyEvent.VK_A,KeyEvent.CTRL_DOWN_MASK));assertEquals(label.getText(),label.getSelectedText());
        label.select(11,16);assertEquals("reset",label.getSelectedText());
        label.keyPressed(key(KeyEvent.VK_DELETE,0));assertEquals("Connection reset by peer",label.getText());
        label.keyPressed(key(KeyEvent.VK_HOME,0));label.keyPressed(key(KeyEvent.VK_RIGHT,KeyEvent.SHIFT_DOWN_MASK));assertEquals("C",label.getSelectedText());
        label.setText("Changed");assertEquals("",label.getSelectedText());
        label.setSelectable(false);assertFalse(label.isMouseTargetEnabled());
    }
    @Test void navigationDoesNotSplitSurrogatePairs(){
        var label=new GLLabel("A\uD83D\uDE00B");label.setSelectable(true);label.select(1,1);
        label.keyPressed(key(KeyEvent.VK_RIGHT,KeyEvent.SHIFT_DOWN_MASK));assertEquals("\uD83D\uDE00",label.getSelectedText());
    }
}
