package com.vingame.glswing.node.execution;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Prepares plans away from the caller thread and atomically publishes only the
 * newest successful request. Leases protect active resources during a swap.
 */
public final class GLExecutionPlanController<T> implements AutoCloseable {
	private final GLExecutionBackend<T> backend;
	private final Executor executor;
	private final Consumer<? super T> disposer;
	private final AtomicReference<Slot<T>> current = new AtomicReference<>();
	private final AtomicLong generation = new AtomicLong();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Object publicationLock = new Object();

	public GLExecutionPlanController(GLExecutionBackend<T> backend,
			Executor executor, Consumer<? super T> disposer) {
		if (backend == null || executor == null || disposer == null)
			throw new IllegalArgumentException("Backend, executor, and disposer required");
		this.backend = backend;
		this.executor = executor;
		this.disposer = disposer;
	}

	public CompletionStage<GLExecutionPlanSwapResult> prepareAndSwap(
			GLExecutionPlan plan) {
		if (plan == null) throw new IllegalArgumentException("Plan required");
		long request = generation.incrementAndGet();
		return CompletableFuture.supplyAsync(() -> prepare(request, plan), executor);
	}

	public Optional<GLExecutionPlanLease<T>> acquire() {
		while (true) {
			Slot<T> slot = current.get();
			if (slot == null) return Optional.empty();
			if (slot.acquire()) return Optional.of(new GLExecutionPlanLease<>(
					slot.plan, slot.prepared, slot::release));
		}
	}

	public Optional<GLExecutionPlan> currentPlan() {
		Slot<T> slot = current.get();
		return slot == null ? Optional.empty() : Optional.of(slot.plan);
	}

	@Override public void close() {
		synchronized (publicationLock) {
			if (!closed.compareAndSet(false, true)) return;
			generation.incrementAndGet();
			Slot<T> previous = current.getAndSet(null);
			if (previous != null) previous.retire();
		}
	}

	private GLExecutionPlanSwapResult prepare(long request, GLExecutionPlan plan) {
		String backendId = backend.descriptor().id();
		if (closed.get()) return result(GLExecutionPlanSwapResult.Status.SUPERSEDED,
				plan, List.of());
		GLExecutionBackendCompatibility compatibility =
				GLExecutionBackendCompatibility.check(plan, backend.descriptor());
		if (!compatibility.isCompatible()) return result(
				GLExecutionPlanSwapResult.Status.REJECTED, plan,
				compatibility.diagnostics());
		T prepared;
		try {
			prepared = backend.prepare(plan);
			if (prepared == null) throw new IllegalStateException(
					"Backend returned no prepared plan");
		} catch (Exception failure) {
			return result(GLExecutionPlanSwapResult.Status.FAILED, plan,
					List.of(GLBackendDiagnostic.error(backendId, "backend.prepare",
							"Backend preparation failed: " + usefulMessage(failure))));
		}
		synchronized (publicationLock) {
			if (closed.get() || generation.get() != request) {
				dispose(prepared);
				return result(GLExecutionPlanSwapResult.Status.SUPERSEDED, plan, List.of());
			}
			Slot<T> next = new Slot<>(plan, prepared, disposer);
			Slot<T> previous = current.getAndSet(next);
			if (previous != null) previous.retire();
		}
		return result(GLExecutionPlanSwapResult.Status.ACTIVATED, plan, List.of());
	}

	private void dispose(T prepared) {
		try { disposer.accept(prepared); }
		catch (RuntimeException ignored) { }
	}
	private static String usefulMessage(Exception failure) {
		return failure.getMessage() == null ? failure.getClass().getSimpleName()
				: failure.getMessage();
	}
	private static GLExecutionPlanSwapResult result(
			GLExecutionPlanSwapResult.Status status, GLExecutionPlan plan,
			List<GLBackendDiagnostic> diagnostics) {
		return new GLExecutionPlanSwapResult(status,
				status == GLExecutionPlanSwapResult.Status.ACTIVATED
						? Optional.of(plan) : Optional.empty(), diagnostics);
	}

	private static final class Slot<T> {
		private final GLExecutionPlan plan;
		private final T prepared;
		private final Consumer<? super T> disposer;
		private final AtomicInteger leases = new AtomicInteger();
		private final AtomicBoolean retired = new AtomicBoolean();
		private final AtomicBoolean disposed = new AtomicBoolean();
		private Slot(GLExecutionPlan plan, T prepared, Consumer<? super T> disposer) {
			this.plan = plan; this.prepared = prepared; this.disposer = disposer;
		}
		private boolean acquire() {
			if (retired.get()) return false;
			leases.incrementAndGet();
			if (!retired.get()) return true;
			release();
			return false;
		}
		private void release() {
			int remaining = leases.decrementAndGet();
			if (remaining < 0) throw new IllegalStateException("Lease released twice");
			if (remaining == 0 && retired.get()) dispose();
		}
		private void retire() {
			retired.set(true);
			if (leases.get() == 0) dispose();
		}
		private void dispose() {
			if (disposed.compareAndSet(false, true)) {
				try { disposer.accept(prepared); }
				catch (RuntimeException ignored) { }
			}
		}
	}
}
