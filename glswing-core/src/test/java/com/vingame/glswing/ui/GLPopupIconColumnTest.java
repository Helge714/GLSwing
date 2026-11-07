package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.rendering.*;
class GLPopupIconColumnTest {
 @Test void filteredSearchRowsDoNotReserveIconsForCategories(){
  var menu=new GLPopupMenu();var category=menu.addSubMenu("Category",new GLPopupMenu());var result=menu.addItem("Group");
  result.setIcon(GLIcon.fromPath(new GLPath2D().moveTo(0,0).lineTo(10,0).lineTo(5,10).closePath()));
  assertTrue(menu.hasIconColumn());menu.setItemFilter(item->item==category);assertFalse(menu.hasIconColumn());
  menu.setItemFilter(item->item==result);assertTrue(menu.hasIconColumn());
 }
 @Test void escapeFromUnchangedTextCanReachContainer(){
  var editor=new GLTextEditor("Name");var event=new com.vingame.glswing.input.GLKeyEvent(new java.awt.event.KeyEvent(new java.awt.Canvas(),java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_ESCAPE,(char)27));
  editor.keyPressed(event);assertFalse(event.isConsumed());
 }
}
