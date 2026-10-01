package com.vingame.glswing.node.binding;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeConnection;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodeEditorListener;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeGraphValidationResult;
import com.vingame.glswing.node.model.GLNodeGraphValidator;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeState;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortDirection;

/**
 * Keeps a {@link GLNodeEditor} projection synchronized with an authoritative
 * {@link GLNodeGraph}. Graph semantics and editor-only bounds remain separate.
 */
public final class GLNodeEditorBinding implements AutoCloseable {
	@FunctionalInterface public interface ValidationListener {
		void validationChanged(GLNodeEditorBinding binding,
				GLNodeGraphValidationResult result);
	}

	private final GLNodeGraph graph;
	private final GLNodeTypeRegistry types;
	private final GLNodeEditor editor;
	private final GLNodeViewFactory viewFactory;
	private final Map<GLNodeId, GLNode> nodeViews = new LinkedHashMap<>();
	private final Map<GLNode, GLNodeId> nodeIds = new LinkedHashMap<>();
	private final Map<GLNode, GLGraphNode> detachedNodeModels =
			new IdentityHashMap<>();
	private final Map<GLNodeConnectionId, GLNodeConnection> connectionViews =
			new LinkedHashMap<>();
	private final Map<GLNodeId, String> projectedTypes = new LinkedHashMap<>();
	private final Map<GLNodeId, GLNodeViewState> pendingViewState =
			new LinkedHashMap<>();
	private final Set<GLNodeId> growRebuiltViews = new LinkedHashSet<>();
	private final CopyOnWriteArrayList<ValidationListener> validationListeners =
			new CopyOnWriteArrayList<>();
	private final GLNodeGraph.Listener graphListener = (source, change) ->
			synchronize(change.after());
	private final GLNodeEditorListener editorListener = new EditorListener();
	private GLGraphCyclePolicy cyclePolicy;
	private GLNodeGraphValidationResult validationResult;
	private RuntimeException lastProjectionFailure;
	private boolean synchronizing;
	private boolean closed;
    private GLNodeGraphSnapshot projectedSnapshot;

	public GLNodeEditorBinding(GLNodeGraph graph, GLNodeTypeRegistry types,
			GLNodeEditor editor, GLNodeViewFactory viewFactory,
			GLGraphCyclePolicy cyclePolicy) {
		this(graph, types, editor, viewFactory, cyclePolicy,
				GLNodeEditorViewState.empty());
	}

	public GLNodeEditorBinding(GLNodeGraph graph, GLNodeTypeRegistry types,
			GLNodeEditor editor, GLNodeViewFactory viewFactory,
			GLGraphCyclePolicy cyclePolicy, GLNodeEditorViewState viewState) {
		if (graph == null || types == null || editor == null || viewFactory == null
				|| cyclePolicy == null || viewState == null)
			throw new IllegalArgumentException("Complete node-editor binding required");
		this.graph = graph;
		this.types = types;
		this.editor = editor;
		this.viewFactory = viewFactory;
		this.cyclePolicy = cyclePolicy;
		pendingViewState.putAll(viewState.nodes());
		graph.addListener(graphListener);
		editor.addNodeEditorListener(editorListener);
		synchronize(graph.snapshot());
	}

	public GLNodeGraph graph() { return graph; }
	public boolean isClosed() { return closed; }
	public GLNodeTypeRegistry types() { return types; }
	public GLNodeEditor editor() { return editor; }
	public GLGraphCyclePolicy cyclePolicy() { return cyclePolicy; }
	public GLNodeGraphValidationResult validationResult() { return validationResult; }
	public Optional<RuntimeException> lastProjectionFailure() {
		return Optional.ofNullable(lastProjectionFailure);
	}
	public Optional<GLNode> findNodeView(GLNodeId id) {
		return Optional.ofNullable(nodeViews.get(id));
	}
	/** Returns the model ID projected by a visible node instance. */
	public Optional<GLNodeId> findNodeId(GLNode node) {
		return Optional.ofNullable(nodeIds.get(node));
	}
	/** Adds a registered model node and projects it at the requested graph position. */
	public GLNodeId addNode(String typeId, Map<String, GLNodeValue> properties,
			float x, float y) {
		checkOpen();
		if (types.find(typeId).isEmpty())
			throw new IllegalArgumentException("Unknown node type: " + typeId);
		if (properties == null || !Float.isFinite(x) || !Float.isFinite(y))
			throw new IllegalArgumentException("Node properties and position required");
		GLNodeId id = GLNodeId.random();
		pendingViewState.put(id, new GLNodeViewState(x, y));
		graph.edit("Add " + typeId, edit ->
				edit.addNode(new GLGraphNode(id, typeId, properties)));
		findNodeView(id).ifPresent(node -> editor.setSelectedNodes(List.of(node)));
		return id;
	}
	public Optional<GLNodeConnection> findConnectionView(GLNodeConnectionId id) {
		return Optional.ofNullable(connectionViews.get(id));
	}

