package com.vingame.glswing.node.model;

/** Boolean property rendered as a checkbox. */
public record GLBooleanNodeProperty(String id, String displayName)
		implements GLNodePropertyDescriptor {
	public GLBooleanNodeProperty {
		if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
				|| displayName == null || displayName.isBlank())
			throw new IllegalArgumentException("Invalid boolean node property");
	}
}
