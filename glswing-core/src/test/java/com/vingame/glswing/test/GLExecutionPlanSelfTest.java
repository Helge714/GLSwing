package com.vingame.glswing.test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.composite.GLCompositeExpansionLimits;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.execution.GLExecutionBackendCompatibility;
import com.vingame.glswing.node.execution.GLExecutionBackendDescriptor;
import com.vingame.glswing.node.execution.GLExecutionPlan;
import com.vingame.glswing.node.execution.GLExecutionPlanCodec;
import com.vingame.glswing.node.execution.GLExecutionPlanCompiler;
import com.vingame.glswing.node.execution.GLExecutionPlanController;
import com.vingame.glswing.node.execution.GLExecutionPlanOptions;
import com.vingame.glswing.node.execution.GLExecutionPlanSwapResult;
import com.vingame.glswing.node.execution.GLExecutionStorage;
import com.vingame.glswing.node.execution.GLNodeImplementationDescriptor;
import com.vingame.glswing.node.execution.GLNodeImplementationKind;
import com.vingame.glswing.node.execution.GLNodeImplementationRegistry;
import com.vingame.glswing.node.execution.java.GLJavaExecutionBackend;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortTiming;

/** Deterministic compiler, optimizer, state, and Java-backend checks. */
public final class GLExecutionPlanSelfTest {
	private GLExecutionPlanSelfTest() { }

	public static void main(String[] args) throws Exception {
		constantFoldingAndDeadCode();
		temporaryReuse();
		latencyPropagation();
		previousStepState();
		globalPreviousStepScheduling();
		planExchangeAndCompatibility();
		structuredBackendFailure();
		atomicPlanReplacement();
		missingImplementation();
		mutedNodeBypass();
		System.out.println("GL execution-plan self-test passed.");
	}