	public void setCyclePolicy(GLGraphCyclePolicy policy) {
		checkOpen();
		if (policy == null)
			throw new IllegalArgumentException("Cycle policy required");
		if (cyclePolicy == policy) return;
		cyclePolicy = policy;
        validate();
	}

	/** Reprojects the current snapshot, for example after registry changes. */
	public void refresh() {
        projectedSnapshot = null;
		checkOpen();
		synchronize(graph.snapshot());
	}

	public GLNodeEditorViewState captureViewState() {
		LinkedHashMap<GLNodeId, GLNodeViewState> result = new LinkedHashMap<>();
		for (GLGraphNode model : graph.snapshot().nodes()) {
			GLNode view = nodeViews.get(model.id());
			GLNodeViewState state = view == null ? pendingViewState.get(model.id())
					: GLNodeViewState.capture(view);
			if (state != null) result.put(model.id(), state);
		}
		return new GLNodeEditorViewState(result);
	}

	public void applyViewState(GLNodeEditorViewState state) {
		checkOpen();
		if (state == null)
			throw new IllegalArgumentException("Editor view state required");
		pendingViewState.clear();
		pendingViewState.putAll(state.nodes());
		int index = 0;
		for (Map.Entry<GLNodeId, GLNode> entry : nodeViews.entrySet())
			applyBounds(entry.getValue(), pendingViewState.get(entry.getKey()),
					index++);
	}

	/**
	 * Grows projected nodes whose current bounds no longer contain their
	 * preferred content. Existing larger user-defined dimensions are preserved.
	 */
	public void ensureViewsFitContent() {
		checkOpen();
		for (GLNode node : nodeViews.values()) {
			var preferred = node.getPreferredGLSize();
			float width = Math.max(node.getWidth(), preferred.width());
			float height = Math.max(node.getHeight(), preferred.height());
			if (width != node.getWidth() || height != node.getHeight())
				node.setSize(width, height);
		}
	}

	public void addValidationListener(ValidationListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Validation listener required");
		validationListeners.addIfAbsent(listener);
	}

	public void removeValidationListener(ValidationListener listener) {
		validationListeners.remove(listener);
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		graph.removeListener(graphListener);
		editor.removeNodeEditorListener(editorListener);
	}

