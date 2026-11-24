package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.component.*;
import com.vingame.glswing.input.*;
import java.awt.event.MouseEvent;
class GLTooltipLifecycleTest {
 private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 @Test void hiddenOrDetachedAncestorAndChangedPointerClearPendingTips()throws Exception{
  javax.swing.SwingUtilities.invokeAndWait(()->{try{
   var root=new GLContainer(){protected void record(com.vingame.glswing.rendering.GLContext2D context){}};var panel=new GLContainer(){protected void record(com.vingame.glswing.rendering.GLContext2D context){}};var button=new GLButton("Help");root.addGL(panel);panel.addGL(button);
   var event=new GLMouseEvent(new MouseEvent(new java.awt.Canvas(),MouseEvent.MOUSE_MOVED,0,0,10,10,0,false),GLPickResult.none(),0,0);
   var sessions=GLToolTipManager.class.getDeclaredField("SESSIONS");sessions.setAccessible(true);
   GLToolTipManager.hover(button,event,"Description",null);var session=((java.util.Map<?,?>)sessions.get(null)).get(root);assertNotNull(field(session,"owner"));
   panel.setVisible(false);assertNull(field(session,"owner"));assertFalse(((javax.swing.Timer)field(session,"timer")).isRunning());
   panel.setVisible(true);GLToolTipManager.hover(button,event,"Description",null);GLToolTipManager.pointerTarget(root,new GLButton("Other"));assertNull(field(session,"owner"));
   GLToolTipManager.hover(button,event,"Description",null);root.removeGL(panel);assertNull(field(session,"owner"));
  }catch(Exception e){throw new AssertionError(e);}});
 }
}
