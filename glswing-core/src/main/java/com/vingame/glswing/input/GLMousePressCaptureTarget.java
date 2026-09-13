package com.vingame.glswing.input;

/**
 * Retained ancestor that may intercept a press before the picked descendant.
 * This is intended for modal pointer gestures such as drag placement, where
 * confirming the gesture must not activate a control beneath the pointer.
 */
public interface GLMousePressCaptureTarget extends GLMouseTarget {

	/** Returns whether this ancestor captures the pressed AWT mouse button. */
	boolean capturesMousePress(int button);
}
