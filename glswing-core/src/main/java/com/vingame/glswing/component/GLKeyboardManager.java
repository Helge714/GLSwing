package com.vingame.glswing.component;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import com.vingame.glswing.input.*;
import com.vingame.glswing.ui.GLControl;

/** Per-surface command routing. Text and exclusive input never fall through to other areas. */
public final class GLKeyboardManager {
    private final GLParent host;
    private GLKeyTarget defaultTarget;
    private boolean hoverFallbackEnabled = true;
    public GLKeyboardManager(GLParent host) { this.host = java.util.Objects.requireNonNull(host); }
    public void setDefaultTarget(GLKeyTarget target) { defaultTarget = target; }
    public GLKeyTarget getDefaultTarget() { return defaultTarget; }
    public void setHoverFallbackEnabled(boolean enabled) { hoverFallbackEnabled = enabled; }

    /** Routes commands only; typed characters and releases stay with the actual focus owner. */
    public void dispatchPressed(GLKeyTarget focused, GLMouseTarget hovered, GLKeyEvent event) {
        if (event.isConsumed()) return;
        Set<GLKeyTarget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (valid(focused) && focused.isFocusTraversable()) {
            dispatchChain(focused, event, visited);
            if (event.isConsumed() || focused.blocksGlobalShortcuts()) return;
        }
        // Hover fallback is opt-in per command via capture targets, never arbitrary text controls.
        if (hoverFallbackEnabled && hovered instanceof GLContainer area && valid(area)) {
            for (GLParent current = area; current instanceof GLContainer container; current = container.getGLParent()) {
                if (container instanceof GLKeyPressCaptureTarget capture && !visited.contains(capture)
                        && capture.capturesKeyPress(event)) {
                    visited.add(capture);capture.keyPressed(event);
                    if (event.isConsumed()) return;
                }
            }
        }
        if (valid(defaultTarget) && !visited.contains(defaultTarget))
            dispatchChain(defaultTarget, event, visited);
    }

    private boolean valid(Object target) {
        if (!(target instanceof GLContainer container)) return false;
        for (GLContainer current = container;;) {
            if (!current.isVisible() || current instanceof GLControl control && !control.isEnabled()) return false;
            GLParent parent = current.getGLParent();
            if (parent == host) return true;
            if (!(parent instanceof GLContainer ancestor)) return false;
            current = ancestor;
        }
    }

    static void dispatchChain(GLKeyTarget target, GLKeyEvent event) {
        dispatchChain(target, event, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static void dispatchChain(GLKeyTarget target, GLKeyEvent event, Set<GLKeyTarget> visited) {
        if (target.isKeyboardInputExclusive() || target.blocksGlobalShortcuts()) {
            if (visited.add(target)) target.keyPressed(event);
            if (target.isKeyboardInputExclusive() || event.isConsumed() || event.getKeyCode() != KeyEvent.VK_ESCAPE) {
                event.consume();return;
            }
        } else {
            if (target instanceof GLContainer focused)
                for (GLParent parent = focused.getGLParent(); parent instanceof GLContainer ancestor; parent = ancestor.getGLParent())
                    if (ancestor instanceof GLKeyPressCaptureTarget capture && !visited.contains(capture) && capture.capturesKeyPress(event)) {
                        visited.add(capture);capture.keyPressed(event);if (event.isConsumed()) return;
                    }
            if (visited.add(target)) target.keyPressed(event);
        }
        if (event.isConsumed() || !(target instanceof GLContainer container)) return;
        for (GLParent parent = container.getGLParent(); parent instanceof GLContainer ancestor; parent = ancestor.getGLParent()) {
            if (ancestor instanceof GLKeyTarget keyTarget && visited.add(keyTarget)) keyTarget.keyPressed(event);
            if (event.isConsumed()) return;
        }
    }
}
