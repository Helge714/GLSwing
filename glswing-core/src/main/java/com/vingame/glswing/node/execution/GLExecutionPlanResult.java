package com.vingame.glswing.node.execution;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.model.GLGraphDiagnostic;
import com.vingame.glswing.node.model.GLNodeId;

/** Transactional compilation outcome; invalid input never exposes a partial plan. */
public record GLExecutionPlanResult(Optional<GLExecutionPlan> plan,
		List<GLGraphDiagnostic> diagnostics,
		Map<GLNodeId, GLCompositeOrigin> origins) {
	public GLExecutionPlanResult {
		if (plan == null || diagnostics == null || origins == null)
			throw new IllegalArgumentException("Plan result required");
		diagnostics = List.copyOf(diagnostics);
		origins = Map.copyOf(origins);
		if (plan.isEmpty() && !origins.isEmpty())
			throw new IllegalArgumentException("Failed compilation cannot expose partial origins");
	}
	public GLExecutionPlanResult(Optional<GLExecutionPlan> plan,
			List<GLGraphDiagnostic> diagnostics) {
		this(plan, diagnostics, Map.of());
	}
	public boolean isValid() { return plan.isPresent(); }
	public Optional<GLCompositeOrigin> origin(GLNodeId nodeId) {
		return Optional.ofNullable(origins.get(nodeId));
	}
}
