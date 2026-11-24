package com.vingame.glswing.node.execution.java;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.node.execution.GLExecutionBackend;
import com.vingame.glswing.node.execution.GLExecutionBackendCompatibility;
import com.vingame.glswing.node.execution.GLExecutionBackendDescriptor;
import com.vingame.glswing.node.execution.GLExecutionPlan;

/**
 * Deterministic reference backend for tests, offline evaluation, and backend
 * conformance fixtures. It is not intended for real-time workloads.
 */
public final class GLJavaExecutionBackend
		implements GLExecutionBackend<GLJavaExecutablePlan> {
	private final Map<String, GLJavaOperation> operations = new LinkedHashMap<>();
	private final Set<String> latencyUnits;

	public GLJavaExecutionBackend() {
		this(Set.of("step", "sample", "frame", "tick"));
	}

	public GLJavaExecutionBackend(Set<String> latencyUnits) {
		if (latencyUnits == null || latencyUnits.isEmpty())
			throw new IllegalArgumentException("At least one latency unit is required");
		this.latencyUnits = Set.copyOf(latencyUnits);
	}

	public synchronized void register(String operationTypeId,
			GLJavaOperation operation) {
		if (operationTypeId == null || operationTypeId.isBlank() || operation == null)
			throw new IllegalArgumentException("Operation type and implementation required");
		if (operations.putIfAbsent(operationTypeId, operation) != null)
			throw new IllegalArgumentException("Java operation already registered: "
					+ operationTypeId);
	}

	@Override public synchronized GLExecutionBackendDescriptor descriptor() {
		return new GLExecutionBackendDescriptor("java-reference",
				GLExecutionPlan.FORMAT_VERSION, GLExecutionPlan.FORMAT_VERSION,
				operations.keySet(), latencyUnits, true, false);
	}

	@Override public synchronized GLJavaExecutablePlan prepare(GLExecutionPlan plan) {
		if (plan == null) throw new IllegalArgumentException("Execution plan required");
		GLExecutionBackendCompatibility compatibility =
				GLExecutionBackendCompatibility.check(plan, descriptor());
		if (!compatibility.isCompatible())
			throw new IllegalArgumentException(compatibility.diagnostics().get(0).message());
		Map<String, GLJavaOperation> resolved = new LinkedHashMap<>();
		plan.operations().forEach(operation -> {
			GLJavaOperation implementation = operations.get(operation.operationTypeId());
			if (implementation == null)
				throw new IllegalArgumentException("Java backend does not implement "
						+ operation.operationTypeId());
			resolved.put(operation.operationTypeId(), implementation);
		});
		return new GLJavaExecutablePlan(plan, resolved);
	}
}
