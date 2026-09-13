package com.vingame.glswing.node.document;

import com.vingame.glswing.node.model.GLGraphNode;

/** Converts one node-type version to its immediately following version. */
@FunctionalInterface
public interface GLNodeMigration {
	GLGraphNode migrate(GLGraphNode node) throws Exception;
}
