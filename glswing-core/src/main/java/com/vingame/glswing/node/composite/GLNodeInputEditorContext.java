package com.vingame.glswing.node.composite;

import java.util.Optional;

import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeValue;

/** Immutable runtime context supplied when an inline node input editor is built. */
public record GLNodeInputEditorContext(GLGraphNode node,
		GLNodePortDescriptor port, GLCompositePortPresentation presentation) {
	public GLNodeInputEditorContext {
		if (node == null || port == null || presentation == null)
			throw new IllegalArgumentException("Node, port, and presentation required");
	}

	/** Returns a portable editor option stored with the composite contract. */
	public Optional<GLNodeValue> option(String id) {
		return Optional.ofNullable(presentation.editorOptions().get(id));
	}
}
