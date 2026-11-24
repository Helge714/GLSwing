package com.vingame.glswing.node.execution.java;

import java.util.Optional;

/** Result of advancing a prepared Java plan by at most one operation. */
public record GLJavaExecutionProgress(Optional<GLJavaExecutionTrace> trace,
		Optional<GLJavaExecutionResult> completedStep) {
	public GLJavaExecutionProgress {
		if (trace == null || completedStep == null
				|| trace.isEmpty() && completedStep.isEmpty())
			throw new IllegalArgumentException("Execution progress must contain a result");
	}
	public boolean isStepComplete() { return completedStep.isPresent(); }
}
