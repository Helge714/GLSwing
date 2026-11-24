package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
import com.vingame.glswing.input.*;
class GLCodeEditorTooltipTest {
 static class Editor extends GLCodeEditor {
  Editor(){super(new GLTextDocument(),false,new GLCodeLanguage(){public String id(){return "test";}public List<GLCodeToken> tokenize(String s){return List.of();}});}
  String hint(){return toolTipTextAt(new GLMouseEvent(new MouseEvent(new Canvas(),MouseEvent.MOUSE_MOVED,0,0,0,0,0,false),new GLPickResult(1,this,0,0,Float.NaN,Float.NaN,1,0,0,1,0,0),0,0));}
 }
 @Test void emptyHoverHasNoComponentClassNameWhileExplicitHelpRemains()throws Exception {
  EventQueue.invokeAndWait(()->{var editor=new Editor();assertNull(editor.hint());editor.setToolTipText("Useful help");assertEquals("Useful help",editor.hint());editor.setToolTipText(null);assertNull(editor.hint());});
 }
}
