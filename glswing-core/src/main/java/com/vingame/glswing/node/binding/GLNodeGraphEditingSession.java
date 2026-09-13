package com.vingame.glswing.node.binding;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodePlacement;
import com.vingame.glswing.node.composite.GLCompositeExtraction;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphFragment;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeState;
import com.vingame.glswing.node.model.GLNodeValue;

/**
 * Model-aware node clipboard and editing transactions for one bound editor.
 * Pasted nodes always receive fresh IDs and a complete operation occupies one
 * undo/redo entry.
 */
public final class GLNodeGraphEditingSession {
	public record PasteResult(Map<GLNodeId, GLNodeId> nodeIds,
			List<GLNodeId> insertedNodes) {
		public PasteResult {
			nodeIds = Map.copyOf(nodeIds);
			insertedNodes = List.copyOf(insertedNodes);
		}
	}

	private final GLNodeEditorBinding binding;
	private final GLHistoryManager history;
	private GLNodeGraphFragment clipboard;

	public GLNodeGraphEditingSession(GLNodeEditorBinding binding,
			GLHistoryManager history) {
		if (binding == null) throw new IllegalArgumentException("Binding required");
		this.binding = binding;
		this.history = history;
	}

	public boolean hasClipboardContent() { return clipboard != null; }
	public GLNodeGraphFragment clipboard() { return clipboard; }
	public void setClipboard(GLNodeGraphFragment fragment) { clipboard = fragment; }

	public GLNodeGraphFragment copySelection() {
		Set<GLNodeId> ids = selectedIds();
		if (ids.isEmpty()) return null;
		clipboard = GLNodeGraphFragment.capture(binding.graph().snapshot(),
				binding.captureViewState(), ids);
		return clipboard;
	}

	public GLNodeGraphFragment cutSelection() {
		GLNodeGraphFragment result = copySelection();
		if (result != null) removeNodes("Cut nodes", selectedIds());
		return result;
	}

	public PasteResult paste(float x, float y) {
		if (clipboard == null) return new PasteResult(Map.of(), List.of());
		return insert("Paste nodes", clipboard, x, y);
	}

	public GLNodePlacement beginPaste(float x, float y) {
		return clipboard == null ? null : beginPlacement("Paste nodes", clipboard, x, y);
	}

	public PasteResult duplicateSelection(float offsetX, float offsetY) {
		Set<GLNodeId> ids = selectedIds();
		if (ids.isEmpty()) return new PasteResult(Map.of(), List.of());
		GLNodeGraphFragment fragment = GLNodeGraphFragment.capture(
				binding.graph().snapshot(), binding.captureViewState(), ids);
		return insert("Duplicate nodes", fragment,
				fragment.minimumX() + offsetX, fragment.minimumY() + offsetY);
	}

	public GLNodePlacement beginDuplicate(float x, float y) {
		Set<GLNodeId> ids = selectedIds();
		if (ids.isEmpty()) return null;
		return beginPlacement("Duplicate nodes", GLNodeGraphFragment.capture(
				binding.graph().snapshot(), binding.captureViewState(), ids), x, y);
	}

	public GLNodePlacement beginMoveSelection(float x, float y) {
		List<GLNodeId> ids = List.copyOf(selectedIds());
		if (ids.isEmpty()) return null;
		GLNodeGraphSnapshot graph = binding.graph().snapshot();
		GLNodeEditorViewState view = binding.captureViewState();
		return new Placement(ids.size() == 1 ? "Move node" : "Move nodes", ids,
				x, y, graph, view, graph, view, ids);
	}

	/**
	 * Replaces the current selection with a reusable composite and registers its
	 * public contract for validation, projection, and palette discovery.
	 */
	public GLCompositeExtraction.Result extractSelection(String assetId,
			String nodeTypeId, String displayName, String category,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog) {
		if (displayName == null || displayName.isBlank() || category == null
				|| category.isBlank() || composites == null || catalog == null)
			throw new IllegalArgumentException("Composite registration metadata required");
		Set<GLNodeId> selection = selectedIds();
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		GLCompositeExtraction.Result result = GLCompositeExtraction.extract(beforeGraph,
				beforeView, selection, binding.types(), assetId, nodeTypeId);
		GLNodeCatalogEntry catalogEntry = new GLNodeCatalogEntry(
				result.asset().compositeDescriptor().nodeType(), displayName, category,
				Map.of(), "composite", "group");
		register(result, composites, catalog, catalogEntry);
		try {
			binding.graph().replace("Extract composite", result.graph());
			binding.applyViewState(result.viewState());
			binding.refresh();
		} catch (RuntimeException failure) {
			unregister(result, composites, catalog);
			throw failure;
		}
		GLNodeGraphSnapshot afterGraph = binding.graph().snapshot();
		if (history != null) history.record("Extract composite", () -> {
			restore("Undo extract composite", beforeGraph, beforeView);
			unregister(result, composites, catalog);
			binding.refresh();
		}, () -> {
			register(result, composites, catalog, catalogEntry);
			restore("Redo extract composite", afterGraph, result.viewState());
			binding.refresh();
		});
		select(List.of(result.compositeNode().id()));
		return result;
	}

