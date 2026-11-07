package com.vingame.glswing.node.binding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.vingame.glswing.node.model.GLNodeId;

/** Immutable rendering-only state for a node-editor projection. */
public record GLNodeEditorViewState(Map<GLNodeId, GLNodeViewState> nodes,
		Map<String, GLNodeViewState> auxiliaryNodes, GLNodeCameraState camera) {
	public GLNodeEditorViewState {
		if (nodes == null || auxiliaryNodes == null)
			throw new IllegalArgumentException("Node view-state maps required");
		LinkedHashMap<GLNodeId, GLNodeViewState> copy = new LinkedHashMap<>();
		nodes.forEach((id, state) -> {
			if (id == null || state == null)
				throw new IllegalArgumentException("Node view state must be complete");
			copy.put(id, state);
		});
		nodes = Collections.unmodifiableMap(copy);
		LinkedHashMap<String, GLNodeViewState> auxiliaryCopy = new LinkedHashMap<>();
		auxiliaryNodes.forEach((id, state) -> {
			if (id == null || id.isBlank() || state == null)
				throw new IllegalArgumentException("Auxiliary view state must be complete");
			auxiliaryCopy.put(id, state);
		});
		auxiliaryNodes = Collections.unmodifiableMap(auxiliaryCopy);
	}

    public GLNodeEditorViewState(Map<GLNodeId,GLNodeViewState> nodes,Map<String,GLNodeViewState> auxiliaryNodes){this(nodes,auxiliaryNodes,null);}
    public GLNodeEditorViewState withNodes(Map<GLNodeId,GLNodeViewState> nodes){return new GLNodeEditorViewState(nodes,auxiliaryNodes,camera);}
    public GLNodeEditorViewState withCamera(GLNodeCameraState camera){return new GLNodeEditorViewState(nodes,auxiliaryNodes,camera);}
	/** Creates view state without editor-owned auxiliary projections. */
	public GLNodeEditorViewState(Map<GLNodeId, GLNodeViewState> nodes) {
		this(nodes, Map.of());
	}

	public static GLNodeEditorViewState empty() {
		return new GLNodeEditorViewState(Map.of(), Map.of());
	}
}
