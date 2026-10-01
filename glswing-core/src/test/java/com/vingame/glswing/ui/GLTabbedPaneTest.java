package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.component.GLContainer;
class GLTabbedPaneTest {
    @Test void verticalTabsKeepContentToTheRightAndSwitchBackToTop(){
        var tabs=new GLTabbedPane();var first=new GLLabel("One");var second=new GLLabel("Two");
        tabs.addTab("Properties",first);tabs.addTab("Group-IO",second);tabs.setBounds(0,0,500,300);
        tabs.setTabPlacement(GLTabbedPane.Placement.LEFT);tabs.setSelectedIndex(1);
        assertSame(second,tabs.getSelectedComponent());assertFalse(first.isVisible());assertTrue(second.isVisible());
        var host=(GLContainer)second.getGLParent();assertEquals(tabs.getGLStyle().tabHeight(),host.getX(),4);assertTrue(host.getY()<10);
        tabs.setTabPlacement(GLTabbedPane.Placement.TOP);assertTrue(host.getY()>20);assertTrue(host.getX()<10);
        tabs.setBounds(0,0,0,0);tabs.setTabPlacement(GLTabbedPane.Placement.LEFT);
        assertTrue(second.getWidth()>=0);assertTrue(second.getHeight()>=0);
    }
    @Test void selectionScrollsVerticalTabStripIntoView(){
        var tabs=new GLTabbedPane();tabs.setTabPlacement(GLTabbedPane.Placement.LEFT);
        for(int i=0;i<20;i++)tabs.addTab("Tab "+i,new GLLabel("Content "+i));
        tabs.setBounds(0,0,400,100);tabs.setSelectedIndex(19);
        var scroll=tabs.getGLChildren().stream().filter(GLScrollBar.class::isInstance).map(GLScrollBar.class::cast).findFirst().orElseThrow();
        assertTrue(scroll.isVisible());assertTrue(scroll.getValue()>0);
        tabs.setSelectedIndex(0);assertEquals(0,scroll.getValue(),2);
    }
}