	public void removeSelection() {
		Set<GLNodeId> ids = selectedIds();
		if (!ids.isEmpty()) removeNodes(ids.size() == 1 ? "Delete node" : "Delete nodes", ids);
	}

	public void toggleHiddenSelection() {
		toggleSelectionState("Toggle hidden nodes", GLNodeState.HIDDEN,
				node -> GLNodeState.isHidden(node));
	}

	public void toggleMutedSelection() {
		toggleSelectionState("Toggle muted nodes", GLNodeState.MUTED,
				node -> GLNodeState.isMuted(node));
	}

	private void toggleSelectionState(String name, String property,
			java.util.function.Predicate<GLGraphNode> enabled) {
		Set<GLNodeId> ids = selectedIds();
		if (ids.isEmpty()) return;
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		boolean next = ids.stream().map(beforeGraph::findNode)
				.flatMap(java.util.Optional::stream).anyMatch(enabled.negate());
		binding.graph().edit(name, edit -> ids.forEach(id -> {
			if (next) edit.setProperty(id, property, GLNodeValue.of(true));
			else edit.removeProperty(id, property);
		}));
		record(name, beforeGraph, beforeView, binding.graph().snapshot(),
				binding.captureViewState());
	}

	private PasteResult insert(String name, GLNodeGraphFragment fragment,
			float x, float y) {
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		PasteResult result = insertUnrecorded(name, fragment, x, y, beforeView);
		record(name, beforeGraph, beforeView, binding.graph().snapshot(),
				binding.captureViewState());
		return result;
	}

	private GLNodePlacement beginPlacement(String name, GLNodeGraphFragment fragment,
			float x, float y) {
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		List<GLNodeId> beforeSelection = List.copyOf(selectedIds());
		PasteResult result = insertUnrecorded(name, fragment, x, y, beforeView);
		GLNodeGraphSnapshot afterGraph = binding.graph().snapshot();
		GLNodeEditorViewState initialView = binding.captureViewState();
		return new Placement(name, result.insertedNodes(), x, y, beforeGraph,
				beforeView, afterGraph, initialView, beforeSelection);
	}

