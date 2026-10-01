package com.vingame.glswing.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeWorkspace;
import com.vingame.glswing.node.composite.GLCompositeExpansionAnalyzer;
import com.vingame.glswing.node.composite.GLCompositeExpansionLimits;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.composite.GLCompositeGraphFlattener;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.composite.GLCompositeNodeViewFactory;
import com.vingame.glswing.node.composite.GLCompositePortMapping;
import com.vingame.glswing.node.composite.GLCompositePortPresentation;
import com.vingame.glswing.node.composite.GLNodeInputEditorDescriptor;
import com.vingame.glswing.node.composite.GLNodeInputEditorRegistry;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.document.GLNodeGraphDocument;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.composite.GLCompositePropertyDescriptor;
import com.vingame.glswing.node.composite.GLCompositePropertyMapping;
import com.vingame.glswing.node.composite.GLCompositePropertyTransactions;
import com.vingame.glswing.node.composite.GLCompositeInterfaceTransactions;
import com.vingame.glswing.node.composite.GLCompositeValueKind;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;

/** Bounded expansion checks for nested, reused, and recursive composites. */
public final class GLCompositeExpansionSelfTest {
	private GLCompositeExpansionSelfTest() { }

	public static void main(String[] args) {
		flatteningChecks();
		GLNodeGraphAsset leaf = asset("asset.leaf", "plain", "plain", "plain");
		GLNodeGraphAsset wrapper = asset("asset.wrapper", "composite.leaf",
				"composite.leaf");
		GLNodeGraphAsset root = asset("asset.root", "composite.wrapper",
				"composite.wrapper");
		GLCompositeGraphRegistry registry = new GLCompositeGraphRegistry();
		registry.registerAsset(leaf); registry.registerAsset(wrapper);
		registry.registerComposite(descriptor("composite.leaf", leaf.id(), 1));
		registry.registerComposite(descriptor("composite.wrapper", wrapper.id(), 1));
		var result = GLCompositeExpansionAnalyzer.analyze(root, registry,
				GLCompositeExpansionLimits.DEFAULT);
		check(result.isValid() && result.expandedNodes() == 12
				&& result.estimatedOperations() == 12 && result.maximumDepth() == 2,
				"Nested composite estimate is incorrect");

		var limited = GLCompositeExpansionAnalyzer.analyze(root, registry,
				new GLCompositeExpansionLimits(64, 10, 100, 100));
		check(has(limited, "composite.node-limit"),
				"Expanded-node budget was not enforced");
		var shallow = GLCompositeExpansionAnalyzer.analyze(root, registry,
				new GLCompositeExpansionLimits(1, 100, 100, 100));
		check(has(shallow, "composite.depth-limit"),
				"Nesting-depth budget was not enforced independently");

		GLNodeGraphAsset first = asset("asset.first", "composite.second");
		GLNodeGraphAsset second = asset("asset.second", "composite.first");
		GLCompositeGraphRegistry recursive = new GLCompositeGraphRegistry();
		recursive.registerAsset(first); recursive.registerAsset(second);
		recursive.registerComposite(descriptor("composite.first", first.id(), 1));
		recursive.registerComposite(descriptor("composite.second", second.id(), 1));
		check(has(GLCompositeExpansionAnalyzer.analyze(first, recursive,
				GLCompositeExpansionLimits.DEFAULT), "composite.recursive"),
				"Indirect recursive composition was not rejected");

		GLCompositeGraphRegistry missing = new GLCompositeGraphRegistry();
		missing.registerComposite(descriptor("composite.missing", "asset.missing", 1));
		check(has(GLCompositeExpansionAnalyzer.analyze(
				asset("missing.root", "composite.missing"), missing,
				GLCompositeExpansionLimits.DEFAULT), "composite.missing-asset"),
				"Missing composite dependency was not diagnosed");

		GLCompositeGraphRegistry badPort = new GLCompositeGraphRegistry();
		badPort.registerAsset(leaf);
		badPort.registerComposite(new GLCompositeNodeDescriptor("composite.bad-port",
				leaf.id(), 1, List.of(new GLCompositePortMapping(
						GLNodePortDescriptor.input("input", "number", true, 1),
						new GLNodeEndpoint(GLNodeId.random(), "input")))));
		check(has(GLCompositeExpansionAnalyzer.analyze(
				asset("port.root", "composite.bad-port"), badPort,
				GLCompositeExpansionLimits.DEFAULT), "composite.port-node"),
				"Invalid public-port mapping was not diagnosed");
		System.out.println("GL composite expansion self-test passed.");
	}

