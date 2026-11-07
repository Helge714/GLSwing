package com.vingame.glswing.file;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import javax.swing.SwingUtilities;
import com.vingame.glswing.input.GLMouseEvent;
class GLFileNavigationTest {
 @TempDir Path root;
 @Test void onlySingleTreeClicksNavigateTheMainPane()throws Exception{
  var child=Files.createDirectory(root.resolve("child"));var ref=new GLFileBrowser[1];
  SwingUtilities.invokeAndWait(()->ref[0]=new GLFileBrowser(root));var browser=ref[0];
  var click=GLFileBrowser.class.getDeclaredMethod("directoryClicked",Path.class,GLMouseEvent.class);click.setAccessible(true);
  SwingUtilities.invokeAndWait(()->{try{click.invoke(browser,child,event(1));click.invoke(browser,child,event(2));}catch(Exception e){throw new AssertionError(e);}});
  waitForClick();SwingUtilities.invokeAndWait(()->assertEquals(root.toAbsolutePath(),browser.getDirectory()));
  SwingUtilities.invokeAndWait(()->{try{click.invoke(browser,child,event(1));}catch(Exception e){throw new AssertionError(e);}});
  waitForClick();SwingUtilities.invokeAndWait(()->assertEquals(child.toAbsolutePath(),browser.getDirectory()));
 }
 private static GLMouseEvent event(int count){return new GLMouseEvent(new java.awt.event.MouseEvent(new java.awt.Canvas(),java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,100,10,count,false,java.awt.event.MouseEvent.BUTTON1),com.vingame.glswing.input.GLPickResult.none(),0,0);}
 private static void waitForClick()throws Exception{Object value=java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");Thread.sleep((value instanceof Number n?n.intValue():500)+100L);}
}
