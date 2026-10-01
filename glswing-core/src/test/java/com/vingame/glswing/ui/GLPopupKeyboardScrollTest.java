package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLPopupKeyboardScrollTest {
    @Test void selectionRevealsOffscreenRowsAndWrapsBack() throws Exception {
        var menu = new GLPopupMenu();
        for (int i=0;i<40;i++) menu.addItem("Item " + i);
        var layout=GLPopupMenu.class.getDeclaredMethod("prepareLayout",float.class);
        layout.setAccessible(true);layout.invoke(menu,120f);
        var offset=GLPopupMenu.class.getDeclaredField("scrollOffset");offset.setAccessible(true);
        var viewport=GLPopupMenu.class.getDeclaredField("rowsViewportHeight");viewport.setAccessible(true);
        var last=menu.getItems().get(39);
        assertFalse(last.isVisible());
        last.setKeyboardSelected(true);
        assertTrue(last.isVisible());assertTrue(offset.getFloat(menu)>0);
        float end=offset.getFloat(menu);
        last.setKeyboardSelected(true);assertEquals(end,offset.getFloat(menu));
        menu.getItems().get(0).setKeyboardSelected(true);
        assertEquals(0f,offset.getFloat(menu));assertTrue(menu.getItems().get(0).isVisible());
        menu.getItems().get(20).setKeyboardSelected(true);
        assertTrue(menu.getItems().get(20).isVisible());assertTrue(offset.getFloat(menu)<end);
    }
}