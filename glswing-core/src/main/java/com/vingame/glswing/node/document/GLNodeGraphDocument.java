package com.vingame.glswing.node.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Root graph plus embedded and separately stored reusable graph assets. */
public record GLNodeGraphDocument(GLNodeGraphAsset root,
		Map<String, GLNodeGraphAsset> embeddedAssets,
		List<GLNodeGraphAssetReference> externalAssets) {
	public GLNodeGraphDocument {
		if (root == null || embeddedAssets == null || externalAssets == null)
			throw new IllegalArgumentException("Complete node-graph document required");
		LinkedHashMap<String, GLNodeGraphAsset> embedded = new LinkedHashMap<>();
		embeddedAssets.forEach((id, asset) -> {
			if (id == null || asset == null || !id.equals(asset.id())
					|| id.equals(root.id()) || embedded.putIfAbsent(id, asset) != null)
				throw new IllegalArgumentException("Invalid embedded graph asset: " + id);
		});
		embeddedAssets = Collections.unmodifiableMap(embedded);
		externalAssets = List.copyOf(externalAssets);
		Set<String> aliases = new LinkedHashSet<>();
		for (GLNodeGraphAssetReference reference : externalAssets)
			if (reference == null || !aliases.add(reference.alias()))
				throw new IllegalArgumentException("External asset aliases must be unique");
	}

	public GLNodeGraphDocument(GLNodeGraphAsset root) {
		this(root, Map.of(), List.of());
	}

	public Optional<GLNodeGraphAsset> findAsset(String id) {
		return root.id().equals(id) ? Optional.of(root)
				: Optional.ofNullable(embeddedAssets.get(id));
	}

	/** Returns a copy containing the supplied reusable asset. */
	public GLNodeGraphDocument withEmbeddedAsset(GLNodeGraphAsset asset) {
		if (asset == null || asset.id().equals(root.id()))
			throw new IllegalArgumentException("Distinct embedded asset required");
		LinkedHashMap<String, GLNodeGraphAsset> next =
				new LinkedHashMap<>(embeddedAssets);
		next.put(asset.id(), asset);
		return new GLNodeGraphDocument(root, next, externalAssets);
	}

	/** Creates a standalone document whose root is the selected reusable asset. */
	public GLNodeGraphDocument exportAsset(String id) {
		GLNodeGraphAsset exported = findAsset(id).orElseThrow(() ->
				new IllegalArgumentException("Unknown graph asset: " + id));
		LinkedHashMap<String, GLNodeGraphAsset> dependencies =
				new LinkedHashMap<>(embeddedAssets);
		dependencies.remove(id);
		return new GLNodeGraphDocument(exported, dependencies, externalAssets);
	}
}
