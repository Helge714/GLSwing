package com.vingame.glswing.node.composite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphDocument;

/** Thread-safe library of loaded graph assets and their composite node contracts. */
public final class GLCompositeGraphRegistry {
	private final Map<String, GLNodeGraphAsset> assets = new LinkedHashMap<>();
	private final Map<String, GLCompositeNodeDescriptor> composites = new LinkedHashMap<>();

	public synchronized void registerAsset(GLNodeGraphAsset asset) {
		if (asset == null) throw new IllegalArgumentException("Graph asset required");
		if (assets.containsKey(asset.id()))
			throw new IllegalArgumentException("Graph asset is already registered: " + asset.id());
		if (asset.compositeDescriptor() != null
				&& composites.containsKey(asset.compositeDescriptor().nodeTypeId()))
			throw new IllegalArgumentException("Composite node type is already registered: "
					+ asset.compositeDescriptor().nodeTypeId());
		assets.put(asset.id(), asset);
		if (asset.compositeDescriptor() != null)
			composites.put(asset.compositeDescriptor().nodeTypeId(),
					asset.compositeDescriptor());
	}
	/** Atomically replaces a loaded asset while preserving its public identity. */
	public synchronized void replaceAsset(GLNodeGraphAsset asset) {
		if (asset == null || !assets.containsKey(asset.id()))
			throw new IllegalArgumentException("Registered graph asset required");
		GLNodeGraphAsset previous = assets.get(asset.id());
		String previousType = previous.compositeDescriptor() == null ? null
				: previous.compositeDescriptor().nodeTypeId();
		String nextType = asset.compositeDescriptor() == null ? null
				: asset.compositeDescriptor().nodeTypeId();
		if (!java.util.Objects.equals(previousType, nextType))
			throw new IllegalArgumentException(
					"Replacement must preserve the composite node type");
		assets.put(asset.id(), asset);
		if (nextType != null) composites.put(nextType, asset.compositeDescriptor());
	}
	/** Registers the root and every embedded asset without resolving external files. */
	public synchronized void registerDocument(GLNodeGraphDocument document) {
		if (document == null) throw new IllegalArgumentException("Graph document required");
		registerAsset(document.root());
		for (GLNodeGraphAsset asset : document.embeddedAssets().values()) registerAsset(asset);
	}
	public synchronized void registerComposite(GLCompositeNodeDescriptor descriptor) {
		if (descriptor == null) throw new IllegalArgumentException("Composite descriptor required");
		if (composites.putIfAbsent(descriptor.nodeTypeId(), descriptor) != null)
			throw new IllegalArgumentException("Composite node type is already registered: "
					+ descriptor.nodeTypeId());
	}
	public synchronized Optional<GLNodeGraphAsset> findAsset(String assetId) {
		return Optional.ofNullable(assets.get(assetId));
	}
	public synchronized Optional<GLCompositeNodeDescriptor> findComposite(String nodeTypeId) {
		return Optional.ofNullable(composites.get(nodeTypeId));
	}
	public synchronized List<GLCompositeNodeDescriptor> composites() {
		return List.copyOf(composites.values());
	}
	public synchronized List<GLNodeGraphAsset> assets() {
		return List.copyOf(assets.values());
	}
	/** Removes an asset and its composite contract, if present. */
	public synchronized boolean unregisterAsset(String assetId) {
		GLNodeGraphAsset removed = assets.remove(assetId);
		if (removed == null) return false;
		if (removed.compositeDescriptor() != null)
			composites.remove(removed.compositeDescriptor().nodeTypeId());
		return true;
	}
}
