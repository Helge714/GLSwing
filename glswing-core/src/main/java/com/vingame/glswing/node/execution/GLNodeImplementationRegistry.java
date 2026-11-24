package com.vingame.glswing.node.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe registry of execution contracts and optional constant evaluators. */
public final class GLNodeImplementationRegistry {
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public synchronized void register(GLNodeImplementationDescriptor descriptor) {
		register(descriptor, null, null);
	}

	public synchronized void register(GLNodeImplementationDescriptor descriptor,
			GLNodeConstantEvaluator constantEvaluator) {
		register(descriptor, constantEvaluator, null);
	}

	/** Registers a descriptor with property-dependent latency but no constant folder. */
	public synchronized void registerWithLatency(
			GLNodeImplementationDescriptor descriptor,
			GLNodeLatencyEstimator latencyEstimator) {
		register(descriptor, null, latencyEstimator);
	}

	public synchronized void register(GLNodeImplementationDescriptor descriptor,
			GLNodeConstantEvaluator constantEvaluator,
			GLNodeLatencyEstimator latencyEstimator) {
		if (descriptor == null) throw new IllegalArgumentException("Descriptor required");
		if (constantEvaluator != null
				&& descriptor.kind() != GLNodeImplementationKind.PURE)
			throw new IllegalArgumentException("Only pure operations can be constant-folded");
		if (entries.putIfAbsent(descriptor.nodeTypeId(),
				new Entry(descriptor, Optional.ofNullable(constantEvaluator),
						Optional.ofNullable(latencyEstimator))) != null)
			throw new IllegalArgumentException("Implementation already registered: "
					+ descriptor.nodeTypeId());
	}

	public synchronized Optional<Entry> find(String nodeTypeId) {
		return Optional.ofNullable(entries.get(nodeTypeId));
	}

	public synchronized boolean unregister(String nodeTypeId) {
		return entries.remove(nodeTypeId) != null;
	}

	public synchronized List<GLNodeImplementationDescriptor> descriptors() {
		return entries.values().stream().map(Entry::descriptor).toList();
	}

	public record Entry(GLNodeImplementationDescriptor descriptor,
			Optional<GLNodeConstantEvaluator> constantEvaluator,
			Optional<GLNodeLatencyEstimator> latencyEstimator) {
		public Entry {
			if (descriptor == null || constantEvaluator == null
					|| latencyEstimator == null)
				throw new IllegalArgumentException("Complete implementation entry required");
		}
	}
}
