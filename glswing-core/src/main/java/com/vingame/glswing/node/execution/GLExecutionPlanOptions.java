package com.vingame.glswing.node.execution;

/** Domain-neutral compilation settings shared by every backend. */
public record GLExecutionPlanOptions(String latencyUnitId) {
	public static final GLExecutionPlanOptions DEFAULT =
			new GLExecutionPlanOptions("step");

	public GLExecutionPlanOptions {
		if (latencyUnitId == null
				|| !latencyUnitId.matches("[A-Za-z][A-Za-z0-9._-]*"))
			throw new IllegalArgumentException("Invalid latency unit ID");
	}
}