	private void synchronize(GLNodeGraphSnapshot snapshot) {
		if (closed || synchronizing) return;
		synchronizing = true;
		lastProjectionFailure = null;
		try {
			Set<GLNodeConnectionId> desiredConnections = new LinkedHashSet<>();
			for (GLGraphConnection connection : snapshot.connections())
				desiredConnections.add(connection.id());
			for (GLNodeConnectionId id : Set.copyOf(connectionViews.keySet()))
				if (!desiredConnections.contains(id)) removeConnectionView(id);

			Set<GLNodeId> desiredNodes = new LinkedHashSet<>();
			for (GLGraphNode node : snapshot.nodes()) desiredNodes.add(node.id());
			for (GLNodeId id : Set.copyOf(nodeViews.keySet()))
				if (!desiredNodes.contains(id)) removeNodeView(id);

			var previousNodes=projectedSnapshot==null?Map.<GLNodeId,GLGraphNode>of():projectedSnapshot.nodesById();
            boolean topologyChanged = projectedSnapshot == null || !projectedSnapshot.connections().equals(snapshot.connections())
                || !projectedSnapshot.nodes().stream().map(n->List.of(n.id(),n.typeId())).toList().equals(snapshot.nodes().stream().map(n->List.of(n.id(),n.typeId())).toList());
            int index = 0;
			for (GLGraphNode node : snapshot.nodes()) {
                if(!topologyChanged && node.equals(previousNodes.get(node.id()))) { index++; continue; }
				GLNodeTypeDescriptor type = types.find(node.typeId()).orElse(null);
				GLNode existing = nodeViews.get(node.id());
				if (type == null) {
					if (existing != null) removeNodeView(node.id());
					index++;
					continue;
				}
				if (existing != null && !node.typeId().equals(projectedTypes.get(node.id()))) {
					removeNodeView(node.id());
					existing = null;
				}
				if (existing != null && !viewFactory.isViewCurrent(existing, node, type)) {
					pendingViewState.put(node.id(), GLNodeViewState.capture(existing));
					growRebuiltViews.add(node.id());
					removeNodeView(node.id());
					existing = null;
				}
				try {
					if (existing == null) existing = addNodeView(node, type, index);
					else {
						viewFactory.updateNode(existing, node, type);
						applyBuiltInState(existing, node, type);
					}
				} catch (RuntimeException failure) {
					lastProjectionFailure = failure;
				}
				index++;
			}

			for (GLGraphConnection connection : snapshot.connections())
				if (!connectionViews.containsKey(connection.id()))
					try {
						addConnectionView(connection);
					} catch (RuntimeException failure) {
						lastProjectionFailure = failure;
					}
		} finally {
			synchronizing = false;
		}
		projectedSnapshot = snapshot;
        validate();
	}

	private GLNode addNodeView(GLGraphNode node, GLNodeTypeDescriptor type,
			int index) {
		GLNode view = viewFactory.createNode(node, type);
		if (view == null)
			throw new IllegalStateException("Node view factory returned null for "
					+ node.typeId());
		validatePorts(view, type);
		GLNodeViewState state = pendingViewState.get(node.id());
		float x = state == null ? 40 + index % 4 * 260 : state.x();
		float y = state == null ? 40 + index / 4 * 180 : state.y();
		editor.addNode(view, x, y);
		if (state != null && state.width() > 0 && state.height() > 0) {
			if (growRebuiltViews.remove(node.id())) {
				var preferred = view.getPreferredGLSize();
				view.setSize(Math.max(state.width(), preferred.width()),
						Math.max(state.height(), preferred.height()));
			} else view.setSize(state.width(), state.height());
		} else growRebuiltViews.remove(node.id());
		nodeViews.put(node.id(), view);
		nodeIds.put(view, node.id());
		projectedTypes.put(node.id(), node.typeId());
		viewFactory.updateNode(view, node, type);
		applyBuiltInState(view, node, type);
		return view;
	}

	private void applyBuiltInState(GLNode view, GLGraphNode node,
			GLNodeTypeDescriptor type) {
		view.setContentHidden(GLNodeState.isHidden(node));
		view.setMuted(GLNodeState.isMuted(node));
		view.setMuteBypassMappings(types.muteBypassMappings(type.id()));
	}

	private void validatePorts(GLNode view, GLNodeTypeDescriptor type) {
		for (GLNodePortDescriptor contract : type.ports()) {
			GLNodePort port = view.findPort(contract.id());
			GLNodePort.Direction expected = contract.direction() == GLPortDirection.INPUT
					? GLNodePort.Direction.INPUT : GLNodePort.Direction.OUTPUT;
			if (port == null || port.getDirection() != expected)
				throw new IllegalStateException("Node view for " + type.id()
						+ " does not expose " + expected.name().toLowerCase()
						+ " port " + contract.id());
			port.setMaximumConnections(contract.maximumConnections());
			port.setConnectionValidator((own, other) -> {
				GLNodeId otherId = nodeIds.get(other.getNode());
				if (otherId == null) return true;
				GLGraphNode otherNode = graph.snapshot().findNode(otherId).orElse(null);
				GLNodePortDescriptor otherContract = otherNode == null ? null
						: types.find(otherNode.typeId())
								.flatMap(item -> item.findPort(other.getId())).orElse(null);
				return otherContract == null
						|| (contract.direction() == GLPortDirection.OUTPUT
                            ? types.compatibleValueTypes(contract.valueTypeId(), otherContract.valueTypeId())
                            : types.compatibleValueTypes(otherContract.valueTypeId(), contract.valueTypeId()));
			});
		}
	}

