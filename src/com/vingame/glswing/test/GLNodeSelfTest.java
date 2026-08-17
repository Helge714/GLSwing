package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Locale;

import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodeEditorListener;
import com.vingame.glswing.node.GLNodeConnection;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.GLNodePorts;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLColorInterpolation;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.internal.UIQuadInstanceFormat;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLUiKeys;

/** CPU regression checks for node composition, layout and shadow instances. */
public final class GLNodeSelfTest {
	private GLNodeSelfTest() {
	}

	public static void main(String[] args) {
		testNodeCompositionAndLayout();
		testEditorSelectionLifecycle();
		testViewportNavigation();
		testDragAndHorizontalResize();
		testZoomTitleEditingAndConnections();
		testConnectionPolicyAndReplacement();
		testComponentNamesAndConnectedPresentation();
		testLocalizedTitleAction();
		testMultipleNodeContextDeletion();
		testAnalyticShadowInstance();
		System.out.println("GL node self-test passed.");
	}

	private static void testComponentNamesAndConnectedPresentation() {
		GLButton button = new GLButton("Visible text");
		check("Visible text".equals(button.getName()),
				"A textual control did not expose its text as default name");
		button.setName("Semantic name");
		button.setText("Changed text");
		check("Semantic name".equals(button.getName()),
				"Changing visible text replaced an explicit component name");
		button.clearName();
		check("Changed text".equals(button.getName()),
				"Clearing a component name did not restore its text default");

		GLNodeEditor editor = new GLNodeEditor();
		GLNode source = new GLNode("Source");
		GLNodePort output = source.addOutput("value", new GLLabel("Value"));
		GLNode target = new GLNode("Target");
		GLNodePort input = target.addInput("value", button);
		input.setContentHiddenWhenConnected(true);
		editor.addNode(source, 20, 20);
		editor.addNode(target, 300, 20);
		GLNodeConnection connection = editor.connect(output, input);
		check(input.isContentHiddenByConnection(),
				"A connected opt-in input did not hide its control presentation");
		editor.removeConnection(connection);
		check(!input.isContentHiddenByConnection(),
				"Disconnecting an input did not restore its control presentation");
	}

	private static void testConnectionPolicyAndReplacement() {
		GLNodeEditor editor = new GLNodeEditor();
		int[] graphChanges = { 0, 0 };
		editor.addNodeEditorListener(new GLNodeEditorListener() {
			@Override
			public void connectionAdded(GLNodeEditor source,
					GLNodeConnection connection) {
				graphChanges[0]++;
			}

			@Override
			public void connectionRemoved(GLNodeEditor source,
					GLNodeConnection connection) {
				graphChanges[1]++;
			}
		});
		GLNode firstSource = new GLNode("First source");
		GLNodePort firstOutput = firstSource.addOutput("float",
				new GLLabel("Float"));
		GLNode secondSource = new GLNode("Second source");
		GLNodePort secondOutput = secondSource.addOutput("color",
				new GLLabel("Color"));
		GLNode target = new GLNode("Target");
		GLNodePort input = target.addInput("value", new GLLabel("Value"));
		editor.addNode(firstSource, 20, 20);
		editor.addNode(secondSource, 20, 180);
		editor.addNode(target, 380, 90);

		input.setConnectionValidator((own, other) -> "float".equals(other.getId()));
		check(editor.canConnect(firstOutput, input),
				"A compatible port pair was rejected");
		check(!editor.canConnect(secondOutput, input),
				"The input's connection validator was ignored");
		editor.connect(firstOutput, input);
		check(input.getConnectionCount() == 1,
				"Port connection count was not updated");

		input.setConnectionValidator((own, other) -> true);
		editor.connect(secondOutput, input);
		check(editor.getConnections().size() == 1
				&& editor.getConnections().get(0).getOutput() == secondOutput,
				"Single-input replacement retained the previous connection");
		check(graphChanges[0] == 2 && graphChanges[1] == 1,
				"Editor did not report connection lifecycle changes");
	}

