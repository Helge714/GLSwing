package com.vingame.glswing.node.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of explicit inspector metadata keyed by stable node type ID. */
public final class GLNodePropertySchemaRegistry {
	private final Map<String, List<GLNodePropertyDescriptor>> schemas =
			new LinkedHashMap<>();

	public void register(String nodeTypeId,
			List<? extends GLNodePropertyDescriptor> properties) {
		if (nodeTypeId == null || nodeTypeId.isBlank() || properties == null)
			throw new IllegalArgumentException("Node type and property schema required");
		List<GLNodePropertyDescriptor> copy = List.copyOf(properties);
		if (copy.stream().map(GLNodePropertyDescriptor::id).distinct().count()
				!= copy.size())
			throw new IllegalArgumentException("Duplicate node property ID");
		schemas.put(nodeTypeId, copy);
	}

	public List<GLNodePropertyDescriptor> properties(String nodeTypeId) {
		return schemas.getOrDefault(nodeTypeId, List.of());
	}

	public boolean isEmpty() { return schemas.isEmpty(); }
}
