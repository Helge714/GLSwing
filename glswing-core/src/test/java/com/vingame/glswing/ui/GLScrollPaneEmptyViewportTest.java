package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.rendering.GLContext2D;
class GLScrollPaneEmptyViewportTest {
    @Test void emptyViewportDoesNotSubmitInvalidHitGeometry()throws Exception{
        var pane=new GLScrollPane(new GLLabel("Content"));
        var viewport=pane.getGLChildren().stream().filter(c->c.getClass().getSimpleName().equals("Viewport")).findFirst().orElseThrow();
        var record=viewport.getClass().getDeclaredMethod("record",GLContext2D.class);record.setAccessible(true);
        for(float[] size:new float[][]{{0,0},{0,100},{100,0}}){
            viewport.setBounds(0,0,size[0],size[1]);
            assertDoesNotThrow(()->record.invoke(viewport,new Object[]{null}));
        }
    }
}
