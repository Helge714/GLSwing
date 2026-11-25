package com.vingame.glswing.node.binding;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
	private final Consumer<GLNodeEditorViewState> inactiveViewStateConsumer;
	private GLNodeGraphFragment clipboard;

	public GLNodeGraphEditingSession(GLNodeEditorBinding binding,
			GLHistoryManager history) {
		this(binding, history, state -> { });
	}

	/**
	 * Creates a session whose history can restore view state after its binding
	 * has been closed, for example while another composite level is visible.
	 */
	public GLNodeGraphEditingSession(GLNodeEditorBinding binding,
			GLHistoryManager history,
			Consumer<GLNodeEditorViewState> inactiveViewStateConsumer) {
		if (binding == null) throw new IllegalArgumentException("Binding required");
		this.binding = binding;
		this.history = history;
		this.inactiveViewStateConsumer = inactiveViewStateConsumer == null
				? state -> { } : inactiveViewStateConsumer;
        binding.editor().setConnectionHistoryHandler((before,after)->{
            var previous=binding.captureConnections(before);var next=binding.captureConnections(after);
            if(previous.equals(next))return; // Boundary wires have their own interface transaction.
            var snapshot=binding.graph().snapshot();var view=binding.captureViewState();
            // Unknown/plugin node types may have model wires without any visual projection.
            var restored=new LinkedHashMap<GLNodeConnectionId,GLGraphConnection>();
            snapshot.connections().forEach(c->restored.put(c.id(),c));
            next.forEach(c->restored.remove(c.id()));previous.forEach(c->restored.put(c.id(),c));
            record("Change node connection",
                new GLNodeGraphSnapshot(snapshot.graphId(),snapshot.revision(),snapshot.nodes(),List.copyOf(restored.values())),view,
                snapshot,view);
        });
        binding.editor().setConnectionInsertionHandler(new com.vingame.glswing.node.GLNodeEditor.ConnectionInsertionHandler() {
            public boolean canInsert(GLNode node, com.vingame.glswing.node.GLNodeConnection connection) {
                return canInsertIntoConnection(binding.findNodeId(node).orElse(null), connection.getModelId());
            }
            public boolean insert(GLNode node, com.vingame.glswing.node.GLNodeConnection connection, float x, float y) {
                return insertIntoConnection(binding.findNodeId(node).orElse(null), connection.getModelId(), x, y);
            }
        });
	}

    public boolean canInsertIntoConnection(GLNodeId nodeId, GLNodeConnectionId connectionId) {
        if (binding.isClosed() || nodeId == null || connectionId == null) return false;
        var graph = binding.graph().snapshot();
        var node = graph.findNode(nodeId).orElse(null);
        var edge = graph.findConnection(connectionId).orElse(null);
        if (node == null || edge == null || edge.output().nodeId().equals(edge.input().nodeId())) return false;
        if (graph.connections().stream().anyMatch(c -> c.input().nodeId().equals(nodeId) || c.output().nodeId().equals(nodeId))) return false;
        var ports = binding.types().insertionPorts(node.typeId()).orElse(null);
        if (ports == null) return false;
        var type = binding.types().find(node.typeId()).orElseThrow();
        var input = type.findPort(ports.inputPortId()).orElse(null);
        var output = type.findPort(ports.outputPortId()).orElse(null);
        var from = graph.findNode(edge.output().nodeId()).flatMap(n -> binding.types().find(n.typeId()))
                .flatMap(t -> t.findPort(edge.output().portId())).orElse(null);
        var to = graph.findNode(edge.input().nodeId()).flatMap(n -> binding.types().find(n.typeId()))
                .flatMap(t -> t.findPort(edge.input().portId())).orElse(null);
        return input != null && output != null && from != null && to != null
                && input.direction() == com.vingame.glswing.node.model.GLPortDirection.INPUT
                && output.direction() == com.vingame.glswing.node.model.GLPortDirection.OUTPUT
                && from.direction() == com.vingame.glswing.node.model.GLPortDirection.OUTPUT
                && to.direction() == com.vingame.glswing.node.model.GLPortDirection.INPUT
                && input.maximumConnections() > 0 && output.maximumConnections() > 0
                && binding.types().compatibleValueTypes(from.valueTypeId(),input.valueTypeId()) && binding.types().compatibleValueTypes(output.valueTypeId(),to.valueTypeId())
                && binding.findNodeView(nodeId).isPresent()
                && binding.findNodeView(edge.output().nodeId()).isPresent()
                && binding.findNodeView(edge.input().nodeId()).isPresent();
    }

    /** Splits one wire and makes room in one history entry, including the preceding drag. */
    public boolean insertIntoConnection(GLNodeId nodeId, GLNodeConnectionId connectionId, float startX, float startY) {
        return insertIntoConnection(nodeId,connectionId,startX,startY,true);
    }
    private boolean insertIntoConnection(GLNodeId nodeId, GLNodeConnectionId connectionId, float startX, float startY, boolean recordHistory) {
        if (!Float.isFinite(startX) || !Float.isFinite(startY) || !canInsertIntoConnection(nodeId, connectionId)) return false;
        var beforeGraph = binding.graph().snapshot();
        var currentView = binding.captureViewState();
        var beforeBounds = new LinkedHashMap<>(currentView.nodes());
        var node = beforeBounds.get(nodeId);
        beforeBounds.put(nodeId, new GLNodeViewState(startX,startY,node.width(),node.height(),node.customTitle()));
        var beforeView = currentView.withNodes(beforeBounds);
        var edge = beforeGraph.findConnection(connectionId).orElseThrow();
        var from = currentView.nodes().get(edge.output().nodeId());
        var to = currentView.nodes().get(edge.input().nodeId());
        float gap = binding.editor().getHorizontalNodeSpacing();
        float left = from.x()+from.width()+gap;
        float right = to.x()-gap-node.width();
        float x = right >= left ? Math.max(left,Math.min(node.x(),right)) : left;
        float shift = Math.max(0,x+node.width()+gap-to.x());
        var afterBounds = new LinkedHashMap<>(currentView.nodes());
        afterBounds.put(nodeId,new GLNodeViewState(x,node.y(),node.width(),node.height(),node.customTitle()));
        if (shift > 0) {
            var visited = new LinkedHashSet<GLNodeId>();
            var pending = new java.util.ArrayDeque<GLNodeId>();
            pending.add(edge.input().nodeId());
            while (!pending.isEmpty()) {
                var id = pending.removeFirst();
                if (id.equals(edge.output().nodeId()) || id.equals(nodeId) || !visited.add(id)) continue;
                var bounds = currentView.nodes().get(id);
                if (bounds != null && bounds.x() >= to.x())
                    afterBounds.put(id,new GLNodeViewState(bounds.x()+shift,bounds.y(),bounds.width(),bounds.height(),bounds.customTitle()));
                for (var c : beforeGraph.connections()) {
                    if (c.input().nodeId().equals(id)) pending.add(c.output().nodeId());
                    if (c.output().nodeId().equals(id)) pending.add(c.input().nodeId());
                }
            }
        }
        var ports = binding.types().insertionPorts(beforeGraph.findNode(nodeId).orElseThrow().typeId()).orElseThrow();
        var afterView = currentView.withNodes(afterBounds);
        try {
            binding.graph().edit("Insert node into connection", edit -> {
                edit.removeConnection(connectionId);
                edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),edge.output(),new GLNodeEndpoint(nodeId,ports.inputPortId())));
                edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(nodeId,ports.outputPortId()),edge.input()));
            });
            binding.applyViewState(afterView);
        } catch (RuntimeException failure) {
            restore("Cancel node insertion",beforeGraph,beforeView);
            throw failure;
        }
        if (recordHistory) {
            if (!beforeView.equals(currentView)) record("Move node",beforeGraph,beforeView,beforeGraph,currentView);
            record("Insert node into connection",beforeGraph,currentView,binding.graph().snapshot(),afterView);
        }
        return true;
    }

	public boolean hasClipboardContent() { return clipboard != null; }
	public GLNodeGraphFragment clipboard() { return clipboard; }
	public void setClipboard(GLNodeGraphFragment fragment) { clipboard = fragment; }

	public GLNodeGraphFragment copySelection() {
		float[] pointer = binding.editor().pointerGraphPosition();
		return copySelection(pointer[0],pointer[1]);
	}

	public GLNodeGraphFragment copySelection(float pointerX, float pointerY) {
		Set<GLNodeId> ids = selectedIds();
		if (ids.isEmpty()) return null;
		clipboard = GLNodeGraphFragment.capture(binding.graph().snapshot(),
				binding.captureViewState(), ids).withPointerAnchor(pointerX,pointerY);
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
		return clipboard == null ? null : beginPlacement("Paste nodes", clipboard, x, y,
                x-clipboard.pointerOffsetX(),y-clipboard.pointerOffsetY());
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
		var fragment = GLNodeGraphFragment.capture(binding.graph().snapshot(), binding.captureViewState(), ids);
		return beginPlacement("Duplicate nodes",fragment,x,y,fragment.minimumX(),fragment.minimumY());
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
        return extractSelection(assetId,nodeTypeId,displayName,category,composites,catalog,null);
    }
    public GLCompositeExtraction.Result extractSelection(String assetId, String nodeTypeId,
            String displayName, String category, GLCompositeGraphRegistry composites, GLNodeCatalog catalog,
            String enclosingAssetId) {
		if (displayName == null || displayName.isBlank() || category == null
				|| category.isBlank() || composites == null || catalog == null)
			throw new IllegalArgumentException("Composite registration metadata required");
		Set<GLNodeId> selection = selectedIds();
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
        var enclosing=enclosingAssetId==null?null:composites.findAsset(enclosingAssetId).orElseThrow();
		GLCompositeExtraction.Result result = GLCompositeExtraction.extract(beforeGraph,
				beforeView, selection, binding.types(), assetId, nodeTypeId,enclosing==null?null:enclosing.compositeDescriptor());
        var afterEnclosing=enclosing==null?null:new com.vingame.glswing.node.document.GLNodeGraphAsset(
            enclosing.id(),enclosing.version(),enclosing.kind(),result.graph(),result.viewState(),enclosing.metadata(),result.remapEnclosing(enclosing.compositeDescriptor()));
		GLNodeCatalogEntry catalogEntry = new GLNodeCatalogEntry(
				result.asset().compositeDescriptor().nodeType(), displayName, category,
				Map.of(), "composite", "group");
		register(result, composites, catalog, catalogEntry);
		try {
            if(afterEnclosing!=null)composites.replaceAsset(afterEnclosing);
			binding.graph().replace("Extract composite", result.graph());
			binding.applyViewState(result.viewState());
			binding.refresh();
		} catch (RuntimeException failure) {
            if(enclosing!=null)composites.replaceAsset(enclosing);
            binding.graph().replace("Restore extraction",beforeGraph);
			unregister(result, composites, catalog);
			throw failure;
		}
		GLNodeGraphSnapshot afterGraph = binding.graph().snapshot();
		if (history != null) history.record("Extract composite", () -> {
            if(enclosing!=null)composites.replaceAsset(enclosing);
			restore("Undo extract composite", beforeGraph, beforeView);
			unregister(result, composites, catalog);
			if (!binding.isClosed()) binding.refresh();
		}, () -> {
			register(result, composites, catalog, catalogEntry);
            if(afterEnclosing!=null)composites.replaceAsset(afterEnclosing);
			restore("Redo extract composite", afterGraph, result.viewState());
			if (!binding.isClosed()) binding.refresh();
		});
		select(List.of(result.compositeNode().id()));
		return result;
	}

	public void removeSelection() {
		Set<GLNodeId> ids = selectedIds();
		if (!ids.isEmpty()) removeNodes(ids.size() == 1 ? "Delete node" : "Delete nodes", ids);
	}

    public boolean canExcludeSelection() {
        return !binding.isClosed() && com.vingame.glswing.node.model.GLNodeExclusion.plan(
                binding.graph().snapshot(),binding.types(),binding.cyclePolicy(),selectedIds()).isPresent();
    }
    public void excludeSelection() {
        var ids=selectedIds();
        var beforeGraph=binding.graph().snapshot();
        var planned=com.vingame.glswing.node.model.GLNodeExclusion.plan(beforeGraph,binding.types(),binding.cyclePolicy(),ids);
        if (planned.isEmpty()) return;
        var beforeView=binding.captureViewState();
        var positions=new LinkedHashMap<>(beforeView.nodes());
        ids.forEach(positions::remove);
        closeLinearGap(beforeGraph,ids,beforeView,positions);
        var afterView=beforeView.withNodes(positions);
        try {
            binding.graph().replace("Exclude nodes",planned.get());
            binding.applyViewState(afterView);
        } catch (RuntimeException failure) {
            restore("Cancel exclusion",beforeGraph,beforeView); throw failure;
        }
        record("Exclude nodes",beforeGraph,beforeView,binding.graph().snapshot(),afterView);
    }

    private void closeLinearGap(GLNodeGraphSnapshot graph, Set<GLNodeId> removed,
            GLNodeEditorViewState view, Map<GLNodeId,GLNodeViewState> positions) {
        var entering=graph.connections().stream().filter(e->removed.contains(e.input().nodeId())&&!removed.contains(e.output().nodeId())).toList();
        var leaving=graph.connections().stream().filter(e->removed.contains(e.output().nodeId())&&!removed.contains(e.input().nodeId())).toList();
        if (entering.size()!=1 || leaving.size()!=1) return;
        for (var id:removed) {
            if (graph.connections().stream().filter(e->e.input().nodeId().equals(id)).count()!=1
                    || graph.connections().stream().filter(e->e.output().nodeId().equals(id)).count()!=1) return;
        }
        var sourceId=entering.get(0).output().nodeId();
        var targetId=leaving.get(0).input().nodeId();
        var source=view.nodes().get(sourceId); var target=view.nodes().get(targetId);
        if (source==null || target==null || graph.connections().stream().filter(e->e.output().nodeId().equals(sourceId)).count()!=1) return;
        float gap=binding.editor().getHorizontalNodeSpacing();
        for (var id:removed) {
            var bounds=view.nodes().get(id);
            if (bounds==null || bounds.x()<source.x()+source.width() || bounds.x()+bounds.width()>target.x()) return;
        }
        float shift=target.x()-source.x()-source.width()-gap;
        if (shift<=0) return;
        var chain=new LinkedHashSet<GLNodeId>();
        var current=targetId;
        float previousX=target.x()-1;
        while (true) {
            var bounds=positions.get(current);
            if (bounds==null || bounds.x()<=previousX || !chain.add(current)) return;
            previousX=bounds.x();
            final var id=current;
            if (graph.connections().stream().filter(e->e.input().nodeId().equals(id)).count()!=1) return;
            var outgoing=graph.connections().stream().filter(e->e.output().nodeId().equals(id)).toList();
            if (outgoing.size()>1) return;
            if (outgoing.isEmpty()) break;
            current=outgoing.get(0).input().nodeId();
        }
        // Leave the layout intact if closing the gap would collide with another branch.
        for (var id:chain) {
            var b=positions.get(id);
            var moved=new java.awt.geom.Rectangle2D.Float(b.x()-shift,b.y(),b.width(),b.height());
            for (var other:positions.entrySet()) if (!chain.contains(other.getKey())) {
                var o=other.getValue();
                if (moved.intersects(o.x(),o.y(),o.width(),o.height())) return;
            }
        }
        for (var id:chain) {
            var b=positions.get(id);
            positions.put(id,new GLNodeViewState(b.x()-shift,b.y(),b.width(),b.height(),b.customTitle()));
        }
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
		return beginPlacement(name,fragment,x,y,x,y);
	}

	private GLNodePlacement beginPlacement(String name, GLNodeGraphFragment fragment,
			float x, float y, float placementX, float placementY) {
		GLNodeGraphSnapshot beforeGraph = binding.graph().snapshot();
		GLNodeEditorViewState beforeView = binding.captureViewState();
		List<GLNodeId> beforeSelection = List.copyOf(selectedIds());
		PasteResult result = insertUnrecorded(name, fragment, placementX, placementY, beforeView);
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
					source.y() + dy, source.width(), source.height(),source.customTitle()));
		}
		graph.edit(name, edit -> {
			for (GLGraphNode node : fragment.nodes())
				edit.addNode(new GLGraphNode(ids.get(node.id()), node.typeId(),
						node.typeVersion(), node.properties()));
			for (GLGraphConnection connection : fragment.connections())
				edit.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),
						remap(connection.output(), ids), remap(connection.input(), ids)));
		});
		GLNodeEditorViewState afterView = beforeView.withNodes(nextViews);
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
		GLNodeEditorViewState afterView = beforeView.withNodes(remaining);
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
		if (binding.isClosed()) inactiveViewStateConsumer.accept(view);
		else binding.applyViewState(view);
	}

	private Set<GLNodeId> selectedIds() {
		LinkedHashSet<GLNodeId> result = new LinkedHashSet<>();
		for (GLNode node : binding.editor().getSelectedNodes())
			binding.findNodeId(node).ifPresent(result::add);
		return result;
	}

	private void select(java.util.Collection<GLNodeId> ids) {
		if (binding.isClosed()) return;
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
            binding.editor().previewConnectionInsertion(nodes.size()==1 ? binding.findNodeView(nodes.get(0)).orElse(null) : null);
		}

		@Override public void confirm() {
			if (finished) return;
			finished = true;
			var placedGraph = binding.graph().snapshot();
			var placedView = binding.captureViewState();
			if (!sameContent(beforeGraph,placedGraph) || !beforeView.equals(placedView))
				record(name,beforeGraph,beforeView,placedGraph,placedView);
			var candidate = binding.editor().getInsertionCandidate();
			binding.editor().previewConnectionInsertion(null);
            boolean projectionInsertion=false;
			if (nodes.size()==1 && candidate!=null) {
				var id=nodes.get(0); var start=initialView.nodes().get(id);
                if(candidate.getModelId()==null){
                    var view=binding.findNodeView(id).orElseThrow();
                    var handler=binding.editor().getConnectionInsertionHandler();
                    projectionInsertion=handler!=null&&handler.insert(view,candidate,view.getX(),view.getY());
                }else insertIntoConnection(id,candidate.getModelId(),start.x(),start.y(),false);
			}
			GLNodeEditorViewState finalView = binding.captureViewState();
			var finalGraph = binding.graph().snapshot();
			if (!projectionInsertion&&(!sameContent(placedGraph, finalGraph) || !placedView.equals(finalView)))
				record("Insert node into connection", placedGraph, placedView, finalGraph, finalView);
			select(nodes);
			binding.editor().requestGLFocus(binding.editor());
		}

		@Override public void cancel() {
			if (finished) return;
			finished = true;
			binding.editor().previewConnectionInsertion(null);
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
