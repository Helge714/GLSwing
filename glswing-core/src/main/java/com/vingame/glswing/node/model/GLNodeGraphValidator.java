package com.vingame.glswing.node.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates a graph against registered node contracts and a cycle policy. */
public final class GLNodeGraphValidator {
	private GLNodeGraphValidator() { }

	public static GLNodeGraphValidationResult validate(GLNodeGraphSnapshot graph,
			GLNodeTypeRegistry registry, GLGraphCyclePolicy cyclePolicy) {
		if (graph == null || registry == null || cyclePolicy == null)
			throw new IllegalArgumentException(
					"Graph, node registry, and cycle policy are required");
		Map<String, GLNodeTypeDescriptor> types = new LinkedHashMap<>();
		for (GLNodeTypeDescriptor type : registry.descriptors())
			types.put(type.id(), type);
		Map<GLNodeId, GLGraphNode> nodes = graph.nodesById();
		List<GLGraphDiagnostic> diagnostics = new ArrayList<>();
		Map<GLNodeConnectionId, ResolvedConnection> resolved = new LinkedHashMap<>();
		Map<PortKey, Integer> connectionCounts = new HashMap<>();

		for (GLGraphNode node : graph.nodes())
			if (!types.containsKey(node.typeId()))
				diagnostics.add(error("node.unknown-type",
						"Unknown node type: " + node.typeId(), Set.of(node.id()), Set.of()));

		for (GLGraphConnection connection : graph.connections()) {
			GLGraphNode outputNode = nodes.get(connection.output().nodeId());
			GLGraphNode inputNode = nodes.get(connection.input().nodeId());
			GLNodePortDescriptor output = findPort(types, outputNode,
					connection.output(), connection.id(), diagnostics);
			GLNodePortDescriptor input = findPort(types, inputNode,
					connection.input(), connection.id(), diagnostics);
			if (output == null || input == null) continue;
			boolean valid = true;
			if (output.direction() != GLPortDirection.OUTPUT
					|| input.direction() != GLPortDirection.INPUT) {
				diagnostics.add(error("connection.direction",
						"A connection must lead from an output port to an input port",
						Set.of(outputNode.id(), inputNode.id()), Set.of(connection.id())));
				valid = false;
			}
			if (!output.valueTypeId().equals(input.valueTypeId())) {
				diagnostics.add(error("connection.type",
						"Incompatible port types: " + output.valueTypeId() + " and "
								+ input.valueTypeId(),
						Set.of(outputNode.id(), inputNode.id()), Set.of(connection.id())));
				valid = false;
			}
			increment(connectionCounts, connection.output());
			increment(connectionCounts, connection.input());
			if (valid) resolved.put(connection.id(),
					new ResolvedConnection(connection, output));
		}

		for (GLGraphNode node : graph.nodes()) {
			GLNodeTypeDescriptor type = types.get(node.typeId());
			if (type == null) continue;
			for (GLNodePortDescriptor port : type.ports()) {
				int count = connectionCounts.getOrDefault(new PortKey(node.id(), port.id()), 0);
				if (port.required() && count == 0)
					diagnostics.add(error("port.required",
							"Required input is not connected: " + port.id(),
							Set.of(node.id()), Set.of()));
				if (count > port.maximumConnections())
					diagnostics.add(error("port.connection-limit",
							"Port " + port.id() + " accepts at most "
									+ port.maximumConnections() + " connection(s)",
							Set.of(node.id()), connectionsFor(graph, node.id(), port.id())));
			}
		}

		validateCycles(graph, resolved, cyclePolicy, diagnostics);
		return new GLNodeGraphValidationResult(graph, diagnostics);
	}

	private static GLNodePortDescriptor findPort(
			Map<String, GLNodeTypeDescriptor> types, GLGraphNode node,
			GLNodeEndpoint endpoint, GLNodeConnectionId connectionId,
			List<GLGraphDiagnostic> diagnostics) {
		GLNodeTypeDescriptor type = types.get(node.typeId());
		if (type == null) return null;
		GLNodePortDescriptor port = type.findPort(endpoint.portId()).orElse(null);
		if (port == null)
			diagnostics.add(error("port.unknown", "Unknown port " + endpoint.portId()
					+ " on node type " + node.typeId(), Set.of(node.id()),
					Set.of(connectionId)));
		return port;
	}

