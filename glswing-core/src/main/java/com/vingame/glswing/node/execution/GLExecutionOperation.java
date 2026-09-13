package com.vingame.glswing.node.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;

/** Immutable scheduled operation consumed by execution backends. */
public record GLExecutionOperation(int index, GLNodeId nodeId, String nodeTypeId,
		String operationTypeId, GLNodeImplementationKind kind,
		Map<String, GLNodeValue> properties, List<GLExecutionInput> inputs,
		Map<String, GLExecutionValueRef> outputs, long estimatedCost,
		long processingLatency, long outputLatency,
		Optional<GLCompositeOrigin> origin) {
	public GLExecutionOperation {
		if (index < 0 || nodeId == null || nodeTypeId == null || nodeTypeId.isBlank()
				|| operationTypeId == null || operationTypeId.isBlank() || kind == null
				|| properties == null || inputs == null || outputs == null
				|| estimatedCost < 0 || processingLatency < 0 || outputLatency < 0
				|| outputLatency < processingLatency || origin == null)
			throw new IllegalArgumentException("Invalid execution operation");
		properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
		inputs = List.copyOf(inputs);
		outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
	}
	public GLExecutionOperation(int index, GLNodeId nodeId, String nodeTypeId,
			String operationTypeId, GLNodeImplementationKind kind,
			Map<String, GLNodeValue> properties, List<GLExecutionInput> inputs,
			Map<String, GLExecutionValueRef> outputs, long estimatedCost,
			Optional<GLCompositeOrigin> origin) {
		this(index, nodeId, nodeTypeId, operationTypeId, kind, properties, inputs,
				outputs, estimatedCost, 0, 0, origin);
	}
}
