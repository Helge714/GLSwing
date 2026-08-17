package com.vingame.glswing.ui;

/** Receives value changes from a {@link GLScrollBar}. */
@FunctionalInterface
public interface GLScrollListener {
	void scrollChanged(GLScrollBar source, float oldValue, float newValue);
}
