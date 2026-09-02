package com.vingame.glswing.application;

import java.util.List;

/** Immutable progress snapshot suitable for a splash screen or logging. */
public record GLStartupSnapshot(double progress, boolean indeterminate,
		List<Task> tasks) {
	public enum Status { PENDING, RUNNING, COMPLETE, FAILED }
	public record Task(String label, Status status, double progress, String detail) {
		public Task {
			if (label == null || label.isBlank() || status == null || detail == null
					|| progress < 0 || progress > 1 || !Double.isFinite(progress))
				throw new IllegalArgumentException("Invalid startup task snapshot");
		}
	}
	public GLStartupSnapshot {
		if (!Double.isFinite(progress) || progress < 0 || progress > 1 || tasks == null)
			throw new IllegalArgumentException("Invalid startup snapshot");
		tasks = List.copyOf(tasks);
	}
	/** Returns the running task, or {@code null} between tasks. */
	public Task currentTask() {
		return tasks.stream().filter(task -> task.status() == Status.RUNNING)
				.findFirst().orElse(null);
	}
}
