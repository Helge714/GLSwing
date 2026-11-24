package com.vingame.glswing.node.execution.java;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.node.execution.GLExecutionOperation;
import com.vingame.glswing.node.model.GLNodeValue;

/** Values and measured duration of one reference-backend operation. */
public record GLJavaExecutionTrace(long step, GLExecutionOperation operation,
		Map<String, List<GLNodeValue>> inputs, Map<String, GLNodeValue> outputs,
		long durationNanos) {
	public GLJavaExecutionTrace {
		if (step < 0 || operation == null || inputs == null || outputs == null
				|| durationNanos < 0)
			throw new IllegalArgumentException("Invalid Java execution trace");
		Map<String, List<GLNodeValue>> inputCopy = new LinkedHashMap<>();
		inputs.forEach((key, value) -> inputCopy.put(key, List.copyOf(value)));
		inputs = Map.copyOf(inputCopy);
		outputs = Map.copyOf(new LinkedHashMap<>(outputs));
	}
}
