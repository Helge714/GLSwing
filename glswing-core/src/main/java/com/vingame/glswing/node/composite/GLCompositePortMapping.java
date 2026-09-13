package com.vingame.glswing.node.composite;

import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLPortDirection;

/**
 * Defines one public composite port and its optional binding into the nested graph.
 * An unbound port is a valid authoring state; flattening only rejects it when an
 * enclosing graph actually connects to that port.
 */
public record GLCompositePortMapping(GLNodePortDescriptor publicPort,
		GLNodeEndpoint internalEndpoint, GLCompositePortPresentation presentation) {
	public GLCompositePortMapping {
		if (publicPort == null || presentation == null)
			throw new IllegalArgumentException(
					"Public port and presentation required");
		boolean hasEditor = !GLNodeInputEditorRegistry.NONE.equals(
				presentation.inputEditorId());
		if (publicPort.direction() == GLPortDirection.OUTPUT
				&& (hasEditor || !presentation.socketVisible()))
			throw new IllegalArgumentException(
					"Output ports must use a visible socket and no input editor");
		if (publicPort.direction() == GLPortDirection.INPUT
				&& !hasEditor && !presentation.socketVisible())
			throw new IllegalArgumentException(
					"Input port requires an editor, a socket, or both");
	}

	/** Returns whether this public port is currently wired to the nested graph. */
	public boolean isBound() { return internalEndpoint != null; }

	/** Returns the nested endpoint without exposing nullable state to callers. */
	public java.util.Optional<GLNodeEndpoint> boundEndpoint() {
		return java.util.Optional.ofNullable(internalEndpoint);
	}

	public GLCompositePortMapping(GLNodePortDescriptor publicPort,
			GLNodeEndpoint internalEndpoint) {
		this(publicPort, internalEndpoint, GLCompositePortPresentation.socketOnly());
	}
}
