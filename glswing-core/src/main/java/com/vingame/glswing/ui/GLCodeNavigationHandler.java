package com.vingame.glswing.ui;

/** Opens or activates a source location outside the requesting editor. */
@FunctionalInterface
public interface GLCodeNavigationHandler {
	void navigate(GLCodeLocation location);
}
