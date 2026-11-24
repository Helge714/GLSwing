package com.vingame.glswing.node.execution;

import java.util.List;
import java.util.Optional;

/** Outcome of one asynchronous prepare-and-swap request. */
public record GLExecutionPlanSwapResult(Status status,
		Optional<GLExecutionPlan> plan, List<GLBackendDiagnostic> diagnostics) {
	public enum Status { ACTIVATED, SUPERSEDED, REJECTED, FAILED }
	public GLExecutionPlanSwapResult {
		if (status == null || plan == null || diagnostics == null)
			throw new IllegalArgumentException("Invalid plan-swap result");
		diagnostics = List.copyOf(diagnostics);
		if (status == Status.ACTIVATED && plan.isEmpty())
			throw new IllegalArgumentException("Activated result requires a plan");
	}
}
