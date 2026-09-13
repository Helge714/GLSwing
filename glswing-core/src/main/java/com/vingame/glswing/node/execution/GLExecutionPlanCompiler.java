package com.vingame.glswing.node.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.composite.GLCompositeExpansionLimits;
import com.vingame.glswing.node.composite.GLCompositeGraphFlattener;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphDiagnostic;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeGraphValidator;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.node.model.GLPortTiming;

/** Compiles a validated leaf graph into a deterministic execution schedule. */
public final class GLExecutionPlanCompiler {
	private static final Comparator<GLNodeId> NODE_ORDER =
			Comparator.comparing(GLNodeId::toString);

	private GLExecutionPlanCompiler() { }

	/** Expands inline composites transactionally, then compiles the leaf graph. */
	public static GLExecutionPlanResult compile(GLNodeGraphAsset root,
			GLCompositeGraphRegistry composites, GLCompositeExpansionLimits limits,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy) {
		return compile(root, composites, limits, types, implementations, cyclePolicy,
				GLExecutionPlanOptions.DEFAULT);
	}

	public static GLExecutionPlanResult compile(GLNodeGraphAsset root,
			GLCompositeGraphRegistry composites, GLCompositeExpansionLimits limits,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy, GLExecutionPlanOptions options) {
		if (root == null || composites == null || limits == null)
			throw new IllegalArgumentException("Root asset, composites, and limits required");
		var flattened = GLCompositeGraphFlattener.flatten(root, composites, limits);
		if (!flattened.isValid()) return failed(flattened.diagnostics());
		return compile(flattened.graph().orElseThrow(), types, implementations,
				cyclePolicy, flattened.origins(), options);
	}

	public static GLExecutionPlanResult compile(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy) {
		return compile(graph, types, implementations, cyclePolicy, Map.of(),
				GLExecutionPlanOptions.DEFAULT);
	}

	public static GLExecutionPlanResult compile(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy, GLExecutionPlanOptions options) {
		return compile(graph, types, implementations, cyclePolicy, Map.of(), options);
	}

	/**
	 * Compiles a graph and preserves optional flattened-composite origins for
	 * backend diagnostics and profiling.
	 */
	public static GLExecutionPlanResult compile(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy,
			Map<GLNodeId, GLCompositeOrigin> origins) {
		return compile(graph, types, implementations, cyclePolicy, origins,
				GLExecutionPlanOptions.DEFAULT);
	}

	public static GLExecutionPlanResult compile(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, GLNodeImplementationRegistry implementations,
			GLGraphCyclePolicy cyclePolicy,
			Map<GLNodeId, GLCompositeOrigin> origins,
			GLExecutionPlanOptions options) {
		if (graph == null || types == null || implementations == null
				|| cyclePolicy == null || origins == null || options == null)
			throw new IllegalArgumentException("Complete compilation input required");
		graph = GLMutedGraphResolver.resolve(graph, types);
		List<GLGraphDiagnostic> diagnostics = new ArrayList<>(
				GLNodeGraphValidator.validate(graph, types, cyclePolicy).diagnostics());
		Map<GLNodeId, GLNodeImplementationRegistry.Entry> entries = new LinkedHashMap<>();
		for (GLGraphNode node : graph.nodes()) {
			var entry = implementations.find(node.typeId());
			if (entry.isEmpty()) diagnostics.add(error("execution.missing-implementation",
					"No executable implementation is registered for " + node.typeId(), node.id()));
			else entries.put(node.id(), entry.get());
		}
		if (hasErrors(diagnostics)) return failed(diagnostics);

		Set<GLNodeId> retained = retainedNodes(graph, entries);
		List<GLNodeId> schedule = schedule(graph, types, retained, cyclePolicy);
		if (schedule.size() != retained.size()) {
			diagnostics.add(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR,
					"execution.schedule-cycle",
					"Current-step dependencies cannot be scheduled", retained, Set.of()));
			return failed(diagnostics);
		}

