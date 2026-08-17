package com.vingame.glswing.test;

import java.awt.Color;
import java.util.Arrays;
import java.util.Locale;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
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
import com.vingame.glswing.rendering.Projection2D;

/**
 * Reproducible renderer benchmark without Swing painting, buffer swaps or the
 * Windows compositor. CPU times measure command construction/submission, while
 * OpenGL timer queries measure the corresponding GPU work.
 */
public final class RendererBenchmark {

	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	private static final int SAMPLES = 4;
	private static final int PRECONDITION_FRAMES = 80;
	private static final int RECORDING_CREATION_WARMUP = 8;
	private static final int WARMUP_FRAMES = 40;
	private static final int MEASURED_FRAMES = 120;
	private static final int[] BATCHED_OBJECT_COUNTS = { 100, 1_000, 10_000, 50_000 };
	private static final int DRAW_CALL_OBJECT_COUNT = 10_000;
	private static final int[] SPLIT_DRAW_COUNTS = { 1, 5, 25, 100, 500 };
	private static final float ROUND_CURVE_TOLERANCE = GLStroke.CURVE_TOLERANCE_FINE;
	private static final GLStroke LINE_STROKE = new GLStroke(2,
			GLStroke.Cap.BUTT, GLStroke.Join.MITER, 10, 0,
			GLStroke.CURVE_TOLERANCE_NORMAL, GLStroke.WidthSpace.SCREEN);
	private static final GLStroke ROUND_STROKE = new GLStroke(2,
			GLStroke.Cap.ROUND, GLStroke.Join.ROUND, 10, 0,
			ROUND_CURVE_TOLERANCE, GLStroke.WidthSpace.SCREEN);
	private static final Color BACKGROUND = new Color(13, 20, 38);
	private static final Color[] COLORS = {
			new Color(67, 160, 255, 210),
			new Color(255, 105, 135, 210),
			new Color(86, 218, 153, 210),
			new Color(255, 196, 87, 210)
	};

	private RendererBenchmark() {
	}

