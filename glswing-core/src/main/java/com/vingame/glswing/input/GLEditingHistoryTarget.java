package com.vingame.glswing.input;

import java.awt.event.KeyEvent;

/** Focus target whose local editing history takes precedence over host history. */
public interface GLEditingHistoryTarget extends GLKeyTarget {
	boolean handleEditingHistoryKeyPressed(KeyEvent event, GLInputMap inputMap);
	/** Flushes the current local editing transaction into its enclosing history. */
	default boolean commitEditingHistory() { return true; }
}
