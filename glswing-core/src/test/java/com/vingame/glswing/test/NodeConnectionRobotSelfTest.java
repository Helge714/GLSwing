package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.ui.GLLabel;

/** End-to-end regression checks for port gestures and captured node resizing. */
public final class NodeConnectionRobotSelfTest {
	private NodeConnectionRobotSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> createScene(holder));
		try {
			Robot robot = new Robot();
			robot.setAutoDelay(12);
			waitUntil(() -> holder.output.getLastRenderedScreenBounds() != null
					&& holder.input.getLastRenderedScreenBounds() != null,
					"Node ports were not rendered");
			robot.delay(350);

			Point canvasOrigin = holder.frame.getGLCanvas().getLocationOnScreen();
			Point inputCenter = screenCenter(holder.input, canvasOrigin);
			Point insideSnapRadius = new Point(inputCenter.x - 15,
					inputCenter.y + 3);
			drag(robot, screenCenter(holder.output, canvasOrigin),
					insideSnapRadius);
			waitUntil(() -> onEdt(() -> holder.editor.getConnections().size() == 1),
					"Dragging output to input did not create a connection");

			Point empty = new Point(canvasOrigin.x + 440,
					canvasOrigin.y + holder.frame.getGLCanvas().getHeight() - 45);
			drag(robot, screenCenter(holder.input, canvasOrigin), empty);
			waitUntil(() -> onEdt(() -> holder.editor.getConnections().isEmpty()),
					"Dragging a connected input into empty space did not remove it");

			drag(robot, screenCenter(holder.output, canvasOrigin),
					screenCenter(holder.input, canvasOrigin));
			waitUntil(() -> onEdt(() -> holder.editor.getConnections().size() == 1),
					"Reconnecting ports did not create a connection");
			Point outputCenter = screenCenter(holder.output, canvasOrigin);
			Point targetCenter = screenCenter(holder.input, canvasOrigin);
			Point connectionCenter = new Point(
					(outputCenter.x + targetCenter.x) / 2,
					(outputCenter.y + targetCenter.y) / 2);
			click(robot, connectionCenter, InputEvent.BUTTON1_DOWN_MASK);
			waitUntil(() -> onEdt(() -> holder.editor.getSelectedConnection() != null),
					"Clicking a connection did not select its GPU-picked border");

			Point marqueeStart = new Point(canvasOrigin.x + 45,
					canvasOrigin.y + 45);
			Point marqueeEnd = new Point(canvasOrigin.x + 810,
					canvasOrigin.y + 300);
			drag(robot, marqueeStart, marqueeEnd);
			waitUntil(() -> onEdt(() -> holder.editor.getSelectedNodes().size() == 2),
					"Workspace marquee did not select the two intersecting nodes");

			float targetStartX = holder.targetNode.getX();
			float targetStartY = holder.targetNode.getY();
			Point sourceTitle = titlePoint(holder.sourceNode, canvasOrigin);
			drag(robot, sourceTitle,
					new Point(sourceTitle.x + 35, sourceTitle.y + 25));
			waitUntil(() -> holder.targetNode.getX() >= targetStartX + 30
					&& holder.targetNode.getY() >= targetStartY + 20,
					"Dragging one selected node did not move the selected group");

			float oldWidth = holder.resizeNode.getWidth();
			Point resizeStart = new Point(
					Math.round(canvasOrigin.x + holder.resizeNode.getX()
							+ holder.resizeNode.getWidth() - 2),
					Math.round(canvasOrigin.y + holder.resizeNode.getY()
							+ holder.resizeNode.getHeight() * 0.5f));
			Point outsideFrame = new Point(resizeStart.x + 80,
					canvasOrigin.y + holder.frame.getGLCanvas().getHeight() + 20);
			drag(robot, resizeStart, outsideFrame);
			waitUntil(() -> holder.resizeNode.getWidth() >= oldWidth + 70,
					"Node resize stopped when the pointer left the frame");

			float oldPanX = holder.editor.getPanX();
			float oldPanY = holder.editor.getPanY();
			Point panStart = titlePoint(holder.targetNode, canvasOrigin);
			drag(robot, panStart, new Point(panStart.x - 45, panStart.y + 35),
					InputEvent.BUTTON2_DOWN_MASK);
			waitUntil(() -> holder.editor.getPanX() < oldPanX - 35
					&& holder.editor.getPanY() > oldPanY + 25,
					"Middle-button pan did not bubble from a node to the editor");

			Point zoomPoint = titlePoint(holder.targetNode, canvasOrigin);
			robot.mouseMove(zoomPoint.x, zoomPoint.y);
			robot.mouseWheel(-2);
			waitUntil(() -> holder.editor.getZoom() > 1,
					"Unmodified wheel did not zoom the node editor at the mouse");

			float panBeforeHorizontalWheel = holder.editor.getPanX();
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.mouseWheel(1);
			robot.keyRelease(KeyEvent.VK_CONTROL);
			waitUntil(() -> holder.editor.getPanX()
					< panBeforeHorizontalWheel - 35,
					"Ctrl+wheel did not pan the node editor horizontally");

			float panBeforeVerticalWheel = holder.editor.getPanY();
			robot.keyPress(KeyEvent.VK_SHIFT);
			robot.mouseWheel(1);
			robot.keyRelease(KeyEvent.VK_SHIFT);
			waitUntil(() -> holder.editor.getPanY()
					< panBeforeVerticalWheel - 35,
					"Shift+wheel did not pan the node editor vertically");

			System.out.println(
					"Node workspace/connection Robot self-test passed.");
		} finally {
			SwingUtilities.invokeAndWait(holder.frame::dispose);
		}
	}

	private static void createScene(Holder holder) {
		holder.frame = new GLFrame("Node connection regression",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(120));
		holder.frame.setMinimumSize(new Dimension(700, 480));
		holder.frame.setSize(900, 620);
		holder.frame.setLocationRelativeTo(null);
		holder.frame.setAlwaysOnTop(true);
		holder.frame.setGLClearColor(new Color(25, 28, 35));
		holder.frame.setGLLayout(new GLGridLayout());
		holder.editor = new GLNodeEditor();
		holder.frame.addGL(holder.editor,
				GLConstraints.auto().weight(1, 1).fillBoth());

		holder.sourceNode = new GLNode("Source");
		holder.output = holder.sourceNode.addOutput("value", new GLLabel("Value"));
		holder.editor.addNode(holder.sourceNode, 90, 90);
		holder.sourceNode.setSize(220,
				holder.sourceNode.getPreferredGLSize().height());

		holder.targetNode = new GLNode("Target");
		holder.input = holder.targetNode.addInput("value", new GLLabel("Value"));
		holder.editor.addNode(holder.targetNode, 540, 170);
		holder.targetNode.setSize(220,
				holder.targetNode.getPreferredGLSize().height());

		holder.resizeNode = new GLNode("Resize outside frame");
		holder.resizeNode.addContent(new GLLabel("Drag the right edge"));
		holder.resizeNode.setResizeMode(GLNodeResizeMode.HORIZONTAL);
		holder.editor.addNode(holder.resizeNode, 90, 350);
		holder.resizeNode.setSize(230,
				holder.resizeNode.getPreferredGLSize().height());
		holder.frame.setVisible(true);
	}

	private static Point screenCenter(GLNodePort port, Point canvasOrigin) {
		float[] bounds = port.getLastRenderedScreenBounds();
		return new Point(Math.round(canvasOrigin.x + bounds[0] + bounds[2] * 0.5f),
				Math.round(canvasOrigin.y + bounds[1] + bounds[3] * 0.5f));
	}

	private static Point titlePoint(GLNode node, Point canvasOrigin) {
		float[] bounds = node.getLastRenderedScreenBounds();
		return new Point(Math.round(canvasOrigin.x + bounds[0] + 55),
				Math.round(canvasOrigin.y + bounds[1] + 14));
	}

	private static void drag(Robot robot, Point start, Point end) {
		drag(robot, start, end, InputEvent.BUTTON1_DOWN_MASK);
	}

	private static void drag(Robot robot, Point start, Point end, int buttonMask) {
		robot.mouseMove(start.x, start.y);
		robot.delay(90);
		robot.mousePress(buttonMask);
		for (int step = 1; step <= 12; step++) {
			float amount = step / 12f;
			robot.mouseMove(Math.round(start.x + (end.x - start.x) * amount),
					Math.round(start.y + (end.y - start.y) * amount));
		}
		robot.mouseRelease(buttonMask);
	}

	private static void click(Robot robot, Point point, int buttonMask) {
		robot.mouseMove(point.x, point.y);
		robot.delay(90);
		robot.mousePress(buttonMask);
		robot.mouseRelease(buttonMask);
	}

	private static boolean onEdt(Check check) {
		AtomicBoolean result = new AtomicBoolean();
		try {
			SwingUtilities.invokeAndWait(() -> result.set(check.evaluate()));
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
		return result.get();
	}

	private static void waitUntil(Check check, String message) throws Exception {
		long deadline = System.nanoTime() + 5_000_000_000L;
		while (System.nanoTime() < deadline) {
			if (check.evaluate())
				return;
			Thread.sleep(25);
		}
		throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface Check {
		boolean evaluate();
	}

	private static final class Holder {
		private GLFrame frame;
		private GLNodeEditor editor;
		private GLNodePort output;
		private GLNodePort input;
		private GLNode resizeNode;
		private GLNode sourceNode;
		private GLNode targetNode;
	}
}
