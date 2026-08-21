package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.ui.GLSlider;

/** Native Canvas regression for changing render resolution during a slider drag. */
public final class CanvasResolutionSliderRobotSelfTest {

	private CanvasResolutionSliderRobotSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess())
			throw new IllegalStateException("Missing VM arguments: "
					+ GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
		AtomicReference<Throwable> uncaught = new AtomicReference<>();
		Thread.UncaughtExceptionHandler former = Thread.getDefaultUncaughtExceptionHandler();
		Thread mainThread = Thread.currentThread();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
			if (thread == mainThread)
				error.printStackTrace();
			else
				uncaught.compareAndSet(null, error);
		});
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> {
			holder.canvas = new TestCanvas();
			holder.canvas.setPreferredSize(new Dimension(720, 420));
			holder.frame = new JFrame("Canvas resolution slider regression");
			holder.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			holder.frame.add(holder.canvas);
			holder.frame.pack();
			holder.frame.setLocationRelativeTo(null);
			holder.frame.setAlwaysOnTop(true);
			holder.frame.setVisible(true);
			holder.animator = new FPSAnimator(holder.canvas, 144, true);
			holder.animator.start();
		});

		try {
			Robot robot = new Robot();
			robot.setAutoDelay(12);
			for (int iteration = 0; iteration < 20; iteration++) {
				awaitPickReady(holder.canvas);
				float from = iteration % 2 == 0 ? 0.9f : 0.35f;
				float to = iteration % 2 == 0 ? 0.35f : 0.9f;
				Point start = target(holder.canvas, from);
				Point end = target(holder.canvas, to);
				if (iteration == 0)
					robot.mouseMove(start.x - 80, start.y - 80);
				robot.mouseMove(start.x, start.y);
				robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
				for (int step = 1; step <= 8; step++)
					robot.mouseMove(start.x + (end.x - start.x) * step / 8,
							start.y + (end.y - start.y) * step / 8);
				robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
				Thread.sleep(35);
			}
			// Theme mutation used to make a first press perform a synchronous GL
			// frame while the animator was active.
			for (int iteration = 0; iteration < 12; iteration++) {
				awaitPickReady(holder.canvas, holder.canvas.roundnessSlider);
				Point start = target(holder.canvas, holder.canvas.roundnessSlider,
						iteration % 2 == 0 ? 0.15f : 0.85f);
				Point end = target(holder.canvas, holder.canvas.roundnessSlider,
						iteration % 2 == 0 ? 0.85f : 0.15f);
				robot.mouseMove(start.x, start.y);
				robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
				robot.mouseMove(end.x, end.y);
				robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
				Thread.sleep(35);
			}

			// Losing the native Canvas while pressed must cancel capture even when
			// Windows never delivers the later outside release to this component.
			awaitPickReady(holder.canvas, holder.canvas.slider);
			Point inside = target(holder.canvas, holder.canvas.slider, 0.5f);
			Point origin = holder.canvas.getLocationOnScreen();
			robot.mouseMove(inside.x, inside.y);
			robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			robot.mouseMove(origin.x - 40, origin.y - 40);
			robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			Thread.sleep(800);

			Throwable failure = uncaught.get();
			if (failure != null)
				throw new AssertionError("Canvas slider release raised an exception", failure);
			if (!holder.frame.isDisplayable() || !holder.animator.isAnimating())
				throw new AssertionError("Canvas stopped after resolution slider release");
			if (holder.canvas.changeEvents.get() < 20)
				throw new AssertionError("Resolution slider did not emit enough changes: "
						+ holder.canvas.changeEvents.get());
			if (holder.canvas.slider.getControlState() == GLControlState.PRESSED)
				throw new AssertionError("Slider retained PRESS state after pointer exit");
			System.out.println("Canvas resolution slider robot self-test passed.");
		} finally {
			if (holder.animator != null && holder.animator.isStarted())
				holder.animator.stop();
			SwingUtilities.invokeAndWait(holder.frame::dispose);
			Thread.setDefaultUncaughtExceptionHandler(former);
		}
	}

	private static void awaitPickReady(TestCanvas canvas) throws Exception {
		awaitPickReady(canvas, canvas.slider);
	}

	private static void awaitPickReady(TestCanvas canvas, GLSlider slider)
			throws Exception {
		long started = System.nanoTime();
		while ((System.nanoTime() - started) / 1_000_000L < 10_000) {
			boolean[] ready = new boolean[1];
			SwingUtilities.invokeAndWait(() -> {
				int x = Math.round(slider.getX() + slider.getWidth() * 0.5f);
				int y = Math.round(slider.getY() + slider.getHeight() * 0.5f);
				ready[0] = canvas.pickResult(x, y).getTarget() == slider;
			});
			if (ready[0])
				return;
			Thread.sleep(100);
		}
		throw new AssertionError("Slider never became available in GPU picking");
	}

	private static Point target(TestCanvas canvas, float progress) throws Exception {
		return target(canvas, canvas.slider, progress);
	}

	private static Point target(TestCanvas canvas, GLSlider slider, float progress)
			throws Exception {
		Point[] result = new Point[1];
		SwingUtilities.invokeAndWait(() -> {
			Point origin = canvas.getLocationOnScreen();
			result[0] = new Point(origin.x + Math.round(slider.getX()
					+ slider.getWidth() * progress),
					origin.y + Math.round(slider.getY()
							+ slider.getHeight() * 0.5f));
		});
		return result[0];
	}

	private static final class Holder {
		private JFrame frame;
		private TestCanvas canvas;
		private FPSAnimator animator;
	}

	private static final class TestCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final GLSlider slider = GLSlider.fixed("Aufloesung", 25, 100, 100);
		private final GLSlider roundnessSlider =
				GLSlider.fixed("Rundung", 0, 16, 4);
		private final AtomicInteger changeEvents = new AtomicInteger();

		private TestCanvas() {
			super(4, 0);
			slider.setBounds(210, 195, 300, slider.getPreferredGLSize().height());
			roundnessSlider.setBounds(210, 245, 300,
					roundnessSlider.getPreferredGLSize().height());
			slider.addChangeListener(event -> {
				changeEvents.incrementAndGet();
				setRenderResolutionScale((float) slider.getValue() / 100f);
				// Deliberately recreate the former worst case: release arrives while
				// a newer picking generation is pending.
				repaintAndRebuildPicking(slider);
			});
			addGL(slider);
			roundnessSlider.addChangeListener(event ->
					setGLTheme(getGLTheme().withCornerRadius(
							(float) roundnessSlider.getValue())));
			addGL(roundnessSlider);
			for (int index = 0; index < 80; index++) {
				GLButton button = new GLButton("Theme target " + index);
				button.setBounds(10 + index % 10 * 68,
						10 + index / 10 * 20, 64, 16);
				addGL(button);
			}
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(24, 29, 40));
		}
	}
}
