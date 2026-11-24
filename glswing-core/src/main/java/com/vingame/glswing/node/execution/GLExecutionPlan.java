package com.vingame.glswing.node.execution;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeId;

/** Immutable, language-independent program compiled from a node graph snapshot. */
public record GLExecutionPlan(GLNodeGraphId sourceGraphId, long sourceRevision,
		List<GLExecutionOperation> operations, List<GLExecutionValue> values,
		String latencyUnitId, String hash, GLExecutionPlanStatistics statistics) {
	public static final int FORMAT_VERSION = 1;
	public GLExecutionPlan {
		if (sourceGraphId == null || sourceRevision < 0 || operations == null
				|| values == null || latencyUnitId == null
				|| !latencyUnitId.matches("[A-Za-z][A-Za-z0-9._-]*")
				|| hash == null || !hash.matches("[0-9a-f]{64}")
				|| statistics == null)
			throw new IllegalArgumentException("Invalid execution plan");
		operations = List.copyOf(operations);
		values = List.copyOf(values);
		for (int index = 0; index < operations.size(); index++)
			if (operations.get(index).index() != index)
				throw new IllegalArgumentException("Operation indices must be dense");
		for (int index = 0; index < values.size(); index++)
			if (values.get(index).ref().index() != index)
				throw new IllegalArgumentException("Value indices must be dense");
		for (GLExecutionOperation operation : operations) {
			for (GLExecutionInput input : operation.inputs())
				validateRef(input.value(), values.size());
			for (GLExecutionValueRef output : operation.outputs().values())
				validateRef(output, values.size());
		}
		if (statistics.scheduledOperations() != operations.size())
			throw new IllegalArgumentException("Operation statistics do not match plan");
	}

	public GLExecutionPlan(GLNodeGraphId sourceGraphId, long sourceRevision,
			List<GLExecutionOperation> operations, List<GLExecutionValue> values,
			String hash, GLExecutionPlanStatistics statistics) {
		this(sourceGraphId, sourceRevision, operations, values,
				GLExecutionPlanOptions.DEFAULT.latencyUnitId(), hash, statistics);
	}

	public Optional<GLExecutionValue> findValue(GLExecutionValueRef ref) {
		if (ref == null || ref.index() >= values.size()) return Optional.empty();
		return Optional.of(values.get(ref.index()));
	}

	public Map<GLNodeId, GLExecutionOperation> operationsByNodeId() {
		return operations.stream().collect(Collectors.toUnmodifiableMap(
				GLExecutionOperation::nodeId, Function.identity()));
	}
	private static void validateRef(GLExecutionValueRef ref, int size) {
		if (ref.index() >= size)
			throw new IllegalArgumentException("Operation references an unknown value");
	}
}
