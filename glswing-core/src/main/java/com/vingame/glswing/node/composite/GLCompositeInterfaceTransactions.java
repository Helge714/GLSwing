package com.vingame.glswing.node.composite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;

/** Atomic, undoable edits of a registered composite's public port contract. */
public final class GLCompositeInterfaceTransactions {
	private GLCompositeInterfaceTransactions() { }

	public static boolean replacePorts(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId,
			List<GLCompositePortMapping> ports, GLHistoryManager history,
			String editName) {
		if (composites == null || types == null || assetId == null
				|| ports == null || history == null || editName == null
				|| editName.isBlank())
			throw new IllegalArgumentException("Complete composite interface edit required");
		GLNodeGraphAsset before = composites.findAsset(assetId).orElseThrow(() ->
				new IllegalArgumentException("Unknown composite asset: " + assetId));
		GLCompositeNodeDescriptor descriptor = before.composite().orElseThrow(() ->
				new IllegalArgumentException("Asset is not a composite: " + assetId));
        for(var fixed:descriptor.portMappings())if(fixed.presentation().applicationManaged()&&!ports.contains(fixed))
            throw new IllegalArgumentException("Application-managed port cannot be changed or removed");
		var inputIds=ports.stream().filter(p->p.publicPort().direction()==com.vingame.glswing.node.model.GLPortDirection.INPUT).map(p->p.publicPort().id()).collect(java.util.stream.Collectors.toSet());
        ports=ports.stream().map(p->GLCompositeForwarding.isInput(p.internalEndpoint())&&!inputIds.contains(p.internalEndpoint().portId())?p.withEndpoints(List.of()):p).toList();
        validateMappings(before, types, ports);
		GLCompositeNodeDescriptor nextDescriptor = new GLCompositeNodeDescriptor(
				descriptor.nodeTypeId(), descriptor.assetId(),
				descriptor.minimumAssetVersion(), ports, descriptor.properties(),
				descriptor.propertyMappings());
		if (descriptor.equals(nextDescriptor)) return false;
		GLNodeGraphAsset after = withDescriptor(before, nextDescriptor);
		apply(composites, types, after);
		history.record(editName, () -> apply(composites, types, before),
				() -> apply(composites, types, after));
		return true;
	}

	public static boolean addPort(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId, GLCompositePortMapping port,
			GLHistoryManager history, String editName) {
		if (port == null) throw new IllegalArgumentException("Composite port required");
		GLCompositeNodeDescriptor descriptor = descriptor(composites, assetId);
		List<GLCompositePortMapping> ports = new ArrayList<>(descriptor.portMappings());
		ports.add(port);
		return replacePorts(composites, types, assetId, ports, history, editName);
	}

	public static boolean updatePort(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId, String currentPortId,
			GLCompositePortMapping port, GLHistoryManager history, String editName) {
		if (currentPortId == null || port == null)
			throw new IllegalArgumentException("Current and replacement ports required");
		GLCompositeNodeDescriptor descriptor = descriptor(composites, assetId);
		List<GLCompositePortMapping> ports = new ArrayList<>(descriptor.portMappings());
		int index = -1;
		for (int current = 0; current < ports.size(); current++)
			if (ports.get(current).publicPort().id().equals(currentPortId)) {
				index = current; break;
			}
		if (index < 0) throw new IllegalArgumentException(
				"Unknown composite port: " + currentPortId);
		if(ports.get(index).presentation().applicationManaged())throw new IllegalArgumentException("Application-managed port cannot be edited");
		ports.set(index, port);
        if(!currentPortId.equals(port.publicPort().id()))for(int i=0;i<ports.size();i++){
            var p=ports.get(i);if(GLCompositeForwarding.isInput(p.internalEndpoint())&&p.internalEndpoint().portId().equals(currentPortId))ports.set(i,p.withEndpoints(List.of(GLCompositeForwarding.input(port.publicPort().id()))));
        }
		return replacePorts(composites, types, assetId, ports, history, editName);
	}

