package com.vingame.glswing.test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphValidator;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodePropertySchemaRegistry;
import com.vingame.glswing.node.model.GLNumberNodeProperty;
import com.vingame.glswing.node.model.GLEnumNodeProperty;
import com.vingame.glswing.node.model.GLBooleanNodeProperty;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortTiming;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.composite.GLCompositeExtraction;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.document.GLNodeGraphDocument;

/** CPU-only checks for the rendering-independent node graph model. */
public final class GLNodeGraphModelSelfTest {
	private GLNodeGraphModelSelfTest() { }

	public static void main(String[] args) {
		testPortableValues();
		testAtomicGraphEdits();
		testReplacementAndCascadeRemoval();
		testCyclePoliciesAndPortTiming();
		testPortContracts();
		testNodeCatalog();
		testPropertySchemas();
		testCompositeExtraction();
		System.out.println("GL node graph model self-test passed.");
	}

	private static void testPropertySchemas() {
		GLNodePropertySchemaRegistry schemas = new GLNodePropertySchemaRegistry();
		GLNumberNodeProperty gain = new GLNumberNodeProperty("gain", "Gain",
				0, 1, .01, false);
		check(gain.editor() == GLNumberNodeProperty.Editor.SLIDER,
				"Numeric properties do not default to sliders");
		schemas.register("audio.gain", List.of(gain,
				new GLEnumNodeProperty("mode", "Mode", List.of("linear", "db")),
				new GLBooleanNodeProperty("muted", "Muted")));
		check(schemas.properties("audio.gain").size() == 3
				&& schemas.properties("missing").isEmpty(),
				"Property schema registry did not preserve explicit metadata");
		expect(IllegalArgumentException.class, () -> schemas.register("invalid",
				List.of(gain, gain)), "Duplicate property IDs were accepted");
	}