	private static void testLocalizedTitleAction() {
		GLLocalizer localizer = GLLocalizer.forLocale(Locale.GERMAN);
		String german = localizer.text(GLUiKeys.NODE_EDIT_TITLE);
		check("Titel bearbeiten".equals(german),
				"The node title action is not localized in German");
		GLNode node = new GLNode("Editable");
		node.getContextMenu();
		node.setGLLocalizer(localizer);
		check(german.equals(node.getContextMenu().getItems().get(0).getText()),
				"The node context-menu text did not follow its localizer");
		node.getContextMenu().getItems().get(0).doClick();
		check(node.isEditingTitle(),
				"The context-menu action did not open the title editor");
		check("Node löschen".equals(GLLocalizer.forLocale(Locale.GERMAN)
				.text(GLUiKeys.NODE_DELETE)),
				"The node delete action is not localized in German");
	}

	private static void testMultipleNodeContextDeletion() {
		GLNodeEditor editor = new GLNodeEditor();
		GLNode first = new GLNode("First");
		GLNode second = new GLNode("Second");
		editor.addNode(first, 10, 10);
		editor.addNode(second, 200, 10);
		editor.setSelectedNodes(List.of(first, second));
		check("Delete nodes".equals(
				first.getContextMenu().getItems().get(1).getText()),
				"A multi-selection retained the singular node delete text");
		first.setGLLocalizer(GLLocalizer.GERMAN);
		check("Nodes löschen".equals(
				first.getContextMenu().getItems().get(1).getText()),
				"The plural node delete text was not localized");
		first.getContextMenu().getItems().get(1).doClick();
		check(editor.getNodes().isEmpty(),
				"The plural node delete action did not remove the selection");
	}

	private static void testZoomTitleEditingAndConnections() {
		GLNodeEditor editor = new GLNodeEditor();
		float defaultSnapDistance = editor.getConnectionSnapDistance();
		float defaultCurveTolerance = editor.getConnectionCurveTolerance();
		float defaultHandleFactor = editor.getConnectionHandleFactor();
		float defaultMinimumHandleLength = editor.getMinimumConnectionHandleLength();
		float defaultConnectionWidth = editor.getConnectionWidth();
		Color defaultConnectionBorder = editor.getConnectionBorder();
		Color defaultSelectedConnectionBorder = editor.getSelectedConnectionBorder();
		float defaultConnectionBorderWidth = editor.getConnectionBorderWidth();
		var defaultColorInterpolation = editor.getConnectionColorInterpolator();
		editor.setConnectionSnapDistance(24);
		editor.setConnectionCurveTolerance(0.12f);
		editor.setConnectionHandleFactor(0.65f);
		editor.setMinimumConnectionHandleLength(52);
		editor.setConnectionWidth(4);
		editor.setConnectionBorder(Color.BLACK);
		editor.setSelectedConnectionBorder(Color.WHITE);
		editor.setConnectionBorderWidth(2);
		editor.setConnectionColorInterpolator(GLColorInterpolation.OKLAB);
		check(editor.getConnectionSnapDistance() == 24
				&& editor.getConnectionCurveTolerance() == 0.12f
				&& editor.getConnectionHandleFactor() == 0.65f
				&& editor.getMinimumConnectionHandleLength() == 52
				&& editor.getConnectionWidth() == 4
				&& editor.getConnectionBorder().equals(Color.BLACK)
				&& editor.getSelectedConnectionBorder().equals(Color.WHITE)
				&& editor.getConnectionBorderWidth() == 2
				&& editor.getConnectionColorInterpolator()
						== GLColorInterpolation.OKLAB,
				"Editor did not retain its connection curve configuration");
		editor.clearConnectionSnapDistance();
		editor.clearConnectionCurveTolerance();
		editor.clearConnectionHandleFactor();
		editor.clearMinimumConnectionHandleLength();
		editor.clearConnectionWidth();
		editor.clearConnectionBorder();
		editor.clearSelectedConnectionBorder();
		editor.clearConnectionBorderWidth();
		editor.clearConnectionColorInterpolator();
		check(editor.getConnectionSnapDistance() == defaultSnapDistance
				&& editor.getConnectionCurveTolerance() == defaultCurveTolerance
				&& editor.getConnectionHandleFactor() == defaultHandleFactor
				&& editor.getMinimumConnectionHandleLength() == defaultMinimumHandleLength
				&& editor.getConnectionWidth() == defaultConnectionWidth
				&& editor.getConnectionBorder().equals(defaultConnectionBorder)
				&& editor.getSelectedConnectionBorder().equals(
						defaultSelectedConnectionBorder)
				&& editor.getConnectionBorderWidth()
						== defaultConnectionBorderWidth
				&& editor.getConnectionColorInterpolator()
						== defaultColorInterpolation,
				"Clearing connection overrides did not restore theme defaults");
		editor.setConnectionHandleFactor(0);
		editor.setMinimumConnectionHandleLength(0);
		check(editor.getConnectionHandleFactor() == 0
				&& editor.getMinimumConnectionHandleLength() == 0,
				"Editor rejected the straight-connection configuration");
		editor.setSize(800, 500);
		GLNode source = new GLNode("Source");
		GLNodePort output = source.addOutput("value", new GLLabel("Value"));
		GLNode target = new GLNode("Target");
		GLNodePort input = target.addInput("value", new GLLabel("Value"));
		editor.addNode(source, 50, 80);
		editor.addNode(target, 420, 220);
		GLNodeConnection connection = editor.connect(output, input);
		check(connection.getWidth() == 3,
				"Connection did not inherit its editor style width");
		connection.setWidth(5);
		check(connection.getWidth() == 5 && connection.hasExplicitWidth(),
				"Connection-local width override was ignored");
		connection.clearWidth();
		check(connection.getWidth() == 3 && !connection.hasExplicitWidth(),
				"Clearing connection width did not restore editor style");
		check(editor.getConnections().contains(connection),
				"Editor did not retain a Bezier connection");
		editor.setSelectedConnection(connection);
		check(connection.isSelected() && editor.getSelectedNode() == null,
				"Editor did not select its connection exclusively");
		editor.setSelectedNode(source);
		check(!connection.isSelected() && source.isSelected(),
				"Node selection did not clear the selected connection");
		editor.setZoom(1.75f);
		check(editor.getZoom() == 1.75f,
				"Editor did not retain its view zoom");
		source.editTitle();
		check(source.isEditingTitle(),
				"Node did not expose its inline title editor");
		source.setTitleEditable(false);
		check(!source.isEditingTitle(),
				"Disabling title editing left the editor visible");
		check(editor.removeNode(source) && editor.getConnections().isEmpty(),
				"Removing a node retained a dangling connection");
	}