		Map<GLNodeId, GLGraphNode> nodes = graph.nodesById();
		Map<String, GLNodeTypeDescriptor> typeMap = new LinkedHashMap<>();
		for (GLNodeTypeDescriptor type : types.descriptors()) typeMap.put(type.id(), type);
		Map<GLNodeEndpoint, GLExecutionValueRef> refs = new HashMap<>();
		Map<GLExecutionValueRef, GLNodeValue> constants = new HashMap<>();
		Map<GLExecutionValueRef, Long> latencies = new HashMap<>();
		List<ValueDraft> valueDrafts = new ArrayList<>();
		List<OperationDraft> operationDrafts = new ArrayList<>();
		int folded = 0;
		int nextRef = 0;
		for (GLNodeId nodeId : schedule) {
			GLGraphNode node = nodes.get(nodeId);
			for (GLNodePortDescriptor port : typeMap.get(node.typeId()).ports())
				if (port.direction() == GLPortDirection.OUTPUT)
					refs.put(new GLNodeEndpoint(nodeId, port.id()),
							new GLExecutionValueRef(nextRef++));
		}

		for (GLNodeId nodeId : schedule) {
			GLGraphNode node = nodes.get(nodeId);
			GLNodeTypeDescriptor type = typeMap.get(node.typeId());
			GLNodeImplementationRegistry.Entry entry = entries.get(nodeId);
			List<GLExecutionInput> rawInputs = inputs(graph, retained, nodeId, refs,
					typeMap, cyclePolicy);
			boolean allConstant = rawInputs.stream().allMatch(input ->
					input.timing() == GLPortTiming.CURRENT && constants.containsKey(input.value()));
			Map<String, List<GLNodeValue>> constantInputs = new LinkedHashMap<>();
			if (allConstant) for (GLExecutionInput input : rawInputs)
				constantInputs.computeIfAbsent(input.portId(), ignored -> new ArrayList<>())
						.add(constants.get(input.value()));

			Map<String, GLNodeValue> foldedOutputs = Map.of();
			if (entry.descriptor().kind() == GLNodeImplementationKind.PURE
					&& allConstant && entry.constantEvaluator().isPresent()) {
				try {
					Map<String, GLNodeValue> evaluated = entry.constantEvaluator().get()
							.evaluate(node.properties(), immutableLists(constantInputs));
					if (evaluated != null) foldedOutputs = Map.copyOf(evaluated);
				} catch (Exception exception) {
					diagnostics.add(error("execution.constant-folding",
							"Constant evaluation failed for " + node.typeId() + ": "
									+ usefulMessage(exception), nodeId));
				}
			}
			boolean foldedNode = !foldedOutputs.isEmpty();
			long processingLatency = foldedNode ? 0
					: processingLatency(entry, node, diagnostics);
			long inputLatency = rawInputs.stream()
					.filter(input -> input.timing() == GLPortTiming.CURRENT)
					.mapToLong(input -> latencies.getOrDefault(input.value(), 0L))
					.max().orElse(0);
			List<GLExecutionInput> alignedInputs = alignInputs(rawInputs, latencies,
					inputLatency);
			long outputLatency = foldedNode ? 0
					: saturatedAdd(inputLatency, processingLatency);

			Map<String, GLExecutionValueRef> outputs = new LinkedHashMap<>();
			for (GLNodePortDescriptor port : type.ports()) {
				if (port.direction() != GLPortDirection.OUTPUT) continue;
				GLExecutionValueRef ref = refs.get(new GLNodeEndpoint(nodeId, port.id()));
				outputs.put(port.id(), ref);
				GLNodeValue constant = foldedOutputs.get(port.id());
				if (!foldedOutputs.isEmpty() && constant == null)
					diagnostics.add(error("execution.constant-output",
							"Constant evaluator did not produce output " + port.id(), nodeId));
				if (constant != null) constants.put(ref, constant);
				latencies.put(ref, outputLatency);
				valueDrafts.add(new ValueDraft(ref, port.valueTypeId(),
						effectiveTiming(port.timing(), cyclePolicy),
						constant, nodeId, port.id(), operationDrafts.size(), outputLatency));
			}
			if (!foldedOutputs.isEmpty()) {
				for (String output : foldedOutputs.keySet())
					if (!outputs.containsKey(output)) diagnostics.add(error(
							"execution.constant-output", "Constant evaluator produced unknown output "
									+ output, nodeId));
				folded++;
			} else {
				operationDrafts.add(new OperationDraft(node, entry.descriptor(), alignedInputs,
						outputs, processingLatency, outputLatency,
						Optional.ofNullable(origins.get(nodeId))));
			}
		}
		if (hasErrors(diagnostics)) return failed(diagnostics);

