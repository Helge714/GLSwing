package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.rendering.GLContext2D;
class GLScrollPaneEmptyViewportTest {
    @Test void connectedViewportFillsItsCellAndRestoresStandalonePadding(){
        var pane=new GLScrollPane(new GLLabel("Content"));
        pane.setGLTheme(GLThemeLoader.loadResource("/com/vingame/glswing/themes/default.gltheme"));
        pane.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
        pane.setVerticalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
        var viewport=pane.getGLChildren().stream().filter(c->c.getClass().getSimpleName().equals("Viewport")).findFirst().orElseThrow();
        Object owner=new Object();
        pane.setConnectedShape(owner,new com.vingame.glswing.rendering.GLCornerRadii(3,0,0,3));
        pane.setBounds(0,0,240,80);
        assertEquals(0,viewport.getX());assertEquals(0,viewport.getY());
        assertEquals(240,viewport.getWidth());assertEquals(80,viewport.getHeight());
        var radii=pane.viewportContentCornerRadii();
        assertEquals(3,radii.topLeft());assertEquals(3,radii.bottomLeft());
        assertEquals(0,radii.topRight());assertEquals(0,radii.bottomRight());
        pane.setConnectedShape(owner,null);pane.setBounds(0,0,241,81);
        assertTrue(viewport.getX()>0);assertTrue(viewport.getY()>0);
    }
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