	private static void flatteningChecks() {
		GLNodeId processor = GLNodeId.random();
		GLNodeGraphAsset nested = new GLNodeGraphAsset("asset.gain", 1,
				GLNodeGraphAssetKind.COMPOSITE,
				new GLNodeGraphSnapshot(GLNodeGraphId.random(), 0,
						List.of(new GLGraphNode(processor, "plain.processor")), List.of()),
				new GLNodeEditorViewState(Map.of()), Map.of(),
				new GLCompositeNodeDescriptor("composite.gain", "asset.gain", 1,
						List.of(new GLCompositePortMapping(
								GLNodePortDescriptor.input("in", "number", true, 1),
								new GLNodeEndpoint(processor, "in")),
								new GLCompositePortMapping(
										GLNodePortDescriptor.output("out", "number"),
										new GLNodeEndpoint(processor, "out"))),
						List.of(new GLCompositePropertyDescriptor("gain", "number",
								GLCompositeValueKind.DECIMAL, GLNodeValue.of(1.0), 0.0, 4.0)),
						List.of(new GLCompositePropertyMapping("gain", processor, "gain"))));
		GLNodeId source = GLNodeId.random(), first = GLNodeId.random();
		GLNodeId second = GLNodeId.random(), sink = GLNodeId.random();
		List<GLGraphNode> nodes = List.of(new GLGraphNode(source, "plain.source"),
				new GLGraphNode(first, "composite.gain", Map.of("gain", GLNodeValue.of(2.5))),
				new GLGraphNode(second, "composite.gain"),
				new GLGraphNode(sink, "plain.sink"));
		List<GLGraphConnection> connections = List.of(connection(source, "out", first, "in"),
				connection(first, "out", second, "in"),
				connection(second, "out", sink, "in"));
		GLNodeGraphAsset root = new GLNodeGraphAsset("flatten.root", 1,
				GLNodeGraphAssetKind.PROJECT,
				new GLNodeGraphSnapshot(GLNodeGraphId.random(), 0, nodes, connections),
				new GLNodeEditorViewState(Map.of()));
		GLCompositeGraphRegistry registry = new GLCompositeGraphRegistry();
		registry.registerAsset(nested);
		GLNodeGraphAsset replacement = new GLNodeGraphAsset(nested.id(), nested.version(),
				nested.kind(), nested.graph(), nested.viewState(),
				Map.of("edited", GLNodeValue.of(true)), nested.compositeDescriptor());
		registry.replaceAsset(replacement);
		check(registry.findAsset(nested.id()).orElseThrow().metadata()
				.get("edited").equals(GLNodeValue.of(true)),
				"Registered composite asset was not replaced atomically");
		var flattened = GLCompositeGraphFlattener.flatten(root, registry,
				GLCompositeExpansionLimits.DEFAULT);
		check(flattened.isValid() && flattened.graph().orElseThrow().nodes().size() == 4
				&& flattened.graph().orElseThrow().connections().size() == 3,
				"Composite graph was not flattened and rewired");
		List<GLGraphNode> processors = flattened.graph().orElseThrow().nodes().stream()
				.filter(node -> node.typeId().equals("plain.processor")).toList();
		check(processors.size() == 2 && !processors.get(0).id().equals(processors.get(1).id()),
				"Repeated composite instances reused internal node IDs");
		check(processors.stream().map(node -> node.properties().get("gain"))
				.collect(java.util.stream.Collectors.toSet()).equals(
						Set.of(GLNodeValue.of(1.0), GLNodeValue.of(2.5))),
				"Composite defaults or instance property overrides were not applied");
		check(processors.stream().allMatch(node ->
				flattened.origins().get(node.id()).instances().size() == 1),
				"Flattened nodes lost their composite origin path");
		var repeated = GLCompositeGraphFlattener.flatten(root, registry,
				GLCompositeExpansionLimits.DEFAULT);
		check(repeated.graph().equals(flattened.graph()),
				"Composite flattening does not generate deterministic IDs");
		GLNodeGraph editable = new GLNodeGraph(root.graph());
		com.vingame.glswing.history.GLHistoryManager history =
				new com.vingame.glswing.history.GLHistoryManager();
		GLCompositePropertyTransactions.set(editable, nested.compositeDescriptor(), first,
				Map.of("gain", GLNodeValue.of(3.5)), history, "Set gain");
		check(editable.snapshot().findNode(first).orElseThrow().properties()
				.get("gain").equals(GLNodeValue.of(3.5)) && history.canUndo(),
				"Composite property transaction was not recorded");
		history.undo();
		check(editable.snapshot().findNode(first).orElseThrow().properties()
				.get("gain").equals(GLNodeValue.of(2.5)),
				"Composite property transaction did not undo atomically");
		history.redo();
		check(editable.snapshot().findNode(first).orElseThrow().properties()
				.get("gain").equals(GLNodeValue.of(3.5)),
				"Composite property transaction did not redo atomically");

		GLNodeTypeRegistry interfaceTypes = new GLNodeTypeRegistry();
		interfaceTypes.register(new GLNodeTypeDescriptor("plain.processor", List.of(
				GLNodePortDescriptor.input("in", "number", true, 1),
				GLNodePortDescriptor.output("out", "number"))));
		interfaceTypes.register(nested.compositeDescriptor().nodeType());
		var renamedInput = new GLCompositePortMapping(
				GLNodePortDescriptor.input("source", "number", true, 1),
				new GLNodeEndpoint(processor, "in"));
		GLNodeGraph dependentGraph = new GLNodeGraph(root.graph());
		GLCompositeInterfaceTransactions.updatePort(registry, interfaceTypes,
				nested.id(), "in", renamedInput, List.of(dependentGraph), history,
				"Rename composite input");
		check(registry.findComposite("composite.gain").orElseThrow().portMappings()
				.get(0).publicPort().id().equals("source")
				&& interfaceTypes.find("composite.gain").orElseThrow()
						.findPort("source").isPresent()
				&& dependentGraph.snapshot().connections().stream()
						.filter(connection -> connection.input().nodeId().equals(first)
								|| connection.input().nodeId().equals(second))
						.allMatch(connection -> connection.input().portId().equals("source")),
				"Composite interface edit did not update asset and registered type");
		history.undo();
		check(registry.findComposite("composite.gain").orElseThrow().nodeType()
				.findPort("in").isPresent()
				&& dependentGraph.snapshot().connections().stream()
						.filter(connection -> connection.input().nodeId().equals(first)
								|| connection.input().nodeId().equals(second))
						.allMatch(connection -> connection.input().portId().equals("in")),
				"Composite interface edit did not undo atomically");
		history.redo();
		check(registry.findComposite("composite.gain").orElseThrow().nodeType()
				.findPort("source").isPresent(),
				"Composite interface edit did not redo atomically");
		int connectionsBeforeRemoval = dependentGraph.snapshot().connections().size();
		GLCompositeInterfaceTransactions.removePort(registry, interfaceTypes,
				nested.id(), "source", List.of(dependentGraph), history,
				"Remove composite input");
		check(dependentGraph.snapshot().connections().size()
				== connectionsBeforeRemoval - 2,
				"Removing a composite port retained dependent connections");
		history.undo();
		check(registry.findComposite("composite.gain").orElseThrow().nodeType()
				.findPort("source").isPresent()
				&& dependentGraph.snapshot().connections().size()
						== connectionsBeforeRemoval,
				"Removing a composite port did not undo atomically");

		GLNodeCatalog workspaceCatalog = new GLNodeCatalog();
		workspaceCatalog.register(new GLNodeCatalogEntry(
				interfaceTypes.find("plain.processor").orElseThrow(),
				"Processor", "Tests", Map.of()));
		workspaceCatalog.register(new GLNodeCatalogEntry(
				interfaceTypes.find("composite.gain").orElseThrow(),
				"Gain", "Tests", Map.of()));
		GLNodeId compositeInstance = GLNodeId.random();
		GLNodeGraph workspaceRoot = new GLNodeGraph();
		workspaceRoot.edit("Create workspace root", edit -> edit.addNode(
				new GLGraphNode(compositeInstance, "composite.gain")));
		try (GLNodeWorkspace workspace = new GLNodeWorkspace(interfaceTypes, registry,
				workspaceCatalog, (node, type) -> {
					GLNode view = new GLNode(type.id());
					for (GLNodePortDescriptor port : type.ports())
						if (port.direction() == com.vingame.glswing.node.model.GLPortDirection.INPUT)
							view.addInput(port.id(), new com.vingame.glswing.ui.GLLabel(port.id()));
						else view.addOutput(port.id(),
								new com.vingame.glswing.ui.GLLabel(port.id()));
					return view;
				},
				GLGraphCyclePolicy.ACYCLIC, history)) {
			GLNodeGraphAsset workspaceRootAsset = new GLNodeGraphAsset("workspace.root", 1,
					GLNodeGraphAssetKind.PROJECT, workspaceRoot.snapshot(),
					GLNodeEditorViewState.empty());
			workspace.openDocument("Root", new GLNodeGraphDocument(workspaceRootAsset,
					Map.of(nested.id(), nested), List.of()));
			workspace.editor().setSelectedNode(workspace.editor().getNodes().get(0));
			check(workspace.openSelectedComposite()
					&& nested.id().equals(workspace.currentAssetId())
					&& workspace.editor().getNodes().stream()
							.filter(node -> node.getTitle().startsWith("Group ")).count() == 2,
					"Node workspace did not open the composite with unique boundaries");
			GLNode groupInput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Input".equals(node.getTitle()))
					.findFirst().orElseThrow();
			GLNode groupOutput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Output".equals(node.getTitle()))
					.findFirst().orElseThrow();
			groupInput.setLocation(137, 211);
			groupOutput.setLocation(643, 277);
			GLCompositePortMapping sourceMapping = registry.findAsset(nested.id())
					.orElseThrow().compositeDescriptor().portMappings().stream()
					.filter(mapping -> "source".equals(mapping.publicPort().id()))
					.findFirst().orElseThrow();
			GLNodePortDescriptor sourcePort = sourceMapping.publicPort();
			GLCompositeInterfaceTransactions.updatePort(registry, interfaceTypes,
					nested.id(), sourcePort.id(), new GLCompositePortMapping(
							new GLNodePortDescriptor("workspaceSource", sourcePort.direction(),
									sourcePort.valueTypeId(), sourcePort.timing(),
									sourcePort.required(), sourcePort.maximumConnections()),
							sourceMapping.internalEndpoint(), sourceMapping.presentation()),
					List.of(), history, "Test workspace boundary refresh");
			GLNode refreshedInput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Input".equals(node.getTitle()))
					.findFirst().orElseThrow();
			GLNode refreshedOutput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Output".equals(node.getTitle()))
					.findFirst().orElseThrow();
			check(refreshedInput.getX() == 137 && refreshedInput.getY() == 211
					&& refreshedOutput.getX() == 643 && refreshedOutput.getY() == 277,
					"Composite interface refresh reset boundary-node positions");
			history.undo();
			check(workspace.navigateBack() && workspace.currentAssetId() == null,
					"Node workspace did not restore its containing graph");
			check(workspace.canNavigateForward()
					&& workspace.editor().getSelectedNodes().size() == 1,
					"Back navigation lost its forward entry or parent selection");
			check(workspace.navigateForward()
					&& nested.id().equals(workspace.currentAssetId()),
					"Node workspace did not navigate forward into the composite");
			GLNode reopenedInput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Input".equals(node.getTitle()))
					.findFirst().orElseThrow();
			GLNode reopenedOutput = workspace.editor().getNodes().stream()
					.filter(node -> "Group Output".equals(node.getTitle()))
					.findFirst().orElseThrow();
			check(reopenedInput.getX() == 137 && reopenedInput.getY() == 211
					&& reopenedOutput.getX() == 643 && reopenedOutput.getY() == 277,
					"Forward navigation reset composite boundary-node positions");
			check(workspace.navigateBack(),
					"Node workspace did not leave the reopened composite");
			check(workspace.snapshotDocument().embeddedAssets().get(nested.id())
					.compositeDescriptor().nodeType().findPort("source").isPresent(),
					"Node workspace snapshot did not persist the current composite contract");
		}
		GLCompositePortMapping retained = registry.findComposite("composite.gain")
				.orElseThrow().portMappings().stream()
				.filter(mapping -> mapping.publicPort().direction()
						== com.vingame.glswing.node.model.GLPortDirection.INPUT)
				.findFirst().orElseThrow();
		GLNodePortDescriptor mismatchedPort = new GLNodePortDescriptor(
				retained.publicPort().id(), retained.publicPort().direction(), "color",
				retained.publicPort().timing(), retained.publicPort().required(),
				retained.publicPort().maximumConnections());
		GLCompositeInterfaceTransactions.updatePort(registry, interfaceTypes,
				nested.id(), retained.publicPort().id(), new GLCompositePortMapping(
						mismatchedPort, retained.internalEndpoint(), retained.presentation()),
				history, "Change public port type");
		GLCompositePortMapping mismatched = registry.findComposite("composite.gain")
				.orElseThrow().portMappings().stream()
				.filter(mapping -> mapping.publicPort().id().equals(mismatchedPort.id()))
				.findFirst().orElseThrow();
		check("color".equals(mismatched.publicPort().valueTypeId())
				&& mismatched.internalEndpoint().equals(retained.internalEndpoint()),
				"Changing a public type silently removed or reassigned its binding");
		history.undo();

		GLNodeGraphAsset invalid = new GLNodeGraphAsset(root.id(), root.version(), root.kind(),
				new GLNodeGraphSnapshot(root.graph().graphId(), 1,
						List.of(new GLGraphNode(first, "composite.gain",
								Map.of("gain", GLNodeValue.of(9.0)))), List.of()),
				root.viewState());
		var rejected = GLCompositeGraphFlattener.flatten(invalid, registry,
				GLCompositeExpansionLimits.DEFAULT);
		check(!rejected.isValid() && hasDiagnostic(rejected.diagnostics(),
				"composite.property-value"),
				"Out-of-range composite property did not reject the transaction");

		GLCompositePortMapping unboundColor = new GLCompositePortMapping(
				GLNodePortDescriptor.input("tint", "color", false, 1), null,
				new GLCompositePortPresentation("test.color", false, Map.of()));
		GLCompositeNodeDescriptor unboundDescriptor = new GLCompositeNodeDescriptor(
				"composite.unbound", "asset.unbound", 1, List.of(unboundColor));
		GLNodeGraphAsset unboundAsset = new GLNodeGraphAsset("asset.unbound", 1,
				GLNodeGraphAssetKind.COMPOSITE,
				new GLNodeGraphSnapshot(GLNodeGraphId.random(), 0, List.of(), List.of()),
				GLNodeEditorViewState.empty(), Map.of(), unboundDescriptor);
		GLCompositeGraphRegistry unboundRegistry = new GLCompositeGraphRegistry();
		unboundRegistry.registerAsset(unboundAsset);
		GLNodeTypeRegistry unboundTypes = new GLNodeTypeRegistry();
		unboundTypes.register(unboundDescriptor.nodeType());
		GLCompositeInterfaceTransactions.replacePorts(unboundRegistry, unboundTypes,
				unboundAsset.id(), List.of(unboundColor), new GLHistoryManager(),
				"Keep unbound port");
		check(!unboundRegistry.findComposite("composite.unbound").orElseThrow()
				.portMappings().get(0).isBound(),
				"A public port could not exist independently from internal wiring");

		GLNodeInputEditorRegistry editors = new GLNodeInputEditorRegistry();
		editors.register(new GLNodeInputEditorDescriptor("test.color", "Color",
				Set.of("color"), Map.of()), context -> new com.vingame.glswing.ui.GLColorChooser());
		GLCompositeNodeViewFactory views = new GLCompositeNodeViewFactory(
				unboundRegistry, editors, (node, type) -> new GLNode(type.id()));
		GLNode projected = views.createNode(new GLGraphNode(GLNodeId.random(),
				"composite.unbound"), unboundDescriptor.nodeType());
		check(projected.getContents().stream().anyMatch(
				com.vingame.glswing.ui.GLColorChooser.class::isInstance)
				&& projected.getPorts().size() == 1 && !projected.getPorts().get(0).isSocketVisible(),
				"Registered composite input editor was not projected as a control");
	}

