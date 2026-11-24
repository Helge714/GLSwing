package com.vingame.glswing.node.model;

/** Stable reference to a port on one graph node. */
public record GLNodeEndpoint(GLNodeId nodeId, String portId) {
	public GLNodeEndpoint {
		if (nodeId == null || portId == null || portId.isBlank())
			throw new IllegalArgumentException("Node and port IDs are required");
	}
}
