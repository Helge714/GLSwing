package com.vingame.glswing.input;

/** Optional keyboard-focus contract for lightweight GL containers. */
public interface GLKeyTarget {

	default boolean isFocusTraversable() {
		return true;
	}

	default void focusGained() {
	}

	default void focusLost() {
	}

	default void keyPressed(GLKeyEvent event) {
	}

	default void keyReleased(GLKeyEvent event) {
	}

	default void keyTyped(GLKeyEvent event) {
	}
}
