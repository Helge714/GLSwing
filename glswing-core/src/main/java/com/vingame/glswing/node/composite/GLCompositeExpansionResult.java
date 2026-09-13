package com.vingame.glswing.node.composite;

import java.util.List;

import com.vingame.glswing.node.model.GLGraphDiagnostic;

/** Estimated flattened graph size and structured compatibility diagnostics. */
public record GLCompositeExpansionResult(long expandedNodes,
		long expandedConnections, long estimatedOperations, int maximumDepth,
		List<GLGraphDiagnostic> diagnostics) {
	public GLCompositeExpansionResult {
		if (expandedNodes < 0 || expandedConnections < 0 || estimatedOperations < 0
				|| maximumDepth < 0 || diagnostics == null)
			throw new IllegalArgumentException("Invalid composite expansion result");
		diagnostics = List.copyOf(diagnostics);
	}
	public boolean isValid() {
		return diagnostics.stream().noneMatch(value ->
				value.severity() == GLGraphDiagnostic.Severity.ERROR);
	}
}