	private void addConnectionView(GLGraphConnection connection) {
		GLNode outputNode = nodeViews.get(connection.output().nodeId());
		GLNode inputNode = nodeViews.get(connection.input().nodeId());
		if (outputNode == null || inputNode == null) return;
		GLNodePort output = outputNode.findPort(connection.output().portId());
		GLNodePort input = inputNode.findPort(connection.input().portId());
		if (output == null || input == null
				|| output.getDirection() != GLNodePort.Direction.OUTPUT
				|| input.getDirection() != GLNodePort.Direction.INPUT)
			return;
		GLNodeConnection view = new GLNodeConnection(output, input);
		view.setModelId(connection.id());
		editor.addProjectedConnection(view);
		connectionViews.put(connection.id(), view);
	}

	private void removeConnectionView(GLNodeConnectionId id) {
		GLNodeConnection view = connectionViews.remove(id);
		if (view != null) editor.removeConnection(view);
	}

	private void removeNodeView(GLNodeId id) {
		GLNode view = nodeViews.remove(id);
		projectedTypes.remove(id);
		if (view == null) return;
		nodeIds.remove(view);
		for (Map.Entry<GLNodeConnectionId, GLNodeConnection> entry
				: Set.copyOf(connectionViews.entrySet()))
			if (entry.getValue().getOutput().getNode() == view
					|| entry.getValue().getInput().getNode() == view)
				connectionViews.remove(entry.getKey());
		editor.removeNode(view);
	}

	private List<com.vingame.glswing.node.model.GLGraphDiagnostic> additionalDiagnostics=List.of();
    /** Application/backend diagnostics, merged with structural validation. */
    private Set<GLNodeConnectionId> inactiveConnections=Set.of();
    public void setAdditionalDiagnostics(List<com.vingame.glswing.node.model.GLGraphDiagnostic> diagnostics){setAdditionalDiagnostics(diagnostics,Set.of());}
    public void setAdditionalDiagnostics(List<com.vingame.glswing.node.model.GLGraphDiagnostic> diagnostics,Set<GLNodeConnectionId> inactive){
        var next=List.copyOf(diagnostics);var nextInactive=Set.copyOf(inactive);
        if(next.equals(additionalDiagnostics)&&nextInactive.equals(inactiveConnections))return;
        additionalDiagnostics=next;inactiveConnections=nextInactive;validate();
    }
    private java.util.Set<com.vingame.glswing.node.model.GLNodeEndpoint> suppliedInputs=java.util.Set.of();
    /** Declare inputs connected through the enclosing group interface. */
    public void setSuppliedInputs(java.util.Set<com.vingame.glswing.node.model.GLNodeEndpoint> inputs){
        var next=java.util.Set.copyOf(inputs);
        if(next.equals(suppliedInputs))return;
        suppliedInputs=next;validate();
    }
    private void validate() {
		validationResult = GLNodeGraphValidator.validate(graph.snapshot(), types,
				cyclePolicy,suppliedInputs);
        var combined=new java.util.ArrayList<>(validationResult.diagnostics());
        if(!inactiveConnections.isEmpty()){
            var snapshot=graph.snapshot();
            var active=new com.vingame.glswing.node.model.GLNodeGraphSnapshot(snapshot.graphId(),snapshot.revision(),snapshot.nodes(),
                snapshot.connections().stream().filter(w->!inactiveConnections.contains(w.id())).toList());
            combined.removeIf(d->d.code().equals("graph.cycle"));
            GLNodeGraphValidator.validate(active,types,cyclePolicy).diagnostics().stream()
                .filter(d->d.code().equals("graph.cycle")).forEach(combined::add);
        }
        combined.addAll(additionalDiagnostics);
        connectionViews.forEach((id,view)->view.setActive(!inactiveConnections.contains(id)));
        validationResult=new GLNodeGraphValidationResult(validationResult.snapshot(),combined);
        nodeViews.forEach((id,view)->{
            var problems=combined.stream().filter(d->d.nodeIds().contains(id)).toList();
            if(problems.isEmpty())view.clearDiagnostic();
            else view.setDiagnostic(problems.stream().anyMatch(d->d.severity()==com.vingame.glswing.node.model.GLGraphDiagnostic.Severity.ERROR)?com.vingame.glswing.node.GLNodeConnectionStatus.ERROR:com.vingame.glswing.node.GLNodeConnectionStatus.WARNING,
                problems.stream().map(com.vingame.glswing.node.model.GLGraphDiagnostic::message).distinct().collect(java.util.stream.Collectors.joining("\n")));
        });
        editor.applyConnectionDiagnostics(validationResult);
		for (ValidationListener listener : validationListeners)
			listener.validationChanged(this, validationResult);
	}