	private static GLGraphConnection connection(GLNodeId output, String outputPort,
			GLNodeId input, String inputPort) {
		return new GLGraphConnection(GLNodeConnectionId.random(),
				new GLNodeEndpoint(output, outputPort), new GLNodeEndpoint(input, inputPort));
	}

	private static GLCompositeNodeDescriptor descriptor(String type, String asset,
			int minimumVersion) {
		return new GLCompositeNodeDescriptor(type, asset, minimumVersion, List.of());
	}
	private static GLNodeGraphAsset asset(String id, String... nodeTypes) {
		List<GLGraphNode> nodes = new ArrayList<>();
		for (String type : nodeTypes) nodes.add(new GLGraphNode(GLNodeId.random(), type));
		return new GLNodeGraphAsset(id, 1, GLNodeGraphAssetKind.COMPOSITE,
				new GLNodeGraphSnapshot(GLNodeGraphId.random(), 0, nodes, List.of()),
				new GLNodeEditorViewState(Map.of()));
	}
	private static boolean has(com.vingame.glswing.node.composite.GLCompositeExpansionResult result,
			String code) {
		return hasDiagnostic(result.diagnostics(), code);
	}
	private static boolean hasDiagnostic(List<com.vingame.glswing.node.model.GLGraphDiagnostic> values,
			String code) {
		return values.stream().anyMatch(value -> value.code().equals(code));
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}

