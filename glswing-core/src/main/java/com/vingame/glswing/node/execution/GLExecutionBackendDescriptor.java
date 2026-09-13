package com.vingame.glswing.node.execution;

import java.util.Set;

/** Capabilities used to reject incompatible plans before backend preparation. */
public record GLExecutionBackendDescriptor(String id, int minimumPlanVersion,
		int maximumPlanVersion, Set<String> operationTypeIds,
		Set<String> latencyUnitIds, boolean supportsPreviousStep,
		boolean supportsInputCompensation) {
	public GLExecutionBackendDescriptor {
		if (!valid(id) || minimumPlanVersion < 1
				|| maximumPlanVersion < minimumPlanVersion || operationTypeIds == null
				|| latencyUnitIds == null || operationTypeIds.stream().anyMatch(value -> !valid(value))
				|| latencyUnitIds.stream().anyMatch(value -> !valid(value)))
			throw new IllegalArgumentException("Invalid backend descriptor");
		operationTypeIds = Set.copyOf(operationTypeIds);
		latencyUnitIds = Set.copyOf(latencyUnitIds);
	}
	public GLExecutionBackendDescriptor(String id, int minimumPlanVersion,
			int maximumPlanVersion, Set<String> operationTypeIds,
			Set<String> latencyUnitIds, boolean supportsPreviousStep) {
		this(id, minimumPlanVersion, maximumPlanVersion, operationTypeIds,
				latencyUnitIds, supportsPreviousStep, false);
	}
	private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