		List<GLExecutionValue> values = allocate(valueDrafts, operationDrafts);
		List<GLExecutionOperation> operations = new ArrayList<>();
		long cost = 0;
		for (int index = 0; index < operationDrafts.size(); index++) {
			OperationDraft draft = operationDrafts.get(index);
			cost = saturatedAdd(cost, draft.implementation.estimatedCost());
			operations.add(new GLExecutionOperation(index, draft.node.id(),
					draft.node.typeId(), draft.implementation.operationTypeId(),
					draft.implementation.kind(), draft.node.properties(), draft.inputs,
					draft.outputs, draft.implementation.estimatedCost(),
					draft.processingLatency, draft.outputLatency, draft.origin));
		}
		int temporarySlots = values.stream()
				.filter(value -> value.storage() == GLExecutionStorage.TEMPORARY)
				.mapToInt(GLExecutionValue::storageSlot).max().orElse(-1) + 1;
		int stateSlots = values.stream()
				.filter(value -> value.storage() == GLExecutionStorage.STATE)
				.mapToInt(GLExecutionValue::storageSlot).max().orElse(-1) + 1;
		long maximumLatency = values.stream().mapToLong(GLExecutionValue::latency)
				.max().orElse(0);
		GLExecutionPlanStatistics statistics = new GLExecutionPlanStatistics(
				graph.nodes().size(), retained.size(), folded, operations.size(), cost,
				temporarySlots, stateSlots, maximumLatency);
		String hash = hash(graph, operations, values, options.latencyUnitId());
		Map<GLNodeId, GLCompositeOrigin> retainedOrigins = new LinkedHashMap<>();
		retained.stream().sorted(NODE_ORDER).forEach(nodeId -> {
			GLCompositeOrigin origin = origins.get(nodeId);
			if (origin != null) retainedOrigins.put(nodeId, origin);
		});
		return new GLExecutionPlanResult(Optional.of(new GLExecutionPlan(graph.graphId(),
				graph.revision(), operations, values, options.latencyUnitId(), hash,
				statistics)), diagnostics,
				retainedOrigins);
	}

	private static Set<GLNodeId> retainedNodes(GLNodeGraphSnapshot graph,
			Map<GLNodeId, GLNodeImplementationRegistry.Entry> entries) {
		LinkedHashSet<GLNodeId> roots = new LinkedHashSet<>();
		for (GLGraphNode node : graph.nodes())
			if (entries.get(node.id()).descriptor().observable()) roots.add(node.id());
		if (roots.isEmpty()) return new LinkedHashSet<>(graph.nodesById().keySet());
		Map<GLNodeId, List<GLNodeId>> predecessors = new HashMap<>();
		for (GLGraphConnection connection : graph.connections())
			predecessors.computeIfAbsent(connection.input().nodeId(), ignored -> new ArrayList<>())
					.add(connection.output().nodeId());
		Deque<GLNodeId> pending = new ArrayDeque<>(roots);
		while (!pending.isEmpty()) {
			GLNodeId node = pending.removeFirst();
			for (GLNodeId predecessor : predecessors.getOrDefault(node, List.of()))
				if (roots.add(predecessor)) pending.addLast(predecessor);
		}
		return roots;
	}

	private static List<GLNodeId> schedule(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, Set<GLNodeId> retained,
			GLGraphCyclePolicy cyclePolicy) {
		Map<GLNodeId, Integer> degree = new HashMap<>();
		Map<GLNodeId, List<GLNodeId>> outgoing = new HashMap<>();
		for (GLNodeId id : retained) degree.put(id, 0);
		for (GLGraphConnection connection : graph.connections()) {
			if (!retained.contains(connection.output().nodeId())
					|| !retained.contains(connection.input().nodeId())) continue;
			GLGraphNode source = graph.findNode(connection.output().nodeId()).orElseThrow();
			GLPortTiming timing = effectiveTiming(types.find(source.typeId()).orElseThrow()
					.findPort(connection.output().portId()).orElseThrow().timing(), cyclePolicy);
			if (timing == GLPortTiming.PREVIOUS_STEP) continue;
			outgoing.computeIfAbsent(source.id(), ignored -> new ArrayList<>())
					.add(connection.input().nodeId());
			degree.merge(connection.input().nodeId(), 1, Integer::sum);
		}
		PriorityQueue<GLNodeId> ready = new PriorityQueue<>(NODE_ORDER);
		degree.forEach((node, value) -> { if (value == 0) ready.add(node); });
		List<GLNodeId> result = new ArrayList<>();
		while (!ready.isEmpty()) {
			GLNodeId node = ready.remove();
			result.add(node);
			List<GLNodeId> targets = new ArrayList<>(outgoing.getOrDefault(node, List.of()));
			targets.sort(NODE_ORDER);
			for (GLNodeId target : targets)
				if (degree.merge(target, -1, Integer::sum) == 0) ready.add(target);
		}
		return result;
	}

	private static List<GLExecutionInput> inputs(GLNodeGraphSnapshot graph,
			Set<GLNodeId> retained, GLNodeId nodeId,
			Map<GLNodeEndpoint, GLExecutionValueRef> refs,
			Map<String, GLNodeTypeDescriptor> types,
			GLGraphCyclePolicy cyclePolicy) {
		List<GLGraphConnection> incoming = graph.connections().stream()
				.filter(connection -> retained.contains(connection.output().nodeId())
						&& connection.input().nodeId().equals(nodeId))
				.sorted(Comparator.comparing((GLGraphConnection connection) ->
						connection.input().portId()).thenComparing(connection -> connection.id().toString()))
				.toList();
		List<GLExecutionInput> result = new ArrayList<>();
		for (GLGraphConnection connection : incoming) {
			GLExecutionValueRef ref = refs.get(connection.output());
			if (ref == null) throw new IllegalStateException("Producer was not scheduled");
			GLGraphNode source = graph.findNode(connection.output().nodeId()).orElseThrow();
			GLPortTiming timing = effectiveTiming(types.get(source.typeId())
					.findPort(connection.output().portId()).orElseThrow().timing(), cyclePolicy);
			result.add(new GLExecutionInput(connection.input().portId(), ref, timing));
		}
		return result;
	}
	private static GLPortTiming effectiveTiming(GLPortTiming declared,
			GLGraphCyclePolicy cyclePolicy) {
		return cyclePolicy == GLGraphCyclePolicy.PREVIOUS_STEP
				? GLPortTiming.PREVIOUS_STEP : declared;
	}

	private static List<GLExecutionValue> allocate(List<ValueDraft> drafts,
			List<OperationDraft> operations) {
		Map<GLExecutionValueRef, Integer> lastUses = new HashMap<>();
		for (int operation = 0; operation < operations.size(); operation++)
			for (GLExecutionInput input : operations.get(operation).inputs)
				if (input.timing() == GLPortTiming.CURRENT)
					lastUses.merge(input.value(), operation, Math::max);
		List<GLExecutionValue> result = new ArrayList<>();
		List<Integer> available = new ArrayList<>();
		Map<GLExecutionValueRef, Integer> activeSlots = new HashMap<>();
		int nextTemporary = 0, nextState = 0;
		for (ValueDraft draft : drafts) {
			if (draft.constant != null) {
				result.add(new GLExecutionValue(draft.ref, draft.valueType,
						GLExecutionStorage.CONSTANT, -1, Optional.of(draft.constant),
						draft.nodeId, draft.portId, draft.latency));
				continue;
			}
			if (draft.timing == GLPortTiming.PREVIOUS_STEP) {
				result.add(new GLExecutionValue(draft.ref, draft.valueType,
						GLExecutionStorage.STATE, nextState++, Optional.empty(),
						draft.nodeId, draft.portId, draft.latency));
				continue;
			}
			for (ValueDraft previous : drafts) {
				if (previous == draft) break;
				Integer slot = activeSlots.get(previous.ref);
				if (slot != null && lastUses.getOrDefault(previous.ref,
						previous.producerOperation) < draft.producerOperation) {
					activeSlots.remove(previous.ref);
					if (!available.contains(slot)) available.add(slot);
				}
			}
			available.sort(Integer::compareTo);
			int slot = available.isEmpty() ? nextTemporary++ : available.remove(0);
			activeSlots.put(draft.ref, slot);
			result.add(new GLExecutionValue(draft.ref, draft.valueType,
					GLExecutionStorage.TEMPORARY, slot, Optional.empty(),
					draft.nodeId, draft.portId, draft.latency));
		}
		return result;
	}

	private static String hash(GLNodeGraphSnapshot graph,
			List<GLExecutionOperation> operations, List<GLExecutionValue> values,
			String latencyUnitId) {
		StringBuilder canonical = new StringBuilder(graph.graphId().toString())
				.append('|').append(latencyUnitId).append('\n');
		for (GLExecutionOperation operation : operations) {
			canonical.append(operation.nodeId()).append('|').append(operation.nodeTypeId())
					.append('|').append(operation.operationTypeId()).append('|')
					.append(operation.kind()).append('|');
			operation.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(entry -> canonical.append(entry.getKey()).append('=')
							.append(entry.getValue()).append(';'));
			for (GLExecutionInput input : operation.inputs()) canonical.append("in:")
					.append(input.portId()).append('=').append(input.value().index())
					.append('@').append(input.timing()).append('+')
					.append(input.compensationLatency()).append(';');
			operation.outputs().forEach((port, ref) -> canonical.append("out:")
					.append(port).append('=').append(ref.index()).append(';'));
			canonical.append("latency:").append(operation.processingLatency())
					.append('/').append(operation.outputLatency()).append('\n');
		}
		for (GLExecutionValue value : values) canonical.append(value.ref().index())
				.append('|').append(value.valueTypeId()).append('|').append(value.storage())
				.append('|').append(value.storageSlot()).append('|')
				.append(value.constant()).append('|').append(value.latency()).append('\n');
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static Map<String, List<GLNodeValue>> immutableLists(
			Map<String, List<GLNodeValue>> source) {
		Map<String, List<GLNodeValue>> result = new LinkedHashMap<>();
		source.forEach((key, values) -> result.put(key, List.copyOf(values)));
		return Map.copyOf(result);
	}
	private static List<GLExecutionInput> alignInputs(
			List<GLExecutionInput> inputs,
			Map<GLExecutionValueRef, Long> latencies, long targetLatency) {
		List<GLExecutionInput> result = new ArrayList<>(inputs.size());
		for (GLExecutionInput input : inputs) {
			long compensation = input.timing() == GLPortTiming.PREVIOUS_STEP ? 0
					: targetLatency - latencies.getOrDefault(input.value(), 0L);
			result.add(new GLExecutionInput(input.portId(), input.value(), input.timing(),
					compensation));
		}
		return List.copyOf(result);
	}
	private static long processingLatency(GLNodeImplementationRegistry.Entry entry,
			GLGraphNode node, List<GLGraphDiagnostic> diagnostics) {
		long latency = entry.descriptor().fixedLatency();
		if (entry.latencyEstimator().isEmpty()) return latency;
		try {
			latency = entry.latencyEstimator().get().estimate(node.properties());
			if (latency < 0) throw new IllegalArgumentException(
					"Estimated latency must not be negative");
			return latency;
		} catch (Exception exception) {
			diagnostics.add(error("execution.latency",
					"Latency evaluation failed for " + node.typeId() + ": "
							+ usefulMessage(exception), node.id()));
			return 0;
		}
	}
	private static long saturatedAdd(long left, long right) {
		return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
	}
	private static String usefulMessage(Exception exception) {
		return exception.getMessage() == null ? exception.getClass().getSimpleName()
				: exception.getMessage();
	}
	private static boolean hasErrors(List<GLGraphDiagnostic> diagnostics) {
		return diagnostics.stream().anyMatch(value ->
				value.severity() == GLGraphDiagnostic.Severity.ERROR);
	}
	private static GLGraphDiagnostic error(String code, String message, GLNodeId node) {
		return new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR, code, message,
				Set.of(node), Set.of());
	}
	private static GLExecutionPlanResult failed(List<GLGraphDiagnostic> diagnostics) {
		return new GLExecutionPlanResult(Optional.empty(), diagnostics);
	}

	private record ValueDraft(GLExecutionValueRef ref, String valueType,
			GLPortTiming timing, GLNodeValue constant, GLNodeId nodeId, String portId,
			int producerOperation, long latency) { }
	private record OperationDraft(GLGraphNode node,
			GLNodeImplementationDescriptor implementation,
			List<GLExecutionInput> inputs, Map<String, GLExecutionValueRef> outputs,
			long processingLatency, long outputLatency,
			Optional<GLCompositeOrigin> origin) { }
}
