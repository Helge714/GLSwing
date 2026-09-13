package com.vingame.glswing.node.composite;

import java.util.Map;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;

/** Applies validated composite-instance properties as one graph/history edit. */
public final class GLCompositePropertyTransactions {
	private GLCompositePropertyTransactions() { }

	public static boolean set(GLNodeGraph graph, GLCompositeNodeDescriptor descriptor,
			GLNodeId nodeId, Map<String, GLNodeValue> values,
			GLHistoryManager history, String editName) {
		if (graph == null || descriptor == null || nodeId == null || values == null
				|| history == null || editName == null || editName.isBlank())
			throw new IllegalArgumentException("Complete composite property edit required");
		GLGraphNode node = graph.snapshot().findNode(nodeId).orElseThrow(() ->
				new IllegalArgumentException("Unknown composite node: " + nodeId));
		if (!node.typeId().equals(descriptor.nodeTypeId()))
			throw new IllegalArgumentException("Node does not use the composite descriptor");
		for (Map.Entry<String, GLNodeValue> entry : values.entrySet()) {
			GLCompositePropertyDescriptor property = descriptor.findProperty(entry.getKey())
					.orElseThrow(() -> new IllegalArgumentException(
							"Unknown composite property: " + entry.getKey()));
			if (!property.accepts(entry.getValue()))
				throw new IllegalArgumentException(
						"Invalid value for composite property: " + entry.getKey());
		}
		GLNodeGraphSnapshot before = graph.snapshot();
		boolean changed = graph.edit(editName, edit -> values.forEach(
				(key, value) -> edit.setProperty(nodeId, key, value)));
		if (!changed) return false;
		GLNodeGraphSnapshot after = graph.snapshot();
		history.record(editName, () -> graph.replace(editName, before),
				() -> graph.replace(editName, after));
		return true;
	}

	/** Removes an override so the descriptor default is used again. */
	public static boolean reset(GLNodeGraph graph, GLCompositeNodeDescriptor descriptor,
			GLNodeId nodeId, String propertyId, GLHistoryManager history,
			String editName) {
		if (descriptor == null || descriptor.findProperty(propertyId).isEmpty())
			throw new IllegalArgumentException("Unknown composite property: " + propertyId);
		if (graph == null || nodeId == null || history == null
				|| editName == null || editName.isBlank())
			throw new IllegalArgumentException("Complete composite property reset required");
		GLGraphNode node = graph.snapshot().findNode(nodeId).orElseThrow(() ->
				new IllegalArgumentException("Unknown composite node: " + nodeId));
		if (!node.typeId().equals(descriptor.nodeTypeId()))
			throw new IllegalArgumentException("Node does not use the composite descriptor");
		if (!node.properties().containsKey(propertyId)) return false;
		GLNodeGraphSnapshot before = graph.snapshot();
		graph.edit(editName, edit -> edit.removeProperty(nodeId, propertyId));
		GLNodeGraphSnapshot after = graph.snapshot();
		history.record(editName, () -> graph.replace(editName, before),
				() -> graph.replace(editName, after));
		return true;
	}
}
