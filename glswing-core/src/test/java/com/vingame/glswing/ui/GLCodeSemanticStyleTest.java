package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.EventQueue;
import java.util.*;
import org.junit.jupiter.api.Test;

class GLCodeSemanticStyleTest {
    static class Editor extends GLCodeEditor {
        Editor(GLCodeLanguage language){super(new GLTextDocument("aa bb cc"),false,language);}
        GLTextStyle style(int offset){return presentedTextStyle(offset,GLTextStyle.PLAIN);}
        int boundary(int offset){return nextPresentedTextStyleBoundary(offset,8);}
        float width(int start,int end){return measureRange(start,end);}
    }
    @Test void semanticFontsAffectGeometryWithoutChangingStoredDocumentStyles() throws Exception {
        EventQueue.invokeAndWait(()->{
            GLCodeLanguage language=new GLCodeLanguage(){
                public String id(){return "style-test";}
                public List<GLCodeToken> tokenize(String source){return List.of();}
                public Map<String,GLTextStyle> semanticModifierStyles(){return Map.of("readonly",new GLTextStyle(true,false),"persistent",new GLTextStyle(false,true));}
            };
            var editor=new Editor(language);
            try {
                var field=GLCodeEditor.class.getDeclaredField("semanticTokens");field.setAccessible(true);
                field.set(editor,List.of(new GLCodeSemanticToken(0,2,"variable",Set.of("readonly")),new GLCodeSemanticToken(3,5,"variable",Set.of("persistent"))));
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            assertTrue(editor.style(0).bold());assertFalse(editor.style(0).italic());
            assertTrue(editor.style(3).italic());assertEquals(GLTextStyle.PLAIN,editor.style(2));
            assertEquals(2,editor.boundary(0));assertEquals(3,editor.boundary(2));assertEquals(8,editor.boundary(6));
            assertEquals(editor.width(0,2)+editor.width(2,3)+editor.width(3,5)+editor.width(5,8),editor.width(0,8),0.001f);
            assertTrue(editor.getDocument().getStyleRuns(0,8).stream().allMatch(r->r.style().equals(GLTextStyle.PLAIN)));
            assertEquals("aa bb cc",editor.getText());
            editor.setLanguage(new GLCodeLanguage(){public String id(){return "plain";}public List<GLCodeToken> tokenize(String s){return List.of();}});
            assertEquals(GLTextStyle.PLAIN,editor.style(0));assertEquals(8,editor.boundary(0));
        });
    }
}
