package com.vingame.glswing.node.execution.java;

import com.vingame.glswing.node.execution.GLExecutionOperation;

/** Context supplied to a Java operation during one deterministic evaluation. */
public record GLJavaExecutionContext(long step, GLExecutionOperation operation) {
	public GLJavaExecutionContext {
		if (step < 0 || operation == null)
			throw new IllegalArgumentException("Invalid Java execution context");
	}
}
