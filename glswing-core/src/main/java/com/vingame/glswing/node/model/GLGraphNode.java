package com.vingame.glswing.node.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable domain node instance without editor or rendering state. */
public record GLGraphNode(GLNodeId id, String typeId, int typeVersion,
		Map<String, GLNodeValue> properties) {
	public GLGraphNode {
		if (id == null || !validTypeId(typeId) || typeVersion < 1 || properties == null)
			throw new IllegalArgumentException("Node ID, type, and properties required");
		LinkedHashMap<String, GLNodeValue> copy = new LinkedHashMap<>();
		properties.forEach((key, value) -> {
			if (key == null || key.isBlank() || value == null)
				throw new IllegalArgumentException(
						"Property keys and values must not be blank or null");
			copy.put(key, value);
		});
		properties = Collections.unmodifiableMap(copy);
	}

	public GLGraphNode(GLNodeId id, String typeId,
			Map<String, GLNodeValue> properties) {
		this(id, typeId, 1, properties);
	}

	public GLGraphNode(GLNodeId id, String typeId) {
		this(id, typeId, 1, Map.of());
	}

    /** Persisted provenance; unmarked existing values remain explicit for compatibility. */
    public static final String AUTOMATIC_PREFIX = "__automatic.";
    public static final String AUTOMATIC_DEFAULT_PREFIX = "__automaticDefault.";
    public boolean isPropertyAutomatic(String key) {
        return properties.get(AUTOMATIC_PREFIX+key) instanceof GLNodeValue.BooleanValue value
                ? value.value() && (!properties.containsKey(AUTOMATIC_DEFAULT_PREFIX+key) || java.util.Objects.equals(properties.get(key),properties.get(AUTOMATIC_DEFAULT_PREFIX+key))) : !properties.containsKey(key);
    }
    public GLGraphNode withExplicitProperty(String key, GLNodeValue value) {
        return withProperty(key,value).withProperty(AUTOMATIC_PREFIX+key,GLNodeValue.of(false));
    }
    public GLGraphNode withAutomaticProperty(String key, GLNodeValue value) {
        return withProperty(key,value).withProperty(AUTOMATIC_PREFIX+key,GLNodeValue.of(true)).withProperty(AUTOMATIC_DEFAULT_PREFIX+key,value);
    }

	public GLGraphNode withProperty(String key, GLNodeValue value) {
		LinkedHashMap<String, GLNodeValue> next = new LinkedHashMap<>(properties);
		if (key == null || key.isBlank() || value == null)
			throw new IllegalArgumentException("Property key and value required");
		next.put(key, value);
		return new GLGraphNode(id, typeId, typeVersion, next);
	}

	public GLGraphNode withoutProperty(String key) {
		if (!properties.containsKey(key)) return this;
		LinkedHashMap<String, GLNodeValue> next = new LinkedHashMap<>(properties);
		next.remove(key);
		return new GLGraphNode(id, typeId, typeVersion, next);
	}

	/** Returns this node with a new stable type identity and schema version. */
	public GLGraphNode withType(String nextTypeId, int nextTypeVersion) {
		return new GLGraphNode(id, nextTypeId, nextTypeVersion, properties);
	}

	private static boolean validTypeId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
