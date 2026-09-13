package com.vingame.glswing.input;

/** Ancestor that may handle a key press before the focused descendant. */
public interface GLKeyPressCaptureTarget extends GLKeyTarget {
	boolean capturesKeyPress(GLKeyEvent event);
}
