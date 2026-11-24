package com.vingame.glswing.node.composite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.node.model.GLNodeValue;

/** Portable registration metadata for an inline editor used by an input port. */
public record GLNodeInputEditorDescriptor(String id, String displayName,
		Set<String> supportedValueTypeIds,
		Map<String, GLNodeValue> defaultOptions) {
	public GLNodeInputEditorDescriptor {
		if (!validId(id) || displayName == null || displayName.isBlank()
				|| supportedValueTypeIds == null || defaultOptions == null)
			throw new IllegalArgumentException("Invalid input-editor descriptor");
		supportedValueTypeIds = Set.copyOf(supportedValueTypeIds);
		LinkedHashMap<String, GLNodeValue> options = new LinkedHashMap<>();
		defaultOptions.forEach((key, value) -> {
			if (!validId(key) || value == null)
				throw new IllegalArgumentException("Invalid input-editor option");
			options.put(key, value);
		});
		defaultOptions = Map.copyOf(options);
	}

	/** An empty supported-type set means that the application accepts every type. */
	public boolean supports(String valueTypeId) {
		return supportedValueTypeIds.isEmpty()
				|| supportedValueTypeIds.contains(valueTypeId);
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
