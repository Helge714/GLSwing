package com.vingame.glswing.node.model;

/** Application-facing name and identity of a value type used by node ports. */
public record GLNodeValueTypeDescriptor(String id, String displayName) {
	public GLNodeValueTypeDescriptor {
		if (!validId(id) || displayName == null || displayName.isBlank())
			throw new IllegalArgumentException("Valid value-type ID and name required");
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
