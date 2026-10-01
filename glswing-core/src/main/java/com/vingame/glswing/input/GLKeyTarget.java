package com.vingame.glswing.input;

/** Optional keyboard-focus contract for lightweight GL containers. */
public interface GLKeyTarget {

	/** Exclusive performance/game focus bypasses global shortcuts and ancestor capture. */
	default boolean isKeyboardInputExclusive() { return false; }

	/** Suppresses application shortcuts and ancestor capture/bubbling while preserving
	 * local editing history and normal focus traversal. */
	default boolean blocksGlobalShortcuts() { return isKeyboardInputExclusive(); }

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
