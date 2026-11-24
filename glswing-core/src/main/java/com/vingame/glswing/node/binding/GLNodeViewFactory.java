package com.vingame.glswing.node.binding;

import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;

/** Creates and updates the visual projection of a domain-model node. */
@FunctionalInterface
public interface GLNodeViewFactory {
	GLNode createNode(GLGraphNode node, GLNodeTypeDescriptor type);

	/** Whether an existing view still represents the current type metadata. */
	default boolean isViewCurrent(GLNode view, GLGraphNode node,
			GLNodeTypeDescriptor type) { return true; }

    /** Transfers application-owned transient state when the same model identity
     * receives a replacement view, including type/port-layout changes. Invoked
     * after creation/port validation and before model presentation is updated.
     * Initial views and newly inserted identities do not invoke this callback. */
    default void transferViewState(GLNode previous, GLNode replacement,
            GLGraphNode node, GLNodeTypeDescriptor type) { }

	/** Called when the same model node receives updated properties. */
	default void updateNode(GLNode view, GLGraphNode node,
			GLNodeTypeDescriptor type) {
	}
}
