package com.vingame.glswing.node.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeValue;

/** Independently versioned graph asset suitable for projects or composites. */
public record GLNodeGraphAsset(String id, int version,
		GLNodeGraphAssetKind kind, GLNodeGraphSnapshot graph,
		GLNodeEditorViewState viewState, Map<String, GLNodeValue> metadata,
		GLCompositeNodeDescriptor compositeDescriptor) {
	public GLNodeGraphAsset {
		if (!validId(id) || version < 1 || kind == null || graph == null
				|| viewState == null || metadata == null)
			throw new IllegalArgumentException("Invalid node-graph asset");
		LinkedHashMap<String, GLNodeValue> copy = new LinkedHashMap<>();
		metadata.forEach((key, value) -> {
			if (!validId(key) || value == null)
				throw new IllegalArgumentException("Invalid asset metadata");
			copy.put(key, value);
		});
		metadata = Collections.unmodifiableMap(copy);
		if (compositeDescriptor != null && (kind != GLNodeGraphAssetKind.COMPOSITE
				|| !compositeDescriptor.assetId().equals(id)
				|| compositeDescriptor.minimumAssetVersion() > version))
			throw new IllegalArgumentException(
					"Composite descriptor must describe a compatible containing composite asset");
	}

	public GLNodeGraphAsset(String id, int version, GLNodeGraphAssetKind kind,
			GLNodeGraphSnapshot graph, GLNodeEditorViewState viewState,
			Map<String, GLNodeValue> metadata) {
		this(id, version, kind, graph, viewState, metadata, null);
	}

	public GLNodeGraphAsset(String id, int version, GLNodeGraphAssetKind kind,
			GLNodeGraphSnapshot graph, GLNodeEditorViewState viewState) {
		this(id, version, kind, graph, viewState, Map.of(), null);
	}

	public java.util.Optional<GLCompositeNodeDescriptor> composite() {
		return java.util.Optional.ofNullable(compositeDescriptor);
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