	private static void validateCycles(GLNodeGraphSnapshot graph,
			Map<GLNodeConnectionId, ResolvedConnection> resolved,
			GLGraphCyclePolicy policy, List<GLGraphDiagnostic> diagnostics) {
		if (policy == GLGraphCyclePolicy.PREVIOUS_STEP) return;
		Map<GLNodeId, List<GLNodeId>> adjacency = new LinkedHashMap<>();
		for (GLGraphNode node : graph.nodes()) adjacency.put(node.id(), new ArrayList<>());
		for (ResolvedConnection value : resolved.values()) {
			if (policy == GLGraphCyclePolicy.DELAYED_FEEDBACK
					&& value.output().timing() == GLPortTiming.PREVIOUS_STEP)
				continue;
			adjacency.get(value.connection().output().nodeId())
					.add(value.connection().input().nodeId());
		}
		for (Set<GLNodeId> component : stronglyConnected(adjacency)) {
			boolean selfLoop = component.size() == 1 && adjacency
					.get(component.iterator().next()).contains(component.iterator().next());
			if (component.size() < 2 && !selfLoop) continue;
			LinkedHashSet<GLNodeConnectionId> affected = new LinkedHashSet<>();
			for (ResolvedConnection value : resolved.values()) {
				if (policy == GLGraphCyclePolicy.DELAYED_FEEDBACK
						&& value.output().timing() == GLPortTiming.PREVIOUS_STEP)
					continue;
				GLGraphConnection edge = value.connection();
				if (component.contains(edge.output().nodeId())
						&& component.contains(edge.input().nodeId()))
					affected.add(edge.id());
			}
			diagnostics.add(error("graph.cycle",
					"Dependency cycle requires a previous-step feedback path",
					component, affected));
		}
	}

	private static List<Set<GLNodeId>> stronglyConnected(
			Map<GLNodeId, List<GLNodeId>> adjacency) {
		List<Set<GLNodeId>> result = new ArrayList<>();
		Map<GLNodeId, Integer> indices = new HashMap<>();
		Map<GLNodeId, Integer> lowLinks = new HashMap<>();
		Deque<GLNodeId> stack = new ArrayDeque<>();
		Set<GLNodeId> onStack = new HashSet<>();
		int[] nextIndex = { 0 };
		for (GLNodeId node : adjacency.keySet())
			if (!indices.containsKey(node))
				visit(node, adjacency, indices, lowLinks, stack, onStack,
						nextIndex, result);
		return result;
	}

	private static void visit(GLNodeId node,
			Map<GLNodeId, List<GLNodeId>> adjacency,
			Map<GLNodeId, Integer> indices, Map<GLNodeId, Integer> lowLinks,
			Deque<GLNodeId> stack, Set<GLNodeId> onStack, int[] nextIndex,
			List<Set<GLNodeId>> result) {
		indices.put(node, nextIndex[0]);
		lowLinks.put(node, nextIndex[0]++);
		stack.push(node);
		onStack.add(node);
		for (GLNodeId target : adjacency.get(node)) {
			if (!indices.containsKey(target)) {
				visit(target, adjacency, indices, lowLinks, stack, onStack,
						nextIndex, result);
				lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(target)));
			} else if (onStack.contains(target)) {
				lowLinks.put(node, Math.min(lowLinks.get(node), indices.get(target)));
			}
		}
		if (!lowLinks.get(node).equals(indices.get(node))) return;
		LinkedHashSet<GLNodeId> component = new LinkedHashSet<>();
		GLNodeId member;
		do {
			member = stack.pop();
			onStack.remove(member);
			component.add(member);
		} while (!member.equals(node));
		result.add(component);
	}

	private static void increment(Map<PortKey, Integer> counts,
			GLNodeEndpoint endpoint) {
		counts.merge(new PortKey(endpoint.nodeId(), endpoint.portId()), 1, Integer::sum);
	}

	private static Set<GLNodeConnectionId> connectionsFor(
			GLNodeGraphSnapshot graph, GLNodeId nodeId, String portId) {
		LinkedHashSet<GLNodeConnectionId> result = new LinkedHashSet<>();
		GLNodeEndpoint endpoint = new GLNodeEndpoint(nodeId, portId);
		for (GLGraphConnection connection : graph.connections())
			if (connection.output().equals(endpoint) || connection.input().equals(endpoint))
				result.add(connection.id());
		return result;
	}

	private static GLGraphDiagnostic error(String code, String message,
			Set<GLNodeId> nodes, Set<GLNodeConnectionId> connections) {
		return new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR, code,
				message, nodes, connections);
	}

	private record PortKey(GLNodeId nodeId, String portId) { }
	private record ResolvedConnection(GLGraphConnection connection,
			GLNodePortDescriptor output) { }
}
