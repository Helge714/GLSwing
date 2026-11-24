package com.vingame.glswing.node.execution;

import java.util.ArrayList;
import java.util.List;

import com.vingame.glswing.node.model.GLPortTiming;

/** Result of checking a plan against an advertised backend contract. */
public record GLExecutionBackendCompatibility(List<GLBackendDiagnostic> diagnostics) {
	public GLExecutionBackendCompatibility {
		if (diagnostics == null) throw new IllegalArgumentException("Diagnostics required");
		diagnostics = List.copyOf(diagnostics);
	}
	public boolean isCompatible() {
		return diagnostics.stream().noneMatch(value ->
				value.severity() == GLBackendDiagnostic.Severity.ERROR);
	}

	public static GLExecutionBackendCompatibility check(GLExecutionPlan plan,
			GLExecutionBackendDescriptor backend) {
		if (plan == null || backend == null)
			throw new IllegalArgumentException("Plan and backend required");
		List<GLBackendDiagnostic> result = new ArrayList<>();
		if (GLExecutionPlan.FORMAT_VERSION < backend.minimumPlanVersion()
				|| GLExecutionPlan.FORMAT_VERSION > backend.maximumPlanVersion())
			result.add(GLBackendDiagnostic.error(backend.id(), "backend.plan-version",
					"Execution-plan version " + GLExecutionPlan.FORMAT_VERSION
							+ " is not supported"));
		if (!backend.latencyUnitIds().contains(plan.latencyUnitId()))
			result.add(GLBackendDiagnostic.error(backend.id(), "backend.latency-unit",
					"Latency unit is not supported: " + plan.latencyUnitId()));
		for (GLExecutionOperation operation : plan.operations())
			if (!backend.operationTypeIds().contains(operation.operationTypeId()))
				result.add(GLBackendDiagnostic.error(backend.id(), "backend.operation",
						"Operation is not supported: " + operation.operationTypeId(),
						operation, null));
		if (!backend.supportsPreviousStep() && plan.operations().stream()
				.flatMap(operation -> operation.inputs().stream())
				.anyMatch(input -> input.timing() == GLPortTiming.PREVIOUS_STEP))
			result.add(GLBackendDiagnostic.error(backend.id(), "backend.previous-step",
					"Previous-step inputs are not supported"));
		if (!backend.supportsInputCompensation() && plan.operations().stream()
				.flatMap(operation -> operation.inputs().stream())
				.anyMatch(input -> input.compensationLatency() > 0))
			result.add(GLBackendDiagnostic.error(backend.id(),
					"backend.latency-compensation",
					"Input latency compensation is not supported"));
		return new GLExecutionBackendCompatibility(result);
	}
}
