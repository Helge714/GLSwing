package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.GLAnimatorControl;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.component.GLSwingCanvas;

/** Automated end-to-end comparison of the two showcase presentation backends. */
public final class ShowcaseBackendBenchmark {
	private static final long POST_READY_WARMUP_MILLIS = 2_000;
	private static final long READY_TIMEOUT_MILLIS = 30_000;
	private static final int SAMPLE_COUNT = 10;
	private static final long SAMPLE_INTERVAL_MILLIS = 500;

	private ShowcaseBackendBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("ShowcaseBackendBenchmark benoetigt diese VM-Argumente:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			return;
		}
		System.out.println("GLSwingNext Showcase Backend Benchmark");
		System.out.printf("GLJPanel und GLCanvas, ungekappt | Warmup nach Initialisierung: %.1f s | Messung: %.1f s%n%n",
				POST_READY_WARMUP_MILLIS / 1000.0,
				SAMPLE_COUNT * SAMPLE_INTERVAL_MILLIS / 1000.0);
		Result panel4x = measurePanel(4);
		Result canvas4x = measureCanvas(4);
		Result panel0x = measurePanel(0);
		Result canvas0x = measureCanvas(0);
		print(panel4x);
		print(canvas4x);
		print(panel0x);
		print(canvas0x);
		printComparison(panel4x, canvas4x);
		printComparison(panel0x, canvas0x);
		printMsaaComparison(panel4x, panel0x);
		printMsaaComparison(canvas4x, canvas0x);
	}

	private static Result measurePanel(int antialiasingSamples) throws Exception {
		GuiShowcaseDemo.ShowcasePanel[] component = new GuiShowcaseDemo.ShowcasePanel[1];
		JFrame[] frame = new JFrame[1];
		Animator[] animator = new Animator[1];
		SwingUtilities.invokeAndWait(() -> {
			component[0] = new GuiShowcaseDemo.ShowcasePanel(antialiasingSamples);
			component[0].setPreferredSize(new Dimension(1260, 800));
			frame[0] = frame("Benchmark - GLJPanel " + antialiasingSamples + "x MSAA",
					component[0]);
			animator[0] = new Animator(component[0]);
			animator[0].setRunAsFastAsPossible(true);
			animator[0].start();
		});
		try {
			return collect("GLJPanel " + antialiasingSamples + "x",
					component[0]::getWidth, component[0]::getHeight,
					component[0]::getFramesPerSecond,
					component[0]::getFrameProfile, component[0]::getLastDrawCallCount,
					component[0]::getSurfacePixelWidth,
					component[0]::getSurfacePixelHeight,
					component[0]::getActualAntialiasingSamples, () -> -1);
		} finally {
			component[0].closeThemeWatcher();
			stop(animator[0], frame[0]);
		}
	}

	private static Result measureCanvas(int antialiasingSamples) throws Exception {
		CanvasGuiShowcaseDemo.ShowcaseCanvas[] component =
				new CanvasGuiShowcaseDemo.ShowcaseCanvas[1];
		JFrame[] frame = new JFrame[1];
		Animator[] animator = new Animator[1];
		SwingUtilities.invokeAndWait(() -> {
			component[0] = new CanvasGuiShowcaseDemo.ShowcaseCanvas(antialiasingSamples);
			component[0].setPreferredSize(new Dimension(1260, 800));
			frame[0] = frame("Benchmark - GLCanvas " + antialiasingSamples + "x MSAA",
					component[0]);
			animator[0] = new Animator(component[0]);
			animator[0].setRunAsFastAsPossible(true);
			animator[0].start();
		});
		try {
			return collect("GLCanvas " + antialiasingSamples + "x",
					component[0]::getWidth, component[0]::getHeight,
					component[0]::getFramesPerSecond,
					component[0]::getFrameProfile, component[0]::getLastDrawCallCount,
					component[0]::getSurfacePixelWidth,
					component[0]::getSurfacePixelHeight,
					component[0]::getActualAntialiasingSamples,
					component[0]::getActualSwapInterval);
		} finally {
			component[0].closeThemeWatcher();
			stop(animator[0], frame[0]);
		}
	}

	private static JFrame frame(String title, Component component) {
		JFrame frame = new JFrame(title);
		frame.setLayout(new BorderLayout());
		frame.add(component, BorderLayout.CENTER);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		return frame;
	}

	private static Result collect(String name, IntValue logicalWidth,
			IntValue logicalHeight,
			DoubleValue fps, ProfileValue profile, IntValue drawCalls,
			IntValue surfaceWidth, IntValue surfaceHeight, IntValue samples,
			IntValue swapInterval) throws InterruptedException {
		System.out.println(name + " waermt auf ...");
		awaitStable(name, fps, profile, drawCalls);
		Thread.sleep(POST_READY_WARMUP_MILLIS);
		List<Sample> samplesList = new ArrayList<>();
		for (int index = 0; index < SAMPLE_COUNT; index++) {
			Thread.sleep(SAMPLE_INTERVAL_MILLIS);
			GLFrameProfile value = profile.get();
			samplesList.add(new Sample(fps.get(), value, drawCalls.get()));
		}
		System.out.println(name + " abgeschlossen.");
		return new Result(name, logicalWidth.get(), logicalHeight.get(),
				surfaceWidth.get(), surfaceHeight.get(), samples.get(), swapInterval.get(),
				samplesList);
	}

	private static void awaitStable(String name, DoubleValue fps,
			ProfileValue profile, IntValue drawCalls) throws InterruptedException {
		long started = System.nanoTime();
		int stableWindows = 0;
		while ((System.nanoTime() - started) / 1_000_000L < READY_TIMEOUT_MILLIS) {
			Thread.sleep(250);
			GLFrameProfile current = profile.get();
			boolean stable = fps.get() > 5 && current.endToEndMillis() > 0
					&& current.endToEndMillis() < 250
					&& current.callbackCpuMillis() < 250
					&& drawCalls.get() == 0;
			stableWindows = stable ? stableWindows + 1 : 0;
			if (stableWindows >= 4) {
				System.out.printf("%s initialisiert nach %.2f s.%n", name,
						(System.nanoTime() - started) / 1_000_000_000.0);
				return;
			}
		}
		throw new IllegalStateException(name
				+ " erreichte innerhalb des Timeouts keinen stabilen Zustand");
	}

	private static void stop(GLAnimatorControl animator, Window frame)
			throws Exception {
		Thread stopper = new Thread(animator::stop, "benchmark-animator-stop");
		stopper.start();
		stopper.join(5_000);
		SwingUtilities.invokeAndWait(frame::dispose);
	}

	private static void print(Result result) {
		System.out.printf("%n%s  logisch %dx%d | physisch %dx%d | MSAA %dx",
				result.name, result.logicalWidth, result.logicalHeight,
				result.surfaceWidth, result.surfaceHeight, result.samples);
		if (result.swapInterval >= 0)
			System.out.print(" | Swap " + result.swapInterval);
		System.out.println();
		metric("FPS", result.values(Sample::fps));
		metric("Frameperiode ms", result.values(sample ->
				sample.fps > 0 ? 1000.0 / sample.fps : 0));
		metric("Call E2E ms", result.values(sample -> sample.profile.endToEndMillis()));
		metric("Schedule/Wait ms", result.values(sample -> Math.max(0,
				(sample.fps > 0 ? 1000.0 / sample.fps : 0)
						- sample.profile.endToEndMillis())));
		metric("Callback CPU ms", result.values(sample ->
				sample.profile.callbackCpuMillis()));
		metric("Backend-Rest ms", result.values(sample ->
				sample.profile.backendRemainderMillis()));
		metric("Housekeeping CPU ms", result.values(sample ->
				sample.profile.housekeepingCpuMillis()));
		metric("Scene CPU ms", result.values(sample -> sample.profile.sceneCpuMillis()));
		metric("Present CPU ms", result.values(sample ->
				sample.profile.presentCpuMillis()));
		metric("Scene GPU ms", result.values(sample -> sample.profile.sceneGpuMillis()));
		metric("Present GPU ms", result.values(sample ->
				sample.profile.presentGpuMillis()));
		metric("Drawcalls", result.values(Sample::drawCalls));
	}

	private static void printComparison(Result panel, Result canvas) {
		double panelFps = average(panel.values(Sample::fps));
		double canvasFps = average(canvas.values(Sample::fps));
		double panelPresent = average(panel.values(sample ->
				sample.profile.presentGpuMillis()));
		double canvasPresent = average(canvas.values(sample ->
				sample.profile.presentGpuMillis()));
		System.out.printf("%nBackend-Vergleich %dx MSAA: Canvas %.2fx Panel-FPS | GPU-Present Panel %.3f ms, Canvas %.3f ms%n",
				panel.samples,
				panelFps > 0 ? canvasFps / panelFps : 0, panelPresent, canvasPresent);
	}

	private static void printMsaaComparison(Result withMsaa, Result withoutMsaa) {
		double fps4x = average(withMsaa.values(Sample::fps));
		double fps0x = average(withoutMsaa.values(Sample::fps));
		double present4x = average(withMsaa.values(sample ->
				sample.profile.presentGpuMillis()));
		double present0x = average(withoutMsaa.values(sample ->
				sample.profile.presentGpuMillis()));
		System.out.printf("MSAA-Vergleich %s: 4x %.1f FPS / %.3f ms GPU-Present, 0x %.1f FPS / %.3f ms%n",
				withMsaa.name.replace(" 4x", ""), fps4x, present4x, fps0x, present0x);
	}

	private static void metric(String label, double[] values) {
		double minimum = Double.POSITIVE_INFINITY;
		double maximum = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			minimum = Math.min(minimum, value);
			maximum = Math.max(maximum, value);
		}
		System.out.printf("  %-22s %9.3f  (min %9.3f | max %9.3f)%n",
				label, average(values), minimum, maximum);
	}

	private static double average(double[] values) {
		double sum = 0;
		for (double value : values)
			sum += value;
		return values.length == 0 ? 0 : sum / values.length;
	}

	private record Sample(double fps, GLFrameProfile profile, int drawCalls) {
	}

	private record Result(String name, int logicalWidth, int logicalHeight,
			int surfaceWidth, int surfaceHeight, int samples, int swapInterval,
			List<Sample> samplesList) {
		double[] values(SampleValue extractor) {
			return samplesList.stream().mapToDouble(extractor::get).toArray();
		}
	}

	@FunctionalInterface
	private interface DoubleValue { double get(); }
	@FunctionalInterface
	private interface IntValue { int get(); }
	@FunctionalInterface
	private interface ProfileValue { GLFrameProfile get(); }
	@FunctionalInterface
	private interface SampleValue { double get(Sample sample); }
}