	public static void main(String[] args) {
		Locale.setDefault(Locale.ROOT);
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		capabilities.setSampleBuffers(SAMPLES > 1);
		capabilities.setNumSamples(SAMPLES);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();

		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make the offscreen OpenGL context current");

		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			if (!gl.isExtensionAvailable("GL_ARB_timer_query")
					&& !gl.isGL3())
				throw new IllegalStateException("OpenGL timer queries are not supported");
			renderer = GLRenderer2D.create(gl);

			System.out.printf("GLSwing renderer benchmark%n");
			System.out.printf("GPU: %s%n", gl.glGetString(GL.GL_RENDERER));
			System.out.printf("OpenGL: %s%n", gl.glGetString(GL.GL_VERSION));
			int actualSamples = drawable.getChosenGLCapabilities().getSampleBuffers()
					? drawable.getChosenGLCapabilities().getNumSamples() : 1;
			System.out.printf("Surface: %dx%d, MSAA: %dx requested / %dx active%n",
					WIDTH, HEIGHT, SAMPLES, actualSamples);
			System.out.printf("Preconditioning: %d frames at %,d objects%n",
					PRECONDITION_FRAMES, BATCHED_OBJECT_COUNTS[BATCHED_OBJECT_COUNTS.length - 1]);
			System.out.printf("Warm-up: %d frames, measurement: %d frames per case%n%n",
					WARMUP_FRAMES, MEASURED_FRAMES);
			for (int frame = 0; frame < PRECONDITION_FRAMES; frame++)
				drawFrame(gl, renderer, Geometry.FILLED,
						BATCHED_OBJECT_COUNTS[BATCHED_OBJECT_COUNTS.length - 1], 0);
			gl.glFinish();
			printHeader();

			for (Geometry geometry : Geometry.values()) {
				for (int warmup = 0; warmup < RECORDING_CREATION_WARMUP; warmup++)
					createRecording(gl, renderer, geometry,
							BATCHED_OBJECT_COUNTS[0]);
				System.out.printf("%n%s%n", geometry.description);
				for (int objectCount : BATCHED_OBJECT_COUNTS) {
					RecordingCreation creation = createRecording(
							gl, renderer, geometry, objectCount);
					System.out.printf("record once %,8d objects: %7.3f ms, %,d commands%n",
							objectCount, creation.milliseconds,
							creation.recording.getCommandCount());
					print(runCase(gl, renderer, geometry.label + " immediate",
							geometry, objectCount, 0, null));
					print(runCase(gl, renderer, geometry.label + " recording",
							geometry, objectCount, 0, creation.recording));
				}
			}
			for (int drawCalls : SPLIT_DRAW_COUNTS)
				print(runCase(gl, renderer, "fill 10k split", Geometry.FILLED,
						DRAW_CALL_OBJECT_COUNT, drawCalls, null));

			System.out.println();
			System.out.println("CPU build: calls to the drawing API only.");
			System.out.println("CPU submit: complete beginFrame/endFrame CPU time; it does not wait for the GPU.");
			System.out.println("GPU: elapsed device time from an OpenGL timer query; no swap/presentation included.");
			System.out.println("For slow immediate tessellation, the GPU timer can include device-idle gaps between CPU-fed batches.");
			System.out.println("p50 is the median, p95 exposes recurring slow frames, max exposes individual spikes.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static Result runCase(GL3 gl, GLRenderer2D renderer, String workload,
			Geometry geometry, int objectCount, int targetDrawCalls,
			GLRecording recording) {
		for (int frame = 0; frame < WARMUP_FRAMES; frame++)
			drawFrame(gl, renderer, geometry,
					objectCount, targetDrawCalls, recording);
		gl.glFinish();

		int[] queries = new int[MEASURED_FRAMES];
		gl.glGenQueries(queries.length, queries, 0);
		double[] buildMilliseconds = new double[MEASURED_FRAMES];
		double[] submitMilliseconds = new double[MEASURED_FRAMES];
		double[] gpuMilliseconds = new double[MEASURED_FRAMES];
		int drawCalls = 0;
		try {
			for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
				gl.glBeginQuery(GL2ES2.GL_TIME_ELAPSED, queries[frame]);
				long submitStart = System.nanoTime();
				GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
						Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
				context.clear(BACKGROUND);
				long buildStart = System.nanoTime();
				drawWorkload(context, geometry,
						objectCount, targetDrawCalls, recording);
				long buildEnd = System.nanoTime();
				renderer.endFrame();
				long submitEnd = System.nanoTime();
				gl.glEndQuery(GL2ES2.GL_TIME_ELAPSED);

				buildMilliseconds[frame] = nanosToMilliseconds(buildEnd - buildStart);
				submitMilliseconds[frame] = nanosToMilliseconds(submitEnd - submitStart);
				drawCalls = renderer.drawCallCount();
			}

			// Query results are collected only after all CPU samples, so waiting for
			// the GPU cannot accidentally inflate the measured CPU submission time.
			gl.glFinish();
			long[] elapsedNanoseconds = new long[1];
			for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
				gl.glGetQueryObjectui64v(queries[frame], GL2ES2.GL_QUERY_RESULT,
						elapsedNanoseconds, 0);
				gpuMilliseconds[frame] = nanosToMilliseconds(elapsedNanoseconds[0]);
			}
		} finally {
			gl.glDeleteQueries(queries.length, queries, 0);
		}

