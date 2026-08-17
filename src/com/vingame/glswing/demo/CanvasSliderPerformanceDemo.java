package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.Animator;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.demo.SliderBenchmarkScene.SliderKind;
import com.vingame.glswing.demo.SliderBenchmarkScene.UpdateLoad;
import com.vingame.glswing.rendering.GLContext2D;

/** Direct-GLCanvas benchmark for large static and changing slider scenes. */
public final class CanvasSliderPerformanceDemo {

	private CanvasSliderPerformanceDemo() {
	}

	public static void main(String[] args) {
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("CanvasSliderPerformanceDemo benötigt diese VM-Argumente:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			System.err.println(
					"Starte die vorhandene 'Canvas Slider Performance Demo.launch'.");
			return;
		}
		SwingUtilities.invokeLater(CanvasSliderPerformanceDemo::showWindow);
	}

	private static void showWindow() {
		DirectBenchmarkCanvas canvas = new DirectBenchmarkCanvas();
		canvas.setPreferredSize(new Dimension(1400, 850));
		JComboBox<Integer> count = new JComboBox<>(
				new Integer[] { 100, 500, 1000, 2000 });
		count.setSelectedItem(1000);
		JComboBox<SliderKind> kind = new JComboBox<>(SliderKind.values());
		kind.setSelectedItem(SliderKind.MIXED);
		JComboBox<UpdateLoad> updateLoad = new JComboBox<>(UpdateLoad.values());
		updateLoad.setSelectedItem(UpdateLoad.STATIC);
		JLabel status = new JLabel("Direkte Messung startet ...");

		Runnable rebuild = () -> canvas.setScenario(
				(Integer) count.getSelectedItem(),
				(SliderKind) kind.getSelectedItem(),
				(UpdateLoad) updateLoad.getSelectedItem());
		count.addActionListener(event -> rebuild.run());
		kind.addActionListener(event -> rebuild.run());
		updateLoad.addActionListener(event -> canvas.setUpdateLoad(
				(UpdateLoad) updateLoad.getSelectedItem()));

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(new JLabel("Slider:"));
		controls.add(count);
		controls.add(new JLabel("Typ:"));
		controls.add(kind);
		controls.add(new JLabel("Änderungslast:"));
		controls.add(updateLoad);
		controls.add(status);

		Animator animator = new Animator(canvas);
		animator.setRunAsFastAsPossible(true);
		Timer valueUpdates = new Timer(16, event -> canvas.advanceValues());
		Timer statistics = new Timer(300, event -> status.setText(String.format(
				"| %.0f FPS | %.2f ms Render-CPU | %.2f ms Werte-Update | %d Drawcalls | Swap %d",
				canvas.getFramesPerSecond(), canvas.getAverageFrameTimeMillis(),
				canvas.getUpdateTimeMillis(), canvas.getLastDrawCallCount(),
				canvas.getActualSwapInterval())));
		AtomicBoolean closing = new AtomicBoolean();

		JFrame frame = new JFrame(
				"GLSwingNext - Direct GLCanvas Slider Performance");
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.add(controls, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (!closing.compareAndSet(false, true))
					return;
				statistics.stop();
				valueUpdates.stop();
				Thread shutdown = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "GL-canvas-slider-performance-stop");
				shutdown.setDaemon(true);
				shutdown.start();
			}
		});
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		rebuild.run();
		valueUpdates.start();
		statistics.start();
		animator.start();
	}

	private static final class DirectBenchmarkCanvas
			extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final SliderBenchmarkScene scene;

		private DirectBenchmarkCanvas() {
			super(4, 0);
			scene = new SliderBenchmarkScene(this);
		}

		private void setScenario(int count, SliderKind kind,
				UpdateLoad updateLoad) {
			scene.setScenario(count, kind, updateLoad, getWidth(), getHeight());
		}

		private void setUpdateLoad(UpdateLoad updateLoad) {
			scene.setUpdateLoad(updateLoad);
		}

		private void advanceValues() {
			scene.advanceValues();
		}

		private double getUpdateTimeMillis() {
			return scene.getUpdateTimeMillis();
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(10, 15, 24));
		}

		@Override
		protected void resizedContainer(GL3 gl,
				int surfaceWidth, int surfaceHeight) {
			scene.arrange(getWidth(), getHeight());
		}
	}
}
