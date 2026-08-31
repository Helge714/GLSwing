package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.ui.GLLabel;

/** Reproducible direct-canvas workload for node-curve and viewport performance. */
public final class NodeEditorBenchmarkDemo {
	private static final int COLUMNS = 10, ROWS = 8, PHASE_MILLIS = 6_000;
	private NodeEditorBenchmarkDemo() { }

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Node Editor Benchmark / Canvas");
		SwingUtilities.invokeLater(NodeEditorBenchmarkDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwingNext - Node Editor Benchmark / Canvas",
				GLFrame.RenderingSettings.DEFAULT.withVSync(false).withTargetFps(1_000));
		frame.setGLClearColor(new Color(18, 21, 28));
		frame.setMinimumSize(new Dimension(960, 620));
		frame.setSize(1400, 850);
		frame.setLocationRelativeTo(null);
		GLNodeEditor editor = new GLNodeEditor();
		editor.setBounds(0, 74, 1400, 776);
		frame.addGL(editor);
		List<GLNode> nodes = populate(editor);
		GLNode moving = nodes.get(nodes.size() / 2);
		float originX = moving.getX(), originY = moving.getY();
		GLLabel primary = new GLLabel("Benchmark wird initialisiert …");
		primary.setBounds(16, 10, 1360, 22);
		frame.addGL(primary);
		GLLabel secondary = new GLLabel("");
		secondary.setBounds(16, 38, 1360, 22);
		frame.addGL(secondary);

		long started = System.nanoTime();
		Timer workload = new Timer(8, event -> {
			long elapsed = (System.nanoTime() - started) / 1_000_000;
			double t = elapsed % PHASE_MILLIS / 1000.0;
			if ((elapsed / PHASE_MILLIS & 1) == 0)
				moving.setLocation(originX + (float) Math.sin(t * 3.2) * 150,
						originY + (float) Math.cos(t * 2.1) * 70);
			else
				editor.setZoomAtScreenPosition(0.85f
						+ (float) (0.35 * (0.5 + 0.5 * Math.sin(t * 2.4))),
						editor.getWidth() / 2, editor.getHeight() / 2);
		});
		workload.setCoalesce(true);
		workload.start();
		Timer status = new Timer(250, event -> updateStatus(frame, editor,
				primary, secondary, (System.nanoTime() - started) / 1_000_000));
		status.start();
		frame.getGLCanvas().addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent event) {
				editor.setSize(Math.max(1, frame.getGLCanvas().getWidth()),
						Math.max(1, frame.getGLCanvas().getHeight() - 74));
				float width = Math.max(1, frame.getGLCanvas().getWidth() - 32);
				primary.setSize(width, primary.getHeight());
				secondary.setSize(width, secondary.getHeight());
			}
		});
		frame.closedFuture().whenComplete((unused, error) -> {
			workload.stop();
			status.stop();
		});
		frame.setVisible(true);
	}

	private static List<GLNode> populate(GLNodeEditor editor) {
		List<GLNode> nodes = new ArrayList<>();
		List<GLNodePort> outputs = new ArrayList<>();
		for (int row = 0; row < ROWS; row++) for (int column = 0; column < COLUMNS; column++) {
			int index = row * COLUMNS + column;
			GLNode node = new GLNode("Node " + index);
			GLNodePort input = index > 0 ? node.addInput("in", new GLLabel("Input")) : null;
			GLNodePort output = node.addOutput("out", new GLLabel("Output"));
			editor.addNode(node, 50 + column * 235, 45 + row * 135);
			nodes.add(node);
			outputs.add(output);
			if (input != null) editor.connect(outputs.get(index - 1), input);
		}
		return nodes;
	}

	private static void updateStatus(GLFrame frame, GLNodeEditor editor,
			GLLabel primary, GLLabel secondary, long elapsed) {
		GLFrameProfile profile = frame.getFrameProfile();
		String phase = (elapsed / PHASE_MILLIS & 1) == 0 ? "Node-Dragging" : "Zoom";
		primary.setText(String.format(Locale.ROOT,
				"%s | %d Nodes / %d Verbindungen | %.0f FPS | CPU %.2f ms | GPU %.2f ms",
				phase, editor.getNodes().size(), editor.getConnections().size(),
				frame.getFramesPerSecond(), profile.sceneCpuMillis(), profile.sceneGpuMillis()));
		secondary.setText(String.format(Locale.ROOT,
				"Kurven: %d neu, %d Cache-Treffer, %d Segmente | %d Drawcalls | E2E %.2f ms",
				editor.getLastCurveTessellationCount(), editor.getLastCurveCacheHitCount(),
				editor.getLastCurveSegmentCount(), frame.getLastDrawCallCount(),
				profile.endToEndMillis()));
	}
}
