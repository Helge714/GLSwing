package com.vingame.glswing.node.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;

/**
 * Portable clipboard representation of a node selection. Connections to nodes
 * outside the selection are intentionally excluded.
 */
public record GLNodeGraphFragment(List<GLGraphNode> nodes,
		List<GLGraphConnection> connections,
		Map<GLNodeId, GLNodeViewState> viewStates, float pointerOffsetX, float pointerOffsetY) {
	public GLNodeGraphFragment(List<GLGraphNode> nodes, List<GLGraphConnection> connections,
			Map<GLNodeId, GLNodeViewState> viewStates) {
		this(nodes,connections,viewStates,0,0);
	}
	public GLNodeGraphFragment withPointerAnchor(float x, float y) {
		return new GLNodeGraphFragment(nodes,connections,viewStates,x-minimumX(),y-minimumY());
	}
	public GLNodeGraphFragment {
		if (!Float.isFinite(pointerOffsetX) || !Float.isFinite(pointerOffsetY))
			throw new IllegalArgumentException("Finite clipboard pointer anchor required");
		if (nodes == null || nodes.isEmpty() || connections == null
				|| viewStates == null)
			throw new IllegalArgumentException("A non-empty graph fragment is required");
		nodes = List.copyOf(nodes);
		connections = List.copyOf(connections);
		LinkedHashSet<GLNodeId> ids = new LinkedHashSet<>();
		for (GLGraphNode node : nodes)
			if (node == null || !ids.add(node.id()))
				throw new IllegalArgumentException("Fragment node IDs must be unique");
		for (GLGraphConnection connection : connections)
			if (connection == null || !ids.contains(connection.output().nodeId())
					|| !ids.contains(connection.input().nodeId()))
				throw new IllegalArgumentException(
						"Fragment connections must remain inside the fragment");
		LinkedHashMap<GLNodeId, GLNodeViewState> views = new LinkedHashMap<>();
		for (GLNodeId id : ids) {
			GLNodeViewState state = viewStates.get(id);
			if (state == null)
				throw new IllegalArgumentException("Every fragment node requires view state");
			views.put(id, state);
		}
		viewStates = Collections.unmodifiableMap(views);
	}

	public static GLNodeGraphFragment capture(GLNodeGraphSnapshot graph,
			GLNodeEditorViewState viewState, Set<GLNodeId> selection) {
		if (graph == null || viewState == null || selection == null
				|| selection.isEmpty())
			throw new IllegalArgumentException("Graph, view state, and selection required");
		List<GLGraphNode> nodes = graph.nodes().stream()
				.filter(node -> selection.contains(node.id())).toList();
		if (nodes.size() != selection.size())
			throw new IllegalArgumentException("Selection contains nodes outside the graph");
		List<GLGraphConnection> connections = graph.connections().stream()
				.filter(connection -> selection.contains(connection.output().nodeId())
						&& selection.contains(connection.input().nodeId())).toList();
		LinkedHashMap<GLNodeId, GLNodeViewState> views = new LinkedHashMap<>();
		for (GLGraphNode node : nodes) {
			GLNodeViewState state = viewState.nodes().get(node.id());
			if (state == null) state = new GLNodeViewState(0, 0);
			views.put(node.id(), state);
		}
		return new GLNodeGraphFragment(nodes, connections, views);
	}

	public float minimumX() {
		return (float) viewStates.values().stream().mapToDouble(GLNodeViewState::x)
				.min().orElse(0);
	}
	public float minimumY() {
		return (float) viewStates.values().stream().mapToDouble(GLNodeViewState::y)
				.min().orElse(0);
	}
}
