package com.vingame.glswing.node.model;

import java.util.List;

/** Text-valued property restricted to an explicit set of choices. */
public record GLEnumNodeProperty(String id, String displayName,
		List<String> values) implements GLNodePropertyDescriptor {
	public GLEnumNodeProperty {
		if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
				|| displayName == null || displayName.isBlank() || values == null
				|| values.isEmpty() || values.stream().anyMatch(value ->
						value == null || value.isBlank()) || values.stream().distinct().count() != values.size())
			throw new IllegalArgumentException("Invalid enum node property");
		values = List.copyOf(values);
	}
}
