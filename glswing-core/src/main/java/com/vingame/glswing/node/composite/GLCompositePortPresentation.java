package com.vingame.glswing.node.composite;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vingame.glswing.node.model.GLNodeValue;

/** Portable UI metadata for one public composite port. */
public record GLCompositePortPresentation(String inputEditorId,
		boolean socketVisible, Map<String, GLNodeValue> editorOptions) {
	public GLCompositePortPresentation {
		if (!validId(inputEditorId) || editorOptions == null)
			throw new IllegalArgumentException("Invalid composite-port presentation");
		LinkedHashMap<String, GLNodeValue> options = new LinkedHashMap<>();
		editorOptions.forEach((key, value) -> {
			if (!validId(key) || value == null)
				throw new IllegalArgumentException("Invalid editor option");
			options.put(key, value);
		});
		editorOptions = Map.copyOf(options);
	}

	public static GLCompositePortPresentation socketOnly() {
		return new GLCompositePortPresentation(GLNodeInputEditorRegistry.NONE,
				true, Map.of());
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
