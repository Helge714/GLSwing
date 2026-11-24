package com.vingame.glswing.node.execution;

/** Backend SPI that prepares a language-independent plan for execution. */
public interface GLExecutionBackend<T> {
	GLExecutionBackendDescriptor descriptor();
	T prepare(GLExecutionPlan plan) throws Exception;
}
