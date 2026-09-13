package com.vingame.glswing.node.execution;

/** Dense stable reference to a value inside one execution plan. */
public record GLExecutionValueRef(int index) {
	public GLExecutionValueRef {
		if (index < 0) throw new IllegalArgumentException("Value index must not be negative");
	}
}
