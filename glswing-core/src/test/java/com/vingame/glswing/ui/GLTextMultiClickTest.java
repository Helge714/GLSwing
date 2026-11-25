package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.input.*;
import com.vingame.glswing.history.GLHistoryManager;

class GLTextMultiClickTest {
    private void click(GLControl control,float x,float y,int count,int modifiers,int button) {
        var source=new Canvas();var pick=new GLPickResult(1,control,x,y,x,y,1,0,0,1,0,0);
        for(int id:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED}) {
            var event=new GLMouseEvent(new MouseEvent(source,id,System.currentTimeMillis(),modifiers,Math.round(x),Math.round(y),count,false,button),pick,0,0);
            if(id==MouseEvent.MOUSE_PRESSED)control.mousePressed(event);
            else if(id==MouseEvent.MOUSE_RELEASED)control.mouseReleased(event);
            else control.mouseClicked(event);
        }
    }
    private void click(GLTextEditor editor,int offset,int count) {
        float x=editor.getGLStyle().padding().left()+editor.getGLStyle().font().measurementFont().measureText(editor.getText().substring(0,offset))+1;
        click(editor,x,12,count,0,MouseEvent.BUTTON1);
    }
    private void click(GLTextArea area,int offset,int count) {
        int line=area.getDocument().getLineOfOffset(offset),start=area.getDocument().getLineStart(line);
        var font=area.getGLStyle().font().measurementFont();
        float x=area.getGLStyle().padding().left()+area.leadingTextInset()+font.measureText(area.getText().substring(start,offset))+1;
        float y=area.getGLStyle().padding().top()+(line+.5f)*font.getLineHeight();
        click(area,x,y,count,0,MouseEvent.BUTTON1);
    }
    private String selected(GLTextEditor editor){return editor.getText().substring(editor.getSelectionStart(),editor.getSelectionEnd());}
    private String selected(GLTextArea area){return area.getText().substring(area.getSelectionStart(),area.getSelectionEnd());}

    @Test void singleLineWordsAndHigherClicksSelectWithoutEditingOrUndo()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var editor=new GLTextEditor("Äußere Frequenz Steuerung");editor.setSize(600,32);var history=new GLHistoryManager();editor.setGLHistoryManager(history);
            click(editor,10,2);assertEquals("Frequenz",selected(editor));
            for(int count:new int[]{3,4,5,6}){click(editor,10,count);assertEquals(editor.getText(),selected(editor));}
            click(editor,10,1);assertEquals("",selected(editor));assertEquals("Äußere Frequenz Steuerung",editor.getText());assertFalse(history.canUndo());
        });
    }
    @Test void emptyDisabledAndShiftSelectionKeepTheirNormalBehaviour()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var editor=new GLTextEditor();editor.setSize(600,32);click(editor,0,2);click(editor,0,3);assertEquals("",selected(editor));
            editor.setText("Alpha Beta");editor.setCaretPosition(0);
            float x=editor.getGLStyle().padding().left()+editor.getGLStyle().font().measurementFont().measureText("Alpha");
            click(editor,x,12,1,InputEvent.SHIFT_DOWN_MASK,MouseEvent.BUTTON1);assertEquals("Alpha",selected(editor));
            editor.setEnabled(false);click(editor,7,3);assertEquals("Alpha",selected(editor));
        });
    }
    @Test void plainEditorsShareUnicodeWordsWhitespaceAndPunctuationRanges()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            String text="Äußere Größe,  \uD83D\uDE00";var field=new GLTextEditor(text);field.setSize(600,32);var area=new GLTextArea(text);area.setSize(600,160);
            for(int offset:new int[]{2,8,12,14,15}) {
                click(field,offset,2);click(area,offset,2);assertEquals(selected(area),selected(field));assertFalse(selected(field).isEmpty());
                assertFalse(selected(field).length()==1&&Character.isSurrogate(selected(field).charAt(0)));
            }
        });
    }
    @Test void multilineTripleClickSelectsLogicalLineAndFourOrMoreSelectEverything()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var area=new GLTextArea("Alpha Beta\nZweite Zeile\nEnde");area.setSize(600,200);area.setEditable(false);
            click(area,15,2);assertEquals("Zweite",selected(area));click(area,15,3);assertEquals("Zweite Zeile\n",selected(area));
            click(area,25,3);assertEquals("Ende",selected(area));
            for(int count:new int[]{4,5,6}){click(area,3,count);assertEquals(area.getText(),selected(area));}
        });
    }
    @Test void codeEditorKeepsIdentifierSelectionAndAddsLineAndDocumentSelection()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var code=new GLCodeEditor("float some_name = 0;\nresult = some_name;",false);code.setSize(700,240);
            click(code,9,2);assertEquals("some_name",selected(code));click(code,9,3);assertEquals("float some_name = 0;\n",selected(code));
            click(code,9,4);assertEquals(code.getText(),selected(code));
        });
    }
}
