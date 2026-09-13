package com.vingame.glswing.node.model;

import java.util.UUID;

/** Stable identity of one node instance inside a graph. */
public record GLNodeId(UUID value) {
	public GLNodeId {
		if (value == null) throw new IllegalArgumentException("Node ID required");
	}
	public static GLNodeId random() { return new GLNodeId(UUID.randomUUID()); }
	public static GLNodeId parse(String value) {
		return new GLNodeId(UUID.fromString(value));
	}
	@Override public String toString() { return value.toString(); }
}
