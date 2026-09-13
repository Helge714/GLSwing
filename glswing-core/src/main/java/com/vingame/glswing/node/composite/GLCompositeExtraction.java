package com.vingame.glswing.node.composite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.document.GLNodeGraphDocument;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLPortDirection;

/** Pure transformation that replaces a selected subgraph with a composite node. */
public final class GLCompositeExtraction {
	public record Result(GLNodeGraphAsset asset, GLGraphNode compositeNode,
			GLNodeGraphSnapshot graph, GLNodeEditorViewState viewState) {
		public Result {
			if (asset == null || compositeNode == null || graph == null || viewState == null)
				throw new IllegalArgumentException("Complete composite extraction required");
		}
		public GLNodeGraphDocument standaloneDocument() {
			return new GLNodeGraphDocument(asset);
		}
		/** Includes recursively referenced composite assets in a portable document. */
		public GLNodeGraphDocument standaloneDocument(
				GLCompositeGraphRegistry registry) {
			if (registry == null)
				throw new IllegalArgumentException("Composite registry required");
			LinkedHashMap<String, GLNodeGraphAsset> dependencies = new LinkedHashMap<>();
			collectDependencies(asset, registry, dependencies, new LinkedHashSet<>());
			dependencies.remove(asset.id());
			return new GLNodeGraphDocument(asset, dependencies, List.of());
		}
		public GLNodeGraphDocument embedIn(GLNodeGraphDocument document) {
			if (document == null
					|| !document.root().graph().graphId().equals(graph.graphId()))
				throw new IllegalArgumentException(
						"Containing document must own the extracted root graph");
			GLNodeGraphAsset previous = document.root();
			GLNodeGraphAsset updatedRoot = new GLNodeGraphAsset(previous.id(),
					previous.version(), previous.kind(), graph, viewState,
					previous.metadata(), previous.compositeDescriptor());
			return new GLNodeGraphDocument(updatedRoot, document.embeddedAssets(),
					document.externalAssets()).withEmbeddedAsset(asset);
		}
	}

	private GLCompositeExtraction() { }

	private static void collectDependencies(GLNodeGraphAsset source,
			GLCompositeGraphRegistry registry,
			Map<String, GLNodeGraphAsset> result, Set<String> visiting) {
		if (!visiting.add(source.id())) return;
		for (GLGraphNode node : source.graph().nodes())
			registry.findComposite(node.typeId()).flatMap(descriptor ->
					registry.findAsset(descriptor.assetId())).ifPresent(dependency -> {
						if (!dependency.id().equals(source.id())
								&& result.putIfAbsent(dependency.id(), dependency) == null)
							collectDependencies(dependency, registry, result, visiting);
					});
		visiting.remove(source.id());
	}

