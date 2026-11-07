package com.vingame.glswing.test;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.*;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.*;

class GLTextShortcutFocusTest {
    static class Parent extends GLContainer implements GLKeyPressCaptureTarget {
        int captures,keys;
        public boolean capturesKeyPress(GLKeyEvent event){captures++;return event.getKeyCode()==KeyEvent.VK_ESCAPE;}
        public void keyPressed(GLKeyEvent event){keys++;event.consume();}
        protected void record(GLContext2D c){}
    }
    private static KeyEvent awt(int code,int modifiers){return new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,modifiers,code,KeyEvent.CHAR_UNDEFINED);}
    private static void dispatch(GLKeyTarget target,KeyEvent key)throws Exception{
        var method=Class.forName("com.vingame.glswing.component.GLContainerInputDispatcher").getDeclaredMethod("dispatchKeyPressed",GLKeyTarget.class,GLKeyEvent.class);
        method.setAccessible(true);method.invoke(null,target,new GLKeyEvent(key));
    }
    @Test void typingAndShortcutsCannotReachAncestors()throws Exception{
        for(GLControl editor:new GLControl[]{new GLTextEditor(),new GLTextArea(),new GLCodeEditor()}){
            var parent=new Parent();parent.addGL(editor);
            for(int code:new int[]{KeyEvent.VK_N,KeyEvent.VK_DELETE,KeyEvent.VK_F2})dispatch(editor,awt(code,0));
            dispatch(editor,awt(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK));
            dispatch(editor,awt(KeyEvent.VK_O,InputEvent.CTRL_DOWN_MASK));
            assertEquals(0,parent.captures);assertEquals(0,parent.keys);
            assertTrue(editor.blocksGlobalShortcuts());assertFalse(editor.isKeyboardInputExclusive(),"Text fields retain normal Tab traversal");
        }
    }
    @Test void textInsertionSelectionAndLocalUndoStillWork()throws Exception{
        var text=new GLTextEditor();var parent=new Parent();parent.addGL(text);text.focusGained();
        try{
            dispatch(text,awt(KeyEvent.VK_N,0));
            text.keyTyped(new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,'n')));
            assertEquals("n",text.getText());
            assertTrue(text.handleEditingHistoryKeyPressed(awt(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK),text.getGLInputMap()));
            assertEquals("",text.getText());assertEquals(0,parent.keys);
        }finally{text.focusLost();}
    }
    @Test void codeEditorEscapeBubblesButTextFieldCancelStaysLocal()throws Exception{
        var parent=new Parent();var code=new GLCodeEditor();parent.addGL(code);
        dispatch(code,awt(KeyEvent.VK_ESCAPE,0));assertEquals(1,parent.keys);assertEquals(0,parent.captures);
        var text=new GLTextEditor();parent.addGL(text);text.focusGained();
        try{
            text.keyTyped(new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,'n')));
            dispatch(text,awt(KeyEvent.VK_ESCAPE,0));assertEquals(1,parent.keys);assertEquals("",text.getText());
            dispatch(text,awt(KeyEvent.VK_ESCAPE,0));assertEquals(2,parent.keys);
        }finally{text.focusLost();}
    }
    @Test void ordinaryControlsStillBubble()throws Exception{
        var parent=new Parent();var button=new GLButton("Focus");parent.addGL(button);
        dispatch(button,awt(KeyEvent.VK_N,0));assertEquals(1,parent.keys);
    }
}


