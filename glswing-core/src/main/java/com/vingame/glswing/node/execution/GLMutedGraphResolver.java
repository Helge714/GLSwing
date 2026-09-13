package com.vingame.glswing.node.execution;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeMuteBypass;
import com.vingame.glswing.node.model.GLNodeState;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;

/** Rewrites muted nodes into deterministic direct graph connections. */
public final class GLMutedGraphResolver {
	private GLMutedGraphResolver() { }

	public static GLNodeGraphSnapshot resolve(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry types) {
		if (graph == null || types == null)
			throw new IllegalArgumentException("Graph and node types required");
		Set<GLNodeId> muted = new HashSet<>();
		for (GLGraphNode node : graph.nodes())
			if (GLNodeState.isMuted(node)) muted.add(node.id());
		if (muted.isEmpty()) return graph;
		List<GLGraphNode> nodes = graph.nodes().stream()
				.filter(node -> !muted.contains(node.id())).toList();
		List<GLGraphConnection> connections = new ArrayList<>();
		for (GLGraphConnection connection : graph.connections()) {
			if (muted.contains(connection.output().nodeId())) continue;
			if (!muted.contains(connection.input().nodeId())) {
				connections.add(connection);
				continue;
			}
			forward(graph, types, muted, connection.output(), connection.input(),
					new HashSet<>(), connections);
		}
		return new GLNodeGraphSnapshot(graph.graphId(), graph.revision(), nodes,
				connections);
	}

	private static void forward(GLNodeGraphSnapshot graph, GLNodeTypeRegistry types,
			Set<GLNodeId> muted, GLNodeEndpoint source, GLNodeEndpoint mutedInput,
			Set<GLNodeId> path, List<GLGraphConnection> result) {
		if (!path.add(mutedInput.nodeId())) return;
		GLGraphNode node = graph.findNode(mutedInput.nodeId()).orElse(null);
		if (node == null) return;
		for (GLNodeMuteBypass bypass : types.muteBypasses(node.typeId())) {
			if (!bypass.inputPortId().equals(mutedInput.portId())) continue;
			for (GLGraphConnection outgoing : graph.connections()) {
				if (!outgoing.output().nodeId().equals(node.id())
						|| !outgoing.output().portId().equals(bypass.outputPortId())) continue;
				if (muted.contains(outgoing.input().nodeId()))
					forward(graph, types, muted, source, outgoing.input(),
							new HashSet<>(path), result);
				else addResolved(result, source, outgoing.input(), path);
			}
		}
	}

	private static void addResolved(List<GLGraphConnection> result,
			GLNodeEndpoint source, GLNodeEndpoint target, Set<GLNodeId> path) {
		if (result.stream().anyMatch(connection -> connection.output().equals(source)
				&& connection.input().equals(target))) return;
		result.add(new GLGraphConnection(derivedId(source, target, path), source, target));
	}

	private static GLNodeConnectionId derivedId(GLNodeEndpoint source,
			GLNodeEndpoint target, Set<GLNodeId> path) {
		String route = path.stream().map(GLNodeId::toString).sorted()
				.collect(java.util.stream.Collectors.joining(","));
		String key = source.nodeId() + ":" + source.portId() + "->"
				+ target.nodeId() + ":" + target.portId() + ":" + route;
		return new GLNodeConnectionId(UUID.nameUUIDFromBytes(
				key.getBytes(StandardCharsets.UTF_8)));
	}
}