	public static Result extract(GLNodeGraphSnapshot source,
			GLNodeEditorViewState viewState, Set<GLNodeId> selection,
			GLNodeTypeRegistry types, String assetId, String nodeTypeId) {
		if (source == null || viewState == null || selection == null
				|| selection.isEmpty() || types == null)
			throw new IllegalArgumentException("Graph, selection, view state, and types required");
		List<GLGraphNode> selectedNodes = source.nodes().stream()
				.filter(node -> selection.contains(node.id())).toList();
		if (selectedNodes.size() != selection.size())
			throw new IllegalArgumentException("Selection contains nodes outside the graph");

		List<GLGraphConnection> internal = new ArrayList<>();
		List<GLGraphConnection> incoming = new ArrayList<>();
		List<GLGraphConnection> outgoing = new ArrayList<>();
		for (GLGraphConnection connection : source.connections()) {
			boolean outputInside = selection.contains(connection.output().nodeId());
			boolean inputInside = selection.contains(connection.input().nodeId());
			if (outputInside && inputInside) internal.add(connection);
			else if (!outputInside && inputInside) incoming.add(connection);
			else if (outputInside) outgoing.add(connection);
		}

		List<GLCompositePortMapping> mappings = new ArrayList<>();
		LinkedHashMap<GLNodeEndpoint, String> incomingPorts = new LinkedHashMap<>();
		LinkedHashMap<GLNodeEndpoint, String> outgoingPorts = new LinkedHashMap<>();
		Set<String> usedPortIds = new LinkedHashSet<>();
		for (GLGraphConnection connection : incoming) {
			GLNodeEndpoint endpoint = connection.input();
			incomingPorts.computeIfAbsent(endpoint, key -> {
				GLNodePortDescriptor internalPort = requirePort(source, types, key,
						GLPortDirection.INPUT);
				String id = uniqueId(internalPort.id(), usedPortIds);
				mappings.add(new GLCompositePortMapping(new GLNodePortDescriptor(id,
						GLPortDirection.INPUT, internalPort.valueTypeId(), internalPort.timing(),
						internalPort.required(), internalPort.maximumConnections()), key));
				return id;
			});
		}
		for (GLGraphConnection connection : outgoing) {
			GLNodeEndpoint endpoint = connection.output();
			outgoingPorts.computeIfAbsent(endpoint, key -> {
				GLNodePortDescriptor internalPort = requirePort(source, types, key,
						GLPortDirection.OUTPUT);
				String id = uniqueId(internalPort.id(), usedPortIds);
				mappings.add(new GLCompositePortMapping(GLNodePortDescriptor.output(id,
						internalPort.valueTypeId(), internalPort.timing(),
						internalPort.maximumConnections()), key));
				return id;
			});
		}

		GLCompositeNodeDescriptor descriptor = new GLCompositeNodeDescriptor(
				nodeTypeId, assetId, 1, mappings);
		LinkedHashMap<GLNodeId, GLNodeViewState> childViews = new LinkedHashMap<>();
		for (GLGraphNode node : selectedNodes) childViews.put(node.id(),
				viewState.nodes().getOrDefault(node.id(), new GLNodeViewState(0, 0)));
		GLNodeGraphAsset asset = new GLNodeGraphAsset(assetId, 1,
				GLNodeGraphAssetKind.COMPOSITE,
				new GLNodeGraphSnapshot(GLNodeGraphId.random(), 0, selectedNodes, internal),
				new GLNodeEditorViewState(childViews), Map.of(), descriptor);

		GLNodeId compositeId = GLNodeId.random();
		GLGraphNode composite = new GLGraphNode(compositeId, nodeTypeId);
		List<GLGraphNode> rootNodes = new ArrayList<>();
		for (GLGraphNode node : source.nodes())
			if (!selection.contains(node.id())) rootNodes.add(node);
		rootNodes.add(composite);
		List<GLGraphConnection> rootConnections = new ArrayList<>();
		for (GLGraphConnection connection : source.connections()) {
			boolean outputInside = selection.contains(connection.output().nodeId());
			boolean inputInside = selection.contains(connection.input().nodeId());
			if (!outputInside && !inputInside) rootConnections.add(connection);
			else if (!outputInside)
				rootConnections.add(new GLGraphConnection(connection.id(), connection.output(),
						new GLNodeEndpoint(compositeId, incomingPorts.get(connection.input()))));
			else if (!inputInside)
				rootConnections.add(new GLGraphConnection(connection.id(),
						new GLNodeEndpoint(compositeId, outgoingPorts.get(connection.output())),
						connection.input()));
		}
		LinkedHashMap<GLNodeId, GLNodeViewState> rootViews =
				new LinkedHashMap<>(viewState.nodes());
		selection.forEach(rootViews::remove);
		rootViews.put(compositeId, compositeBounds(childViews.values()));
		return new Result(asset, composite,
				new GLNodeGraphSnapshot(source.graphId(), source.revision(), rootNodes,
						rootConnections), new GLNodeEditorViewState(rootViews));
	}

	private static GLNodePortDescriptor requirePort(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types, GLNodeEndpoint endpoint,
			GLPortDirection direction) {
		GLGraphNode node = graph.findNode(endpoint.nodeId()).orElseThrow();
		GLNodeTypeDescriptor type = types.find(node.typeId()).orElseThrow(() ->
				new IllegalArgumentException("Unknown node type: " + node.typeId()));
		GLNodePortDescriptor port = type.findPort(endpoint.portId()).orElseThrow(() ->
				new IllegalArgumentException("Unknown node port: " + endpoint.portId()));
		if (port.direction() != direction)
			throw new IllegalArgumentException("Unexpected boundary-port direction");
		return port;
	}

	private static String uniqueId(String base, Set<String> used) {
		String candidate = base;
		for (int suffix = 2; !used.add(candidate); suffix++) candidate = base + suffix;
		return candidate;
	}

	private static GLNodeViewState compositeBounds(
			java.util.Collection<GLNodeViewState> views) {
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
		for (GLNodeViewState state : views) {
			minX = Math.min(minX, state.x()); minY = Math.min(minY, state.y());
			maxX = Math.max(maxX, state.x() + state.width());
			maxY = Math.max(maxY, state.y() + state.height());
		}
		return new GLNodeViewState((minX + maxX) * .5f, (minY + maxY) * .5f);
	}
}
