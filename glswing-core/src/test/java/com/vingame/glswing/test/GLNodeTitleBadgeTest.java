package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.*;
import com.vingame.glswing.ui.GLTextEditor;
class GLNodeTitleBadgeTest {
 @Test void trailingBadgeReservesSpaceIndependentlyOfLongAndCustomTitles()throws Exception {
  java.awt.EventQueue.invokeAndWait(()->{
   var node=new GLNode("A very long title that cannot fit in this narrow node");node.setBounds(0,0,160,90);
   var editor=(GLTextEditor)node.getGLChildren().stream().filter(GLTextEditor.class::isInstance).findFirst().orElseThrow();
   node.setTitleBadge("!",GLNodeConnectionStatus.ERROR);float reserved=node.getWidth()-editor.getWidth();
   assertTrue(reserved>8);assertEquals("!",node.getTitleBadge());assertEquals(GLNodeConnectionStatus.ERROR,node.getTitleBadgeStatus());
   assertFalse(node.getTitle().contains("!"));node.setCustomTitle("Custom title that is also far too long");
   assertEquals(reserved,node.getWidth()-editor.getWidth(),.01);
   node.setContentHidden(true);assertEquals(reserved,node.getWidth()-editor.getWidth(),.01);
   node.setTitleBadge("",GLNodeConnectionStatus.NORMAL);assertEquals(node.getWidth(),editor.getWidth(),.01);
  });
 }
}
