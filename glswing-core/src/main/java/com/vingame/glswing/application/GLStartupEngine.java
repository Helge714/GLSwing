package com.vingame.glswing.application;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.application.GLStartupPlan.Definition;
import com.vingame.glswing.application.GLStartupPlan.ThreadAffinity;
import com.vingame.glswing.application.GLStartupSnapshot.Status;

final class GLStartupEngine {
	@FunctionalInterface
	interface GLDispatcher {
		Object execute(Callable<Object> operation, Consumer<GL3> glConsumer)
				throws Exception;
	}

	private final List<TaskState> states = new ArrayList<>();
	private final Map<String, Object> results = new LinkedHashMap<>();
	private final Consumer<GLStartupSnapshot> listener;
	private final double totalWeight;

	GLStartupEngine(GLStartupPlan plan, Consumer<GLStartupSnapshot> listener) {
		if (plan == null || listener == null)
			throw new IllegalArgumentException("Startup plan and listener are required");
		for (Definition definition : plan.definitions()) states.add(new TaskState(definition));
		totalWeight = Math.max(1, plan.totalWeight());
		this.listener = listener;
	}

	Map<String, Object> run(GLDispatcher glDispatcher) throws Exception {
		publish();
		for (TaskState state : states) run(state, glDispatcher);
		return Map.copyOf(results);
	}

	private void run(TaskState state, GLDispatcher glDispatcher) throws Exception {
		state.status = Status.RUNNING;
		state.progress = 0;
		publish();
		TaskContext context = new TaskContext(state);
		try {
			Callable<Object> operation = () -> state.definition.operation().run(context);
			Object result = switch (state.definition.affinity()) {
			case BACKGROUND -> operation.call();
			case AWT -> onAwt(operation);
			case GL -> {
				if (glDispatcher == null)
					throw new IllegalStateException("A GL startup task requires an active host");
				yield glDispatcher.execute(operation, context::gl);
			}
			};
			if (state.definition.resultKey() != null && result == null)
				throw new IllegalStateException("Startup result must not be null: "
						+ state.definition.resultKey());
			if (state.definition.resultKey() != null)
				results.put(state.definition.resultKey(), result);
			state.progress = 1;
			state.status = Status.COMPLETE;
			publish();
		} catch (Throwable failure) {
			state.status = Status.FAILED;
			state.detail = failure.getMessage() == null
					? failure.getClass().getSimpleName() : failure.getMessage();
			publish();
			if (failure instanceof Exception error) throw error;
			throw (Error) failure;
		}
	}

	private static Object onAwt(Callable<Object> operation) throws Exception {
		if (EventQueue.isDispatchThread()) return operation.call();
		AtomicReference<Object> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try {
			EventQueue.invokeAndWait(() -> {
				try { result.set(operation.call()); }
				catch (Throwable error) { failure.set(error); }
			});
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		} catch (InvocationTargetException error) {
			throw new IllegalStateException("AWT startup task failed", error.getCause());
		}
		if (failure.get() instanceof Exception error) throw error;
		if (failure.get() instanceof Error error) throw error;
		return result.get();
	}

	private synchronized void publish() {
		double completed = 0;
		boolean indeterminate = false;
		List<GLStartupSnapshot.Task> tasks = new ArrayList<>(states.size());
		for (TaskState state : states) {
			completed += state.definition.weight() * state.progress;
			if (state.status == Status.RUNNING && !state.determinate) indeterminate = true;
			tasks.add(new GLStartupSnapshot.Task(state.definition.label(), state.status,
					state.progress, state.detail));
		}
		listener.accept(new GLStartupSnapshot(Math.min(1, completed / totalWeight),
				indeterminate, tasks));
	}

	private final class TaskContext implements GLStartupTaskContext {
		private final TaskState state;
		private GL3 gl;
		TaskContext(TaskState state) { this.state = state; }
		void gl(GL3 gl) { this.gl = gl; }

		@Override public void detail(String detail) {
			state.detail = detail == null ? "" : detail;
			publish();
		}
		@Override public void progress(double progress) {
			if (!Double.isFinite(progress) || progress < 0 || progress > 1)
				throw new IllegalArgumentException("Task progress must be between zero and one");
			state.determinate = true;
			state.progress = progress;
			publish();
		}
		@Override public <T> T require(String key, Class<T> type) {
			if (key == null || key.isBlank() || type == null)
				throw new IllegalArgumentException("Startup result key and type are required");
			Object result = results.get(key);
			if (result == null)
				throw new IllegalStateException("Startup result is not available: " + key);
			return type.cast(result);
		}
		@Override public GL3 requireGL() {
			if (gl == null) throw new IllegalStateException("No GL context is active");
			return gl;
		}
	}

	private static final class TaskState {
		private final Definition definition;
		private Status status = Status.PENDING;
		private double progress;
		private boolean determinate;
		private String detail = "";
		TaskState(Definition definition) { this.definition = definition; }
	}
}
