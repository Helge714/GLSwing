package com.vingame.glswing.node.model;

import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable, backend-neutral snapshot of one graph revision. */
public record GLNodeGraphSnapshot(GLNodeGraphId graphId, long revision,
		List<GLGraphNode> nodes, List<GLGraphConnection> connections) {
	public GLNodeGraphSnapshot {
		if (graphId == null || revision < 0 || nodes == null || connections == null)
			throw new IllegalArgumentException("Invalid graph snapshot metadata");
		nodes = List.copyOf(nodes);
		connections = List.copyOf(connections);
		validate(nodes, connections);
	}

	public Optional<GLGraphNode> findNode(GLNodeId id) {
		return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
	}

	public Optional<GLGraphConnection> findConnection(GLNodeConnectionId id) {
		return connections.stream()
				.filter(connection -> connection.id().equals(id)).findFirst();
	}

	public Map<GLNodeId, GLGraphNode> nodesById() {
		LinkedHashMap<GLNodeId, GLGraphNode> result = new LinkedHashMap<>();
		for (GLGraphNode node : nodes) result.put(node.id(), node);
		return Collections.unmodifiableMap(result);
	}

	private static void validate(List<GLGraphNode> nodes,
			List<GLGraphConnection> connections) {
		Set<GLNodeId> nodeIds = new HashSet<>();
		for (GLGraphNode node : nodes)
			if (node == null || !nodeIds.add(node.id()))
				throw new IllegalArgumentException("Duplicate or null graph node");
		Set<GLNodeConnectionId> connectionIds = new HashSet<>();
		Set<EndpointPair> edges = new HashSet<>();
		for (GLGraphConnection connection : connections) {
			if (connection == null || !connectionIds.add(connection.id()))
				throw new IllegalArgumentException("Duplicate or null graph connection");
			if (!nodeIds.contains(connection.output().nodeId())
					|| !nodeIds.contains(connection.input().nodeId()))
				throw new IllegalArgumentException(
						"Connection references a node outside the graph");
			if (!edges.add(new EndpointPair(connection.output(), connection.input())))
				throw new IllegalArgumentException("Duplicate graph edge");
		}
	}

	private record EndpointPair(GLNodeEndpoint output, GLNodeEndpoint input) { }
}
