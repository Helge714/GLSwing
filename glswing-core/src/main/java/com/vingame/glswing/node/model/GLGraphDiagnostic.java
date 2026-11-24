package com.vingame.glswing.node.model;

import java.util.Set;

/** Structured graph problem attached to concrete model elements. */
public record GLGraphDiagnostic(Severity severity, String code, String message,
		Set<GLNodeId> nodeIds, Set<GLNodeConnectionId> connectionIds) {
	public enum Severity { WARNING, ERROR }

	public GLGraphDiagnostic {
		if (severity == null || code == null || code.isBlank()
				|| message == null || message.isBlank() || nodeIds == null
				|| connectionIds == null)
			throw new IllegalArgumentException("Invalid graph diagnostic");
		nodeIds = Set.copyOf(nodeIds);
		connectionIds = Set.copyOf(connectionIds);
	}
}