		return new Result(workload, objectCount, drawCalls,
				Statistics.of(buildMilliseconds), Statistics.of(submitMilliseconds),
				Statistics.of(gpuMilliseconds));
	}

	private static void drawFrame(GL3 gl, GLRenderer2D renderer,
			Geometry geometry, int objectCount, int targetDrawCalls) {
		drawFrame(gl, renderer, geometry, objectCount, targetDrawCalls, null);
	}

	private static void drawFrame(GL3 gl, GLRenderer2D renderer,
			Geometry geometry, int objectCount,
			int targetDrawCalls, GLRecording recording) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(BACKGROUND);
		drawWorkload(context, geometry, objectCount, targetDrawCalls, recording);
		renderer.endFrame();
	}

	private static RecordingCreation createRecording(GL3 gl,
			GLRenderer2D renderer, Geometry geometry, int objectCount) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(BACKGROUND);
		long start = System.nanoTime();
		context.beginRecording();
		drawObjects(context, geometry, objectCount, 0);
		GLRecording recording = context.endRecording();
		long end = System.nanoTime();
		renderer.endFrame();
		return new RecordingCreation(recording, nanosToMilliseconds(end - start));
	}

	private static void drawWorkload(GLContext2D context, Geometry geometry,
			int objectCount, int targetDrawCalls, GLRecording recording) {
		if (recording == null)
			drawObjects(context, geometry, objectCount, targetDrawCalls);
		else
			context.draw(recording);
	}

	private static void drawObjects(GLContext2D context, Geometry geometry,
			int objectCount, int targetDrawCalls) {
		if (geometry.stroke != null)
			context.setStroke(geometry.stroke);
		double aspect = (double) WIDTH / HEIGHT;
		int columns = Math.max(1, (int) Math.ceil(Math.sqrt(objectCount * aspect)));
		int rows = Math.max(1, (objectCount + columns - 1) / columns);
		float cellWidth = (float) WIDTH / columns;
		float cellHeight = (float) HEIGHT / rows;
		float insetX = cellWidth * 0.1f;
		float insetY = cellHeight * 0.1f;
		float rectangleWidth = Math.max(0.25f, cellWidth - 2 * insetX);
		float rectangleHeight = Math.max(0.25f, cellHeight - 2 * insetY);
		int objectsPerDraw = targetDrawCalls > 1
				? (objectCount + targetDrawCalls - 1) / targetDrawCalls : 0;

		for (int index = 0; index < objectCount; index++) {
			int column = index % columns;
			int row = index / columns;
			float x = column * cellWidth + insetX;
			float y = row * cellHeight + insetY;
			if (geometry == Geometry.FILLED)
				context.fillRect(x, y, rectangleWidth, rectangleHeight, COLORS[index & 3]);
			else
				context.drawRect(x, y, rectangleWidth, rectangleHeight, COLORS[index & 3]);
			if (objectsPerDraw > 0 && (index + 1) % objectsPerDraw == 0
					&& index + 1 < objectCount)
				context.flush();
		}
	}

	private static double nanosToMilliseconds(long nanoseconds) {
		return nanoseconds / 1_000_000.0;
	}

	private static void printHeader() {
		System.out.printf("%-20s %8s %7s | %17s | %22s | %17s%n",
				"workload", "objects", "draws", "CPU build p50/p95",
				"CPU submit p50/p95/max", "GPU p50/p95");
		System.out.println("-".repeat(108));
	}

	private static void print(Result result) {
		System.out.printf("%-20s %,8d %,7d | %7.3f / %7.3f | %7.3f / %7.3f / %7.3f | %7.3f / %7.3f ms%n",
				result.workload, result.objectCount, result.drawCalls,
				result.build.p50, result.build.p95,
				result.submit.p50, result.submit.p95, result.submit.maximum,
				result.gpu.p50, result.gpu.p95);
	}

	private record Result(String workload, int objectCount, int drawCalls,
			Statistics build, Statistics submit, Statistics gpu) {
	}

	private record RecordingCreation(GLRecording recording, double milliseconds) {
	}

	private enum Geometry {
		FILLED("fill", "Filled rectangles", null),
		LINE("line", "Stroked rectangles: 2 px, MITER joins", LINE_STROKE),
		ROUND("round", "Stroked rectangles: 2 px, ROUND joins, curve tolerance "
				+ ROUND_CURVE_TOLERANCE + " px", ROUND_STROKE);

		private final String label;
		private final String description;
		private final GLStroke stroke;

		Geometry(String label, String description, GLStroke stroke) {
			this.label = label;
			this.description = description;
			this.stroke = stroke;
		}
	}

	private record Statistics(double p50, double p95, double maximum) {

		private static Statistics of(double[] samples) {
			double[] sorted = samples.clone();
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