	/**
	 * Updates a port and remaps matching connections in the supplied open graph
	 * documents as part of the same history transaction.
	 */
	public static boolean updatePort(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId, String currentPortId,
			GLCompositePortMapping port, Collection<GLNodeGraph> dependentGraphs,
			GLHistoryManager history, String editName) {
		return editWithDependents(composites, types, assetId, dependentGraphs,
				history, editName, () -> updatePort(composites, types, assetId,
						currentPortId, port, history, editName));
	}

	public static boolean removePort(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId, String portId,
			GLHistoryManager history, String editName) {
		GLCompositeNodeDescriptor descriptor = descriptor(composites, assetId);
        if(descriptor.portMappings().stream().anyMatch(p->p.publicPort().id().equals(portId)&&p.presentation().applicationManaged()))throw new IllegalArgumentException("Application-managed port cannot be removed");
		List<GLCompositePortMapping> ports = descriptor.portMappings().stream()
				.filter(port -> !port.publicPort().id().equals(portId)).toList();
		if (ports.size() == descriptor.portMappings().size()) return false;
		return replacePorts(composites, types, assetId, ports, history, editName);
	}

	/** Removes a port and its matching connections from supplied open graphs. */
	public static boolean removePort(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId, String portId,
			Collection<GLNodeGraph> dependentGraphs, GLHistoryManager history,
			String editName) {
		return editWithDependents(composites, types, assetId, dependentGraphs,
				history, editName, () -> removePort(composites, types, assetId,
						portId, history, editName));
	}

	private static boolean editWithDependents(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, String assetId,
			Collection<GLNodeGraph> dependentGraphs, GLHistoryManager history,
			String editName, java.util.function.BooleanSupplier interfaceEdit) {
		if (dependentGraphs == null)
			throw new IllegalArgumentException("Dependent graph collection required");
		GLCompositeNodeDescriptor before = descriptor(composites, assetId);
		List<GLNodeGraph> graphs = dependentGraphs.stream().filter(
				java.util.Objects::nonNull).distinct().toList();
		try (GLHistoryManager.Transaction ignored = history.beginTransaction(editName)) {
			if (!interfaceEdit.getAsBoolean()) return false;
			GLCompositeNodeDescriptor after = descriptor(composites, assetId);
			for (GLNodeGraph graph : graphs)
				reconcileGraph(graph, before, after, history, editName);
			return true;
		}
	}

	private static void reconcileGraph(GLNodeGraph graph,
			GLCompositeNodeDescriptor before, GLCompositeNodeDescriptor after,
			GLHistoryManager history, String editName) {
		Map<String, String> replacements = new LinkedHashMap<>();
		for (GLCompositePortMapping oldMapping : before.portMappings()) {
			String replacement = after.portMappings().stream()
					.filter(candidate -> candidate.publicPort().id().equals(
							oldMapping.publicPort().id()))
					.map(candidate -> candidate.publicPort().id()).findFirst()
					.orElseGet(() -> after.portMappings().stream()
					.filter(candidate -> oldMapping.internalEndpoint() != null
							&& oldMapping.internalEndpoint().equals(
									candidate.internalEndpoint())
							&& candidate.publicPort().direction()
									== oldMapping.publicPort().direction())
					.map(candidate -> candidate.publicPort().id()).findFirst()
					.orElse(null));
			replacements.put(oldMapping.publicPort().id(), replacement);
		}
		GLNodeGraphSnapshot beforeGraph = graph.snapshot();
		Map<com.vingame.glswing.node.model.GLNodeId, GLGraphNode> nodes =
				beforeGraph.nodesById();
		List<GLGraphConnection> connections = new ArrayList<>();
		for (GLGraphConnection connection : beforeGraph.connections()) {
			GLNodeEndpoint output = reconcileEndpoint(connection.output(), nodes,
					before.nodeTypeId(), replacements);
			GLNodeEndpoint input = reconcileEndpoint(connection.input(), nodes,
					before.nodeTypeId(), replacements);
			if (output != null && input != null)
				connections.add(new GLGraphConnection(connection.id(), output, input));
		}
		GLNodeGraphSnapshot replacement = new GLNodeGraphSnapshot(
				beforeGraph.graphId(), beforeGraph.revision(), beforeGraph.nodes(), connections);
		if (!graph.replace(editName, replacement)) return;
		GLNodeGraphSnapshot afterGraph = graph.snapshot();
		history.record(editName, () -> graph.replace(editName, beforeGraph),
				() -> graph.replace(editName, afterGraph));
	}

