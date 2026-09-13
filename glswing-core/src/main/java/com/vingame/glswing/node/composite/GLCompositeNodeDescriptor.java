package com.vingame.glswing.node.composite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.node.model.GLNodeTypeDescriptor;

/** Public node contract backed by a separately versioned nested graph asset. */
public record GLCompositeNodeDescriptor(String nodeTypeId, String assetId,
		int minimumAssetVersion, List<GLCompositePortMapping> portMappings,
		List<GLCompositePropertyDescriptor> properties,
		List<GLCompositePropertyMapping> propertyMappings) {
	public GLCompositeNodeDescriptor {
		if (!validId(nodeTypeId) || !validId(assetId) || minimumAssetVersion < 1
				|| portMappings == null || properties == null || propertyMappings == null)
			throw new IllegalArgumentException("Invalid composite descriptor");
		portMappings = List.copyOf(portMappings);
		Map<String, GLCompositePortMapping> unique = new LinkedHashMap<>();
		for (GLCompositePortMapping mapping : portMappings)
			if (mapping == null || unique.putIfAbsent(mapping.publicPort().id(), mapping) != null)
				throw new IllegalArgumentException("Composite public ports must be unique");
		properties = List.copyOf(properties);
		propertyMappings = List.copyOf(propertyMappings);
		Map<String, GLCompositePropertyDescriptor> propertyIds = new LinkedHashMap<>();
		for (GLCompositePropertyDescriptor property : properties)
			if (property == null || propertyIds.putIfAbsent(property.id(), property) != null)
				throw new IllegalArgumentException("Composite public properties must be unique");
		java.util.Set<String> mappedProperties = new java.util.LinkedHashSet<>();
		java.util.Set<String> targets = new java.util.LinkedHashSet<>();
		for (GLCompositePropertyMapping mapping : propertyMappings)
			if (mapping == null || !propertyIds.containsKey(mapping.publicPropertyId()))
				throw new IllegalArgumentException(
						"Property mappings must reference a public property");
			else {
				mappedProperties.add(mapping.publicPropertyId());
				String target = mapping.internalNodeId() + "/" + mapping.internalPropertyId();
				if (!targets.add(target))
					throw new IllegalArgumentException(
							"An internal property can have only one public source");
			}
		if (!mappedProperties.containsAll(propertyIds.keySet()))
			throw new IllegalArgumentException("Every public property requires a mapping");
	}

	public GLCompositeNodeDescriptor(String nodeTypeId, String assetId,
			int minimumAssetVersion, List<GLCompositePortMapping> portMappings) {
		this(nodeTypeId, assetId, minimumAssetVersion, portMappings, List.of(), List.of());
	}

	public GLNodeTypeDescriptor nodeType() {
		return new GLNodeTypeDescriptor(nodeTypeId,
				portMappings.stream().map(GLCompositePortMapping::publicPort).toList());
	}

	public java.util.Optional<GLCompositePropertyDescriptor> findProperty(String id) {
		return properties.stream().filter(value -> value.id().equals(id)).findFirst();
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
