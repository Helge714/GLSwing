package com.vingame.glswing.node.model;

import java.util.UUID;

/** Stable identity of one graph document. */
public record GLNodeGraphId(UUID value) {
	public GLNodeGraphId {
		if (value == null) throw new IllegalArgumentException("Graph ID required");
	}
	public static GLNodeGraphId random() {
		return new GLNodeGraphId(UUID.randomUUID());
	}
	public static GLNodeGraphId parse(String value) {
		return new GLNodeGraphId(UUID.fromString(value));
	}
	@Override public String toString() { return value.toString(); }
}
