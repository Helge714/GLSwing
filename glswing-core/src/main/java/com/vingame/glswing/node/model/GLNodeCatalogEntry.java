package com.vingame.glswing.node.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Searchable presentation metadata and defaults for one registered node type. */
public record GLNodeCatalogEntry(GLNodeTypeDescriptor type, String displayName,
		String category, List<String> keywords,
		Map<String, GLNodeValue> defaultProperties) {
	public GLNodeCatalogEntry {
		if (type == null || displayName == null || displayName.isBlank()
				|| category == null || category.isBlank() || keywords == null
				|| defaultProperties == null)
			throw new IllegalArgumentException("Invalid node catalog entry");
		keywords = keywords.stream().map(String::strip)
				.filter(value -> !value.isEmpty()).distinct().toList();
		LinkedHashMap<String, GLNodeValue> properties = new LinkedHashMap<>();
		defaultProperties.forEach((key, value) -> {
			if (key == null || key.isBlank() || value == null)
				throw new IllegalArgumentException("Invalid default node property");
			properties.put(key, value);
		});
		defaultProperties = Map.copyOf(properties);
	}

	public GLNodeCatalogEntry(GLNodeTypeDescriptor type, String displayName,
			String category, Map<String, GLNodeValue> defaultProperties,
			String... keywords) {
		this(type, displayName, category, List.of(keywords), defaultProperties);
	}
}
