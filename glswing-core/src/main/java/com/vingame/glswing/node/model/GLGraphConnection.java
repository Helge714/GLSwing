package com.vingame.glswing.node.model;

/** Immutable directed connection between two graph endpoints. */
public record GLGraphConnection(GLNodeConnectionId id, GLNodeEndpoint output,
		GLNodeEndpoint input) {
	public GLGraphConnection {
		if (id == null || output == null || input == null)
			throw new IllegalArgumentException("Connection ID and endpoints required");
		if (output.equals(input))
			throw new IllegalArgumentException("A port cannot connect to itself");
	}
}
