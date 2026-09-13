package com.vingame.glswing.node.composite;

import java.util.List;

import com.vingame.glswing.node.model.GLNodeId;

/** Source location of one leaf node produced by composite flattening. */
public record GLCompositeOrigin(List<Instance> instances, String sourceAssetId,
		GLNodeId sourceNodeId) {
	public record Instance(GLNodeId nodeId, String nodeTypeId, String assetId) {
		public Instance {
			if (nodeId == null || nodeTypeId == null || nodeTypeId.isBlank()
					|| assetId == null || assetId.isBlank())
				throw new IllegalArgumentException("Invalid composite instance origin");
		}
	}
	public GLCompositeOrigin {
		if (instances == null || sourceAssetId == null || sourceAssetId.isBlank()
				|| sourceNodeId == null)
			throw new IllegalArgumentException("Invalid flattened-node origin");
		instances = List.copyOf(instances);
	}
}
