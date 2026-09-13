package com.vingame.glswing.node.execution;

/** Static estimates derived while compiling an execution plan. */
public record GLExecutionPlanStatistics(int sourceNodes, int retainedNodes,
		int foldedOperations, int scheduledOperations, long estimatedOperationCost,
		int temporarySlots, int stateSlots, long maximumLatency) {
	public GLExecutionPlanStatistics {
		if (sourceNodes < 0 || retainedNodes < 0 || foldedOperations < 0
				|| scheduledOperations < 0 || estimatedOperationCost < 0
				|| temporarySlots < 0 || stateSlots < 0 || maximumLatency < 0)
			throw new IllegalArgumentException("Execution statistics must not be negative");
	}
	public GLExecutionPlanStatistics(int sourceNodes, int retainedNodes,
			int foldedOperations, int scheduledOperations,
			long estimatedOperationCost, int temporarySlots, int stateSlots) {
		this(sourceNodes, retainedNodes, foldedOperations, scheduledOperations,
				estimatedOperationCost, temporarySlots, stateSlots, 0);
	}
}
