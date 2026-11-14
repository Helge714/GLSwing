package com.vingame.glswing.component;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.input.*;

class GLKeyboardManagerTest {
    static class Target extends GLContainer implements GLKeyTarget {
        int calls;boolean consume, text, exclusive;
        @Override protected void record(com.vingame.glswing.rendering.GLContext2D context) {}
        @Override public void keyPressed(GLKeyEvent event){calls++;if(consume)event.consume();}
        @Override public boolean blocksGlobalShortcuts(){return text||exclusive;}
        @Override public boolean isKeyboardInputExclusive(){return exclusive;}
    }
    static class Area extends Target implements GLKeyPressCaptureTarget {
        @Override public boolean capturesKeyPress(GLKeyEvent event){return event.getKeyCode()==KeyEvent.VK_N;}
    }
    GLKeyEvent key(){return new GLKeyEvent(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_N,'n'));}
    @Test void missingFocusUsesConfiguredDefault(){
        var host=new Target();var fallback=new Target();fallback.consume=true;host.addGL(fallback);
        var manager=new GLKeyboardManager(host);manager.setDefaultTarget(fallback);
        var event=key();manager.dispatchPressed(null,null,event);assertTrue(event.isConsumed());assertEquals(1,fallback.calls);
    }
    @Test void localHandlingPreventsFallbackAndUnhandledKeysFallThrough(){
        var host=new Target();var active=new Target();var fallback=new Target();host.addGL(active);host.addGL(fallback);
        var manager=new GLKeyboardManager(host);manager.setDefaultTarget(fallback);
        active.consume=true;manager.dispatchPressed(active,null,key());assertEquals(0,fallback.calls);
        active.consume=false;manager.dispatchPressed(active,null,key());assertEquals(1,fallback.calls);
    }
    @Test void hoverUsesOnlyDeclaredCommandsBeforeDefault(){
        var host=new Target();var area=new Area();area.consume=true;var child=new Target();area.addGL(child);host.addGL(area);
        var fallback=new Target();host.addGL(fallback);var manager=new GLKeyboardManager(host);manager.setDefaultTarget(fallback);
        manager.dispatchPressed(null,child,key());assertEquals(1,area.calls);assertEquals(0,child.calls);assertEquals(0,fallback.calls);
        manager.setHoverFallbackEnabled(false);manager.dispatchPressed(null,child,key());assertEquals(1,fallback.calls);
    }
    @Test void textAndExclusiveTargetsBlockHoverAndDefault(){
        var host=new Target();var active=new Target();var area=new Area();host.addGL(active);host.addGL(area);
        var manager=new GLKeyboardManager(host);manager.setDefaultTarget(area);
        active.text=true;manager.dispatchPressed(active,area,key());assertEquals(0,area.calls);
        active.text=false;active.exclusive=true;manager.dispatchPressed(active,area,key());assertEquals(0,area.calls);assertEquals(2,active.calls);
    }
    @Test void hiddenAndDetachedTargetsAreIgnored(){
        var host=new Target();var stale=new Target();var fallback=new Target();host.addGL(stale);host.addGL(fallback);
        var manager=new GLKeyboardManager(host);manager.setDefaultTarget(fallback);
        stale.setVisible(false);manager.dispatchPressed(stale,stale,key());assertEquals(0,stale.calls);assertEquals(1,fallback.calls);
        host.removeGL(stale);stale.setVisible(true);manager.dispatchPressed(stale,stale,key());assertEquals(0,stale.calls);assertEquals(2,fallback.calls);
        fallback.setVisible(false);manager.dispatchPressed(null,null,key());assertEquals(2,fallback.calls);
    }
    @Test void sharedAncestorsRunAtMostOnce(){
        var host=new Target();var area=new Area();var child=new Target();host.addGL(area);area.addGL(child);
        var manager=new GLKeyboardManager(host);manager.setDefaultTarget(area);
        manager.dispatchPressed(child,child,key());assertEquals(1,area.calls);assertEquals(1,child.calls);
    }
}
