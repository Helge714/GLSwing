package com.vingame.glswing.node;

/** Receives structural and selection changes from a {@link GLNodeEditor}. */
public interface GLNodeEditorListener {
	default void nodeAdded(GLNodeEditor editor, GLNode node) {
	}

	default void nodeRemoved(GLNodeEditor editor, GLNode node) {
	}

	default void connectionAdded(GLNodeEditor editor,
			GLNodeConnection connection) {
	}

	default void connectionRemoved(GLNodeEditor editor,
			GLNodeConnection connection) {
	}

	/** A wire drag ended or was cancelled; final connection notifications may follow. */
	default void connectionGestureFinished(GLNodeEditor editor) {}

	/** Query the editor for its current node or connection selection. */
	default void selectionChanged(GLNodeEditor editor) {
	}
}
