package com.vingame.glswing.node.model;

import java.util.UUID;

/** Stable identity of one directed graph connection. */
public record GLNodeConnectionId(UUID value) {
	public GLNodeConnectionId {
		if (value == null)
			throw new IllegalArgumentException("Connection ID required");
	}
	public static GLNodeConnectionId random() {
		return new GLNodeConnectionId(UUID.randomUUID());
	}
	public static GLNodeConnectionId parse(String value) {
		return new GLNodeConnectionId(UUID.fromString(value));
	}
	@Override public String toString() { return value.toString(); }
}
