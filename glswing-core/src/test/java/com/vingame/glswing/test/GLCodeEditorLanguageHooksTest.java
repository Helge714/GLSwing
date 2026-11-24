package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
import com.vingame.glswing.input.GLKeyEvent;
class GLCodeEditorLanguageHooksTest {
 static class Editor extends GLCodeEditor {
  Editor(GLCodeLanguage language){super(new GLTextDocument(),false,language);}
  void type(char c){controlKeyTyped(new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,c)));}
  void enter(){controlKeyPressed(new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n')));}
 }
 @Test void identifiersRequestCompletionAndLanguageControlsIndentationAndFormatting()throws Exception {
  var requests=new AtomicInteger();
  EventQueue.invokeAndWait(()->{
   GLCodeLanguage language=new GLCodeLanguage(){
    public String id(){return "formula-probe";}
    public List<GLCodeToken> tokenize(String source){return List.of();}
    public boolean supportsAutomaticCompletion(){return true;}
    public String indentationAfter(String source,int offset,int width){return " ".repeat(width);}
    public String formatSource(String source,int width){return "formatted";}
    public GLCodeDiagnosticProvider codeIntelligenceProvider(){return (document,listener)->new GLCodeDiagnosticProvider.Session(){
     public void update(GLCodeDocument d){}
     public void close(){}
     public CompletionStage<List<GLCodeCompletion>> complete(GLCodeDocument d,int offset,Character trigger){requests.incrementAndGet();assertEquals("s",d.text());return CompletableFuture.completedFuture(List.of());}
    };}
   };
   var editor=new Editor(language);editor.configureDocument(URI.create("test:/formula"),language);
   editor.type('s');assertEquals(1,requests.get());
   editor.type('\b');assertEquals(1,requests.get(),"Backspace is not an identifier completion trigger");
   editor.enter();assertEquals("s\n    ",editor.getText());
   editor.formatCode();assertEquals("formatted",editor.getText());editor.clearDiagnosticProvider();
  });
 }
}