	private static void testNodeCompositionAndLayout() {
		GLNode node = new GLNode("Node");
		GLLabel label = new GLLabel("Information");
		node.addContent(label);
		GLNodePort input = node.addInput("factor",
				GLSlider.fixed("Factor", 0, 1, 0.5));
		GLNodePorts pair = node.addInputOutput("source",
				new GLLabel("Conversion"), "result");
		GLNodePort output = node.addOutput("color", new GLLabel("Color"));
		node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
		node.setSize(280, node.getPreferredGLSize().height());

		check(node.getContents().size() == 4,
				"Node did not retain all convenience rows");
		check(input.getNode() == node && pair.input().getNode() == node
				&& pair.output().getNode() == node && output.getNode() == node,
				"Node ports were not attached to their node");
		check(input.getX() < 0 && output.getX() < node.getWidth()
				&& output.getX() + output.getWidth() > node.getWidth(),
				"Node ports were not centered on the outer edges");
		check(node.getResizeMode() == GLNodeResizeMode.HORIZONTAL,
				"Node resize mode was not retained");
		check(node.removeContent(label) && !node.getContents().contains(label),
				"Node content could not be removed");
	}

	private static void testEditorSelectionLifecycle() {
		GLNodeEditor editor = new GLNodeEditor();
		int[] selectionChanges = { 0 };
		editor.addNodeEditorListener(new GLNodeEditorListener() {
			@Override
			public void selectionChanged(GLNodeEditor source) {
				selectionChanges[0]++;
			}
		});
		GLNode first = new GLNode("First");
		GLNode second = new GLNode("Second");
		editor.addNode(first, 10, 20);
		editor.addNode(second, 50, 70);
		editor.setSelectedNode(first);
		check(first.isSelected() && !second.isSelected(),
				"Editor did not apply its single selection");
		editor.setSelectedNode(second);
		check(!first.isSelected() && second.isSelected(),
				"Editor did not transfer selection");
		editor.setSelectedNodes(List.of(first, second));
		check(first.isSelected() && second.isSelected()
				&& !first.isActiveSelected() && second.isActiveSelected()
				&& editor.getSelectedNodes().size() == 2
				&& editor.getActiveNode() == second,
				"Editor did not retain multiple selected nodes and a primary node");
		Canvas mouseSource = new Canvas();
		click(mouseSource, first, 0);
		check(first.isActiveSelected() && !second.isSelected()
				&& editor.getSelectedNodes().size() == 1,
				"A normal click did not replace the remaining node selection");
		editor.setSelectedNodes(List.of(first, second));
		click(mouseSource, first, MouseEvent.SHIFT_DOWN_MASK);
		check(first.isActiveSelected() && second.isSelected()
				&& !second.isActiveSelected(),
				"Shift-clicking a normal selection did not make it active");
		click(mouseSource, first, MouseEvent.SHIFT_DOWN_MASK);
		check(!first.isSelected() && second.isActiveSelected(),
				"Shift-clicking the active node did not remove it and promote another");
		click(mouseSource, first, MouseEvent.CTRL_DOWN_MASK);
		check(first.isActiveSelected() && !second.isSelected()
				&& editor.getSelectedNodes().size() == 1,
				"Ctrl unexpectedly retained its former multi-selection behavior");
		editor.setSelectedNodes(List.of(first, second));
		editor.setNodeSelected(first, false);
		check(!first.isSelected() && second.isActiveSelected(),
				"Editor did not remove one node from a multi-selection");
		check(editor.removeNode(second) && editor.getSelectedNode() == null
				&& editor.getSelectedNodes().isEmpty(),
				"Removing the selected node did not clear selection");
		editor.setSelectedNode(first);
		first.getContextMenu().getItems().get(1).doClick();
		check(editor.getNodes().isEmpty(),
				"The built-in node delete context action did not remove selection");
		check(selectionChanges[0] >= 4,
				"Editor did not report selection changes");
	}

