package com.vingame.glswing.test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.binding.GLNodeEditorBinding;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeGraphEditingSession;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.ui.GLLabel;

/** CPU-only synchronization checks for the graph/editor binding. */
public final class GLNodeEditorBindingSelfTest {
	private GLNodeEditorBindingSelfTest() { }

	public static void main(String[] args) {
		GLNodeTypeRegistry types = new GLNodeTypeRegistry();
		types.register(new GLNodeTypeDescriptor("test.source",
				GLNodePortDescriptor.output("out", "number")));
		types.register(new GLNodeTypeDescriptor("test.sink",
				GLNodePortDescriptor.input("in", "number", true, 1)));
		GLNodeId sourceId = GLNodeId.random();
		GLNodeId sinkId = GLNodeId.random();
		GLNodeConnectionId edgeId = GLNodeConnectionId.random();
		GLNodeGraph graph = new GLNodeGraph();
		graph.edit("Create", edit -> {
			edit.addNode(new GLGraphNode(sourceId, "test.source"));
			edit.addNode(new GLGraphNode(sinkId, "test.sink"));
			edit.addConnection(new GLGraphConnection(edgeId,
					new GLNodeEndpoint(sourceId, "out"),
					new GLNodeEndpoint(sinkId, "in")));
		});

		GLNodeEditor editor = new GLNodeEditor();
		GLHistoryManager history = new GLHistoryManager();
		editor.setGLHistoryManager(history);
		int[] updates = { 0 };
		GLNodeEditorViewState initialView = new GLNodeEditorViewState(Map.of(
				sourceId, new GLNodeViewState(25, 35, 180, 120)));
		try (GLNodeEditorBinding binding = new GLNodeEditorBinding(graph, types,
				editor, (node, type) -> createView(type),
				GLGraphCyclePolicy.ACYCLIC, initialView)) {
			check(editor.getNodes().size() == 2 && editor.getConnections().size() == 1,
					"Initial graph was not projected");
			GLNode sourceView = binding.findNodeView(sourceId).orElseThrow();
			check(sourceView.getX() == 25 && sourceView.getY() == 35
					&& sourceView.getWidth() == 180 && sourceView.getHeight() == 120,
					"Initial editor view state was not restored");
			binding.addValidationListener((changed, result) -> updates[0]++);
			graph.edit("Set value", edit -> edit.setProperty(sourceId, "value",
					GLNodeValue.of(3)));
			check(updates[0] == 1 && binding.validationResult().isValid(),
					"Graph update did not refresh validation");

			editor.removeConnection(editor.getConnections().get(0));
			check(graph.snapshot().connections().isEmpty(),
					"Editor connection removal was not reflected in the model");
			graph.edit("Reconnect", edit -> edit.addConnection(new GLGraphConnection(
					edgeId, new GLNodeEndpoint(sourceId, "out"),
					new GLNodeEndpoint(sinkId, "in"))));
			check(binding.findConnectionView(edgeId).isPresent(),
					"Model connection addition was not projected");

			sourceView.setBounds(70, 80, 210, 140);
			var captured = binding.captureViewState().nodes().get(sourceId);
			check(captured.equals(new GLNodeViewState(70, 80, 210, 140)),
					"Editor bounds were not captured separately from graph data");
			GLNodeId added = binding.addNode("test.source",
					Map.of("value", GLNodeValue.of(9)), 310, 220);
			GLNode addedView = binding.findNodeView(added).orElseThrow();
			check(addedView.getX() == 310 && addedView.getY() == 220
					&& binding.findNodeId(addedView).orElseThrow().equals(added)
					&& graph.snapshot().findNode(added).orElseThrow().properties()
							.get("value").equals(GLNodeValue.of(9)),
					"Catalog-style node insertion lost model data or view position");

			var nodeMenu = binding.findNodeView(sinkId).orElseThrow().getContextMenu();
			nodeMenu.getItems().get(nodeMenu.getItems().size() - 1).doClick();
			check(graph.snapshot().findNode(sinkId).isEmpty()
					&& graph.snapshot().connections().isEmpty(),
					"Editor node deletion was not one cascading model edit");
			check(history.undo() && graph.snapshot().findNode(sinkId).isPresent()
					&& graph.snapshot().connections().size() == 1,
					"Undoing the editor deletion did not restore the model graph");
			check(history.redo() && graph.snapshot().findNode(sinkId).isEmpty(),
					"Redoing the editor deletion did not update the model graph");

			history.reset();
			GLNodeId copySource = graph.snapshot().nodes().get(0).id();
			editor.setSelectedNode(binding.findNodeView(copySource).orElseThrow());
			AtomicReference<GLNodeEditorViewState> inactiveView = new AtomicReference<>();
			GLNodeGraphEditingSession editing = new GLNodeGraphEditingSession(binding,
					history, inactiveView::set);
			check(editing.copySelection() != null, "Node selection was not copied");
			var pasted = editing.paste(500, 300);
			check(pasted.insertedNodes().size() == 1
					&& !pasted.insertedNodes().get(0).equals(copySource)
					&& graph.snapshot().nodes().size() == 3,
					"Paste did not allocate a fresh node identity");
			check(history.undo() && graph.snapshot().nodes().size() == 2,
					"Pasted graph and view state were not one undoable edit");
			check(history.redo() && graph.snapshot().nodes().size() == 3,
					"Redo did not restore the pasted node");

			history.reset();
			editor.setSelectedNode(binding.findNodeView(copySource).orElseThrow());
			GLNodeViewState moveStart = binding.captureViewState().nodes().get(copySource);
			var moving = editing.beginMoveSelection(100, 100);
			moving.moveTo(140, 155);
			GLNodeViewState moved = binding.captureViewState().nodes().get(copySource);
			check(moved.x() == moveStart.x() + 40 && moved.y() == moveStart.y() + 55,
					"Floating move did not track its graph-space pointer");
			moving.cancel();
			check(binding.captureViewState().nodes().get(copySource).equals(moveStart)
					&& !history.canUndo(),
					"Cancelling floating placement changed history or retained its position");
			var duplicating = editing.beginDuplicate(200, 200);
			duplicating.moveTo(230, 240);
			duplicating.confirm();
			check(graph.snapshot().nodes().size() == 4 && history.canUndo(),
					"Confirmed floating duplicate was not one undoable graph edit");
			check(history.undo() && graph.snapshot().nodes().size() == 3,
					"Undo did not remove the floating duplicate");

			history.reset();
			editor.setSelectedNode(binding.findNodeView(
					pasted.insertedNodes().get(0)).orElseThrow());
			GLCompositeGraphRegistry composites = new GLCompositeGraphRegistry();
			GLNodeCatalog catalog = new GLNodeCatalog();
			var extracted = editing.extractSelection("test.extracted.asset",
					"test.extracted", "Extracted", "Tests", composites, catalog);
			check(types.find("test.extracted").isPresent()
					&& composites.findAsset("test.extracted.asset").isPresent()
					&& catalog.find("test.extracted").isPresent()
					&& graph.snapshot().findNode(extracted.compositeNode().id()).isPresent(),
					"Composite extraction did not register or project its result");
			check(history.undo() && types.find("test.extracted").isEmpty()
					&& composites.findAsset("test.extracted.asset").isEmpty(),
					"Undo did not remove the extracted composite registration");
			check(history.redo() && types.find("test.extracted").isPresent()
					&& graph.snapshot().findNode(extracted.compositeNode().id()).isPresent(),
					"Redo did not restore the extracted composite");
			binding.close();
			check(history.undo() && types.find("test.extracted").isEmpty()
					&& inactiveView.get() != null,
					"Undo through a closed binding did not restore offline state");
		}
		System.out.println("GL node-editor binding self-test passed.");
	}

	private static GLNode createView(GLNodeTypeDescriptor type) {
		GLNode node = new GLNode(type.id());
		for (GLNodePortDescriptor port : type.ports()) {
			if (port.direction() == GLPortDirection.INPUT)
				node.addInput(port.id(), new GLLabel(port.id()));
			else
				node.addOutput(port.id(), new GLLabel(port.id()));
		}
		return node;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
