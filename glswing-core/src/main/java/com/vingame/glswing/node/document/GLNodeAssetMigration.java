package com.vingame.glswing.node.document;

/** Converts one graph-asset version to its immediately following version. */
@FunctionalInterface
public interface GLNodeAssetMigration {
	GLNodeGraphAsset migrate(GLNodeGraphAsset asset) throws Exception;
}
