package com.vingame.glswing.test;

import java.awt.Color;
import java.util.Arrays;
import java.util.Locale;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** Measures relative recording replay with identity and component transforms. */
public final class RecordingTransformBenchmark {

	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	private static final int OBJECTS = 50_000;
	private static final int WARMUP_FRAMES = 30;
	private static final int MEASURED_FRAMES = 100;
	private static final Color BACKGROUND = new Color(13, 20, 38);
	private static final Color FOREGROUND = new Color(80, 190, 255, 210);
	private static final GLStroke ROUND_STROKE = new GLStroke(2,
			GLStroke.Cap.ROUND, GLStroke.Join.ROUND, 10, 0,
			GLStroke.CURVE_TOLERANCE_FINE, GLStroke.WidthSpace.SCREEN);

	private RecordingTransformBenchmark() {
	}

	public static void main(String[] args) {
		Locale.setDefault(Locale.ROOT);
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		capabilities.setSampleBuffers(true);
		capabilities.setNumSamples(4);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();
		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");

		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			System.out.printf("Relative recording transform benchmark: %,d objects%n", OBJECTS);
			System.out.printf("%-12s %-11s %8s | %17s | %22s%n",
					"geometry", "transform", "draws", "replay p50/p95",
					"submit p50/p95/max");
			System.out.println("-".repeat(82));
			for (Geometry geometry : Geometry.values()) {
				GLRecording recording = createRecording(gl, renderer, geometry);
				for (TransformCase transform : TransformCase.values())
					print(measure(gl, renderer, geometry, transform, recording));
			}
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static GLRecording createRecording(GL3 gl, GLRenderer2D renderer,
			Geometry geometry) {
		GLContext2D context = beginFrame(gl, renderer);
		context.beginRecording();
		drawObjects(context, geometry);
		GLRecording recording = context.endRecording();
		renderer.endFrame();
		return recording;
	}

	private static Result measure(GL3 gl, GLRenderer2D renderer,
			Geometry geometry, TransformCase transform, GLRecording recording) {
		for (int frame = 0; frame < WARMUP_FRAMES; frame++)
			drawFrame(gl, renderer, transform.transform, recording, null);
		gl.glFinish();
		double[] replay = new double[MEASURED_FRAMES];
		double[] submit = new double[MEASURED_FRAMES];
		long[] timings = new long[2];
		int draws = 0;
		for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
			drawFrame(gl, renderer, transform.transform, recording, timings);
			replay[frame] = millis(timings[0]);
			submit[frame] = millis(timings[1]);
			draws = renderer.drawCallCount();
		}
		gl.glFinish();
		return new Result(geometry.label, transform.label, draws,
				Statistics.of(replay), Statistics.of(submit));
	}

	private static void drawFrame(GL3 gl, GLRenderer2D renderer,
			GLTransform transform, GLRecording recording,
			long[] timings) {
		long submitStart = System.nanoTime();
		GLContext2D context = beginFrame(gl, renderer);
		context.clear(BACKGROUND);
		context.setTransform(transform);
		long replayStart = System.nanoTime();
		context.draw(recording);
		long replayEnd = System.nanoTime();
		renderer.endFrame();
		long submitEnd = System.nanoTime();
		if (timings != null) {
			timings[0] = replayEnd - replayStart;
			timings[1] = submitEnd - submitStart;
		}
	}

	private static GLContext2D beginFrame(GL3 gl, GLRenderer2D renderer) {
		return renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
	}

	private static void drawObjects(GLContext2D context, Geometry geometry) {
		if (geometry == Geometry.ROUND)
			context.setStroke(ROUND_STROKE);
		double aspect = (double) WIDTH / HEIGHT;
		int columns = Math.max(1, (int) Math.ceil(Math.sqrt(OBJECTS * aspect)));
		int rows = Math.max(1, (OBJECTS + columns - 1) / columns);
		float cellWidth = (float) WIDTH / columns;
		float cellHeight = (float) HEIGHT / rows;
		float insetX = cellWidth * 0.1f;
		float insetY = cellHeight * 0.1f;
		float rectangleWidth = Math.max(0.25f, cellWidth - 2 * insetX);
		float rectangleHeight = Math.max(0.25f, cellHeight - 2 * insetY);
		for (int index = 0; index < OBJECTS; index++) {
			int column = index % columns;
			int row = index / columns;
			float x = column * cellWidth + insetX;
			float y = row * cellHeight + insetY;
			if (geometry == Geometry.FILLED)
				context.fillRect(x, y, rectangleWidth, rectangleHeight, FOREGROUND);
			else
				context.drawRect(x, y, rectangleWidth, rectangleHeight, FOREGROUND);
		}
	}

	private static void print(Result result) {
		System.out.printf("%-12s %-11s %,8d | %7.3f / %7.3f | %7.3f / %7.3f / %7.3f ms%n",
				result.geometry, result.transform, result.draws,
				result.replay.p50, result.replay.p95,
				result.submit.p50, result.submit.p95, result.submit.maximum);
	}

	private static double millis(long nanoseconds) {
		return nanoseconds / 1_000_000.0;
	}

	private enum Geometry {
		FILLED("filled"), ROUND("round");

		private final String label;

		Geometry(String label) {
			this.label = label;
		}
	}

	private enum TransformCase {
		IDENTITY("identity", new GLTransform()),
		TRANSLATE("translate", new GLTransform().translate(0.35f, 0.6f)),
		AFFINE("affine", new GLTransform().translate(2, -1).rotate(0.03f).scale(1.01f, 0.99f));

		private final String label;
		private final GLTransform transform;

		TransformCase(String label, GLTransform transform) {
			this.label = label;
			this.transform = transform;
		}
	}

	private record Result(String geometry, String transform, int draws,
			Statistics replay, Statistics submit) {
	}

	private record Statistics(double p50, double p95, double maximum) {
		private static Statistics of(double[] values) {
			double[] sorted = values.clone();
			Arrays.sort(sorted);
			return new Statistics(percentile(sorted, 0.50), percentile(sorted, 0.95),
					sorted[sorted.length - 1]);
		}

		private static double percentile(double[] sorted, double percentile) {
			double position = percentile * (sorted.length - 1);
			int lower = (int) Math.floor(position);
			int upper = (int) Math.ceil(position);
			if (lower == upper)
				return sorted[lower];
			double fraction = position - lower;
			return sorted[lower] * (1 - fraction) + sorted[upper] * fraction;
		}
	}
}
