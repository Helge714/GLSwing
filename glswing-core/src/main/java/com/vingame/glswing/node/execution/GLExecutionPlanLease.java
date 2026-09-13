package com.vingame.glswing.node.execution;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stable access to one prepared backend plan. Retired resources are disposed
 * only after every outstanding lease has been closed.
 */
public final class GLExecutionPlanLease<T> implements AutoCloseable {
	private final GLExecutionPlan plan;
	private final T prepared;
	private final Runnable release;
	private final AtomicBoolean closed = new AtomicBoolean();

	GLExecutionPlanLease(GLExecutionPlan plan, T prepared, Runnable release) {
		this.plan = plan;
		this.prepared = prepared;
		this.release = release;
	}
	public GLExecutionPlan plan() { return plan; }
	public T prepared() { return prepared; }
	@Override public void close() {
		if (closed.compareAndSet(false, true)) release.run();
	}
}
