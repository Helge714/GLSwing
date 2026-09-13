package com.vingame.glswing.node.composite;

import com.vingame.glswing.node.model.GLNodeId;

/** Routes one public composite property to an internal node property. */
public record GLCompositePropertyMapping(String publicPropertyId,
		GLNodeId internalNodeId, String internalPropertyId) {
	public GLCompositePropertyMapping {
		if (!valid(publicPropertyId) || internalNodeId == null
				|| !valid(internalPropertyId))
			throw new IllegalArgumentException("Invalid composite property mapping");
	}
	private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
