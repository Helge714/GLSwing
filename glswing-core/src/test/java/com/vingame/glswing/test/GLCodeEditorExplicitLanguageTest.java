package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
class GLCodeEditorExplicitLanguageTest {
 @Test void customLanguageIsUsedFromConstructionWithoutFilenameInference()throws Exception {
  java.awt.EventQueue.invokeAndWait(()->{
   var calls=new java.util.concurrent.atomic.AtomicInteger();
   GLCodeLanguage language=new GLCodeLanguage(){public String id(){return "custom";} public java.util.List<GLCodeToken> tokenize(String text){calls.incrementAndGet();return java.util.List.of();}};
   var editor=new GLCodeEditor(new GLTextDocument("custom text"),false,language);
   assertSame(language,editor.getLanguage());assertTrue(calls.get()>0);
   editor.configureDocument(java.net.URI.create("file:/example.java"),language);
   assertSame(language,editor.getLanguage());assertEquals("custom text",editor.getText());editor.clearDiagnosticProvider();
  });
 }
}
