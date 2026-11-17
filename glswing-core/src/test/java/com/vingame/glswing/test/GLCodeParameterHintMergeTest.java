package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.EventQueue;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
class GLCodeParameterHintMergeTest {
 static void invoke(GLCodeEditor editor,String method)throws Exception {
  var m=GLCodeEditor.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(editor);
 }
 @Test void whitespaceAndCommentsDoNotCreateTwoLabelsForOneArgument()throws Exception {
  for(String source:List.of("call(  1)","call( // explanation\n 1)","call( /* explanation */ 1)"))for(boolean providerFirst:List.of(false,true)){
   var provided=new AtomicBoolean(providerFirst);var reference=new AtomicReference<GLCodeEditor>();
   EventQueue.invokeAndWait(()->{
    GLCodeLanguage language=new GLCodeLanguage(){
     public String id(){return "hint-test";}
     public List<GLCodeToken> tokenize(String source){return List.of();}
     public GLCodeDiagnosticProvider codeIntelligenceProvider(){return (d,l)->new GLCodeDiagnosticProvider.Session(){
      public void update(GLCodeDocument d){} public void close(){}
      public CompletionStage<List<GLCodeInlayHint>> inlayHints(GLCodeDocument d){return CompletableFuture.completedFuture(provided.get()?List.of(new GLCodeInlayHint(d.text().indexOf('1'),"value:")):List.of());}
      public CompletionStage<List<GLCodeSignature>> signatureHelp(GLCodeDocument d,int offset,Character trigger){return CompletableFuture.completedFuture(List.of(new GLCodeSignature("call(float value)","",List.of("float value"),0)));}
     };}
    };
    var editor=new GLCodeEditor(new GLTextDocument(source),false,language);reference.set(editor);editor.configureDocument(URI.create("test:/hints"),language);editor.setCaretPosition(5);
    try{var m=GLCodeEditor.class.getDeclaredMethod("requestSignatureHelp",Character.class);m.setAccessible(true);m.invoke(editor,'(');}catch(Exception e){throw new RuntimeException(e);}
   });
   EventQueue.invokeAndWait(()->{});
   provided.set(true);EventQueue.invokeAndWait(()->{try{invoke(reference.get(),"requestInlayHints");}catch(Exception e){throw new RuntimeException(e);}});
   EventQueue.invokeAndWait(()->{assertEquals(1,reference.get().getInlayHints().size(),source);assertEquals(source.indexOf('1'),reference.get().getInlayHints().get(0).offset());});
   EventQueue.invokeAndWait(()->{
    reference.get().getDocument().insert(5,"  ");reference.get().setCaretPosition(5);
    try{var m=GLCodeEditor.class.getDeclaredMethod("requestSignatureHelp",Character.class);m.setAccessible(true);m.invoke(reference.get(),'(');}catch(Exception e){throw new RuntimeException(e);}
   });
   EventQueue.invokeAndWait(()->{
    assertEquals(1,reference.get().getInlayHints().size(),"Edited whitespace must not duplicate the hint");
    assertEquals(reference.get().getText().indexOf('1'),reference.get().getInlayHints().get(0).offset());reference.get().clearDiagnosticProvider();});
  }
 }
}
