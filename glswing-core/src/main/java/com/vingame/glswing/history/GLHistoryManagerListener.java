package com.vingame.glswing.history;

/** Observes undo/redo availability and persisted/modified state. */
@FunctionalInterface
public interface GLHistoryManagerListener {

	void historyChanged(GLHistoryManager manager);
}
