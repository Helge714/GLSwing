package com.vingame.glswing.history;

/** One already-applied, reversible user action. */
public interface GLUndoableEdit {

	String name();

	void undo();

	void redo();

	static GLUndoableEdit of(String name, Runnable undo, Runnable redo) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Edit name must not be blank");
		if (undo == null || redo == null)
			throw new IllegalArgumentException("Undo and redo actions must not be null");
		return new GLUndoableEdit() {
			@Override public String name() { return name; }
			@Override public void undo() { undo.run(); }
			@Override public void redo() { redo.run(); }
			@Override public String toString() { return name; }
		};
	}
}
