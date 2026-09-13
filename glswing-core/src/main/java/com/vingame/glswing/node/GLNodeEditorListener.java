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

	/** Query the editor for its current node or connection selection. */
	default void selectionChanged(GLNodeEditor editor) {
	}
}