	private static void testViewportNavigation() {
		GLNodeEditor editor = new GLNodeEditor();
		editor.setSize(800, 600);
		editor.setPan(30, -20);
		float screenX = 250;
		float screenY = 180;
		float centerX = 400;
		float centerY = 300;
		float worldX = centerX
				+ (screenX - centerX - editor.getPanX()) / editor.getZoom();
		float worldY = centerY
				+ (screenY - centerY - editor.getPanY()) / editor.getZoom();
		editor.setZoomAtScreenPosition(2, screenX, screenY);
		float afterX = centerX
				+ (screenX - centerX - editor.getPanX()) / editor.getZoom();
		float afterY = centerY
				+ (screenY - centerY - editor.getPanY()) / editor.getZoom();
		check(Math.abs(worldX - afterX) < 0.001f
				&& Math.abs(worldY - afterY) < 0.001f,
				"Mouse-centered zoom moved the world point below the cursor");
		editor.resetView();
		check(editor.getZoom() == 1 && editor.getPanX() == 0
				&& editor.getPanY() == 0,
				"Resetting the node-editor view retained camera state");
		Canvas source = new Canvas();
		editor.mouseWheelMoved(wheel(source, editor, 0, -1));
		check(editor.getZoom() > 1 && editor.getPanX() != 0
				&& editor.getPanY() != 0,
				"Unmodified mouse wheel did not zoom around the cursor");
		editor.resetView();
		editor.mouseWheelMoved(wheel(source, editor,
				MouseEvent.CTRL_DOWN_MASK, 1));
		check(editor.getZoom() == 1 && editor.getPanX() == -48
				&& editor.getPanY() == 0,
				"Ctrl+wheel did not pan horizontally");
		editor.resetView();
		editor.mouseWheelMoved(wheel(source, editor,
				MouseEvent.SHIFT_DOWN_MASK, 1));
		check(editor.getZoom() == 1 && editor.getPanX() == 0
				&& editor.getPanY() == -48,
				"Shift+wheel did not pan vertically");
	}