	private static GLNodeEndpoint reconcileEndpoint(GLNodeEndpoint endpoint,
			Map<com.vingame.glswing.node.model.GLNodeId, GLGraphNode> nodes,
			String compositeType, Map<String, String> replacements) {
		GLGraphNode node = nodes.get(endpoint.nodeId());
		if (node == null || !node.typeId().equals(compositeType)
				|| !replacements.containsKey(endpoint.portId())) return endpoint;
		String port = replacements.get(endpoint.portId());
		return port == null ? null : new GLNodeEndpoint(endpoint.nodeId(), port);
	}

	private static GLCompositeNodeDescriptor descriptor(
			GLCompositeGraphRegistry composites, String assetId) {
		if (composites == null || assetId == null)
			throw new IllegalArgumentException("Composite registry and asset ID required");
		return composites.findAsset(assetId).flatMap(GLNodeGraphAsset::composite)
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown composite asset: " + assetId));
	}

	private static void validateMappings(GLNodeGraphAsset asset,
			GLNodeTypeRegistry types, List<GLCompositePortMapping> mappings) {
		for (GLCompositePortMapping mapping : mappings) {
			if (mapping == null) throw new IllegalArgumentException(
					"Composite port mappings must not contain null");
			if (!mapping.isBound()) continue;
			for (var endpoint : mapping.internalEndpoints()) {
            if(GLCompositeForwarding.isInput(endpoint)){
                if(!GLCompositeForwarding.valid(mapping,mappings))throw new IllegalArgumentException("Invalid public input forwarding target");
                continue;
            }
            GLGraphNode node = asset.graph().findNode(endpoint.nodeId())
					.orElseThrow(() -> new IllegalArgumentException(
							"Composite port references an unknown internal node"));
			GLNodeTypeDescriptor type = types.find(node.typeId()).orElseThrow(() ->
					new IllegalArgumentException("Unknown internal node type: " + node.typeId()));
			GLNodePortDescriptor internal = type.findPort(
					endpoint.portId()).orElseThrow(() ->
							new IllegalArgumentException(
									"Composite port references an unknown internal port"));
			if (internal.direction() != mapping.publicPort().direction())
				throw new IllegalArgumentException(
						"Public and internal port directions must match");
			}
            // Type mismatches are retained as diagnosable authoring state. This lets
			// users change a public contract without silently rewiring its graph.
		}
	}

	private static GLNodeGraphAsset withDescriptor(GLNodeGraphAsset asset,
			GLCompositeNodeDescriptor descriptor) {
		return new GLNodeGraphAsset(asset.id(), asset.version(), asset.kind(),
				asset.graph(), asset.viewState(), asset.metadata(), descriptor);
	}

	private static void apply(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, GLNodeGraphAsset asset) {
		GLCompositeNodeDescriptor descriptor = asset.compositeDescriptor();
		GLNodeGraphAsset previousAsset = composites.findAsset(asset.id())
				.orElseThrow(() -> new IllegalArgumentException(
						"Composite asset is not registered: " + asset.id()));
		GLNodeTypeDescriptor previous = types.find(descriptor.nodeTypeId())
				.orElseThrow(() -> new IllegalArgumentException(
						"Composite node type is not registered: "
								+ descriptor.nodeTypeId()));
		try {
			composites.replaceAsset(asset);
			types.replace(descriptor.nodeType());
		} catch (RuntimeException failure) {
			try { composites.replaceAsset(previousAsset); }
			catch (RuntimeException ignored) { }
			try { types.replace(previous); } catch (RuntimeException ignored) { }
			throw failure;
		}
	}
}
