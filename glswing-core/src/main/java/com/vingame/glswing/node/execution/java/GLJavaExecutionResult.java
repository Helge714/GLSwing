package com.vingame.glswing.node.execution.java;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.execution.GLExecutionValueRef;
import com.vingame.glswing.node.model.GLNodeValue;

/** Values produced by one reference-backend evaluation step. */
public record GLJavaExecutionResult(long step,
		Map<GLExecutionValueRef, GLNodeValue> values,
		List<GLJavaExecutionTrace> traces) {
	public GLJavaExecutionResult {
		if (step < 0 || values == null || traces == null)
			throw new IllegalArgumentException("Invalid Java execution result");
		values = Map.copyOf(values);
		traces = List.copyOf(traces);
	}
	public GLJavaExecutionResult(long step,
			Map<GLExecutionValueRef, GLNodeValue> values) {
		this(step, values, List.of());
	}
	public Optional<GLNodeValue> find(GLExecutionValueRef ref) {
		return Optional.ofNullable(values.get(ref));
	}
}