	private void applyBounds(GLNode node, GLNodeViewState state, int index) {
		if (state == null) {
			node.setLocation(40 + index % 4 * 260, 40 + index / 4 * 180);
			return;
		}
		node.setLocation(state.x(), state.y());
		if (state.width() > 0 && state.height() > 0)
			node.setSize(state.width(), state.height());
	}

	private void checkOpen() {
		if (closed) throw new IllegalStateException("Node-editor binding is closed");
	}

	private final class EditorListener implements GLNodeEditorListener {
		@Override public void nodeAdded(GLNodeEditor source, GLNode node) {
			if (synchronizing || closed) return;
			GLGraphNode model = detachedNodeModels.remove(node);
			if (model == null) return;
			nodeViews.put(model.id(), node);
			nodeIds.put(node, model.id());
			projectedTypes.put(model.id(), model.typeId());
			graph.edit("Restore node", edit -> edit.addNode(model));
		}

		@Override public void nodeRemoved(GLNodeEditor source, GLNode node) {
			if (synchronizing || closed) return;
			GLNodeId id = nodeIds.remove(node);
			if (id == null) return;
			graph.snapshot().findNode(id).ifPresent(model ->
					detachedNodeModels.put(node, model));
			nodeViews.remove(id);
			projectedTypes.remove(id);
			graph.edit("Delete node", edit -> edit.removeNode(id));
		}

		@Override public void connectionAdded(GLNodeEditor source,
				GLNodeConnection connection) {
			if (synchronizing || closed) return;
			GLNodeId outputNode = nodeIds.get(connection.getOutput().getNode());
			GLNodeId inputNode = nodeIds.get(connection.getInput().getNode());
			if (outputNode == null || inputNode == null) return;
			GLNodeConnectionId id = connection.getModelId() == null
					? GLNodeConnectionId.random() : connection.getModelId();
			if (graph.snapshot().findConnection(id).isPresent()) {
				connectionViews.put(id, connection);
				return;
			}
			connection.setModelId(id);
			connectionViews.put(id, connection);
			try {
				graph.edit("Create connection", edit -> edit.addConnection(
						new GLGraphConnection(id,
								new GLNodeEndpoint(outputNode,
										connection.getOutput().getId()),
								new GLNodeEndpoint(inputNode,
										connection.getInput().getId()))));
			} catch (RuntimeException failure) {
				lastProjectionFailure = failure;
				connectionViews.remove(id);
				synchronizing = true;
				try { editor.removeConnection(connection); }
				finally { synchronizing = false; }
			}
		}

		@Override public void connectionRemoved(GLNodeEditor source,
				GLNodeConnection connection) {
			if (synchronizing || closed || connection.getModelId() == null) return;
			if (!editor.getNodes().contains(connection.getOutput().getNode())
					|| !editor.getNodes().contains(connection.getInput().getNode()))
				return;
			GLNodeConnectionId id = connection.getModelId();
			connectionViews.remove(id);
			if (graph.snapshot().findConnection(id).isPresent())
				graph.edit("Delete connection", edit -> edit.removeConnection(id));
		}
	}
}