	private static void testAnalyticShadowInstance() {
		float[] shadow = UIQuadInstanceFormat.roundedRectangleShadow(
				10, 20, 100, 60, 8, 12, 3, 5,
				new Color(0, 0, 0, 120), new GLTransform());
		check(shadow.length == UIQuadInstanceFormat.FLOATS_PER_INSTANCE,
				"Analytic shadow did not produce one UI instance");
		check(shadow[14] == UIQuadInstanceFormat.MODE_ROUNDED_SHADOW,
				"Analytic shadow used the wrong shader mode");
		check(shadow[20] > 0 && shadow[21] > 0
				&& shadow[22] == 100 && shadow[23] == 60,
				"Analytic shadow caster bounds are invalid");
	}

	private static void testDragAndHorizontalResize() {
		GLNodeEditor editor = new GLNodeEditor();
		GLNode node = new GLNode("Interactive");
		GLNode companion = new GLNode("Companion");
		node.addContent(new GLLabel("Content"));
		node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
		editor.addNode(node, 20, 30);
		editor.addNode(companion, 320, 90);
		node.setSize(240, node.getPreferredGLSize().height());
		Canvas source = new Canvas();

		node.mousePressed(mouse(source, node, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 40, 12, 0));
		node.mouseDragged(mouse(source, node, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 70, 32, MouseEvent.BUTTON1_DOWN_MASK));
		node.mouseReleased(mouse(source, node, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 70, 32, 0));
		check(node.getX() == 50 && node.getY() == 50,
				"Node title drag did not use local transformed coordinates");
		check(editor.getSelectedNode() == node,
				"Dragging a node did not select it in the editor");

		editor.setSelectedNodes(List.of(node, companion));
		float companionStartX = companion.getX();
		float companionStartY = companion.getY();
		node.mousePressed(mouse(source, node, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 40, 12, 0));
		node.mouseDragged(mouse(source, node, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 55, 37, MouseEvent.BUTTON1_DOWN_MASK));
		node.mouseReleased(mouse(source, node, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 55, 37, 0));
		check(companion.getX() == companionStartX + 15
				&& companion.getY() == companionStartY + 25,
				"Dragging a multi-selected node did not move its companions");

		float oldWidth = node.getWidth();
		float middleY = node.getHeight() * 0.5f;
		node.mousePressed(mouse(source, node, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, oldWidth - 1, middleY, 0));
		node.mouseDragged(mouse(source, node, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, oldWidth + 39, middleY,
				MouseEvent.BUTTON1_DOWN_MASK));
		node.mouseReleased(mouse(source, node, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, oldWidth + 39, middleY, 0));
		check(node.getWidth() == oldWidth + 40,
				"Horizontal node resize did not change width");
		check(node.getHeight() == node.getPreferredGLSize().height(),
				"Horizontal-only resize changed node height");
	}

	private static GLMouseEvent mouse(Canvas source, GLNode node, int eventId,
			int button, float localX, float localY, int modifiers) {
		MouseEvent original = new MouseEvent(source, eventId,
				System.currentTimeMillis(), modifiers,
				Math.round(localX), Math.round(localY), 1, false, button);
		GLPickResult pick = new GLPickResult(0, node, localX, localY,
				localX, localY, 1, 0, 0, 1, 0, 0);
		return new GLMouseEvent(original, pick, 0, 0);
	}

	private static void click(Canvas source, GLNode node, int modifiers) {
		node.mousePressed(mouse(source, node, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 20, 10, modifiers));
		node.mouseReleased(mouse(source, node, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 20, 10, modifiers));
		node.mouseClicked(mouse(source, node, MouseEvent.MOUSE_CLICKED,
				MouseEvent.BUTTON1, 20, 10, modifiers));
	}

	private static GLMouseWheelEvent wheel(Canvas source, GLNodeEditor editor,
			int modifiers, int rotation) {
		MouseWheelEvent original = new MouseWheelEvent(source,
				MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers,
				250, 180, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
				3, rotation);
		GLPickResult pick = new GLPickResult(0, editor, 250, 180,
				250, 180, 1, 0, 0, 1, 0, 0);
		return new GLMouseWheelEvent(original, pick);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
