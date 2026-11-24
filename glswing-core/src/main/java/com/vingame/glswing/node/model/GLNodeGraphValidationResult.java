package com.vingame.glswing.node.model;

import java.util.List;

/** Immutable result of validating one graph revision. */
public record GLNodeGraphValidationResult(GLNodeGraphSnapshot snapshot,
		List<GLGraphDiagnostic> diagnostics) {
	public GLNodeGraphValidationResult {
		if (snapshot == null || diagnostics == null)
			throw new IllegalArgumentException("Snapshot and diagnostics required");
		diagnostics = List.copyOf(diagnostics);
	}

	public boolean isValid() {
		return diagnostics.stream().noneMatch(diagnostic ->
				diagnostic.severity() == GLGraphDiagnostic.Severity.ERROR);
	}
}