	private static void testCyclePoliciesAndPortTiming() {
		GLNodeTypeRegistry registry = new GLNodeTypeRegistry();
		registry.register(new GLNodeTypeDescriptor("test.current",
				GLNodePortDescriptor.input("in", "signal", true, 1),
				GLNodePortDescriptor.output("out", "signal")));
		registry.register(new GLNodeTypeDescriptor("test.delay",
				GLNodePortDescriptor.input("in", "signal", true, 1),
				GLNodePortDescriptor.output("out", "signal",
						GLPortTiming.PREVIOUS_STEP, 1)));
		GLNodeGraph graph = new GLNodeGraph();
		GLNodeId current = GLNodeId.random();
		GLNodeId delay = GLNodeId.random();
		GLNodeConnectionId forward = GLNodeConnectionId.random();
		GLNodeConnectionId feedback = GLNodeConnectionId.random();
		graph.edit("Create feedback", edit -> {
			edit.addNode(new GLGraphNode(current, "test.current"));
			edit.addNode(new GLGraphNode(delay, "test.delay"));
			edit.addConnection(new GLGraphConnection(forward,
					new GLNodeEndpoint(current, "out"),
					new GLNodeEndpoint(delay, "in")));
			edit.addConnection(new GLGraphConnection(feedback,
					new GLNodeEndpoint(delay, "out"),
					new GLNodeEndpoint(current, "in")));
		});
		var acyclic = GLNodeGraphValidator.validate(graph.snapshot(), registry,
				GLGraphCyclePolicy.ACYCLIC);
		check(!acyclic.isValid(), "Acyclic policy accepted feedback");
		var cycle = acyclic.diagnostics().stream()
				.filter(problem -> problem.code().equals("graph.cycle"))
				.findFirst().orElseThrow();
		check(cycle.connectionIds().equals(Set.of(forward, feedback)),
				"Cycle diagnostic did not identify exactly the cycle edges");
		check(GLNodeGraphValidator.validate(graph.snapshot(), registry,
				GLGraphCyclePolicy.DELAYED_FEEDBACK).isValid(),
				"Previous-step output did not break the current dependency cycle");
		check(GLNodeGraphValidator.validate(graph.snapshot(), registry,
				GLGraphCyclePolicy.PREVIOUS_STEP).isValid(),
				"Previous-step graph policy rejected feedback");

		GLNodeGraph immediateCycle = new GLNodeGraph();
		GLNodeId first = GLNodeId.random();
		GLNodeId second = GLNodeId.random();
		immediateCycle.edit("Create immediate cycle", edit -> {
			edit.addNode(new GLGraphNode(first, "test.current"));
			edit.addNode(new GLGraphNode(second, "test.current"));
			edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
					new GLNodeEndpoint(first, "out"),
					new GLNodeEndpoint(second, "in")));
			edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
					new GLNodeEndpoint(second, "out"),
					new GLNodeEndpoint(first, "in")));
		});
		check(!GLNodeGraphValidator.validate(immediateCycle.snapshot(), registry,
				GLGraphCyclePolicy.DELAYED_FEEDBACK).isValid(),
				"Delayed-feedback policy accepted an immediate dependency cycle");
	}

	private static void testPortContracts() {
		GLNodeTypeRegistry registry = new GLNodeTypeRegistry();
		registry.register(new GLNodeTypeDescriptor("test.number-source",
				GLNodePortDescriptor.output("value", "number")));
		registry.register(new GLNodeTypeDescriptor("test.text-sink",
				GLNodePortDescriptor.input("value", "text", true, 1)));
		GLNodeGraph graph = new GLNodeGraph();
		GLNodeId source = GLNodeId.random();
		GLNodeId sink = GLNodeId.random();
		GLNodeConnectionId edge = GLNodeConnectionId.random();
		graph.edit("Create incompatible edge", edit -> {
			edit.addNode(new GLGraphNode(source, "test.number-source"));
			edit.addNode(new GLGraphNode(sink, "test.text-sink"));
			edit.addConnection(new GLGraphConnection(edge,
					new GLNodeEndpoint(source, "value"),
					new GLNodeEndpoint(sink, "value")));
		});
		var result = GLNodeGraphValidator.validate(graph.snapshot(), registry,
				GLGraphCyclePolicy.ACYCLIC);
		check(!result.isValid() && result.diagnostics().stream().anyMatch(problem ->
				problem.code().equals("connection.type")
						&& problem.connectionIds().contains(edge)),
				"Incompatible port types produced no edge diagnostic");
	}

	private static void testNodeCatalog() {
		GLNodePortDescriptor output = GLNodePortDescriptor.output("out", "number");
		GLNodePortDescriptor input = GLNodePortDescriptor.input("in", "number", true, 1);
		GLNodeCatalog catalog = new GLNodeCatalog();
		catalog.register(new GLNodeCatalogEntry(new GLNodeTypeDescriptor(
				"math.source", output), "Number Source", "Sources", Map.of(), "value"));
		catalog.register(new GLNodeCatalogEntry(new GLNodeTypeDescriptor(
				"math.sink", input), "Number Output", "Outputs", Map.of(), "sink"));
		check(catalog.search("source").size() == 1
				&& catalog.search("number", output).stream().allMatch(entry ->
						entry.type().id().equals("math.sink")),
				"Node catalog search or compatible-port filtering failed");
		catalog.markUsed("math.sink");
		catalog.toggleFavorite("math.source");
		check(catalog.search("").get(0).type().id().equals("math.source")
				&& catalog.recentTypeIds().equals(List.of("math.sink")),
				"Node catalog favorites or recents were not retained");
	}

	private static void testCompositeExtraction() {
		GLNodeTypeRegistry types = new GLNodeTypeRegistry();
		types.register(new GLNodeTypeDescriptor("test.source",
				GLNodePortDescriptor.output("out", "number")));
		types.register(new GLNodeTypeDescriptor("test.middle",
				GLNodePortDescriptor.input("in", "number", true, 1),
				GLNodePortDescriptor.output("out", "number")));
		types.register(new GLNodeTypeDescriptor("test.sink",
				GLNodePortDescriptor.input("in", "number", true, 1)));
		GLNodeId source = GLNodeId.random(), first = GLNodeId.random();
		GLNodeId second = GLNodeId.random(), sink = GLNodeId.random();
		GLNodeConnectionId incoming = GLNodeConnectionId.random();
		GLNodeConnectionId internal = GLNodeConnectionId.random();
		GLNodeConnectionId outgoing = GLNodeConnectionId.random();
		GLNodeGraph graph = new GLNodeGraph();
		graph.edit("Create extraction graph", edit -> {
			edit.addNode(new GLGraphNode(source, "test.source"));
			edit.addNode(new GLGraphNode(first, "test.middle"));
			edit.addNode(new GLGraphNode(second, "test.middle"));
			edit.addNode(new GLGraphNode(sink, "test.sink"));
			edit.addConnection(new GLGraphConnection(incoming,
					new GLNodeEndpoint(source, "out"), new GLNodeEndpoint(first, "in")));
			edit.addConnection(new GLGraphConnection(internal,
					new GLNodeEndpoint(first, "out"), new GLNodeEndpoint(second, "in")));
			edit.addConnection(new GLGraphConnection(outgoing,
					new GLNodeEndpoint(second, "out"), new GLNodeEndpoint(sink, "in")));
		});
		GLNodeEditorViewState views = new GLNodeEditorViewState(Map.of(
				source, new GLNodeViewState(0, 0), first, new GLNodeViewState(100, 50),
				second, new GLNodeViewState(300, 50), sink, new GLNodeViewState(500, 0)));
		var result = GLCompositeExtraction.extract(graph.snapshot(), views,
				Set.of(first, second), types, "test.chain.asset", "test.chain");
		check(result.asset().graph().nodes().size() == 2
				&& result.asset().graph().connections().size() == 1
				&& result.asset().compositeDescriptor().portMappings().size() == 2,
				"Extracted asset lost internal content or boundary ports");
		check(result.graph().nodes().size() == 3
				&& result.graph().connections().size() == 2
				&& result.graph().connections().stream().anyMatch(edge ->
						edge.id().equals(incoming)
								&& edge.input().nodeId().equals(result.compositeNode().id()))
				&& result.graph().connections().stream().anyMatch(edge ->
						edge.id().equals(outgoing)
								&& edge.output().nodeId().equals(result.compositeNode().id())),
				"Root graph was not rewired through the extracted composite");
		check(result.standaloneDocument().root().equals(result.asset()),
				"Extracted composite could not be exported standalone");
		GLNodeGraphDocument embedded = result.embedIn(new GLNodeGraphDocument(
				new GLNodeGraphAsset("test.project", 1, GLNodeGraphAssetKind.PROJECT,
						graph.snapshot(), views)));
		check(embedded.root().graph().nodes().size() == 3
				&& embedded.embeddedAssets().containsKey("test.chain.asset"),
				"Embedding did not update the root graph and retain the composite asset");
	}

	private static void testPortableValues() {
		GLNodeValue value = GLNodeValue.of(Map.of(
				"enabled", true,
				"gain", 0.5,
				"channels", List.of(1, 2)));
		@SuppressWarnings("unchecked")
		Map<String, Object> javaValue = (Map<String, Object>) value.toJavaValue();
		check(Boolean.TRUE.equals(javaValue.get("enabled")),
				"Boolean graph value was not preserved");
		check(javaValue.get("gain").equals(0.5),
				"Decimal graph value was not preserved");
		check(javaValue.get("channels").equals(List.of(1L, 2L)),
				"Nested graph values were not normalized");
	}

	private static void testAtomicGraphEdits() {
		GLNodeGraph graph = new GLNodeGraph();
		GLNodeId source = GLNodeId.random();
		GLNodeId sink = GLNodeId.random();
		GLNodeConnectionId edge = GLNodeConnectionId.random();
		int[] notifications = { 0 };
		graph.addListener((changedGraph, change) -> {
			check(changedGraph == graph, "Listener received another graph");
			check(change.before().revision() == 0 && change.after().revision() == 1,
					"Atomic edit published the wrong revisions");
			notifications[0]++;
		});
		AtomicReference<GLNodeGraph.Editor> retainedEditor = new AtomicReference<>();
		check(graph.edit("Create signal path", editor -> {
			retainedEditor.set(editor);
			editor.addNode(new GLGraphNode(source, "audio.source"));
			editor.addNode(new GLGraphNode(sink, "audio.output"));
			editor.setProperty(source, "frequency", GLNodeValue.of(440.0));
			editor.addConnection(new GLGraphConnection(edge,
					new GLNodeEndpoint(source, "signal"),
					new GLNodeEndpoint(sink, "signal")));
		}), "Valid graph transaction reported no change");
		check(notifications[0] == 1 && graph.snapshot().nodes().size() == 2
				&& graph.snapshot().connections().size() == 1,
				"Atomic edit was not published as one complete revision");
		expect(IllegalStateException.class, () -> retainedEditor.get().removeNode(source),
				"Retained graph editor remained usable after its callback");

		var beforeFailure = graph.snapshot();
		expect(IllegalArgumentException.class, () -> graph.edit("Invalid edge", editor ->
				editor.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
						new GLNodeEndpoint(GLNodeId.random(), "signal"),
						new GLNodeEndpoint(sink, "signal")))),
				"Connection to an unknown node was accepted");
		check(graph.snapshot() == beforeFailure && notifications[0] == 1,
				"Failed transaction changed or announced the graph");
	}

	private static void testReplacementAndCascadeRemoval() {
		GLNodeGraph graph = new GLNodeGraph();
		GLNodeId source = GLNodeId.random();
		GLNodeId sink = GLNodeId.random();
		graph.edit("Create", editor -> {
			editor.addNode(new GLGraphNode(source, "test.source"));
			editor.addNode(new GLGraphNode(sink, "test.sink"));
			editor.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
					new GLNodeEndpoint(source, "out"),
					new GLNodeEndpoint(sink, "in")));
		});
		var populated = graph.snapshot();
		graph.edit("Remove source", editor -> editor.removeNode(source));
		check(graph.snapshot().nodes().size() == 1
				&& graph.snapshot().connections().isEmpty(),
				"Removing a node retained a dangling connection");
		check(graph.replace("Restore", populated)
				&& graph.snapshot().revision() == 3
				&& graph.snapshot().connections().size() == 1,
				"Snapshot replacement did not restore graph content");
	}

	private static void expect(Class<? extends Throwable> type, Runnable action,
			String message) {
		try { action.run(); }
		catch (Throwable failure) {
			if (type.isInstance(failure)) return;
			throw new AssertionError(message, failure);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
