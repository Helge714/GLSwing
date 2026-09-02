package com.vingame.glswing.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Immutable ordered startup work executed by {@link GLApplication}. */
public final class GLStartupPlan {
	public enum ThreadAffinity { BACKGROUND, AWT, GL }

	record Definition(String resultKey, String label, double weight,
			ThreadAffinity affinity, GLStartupOperation<?> operation) { }

	private final List<Definition> definitions;

	private GLStartupPlan(List<Definition> definitions) {
		this.definitions = List.copyOf(definitions);
	}

	List<Definition> definitions() { return definitions; }
	public int taskCount() { return definitions.size(); }
	public double totalWeight() {
		return definitions.stream().mapToDouble(Definition::weight).sum();
	}
	public static Builder builder() { return new Builder(); }

	/** Mutable task builder used while configuring an application launch. */
	public static final class Builder {
		private final List<Definition> definitions = new ArrayList<>();

		public Builder background(String label, Runnable operation) {
			return background(label, 1, task -> { operation.run(); return null; });
		}
		public Builder background(String label, double weight,
				GLStartupOperation<?> operation) {
			return add(null, label, weight, ThreadAffinity.BACKGROUND, operation);
		}
		public Builder background(String label, GLStartupOperation<?> operation) {
			return background(label, 1, operation);
		}
		public <T> Builder provide(String key, String label, Supplier<T> operation) {
			return provide(key, label, 1, task -> operation.get());
		}
		public <T> Builder provide(String key, String label, double weight,
				GLStartupOperation<T> operation) {
			return add(key, label, weight, ThreadAffinity.BACKGROUND, operation);
		}
		public <T> Builder provide(String key, String label,
				GLStartupOperation<T> operation) {
			return provide(key, label, 1, operation);
		}
		public Builder awt(String label, Runnable operation) {
			return awt(label, 1, task -> { operation.run(); return null; });
		}
		public Builder awt(String label, double weight,
				GLStartupOperation<?> operation) {
			return add(null, label, weight, ThreadAffinity.AWT, operation);
		}
		public Builder awt(String label, GLStartupOperation<?> operation) {
			return awt(label, 1, operation);
		}
		public Builder gl(String label, double weight,
				GLStartupOperation<?> operation) {
			return add(null, label, weight, ThreadAffinity.GL, operation);
		}
		public Builder gl(String label, GLStartupOperation<?> operation) {
			return gl(label, 1, operation);
		}

		private Builder add(String key, String label, double weight,
				ThreadAffinity affinity, GLStartupOperation<?> operation) {
			if (key != null && key.isBlank())
				throw new IllegalArgumentException("Startup result key must not be blank");
			if (label == null || label.isBlank() || !Double.isFinite(weight) || weight <= 0
					|| affinity == null || operation == null)
				throw new IllegalArgumentException("Invalid startup task");
			if (key != null && definitions.stream().anyMatch(task -> key.equals(task.resultKey())))
				throw new IllegalArgumentException("Duplicate startup result key " + key);
			definitions.add(new Definition(key, label, weight, affinity, operation));
			return this;
		}

		public GLStartupPlan build() { return new GLStartupPlan(definitions); }
	}
}
