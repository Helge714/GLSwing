package com.vingame.glswing.history;

/** Observes undo/redo availability and the saved/dirty state. */
@FunctionalInterface
public interface GLSaveManagerListener {

	void historyChanged(GLSaveManager manager);
}