	private PasteResult insertUnrecorded(String name, GLNodeGraphFragment fragment,
			float x, float y, GLNodeEditorViewState beforeView) {
		if (!Float.isFinite(x) || !Float.isFinite(y))
			throw new IllegalArgumentException("Paste position must be finite");
		GLNodeGraph graph = binding.graph();
		for (GLGraphNode node : fragment.nodes())
			if (binding.types().find(node.typeId()).isEmpty())
				throw new IllegalArgumentException(
						"Clipboard requires an unavailable node type: " + node.typeId());
		LinkedHashMap<GLNodeId, GLNodeId> ids = new LinkedHashMap<>();
		for (GLGraphNode node : fragment.nodes()) ids.put(node.id(), GLNodeId.random());
		float dx = x - fragment.minimumX();
		float dy = y - fragment.minimumY();
		LinkedHashMap<GLNodeId, GLNodeViewState> nextViews =
				new LinkedHashMap<>(beforeView.nodes());
		for (Map.Entry<GLNodeId, GLNodeId> entry : ids.entrySet()) {
			GLNodeViewState source = fragment.viewStates().get(entry.getKey());
			nextViews.put(entry.getValue(), new GLNodeViewState(source.x() + dx,
					source.y() + dy, source.width(), source.height()));
		}
		graph.edit(name, edit -> {
			for (GLGraphNode node : fragment.nodes())
				edit.addNode(new GLGraphNode(ids.get(node.id()), node.typeId(),
						node.typeVersion(), node.properties()));
			for (GLGraphConnection connection : fragment.connections())
				edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
						remap(connection.output(), ids), remap(connection.input(), ids)));
		});
		GLNodeEditorViewState afterView = new GLNodeEditorViewState(nextViews);
		binding.applyViewState(afterView);
		select(ids.values());
		return new PasteResult(ids, List.copyOf(ids.values()));
	}

	private void removeNodes(String name, Set<GLNodeId> ids) {
		GLNodeGraph graph = binding.graph();
		GLNodeGraphSnapshot beforeGraph = graph.snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		graph.edit(name, edit -> ids.forEach(edit::removeNode));
		LinkedHashMap<GLNodeId, GLNodeViewState> remaining =
				new LinkedHashMap<>(beforeView.nodes());
		ids.forEach(remaining::remove);
		GLNodeEditorViewState afterView = new GLNodeEditorViewState(remaining);
		binding.applyViewState(afterView);
		record(name, beforeGraph, beforeView, graph.snapshot(), afterView);
	}

	private void record(String name, GLNodeGraphSnapshot beforeGraph,
			GLNodeEditorViewState beforeView, GLNodeGraphSnapshot afterGraph,
			GLNodeEditorViewState afterView) {
		if (history != null) history.record(name,
				() -> restore(name, beforeGraph, beforeView),
				() -> restore(name, afterGraph, afterView));
	}

	private void restore(String name, GLNodeGraphSnapshot graph,
			GLNodeEditorViewState view) {
		binding.graph().replace(name, graph);
		binding.applyViewState(view);
	}

	private Set<GLNodeId> selectedIds() {
		LinkedHashSet<GLNodeId> result = new LinkedHashSet<>();
		for (GLNode node : binding.editor().getSelectedNodes())
			binding.findNodeId(node).ifPresent(result::add);
		return result;
	}

	private void select(java.util.Collection<GLNodeId> ids) {
		binding.editor().setSelectedNodes(ids.stream().map(binding::findNodeView)
				.flatMap(java.util.Optional::stream).toList());
	}

	private static GLNodeEndpoint remap(GLNodeEndpoint endpoint,
			Map<GLNodeId, GLNodeId> ids) {
		return new GLNodeEndpoint(ids.get(endpoint.nodeId()), endpoint.portId());
	}

	private final class Placement implements GLNodePlacement {
		private final String name;
		private final List<GLNodeId> nodes;
		private final float originX;
		private final float originY;
		private final GLNodeGraphSnapshot beforeGraph;
		private final GLNodeEditorViewState beforeView;
		private final GLNodeGraphSnapshot afterGraph;
		private final GLNodeEditorViewState initialView;
		private final List<GLNodeId> cancelSelection;
		private boolean finished;

		private Placement(String name, List<GLNodeId> nodes, float originX,
				float originY, GLNodeGraphSnapshot beforeGraph,
				GLNodeEditorViewState beforeView, GLNodeGraphSnapshot afterGraph,
				GLNodeEditorViewState initialView,
				List<GLNodeId> cancelSelection) {
			this.name = name; this.nodes = nodes; this.originX = originX;
			this.originY = originY; this.beforeGraph = beforeGraph;
			this.beforeView = beforeView; this.afterGraph = afterGraph;
			this.initialView = initialView;
			this.cancelSelection = List.copyOf(cancelSelection);
		}

		@Override public void moveTo(float graphX, float graphY) {
			if (finished || !Float.isFinite(graphX) || !Float.isFinite(graphY)) return;
			float dx = graphX - originX, dy = graphY - originY;
			for (GLNodeId id : nodes) {
				GLNodeViewState state = initialView.nodes().get(id);
				binding.findNodeView(id).ifPresent(node ->
						node.setLocation(state.x() + dx, state.y() + dy));
			}
		}

		@Override public void confirm() {
			if (finished) return;
			finished = true;
			GLNodeEditorViewState finalView = binding.captureViewState();
			if (!sameContent(beforeGraph, afterGraph) || !beforeView.equals(finalView))
				record(name, beforeGraph, beforeView, afterGraph, finalView);
			select(nodes);
			binding.editor().requestGLFocus(binding.editor());
		}

		@Override public void cancel() {
			if (finished) return;
			finished = true;
			restore("Cancel " + name, beforeGraph, beforeView);
			select(cancelSelection);
			binding.editor().requestGLFocus(binding.editor());
		}
	}

	private static boolean sameContent(GLNodeGraphSnapshot left,
			GLNodeGraphSnapshot right) {
		return left.nodes().equals(right.nodes())
				&& left.connections().equals(right.connections());
	}

	private void register(GLCompositeExtraction.Result result,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog,
			GLNodeCatalogEntry catalogEntry) {
		composites.registerAsset(result.asset());
		try {
			binding.types().register(result.asset().compositeDescriptor().nodeType());
			try { catalog.register(catalogEntry); }
			catch (RuntimeException failure) {
				binding.types().unregister(result.compositeNode().typeId());
				throw failure;
			}
		} catch (RuntimeException failure) {
			composites.unregisterAsset(result.asset().id());
			throw failure;
		}
	}

	private void unregister(GLCompositeExtraction.Result result,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog) {
		catalog.unregister(result.compositeNode().typeId());
		binding.types().unregister(result.compositeNode().typeId());
		composites.unregisterAsset(result.asset().id());
	}
}
