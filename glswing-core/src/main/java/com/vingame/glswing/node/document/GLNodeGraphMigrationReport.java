package com.vingame.glswing.node.document;

import java.util.List;

/** Immutable audit trail produced while a graph document is upgraded in memory. */
public record GLNodeGraphMigrationReport(GLNodeGraphDocument document,
		List<Step> steps) {
	public enum Scope { ASSET, NODE }
	public record Step(Scope scope, String id, int fromVersion, int toVersion) {
		public Step {
			if (scope == null || id == null || id.isBlank() || fromVersion < 1
					|| toVersion != fromVersion + 1)
				throw new IllegalArgumentException("Invalid migration report step");
		}
	}
	public GLNodeGraphMigrationReport {
		if (document == null || steps == null)
			throw new IllegalArgumentException("Migration result required");
		steps = List.copyOf(steps);
	}
	public boolean migrated() { return !steps.isEmpty(); }
}