	private static void mutedNodeBypass() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId source = id(90), muted = id(91), sink = id(92);
		GLNodeGraphSnapshot graph = graph(List.of(
				new GLGraphNode(source, "test.source"),
				new GLGraphNode(muted, "test.identity",
						Map.of("glswing.muted", GLNodeValue.of(true))),
				new GLGraphNode(sink, "test.sink")), List.of(
					edge(source, "value", muted, "value"),
					edge(muted, "result", sink, "value")));
		GLExecutionPlan plan = GLExecutionPlanCompiler.compile(graph, types,
				implementations, GLGraphCyclePolicy.ACYCLIC).plan().orElseThrow();
		check(plan.operations().stream().noneMatch(operation ->
				operation.nodeId().equals(muted)),
				"Muted node remained in the executable schedule");
		check(plan.operations().stream().filter(operation ->
				operation.nodeId().equals(sink)).findFirst().orElseThrow()
				.inputs().size() == 1,
				"Muted node did not bypass its input to its output consumer");
	}

	private static void constantFoldingAndDeadCode() throws Exception {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId one = id(1), two = id(2), add = id(3), sink = id(4), dead = id(5);
		GLNodeGraphSnapshot graph = graph(List.of(
				constant(one, 1), constant(two, 2), new GLGraphNode(add, "math.add"),
				new GLGraphNode(sink, "test.sink"), constant(dead, 99)),
				List.of(edge(one, "value", add, "left"),
						edge(two, "value", add, "right"),
						edge(add, "result", sink, "value")));
		var compiled = GLExecutionPlanCompiler.compile(graph, types, implementations,
				GLGraphCyclePolicy.ACYCLIC);
		check(compiled.isValid(), "Valid graph did not compile");
		GLExecutionPlan plan = compiled.plan().orElseThrow();
		check(plan.statistics().retainedNodes() == 4
				&& plan.statistics().foldedOperations() == 3
				&& plan.operations().size() == 1,
				"Dead-code removal or constant folding is incorrect");
		check(plan.values().stream().filter(value ->
				value.storage() == GLExecutionStorage.CONSTANT).count() == 3,
				"Folded outputs were not represented as constants");
		var repeated = GLExecutionPlanCompiler.compile(graph, types, implementations,
				GLGraphCyclePolicy.ACYCLIC).plan().orElseThrow();
		check(repeated.hash().equals(plan.hash()) && repeated.equals(plan),
				"Equivalent input did not produce a deterministic plan");
		GLNodeGraphAsset asset = new GLNodeGraphAsset("test.execution", 1,
				GLNodeGraphAssetKind.PROJECT, graph,
				new GLNodeEditorViewState(Map.of()));
		var expanded = GLExecutionPlanCompiler.compile(asset,
				new GLCompositeGraphRegistry(), GLCompositeExpansionLimits.DEFAULT,
				types, implementations, GLGraphCyclePolicy.ACYCLIC);
		check(expanded.isValid() && expanded.origins().size() == 4
				&& expanded.origin(add).isPresent(),
				"Asset compilation did not retain flattened origin information");

		AtomicReference<GLNodeValue> observed = new AtomicReference<>();
		GLJavaExecutionBackend backend = new GLJavaExecutionBackend();
		backend.register("test.sink", (context, inputs, properties) -> {
			observed.set(inputs.get("value").get(0));
			return Map.of();
		});
		var executable = backend.prepare(plan);
		check(executable.nextOperation().orElseThrow().nodeId().equals(sink),
				"Incremental execution did not expose the next operation");
		var progress = executable.executeNext();
		check(progress.isStepComplete() && progress.trace().isPresent(),
				"Single-operation plan did not complete incrementally");
		var trace = progress.trace().orElseThrow();
		check(trace.operation().nodeId().equals(sink)
				&& trace.inputs().get("value").equals(List.of(GLNodeValue.of(3L)))
				&& progress.completedStep().orElseThrow().traces().equals(List.of(trace)),
				"Incremental execution trace lost operation values");
		check(GLNodeValue.of(3L).equals(observed.get()),
				"Reference backend did not consume the folded value");
	}

	private static void temporaryReuse() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId source = id(10), first = id(11), second = id(12), sink = id(13);
		GLNodeGraphSnapshot graph = graph(List.of(
				new GLGraphNode(source, "test.source"),
				new GLGraphNode(first, "test.identity"),
				new GLGraphNode(second, "test.identity"),
				new GLGraphNode(sink, "test.sink")),
				List.of(edge(source, "value", first, "value"),
						edge(first, "result", second, "value"),
						edge(second, "result", sink, "value")));
		GLExecutionPlan plan = GLExecutionPlanCompiler.compile(graph, types,
				implementations, GLGraphCyclePolicy.ACYCLIC).plan().orElseThrow();
		check(plan.statistics().temporarySlots() == 2,
				"Non-overlapping temporary lifetimes were not reused");
	}

	private static void previousStepState() throws Exception {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId delay = id(20), sink = id(21);
		GLNodeGraphSnapshot graph = graph(List.of(
				new GLGraphNode(delay, "test.delay",
						Map.of("value", GLNodeValue.of(7L))),
				new GLGraphNode(sink, "test.sink")),
				List.of(edge(delay, "previous", sink, "value")));
		GLExecutionPlan plan = GLExecutionPlanCompiler.compile(graph, types,
				implementations, GLGraphCyclePolicy.DELAYED_FEEDBACK)
				.plan().orElseThrow();
		check(plan.values().stream().anyMatch(value ->
				value.storage() == GLExecutionStorage.STATE),
				"Previous-step output did not receive persistent state storage");
		AtomicReference<GLNodeValue> observed = new AtomicReference<>();
		GLJavaExecutionBackend backend = new GLJavaExecutionBackend();
		backend.register("test.delay", (context, inputs, properties) ->
				Map.of("previous", properties.get("value")));
		backend.register("test.sink", (context, inputs, properties) -> {
			observed.set(inputs.get("value").get(0)); return Map.of();
		});
		var executable = backend.prepare(plan);
		executable.evaluate();
		check(GLNodeValue.NullValue.INSTANCE.equals(observed.get()),
				"First previous-step read was not empty");
		executable.evaluate();
		check(GLNodeValue.of(7L).equals(observed.get()),
				"Previous-step state was not advanced atomically");
	}

	private static void globalPreviousStepScheduling() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId first = id(22), second = id(23);
		GLNodeGraphSnapshot graph = graph(List.of(
				new GLGraphNode(first, "test.identity"),
				new GLGraphNode(second, "test.identity")),
				List.of(edge(first, "result", second, "value"),
						edge(second, "result", first, "value")));
		var compiled = GLExecutionPlanCompiler.compile(graph, types,
				implementations, GLGraphCyclePolicy.PREVIOUS_STEP);
		check(compiled.isValid(),
				"Global previous-step policy did not compile a feedback cycle");
		GLExecutionPlan plan = compiled.plan().orElseThrow();
		check(plan.operations().stream().flatMap(operation ->
				operation.inputs().stream()).allMatch(input ->
						input.timing() == GLPortTiming.PREVIOUS_STEP),
				"Global previous-step policy retained a current-step input");
		check(plan.values().stream().allMatch(value ->
				value.storage() == GLExecutionStorage.STATE),
				"Global previous-step policy did not allocate persistent state");
	}

	private static void latencyPropagation() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId source = id(40), slow = id(41), add = id(42), sink = id(43);
		GLNodeGraphSnapshot graph = graph(List.of(
				new GLGraphNode(source, "test.source"),
				new GLGraphNode(slow, "test.identity",
						Map.of("latency", GLNodeValue.of(5L))),
				new GLGraphNode(add, "math.add"),
				new GLGraphNode(sink, "test.sink")),
				List.of(edge(source, "value", slow, "value"),
						edge(source, "value", add, "left"),
						edge(slow, "result", add, "right"),
						edge(add, "result", sink, "value")));
		GLExecutionPlan plan = GLExecutionPlanCompiler.compile(graph, types,
				implementations, GLGraphCyclePolicy.ACYCLIC,
				new GLExecutionPlanOptions("sample")).plan().orElseThrow();
		var addOperation = plan.operationsByNodeId().get(add);
		check(plan.latencyUnitId().equals("sample")
				&& addOperation.processingLatency() == 1
				&& addOperation.outputLatency() == 8
				&& plan.statistics().maximumLatency() == 8,
				"Feed-forward latency was not propagated");
		check(addOperation.inputs().stream().filter(input ->
				input.portId().equals("left")).findFirst().orElseThrow()
				.compensationLatency() == 5
				&& addOperation.inputs().stream().filter(input ->
						input.portId().equals("right")).findFirst().orElseThrow()
						.compensationLatency() == 0,
				"Parallel input paths were not aligned to the slowest arrival");
	}

	private static void missingImplementation() {
		GLNodeTypeRegistry types = types();
		var result = GLExecutionPlanCompiler.compile(graph(
				List.of(new GLGraphNode(id(30), "test.source")), List.of()), types,
				new GLNodeImplementationRegistry(), GLGraphCyclePolicy.ACYCLIC);
		check(!result.isValid() && result.diagnostics().stream().anyMatch(diagnostic ->
				diagnostic.code().equals("execution.missing-implementation")),
				"Missing implementation was not diagnosed");
	}

	private static void planExchangeAndCompatibility() throws Exception {
		GLExecutionPlan plan = foldedPlan();
		byte[] encoded = GLExecutionPlanCodec.encode(plan);
		check(GLExecutionPlanCodec.decode(encoded).equals(plan),
				"Execution-plan exchange did not round-trip exactly");
		encoded[encoded.length - 1] ^= 1;
		try {
			GLExecutionPlanCodec.decode(encoded);
			throw new AssertionError("Corrupted execution plan was accepted");
		} catch (IOException expected) { }

		GLExecutionBackendDescriptor incompatible = new GLExecutionBackendDescriptor(
				"test-backend", GLExecutionPlan.FORMAT_VERSION,
				GLExecutionPlan.FORMAT_VERSION, Set.of("test.sink"), Set.of("sample"),
				true);
		GLExecutionBackendCompatibility compatibility =
				GLExecutionBackendCompatibility.check(plan, incompatible);
		check(!compatibility.isCompatible() && compatibility.diagnostics().stream()
				.anyMatch(value -> value.code().equals("backend.latency-unit")),
				"Unsupported latency unit was not rejected before preparation");
	}

	private static void atomicPlanReplacement() throws Exception {
		GLExecutionPlan plan = foldedPlan();
		GLJavaExecutionBackend backend = new GLJavaExecutionBackend();
		backend.register("test.sink", (context, inputs, properties) -> Map.of());
		ExecutorService executor = Executors.newSingleThreadExecutor();
		AtomicInteger disposed = new AtomicInteger();
		GLExecutionPlanController<com.vingame.glswing.node.execution.java.GLJavaExecutablePlan>
				controller = new GLExecutionPlanController<>(backend, executor,
						ignored -> disposed.incrementAndGet());
		try {
			check(controller.prepareAndSwap(plan).toCompletableFuture().get().status()
					== GLExecutionPlanSwapResult.Status.ACTIVATED,
					"First prepared plan was not activated");
			var lease = controller.acquire().orElseThrow();
			check(controller.prepareAndSwap(plan).toCompletableFuture().get().status()
					== GLExecutionPlanSwapResult.Status.ACTIVATED && disposed.get() == 0,
					"A leased retired plan was disposed during replacement");
			String activeHash = controller.currentPlan().orElseThrow().hash();
			check(controller.prepareAndSwap(runtimePlan()).toCompletableFuture().get().status()
					== GLExecutionPlanSwapResult.Status.REJECTED
					&& controller.currentPlan().orElseThrow().hash().equals(activeHash),
					"Rejected replacement did not retain the previous working plan");
			lease.close();
			check(disposed.get() == 1,
					"Retired plan was not disposed after its final lease");
		} finally {
			controller.close();
			executor.shutdownNow();
		}
		check(disposed.get() == 2, "Active plan was not disposed at shutdown");
	}

	private static void structuredBackendFailure() throws Exception {
		GLJavaExecutionBackend backend = new GLJavaExecutionBackend();
		backend.register("test.sink", (context, inputs, properties) -> {
			throw new IOException("Fixture failure");
		});
		var executable = backend.prepare(foldedPlan());
		try {
			executable.evaluate();
			throw new AssertionError("Backend operation failure was ignored");
		} catch (com.vingame.glswing.node.execution.java.GLJavaExecutionException failure) {
			check(failure.diagnostic().code().equals("backend.execution")
					&& failure.diagnostic().nodeId().isPresent()
					&& failure.diagnostic().origin().isPresent(),
					"Runtime diagnostic lost its node or composite origin");
			check(!executable.isStepInProgress(),
					"A failed operation retained partial execution state");
		}
	}

	private static GLExecutionPlan foldedPlan() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId one = id(60), two = id(61), add = id(62), sink = id(63);
		Map<String, GLNodeValue> metadata = Map.of("metadata",
				new GLNodeValue.MapValue(Map.of("items", new GLNodeValue.ListValue(List.of(
						GLNodeValue.NullValue.INSTANCE, GLNodeValue.of(true),
						GLNodeValue.of(1.25), GLNodeValue.of("text"))))));
		GLNodeGraphSnapshot graph = graph(List.of(constant(one, 1),
				constant(two, 2), new GLGraphNode(add, "math.add"),
				new GLGraphNode(sink, "test.sink", metadata)),
				List.of(edge(one, "value", add, "left"),
						edge(two, "value", add, "right"),
						edge(add, "result", sink, "value")));
		GLNodeGraphAsset asset = new GLNodeGraphAsset("test.exchange", 1,
				GLNodeGraphAssetKind.PROJECT, graph, new GLNodeEditorViewState(Map.of()));
		return GLExecutionPlanCompiler.compile(asset, new GLCompositeGraphRegistry(),
				GLCompositeExpansionLimits.DEFAULT, types, implementations,
				GLGraphCyclePolicy.ACYCLIC).plan().orElseThrow();
	}

	private static GLExecutionPlan runtimePlan() {
		GLNodeTypeRegistry types = types();
		GLNodeImplementationRegistry implementations = implementations();
		GLNodeId source = id(70), sink = id(71);
		return GLExecutionPlanCompiler.compile(graph(List.of(
				new GLGraphNode(source, "test.source"),
				new GLGraphNode(sink, "test.sink")),
				List.of(edge(source, "value", sink, "value"))), types,
				implementations, GLGraphCyclePolicy.ACYCLIC).plan().orElseThrow();
	}

	private static GLNodeTypeRegistry types() {
		GLNodeTypeRegistry types = new GLNodeTypeRegistry();
		types.register(new GLNodeTypeDescriptor("test.constant",
				GLNodePortDescriptor.output("value", "number")));
		types.register(new GLNodeTypeDescriptor("math.add",
				GLNodePortDescriptor.input("left", "number", true, 1),
				GLNodePortDescriptor.input("right", "number", true, 1),
				GLNodePortDescriptor.output("result", "number")));
		types.register(new GLNodeTypeDescriptor("test.source",
				GLNodePortDescriptor.output("value", "number")));
		types.register(new GLNodeTypeDescriptor("test.identity",
				GLNodePortDescriptor.input("value", "number", true, 1),
				GLNodePortDescriptor.output("result", "number")));
		types.register(new GLNodeTypeDescriptor("test.delay",
				GLNodePortDescriptor.output("previous", "number",
						GLPortTiming.PREVIOUS_STEP, Integer.MAX_VALUE)));
		types.register(new GLNodeTypeDescriptor("test.sink",
				GLNodePortDescriptor.input("value", "number", true, 1)));
		return types;
	}

	private static GLNodeImplementationRegistry implementations() {
		GLNodeImplementationRegistry result = new GLNodeImplementationRegistry();
		result.register(new GLNodeImplementationDescriptor("test.constant",
				"test.constant", GLNodeImplementationKind.PURE),
				(properties, inputs) -> Map.of("value", properties.get("value")));
		result.register(new GLNodeImplementationDescriptor("math.add", "math.add",
				GLNodeImplementationKind.PURE, false, 1, 1),
				(properties, inputs) -> Map.of("result",
					GLNodeValue.of(integer(inputs, "left") + integer(inputs, "right"))));
		result.register(new GLNodeImplementationDescriptor("test.source",
				"test.source", GLNodeImplementationKind.PURE, false, 1, 2));
		result.registerWithLatency(new GLNodeImplementationDescriptor("test.identity",
				"test.identity", GLNodeImplementationKind.PURE), properties -> {
			GLNodeValue value = properties.get("latency");
			return value instanceof GLNodeValue.IntegerValue integer ? integer.value() : 0;
		});
		result.register(new GLNodeImplementationDescriptor("test.delay",
				"test.delay", GLNodeImplementationKind.STATEFUL));
		result.register(new GLNodeImplementationDescriptor("test.sink", "test.sink",
				GLNodeImplementationKind.EFFECT));
		return result;
	}

	private static long integer(Map<String, List<GLNodeValue>> values, String key) {
		return ((GLNodeValue.IntegerValue) values.get(key).get(0)).value();
	}
	private static GLGraphNode constant(GLNodeId id, long value) {
		return new GLGraphNode(id, "test.constant", Map.of("value", GLNodeValue.of(value)));
	}
	private static GLNodeGraphSnapshot graph(List<GLGraphNode> nodes,
			List<GLGraphConnection> connections) {
		return new GLNodeGraphSnapshot(new GLNodeGraphId(new UUID(0, 100)), 0,
				nodes, connections);
	}
	private static GLGraphConnection edge(GLNodeId source, String output,
			GLNodeId target, String input) {
		return new GLGraphConnection(new GLNodeConnectionId(UUID.nameUUIDFromBytes(
				(source + output + target + input).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
				new GLNodeEndpoint(source, output), new GLNodeEndpoint(target, input));
	}
	private static GLNodeId id(long value) { return new GLNodeId(new UUID(0, value)); }
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
