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
		GLNodeEndpoint internalEndpoint, GLCompositePortPresentation presentation,
        java.util.List<GLNodeEndpoint> additionalEndpoints) {
	public GLCompositePortMapping {
        additionalEndpoints = java.util.List.copyOf(additionalEndpoints);
        if (!additionalEndpoints.isEmpty() && (internalEndpoint == null || publicPort.direction() != GLPortDirection.INPUT))
            throw new IllegalArgumentException("Only bound inputs can have multiple targets");
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

    public GLCompositePortMapping(GLNodePortDescriptor port, GLNodeEndpoint endpoint, GLCompositePortPresentation presentation) {
        this(port, endpoint, presentation, java.util.List.of());
    }
    public java.util.List<GLNodeEndpoint> internalEndpoints() {
        if (internalEndpoint == null) return java.util.List.of();
        var result = new java.util.ArrayList<GLNodeEndpoint>(); result.add(internalEndpoint); result.addAll(additionalEndpoints);
        return result.stream().distinct().toList();
    }
    public GLCompositePortMapping withEndpoints(java.util.List<GLNodeEndpoint> endpoints) {
        return new GLCompositePortMapping(publicPort, endpoints.isEmpty()?null:endpoints.get(0), presentation,
            endpoints.isEmpty()?java.util.List.of():endpoints.subList(1,endpoints.size()));
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
