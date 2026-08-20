package com.vingame.glswing.demo;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;

/**
 * Reproducible offscreen benchmark for sprite material channels and picking.
 * It excludes Swing, buffer swaps and the Windows compositor.
 */
public final class SpritePipelineBenchmark {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SAMPLES = 4;
    private static final int PRECONDITION_FRAMES = 20;
    private static final int WARMUP_FRAMES = 20;
    private static final int MEASURED_FRAMES = 60;
    private static final int[] COUNTS = { 100, 1_000, 10_000, 50_000 };
    private static final Color BACKGROUND = new Color(12, 17, 27);

    private SpritePipelineBenchmark() { }

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
            throw new IllegalStateException(
                    "Could not make the offscreen OpenGL context current");

        GLRenderer2D renderer = null;
        try {
            GL3 gl = drawable.getGL().getGL3();
            gl.glViewport(0, 0, WIDTH, HEIGHT);
            renderer = GLRenderer2D.create(gl);
            SpriteBenchmarkWorkload workload = new SpriteBenchmarkWorkload();

            int actualSamples = drawable.getChosenGLCapabilities().getSampleBuffers()
                    ? drawable.getChosenGLCapabilities().getNumSamples() : 1;
            System.out.println("GLSwing sprite-pipeline benchmark");
            System.out.printf("GPU: %s%n", gl.glGetString(GL.GL_RENDERER));
            System.out.printf("OpenGL: %s%n", gl.glGetString(GL.GL_VERSION));
            System.out.printf("Surface: %dx%d, MSAA: %dx requested / %dx active%n",
                    WIDTH, HEIGHT, SAMPLES, actualSamples);
            System.out.printf("Warm-up: %d, measurement: %d frames per case%n%n",
                    WARMUP_FRAMES, MEASURED_FRAMES);

            for (int frame = 0; frame < PRECONDITION_FRAMES; frame++)
                drawFrame(gl, renderer, workload, new Case("precondition",
                        10_000, SpriteBenchmarkWorkload.Channels.LIT,
                        GLAlphaMode.CUTOUT, true, true), frame);
            gl.glFinish();

            List<Case> cases = cases();
            printHeader();
            for (Case benchmarkCase : cases)
                print(runCase(gl, renderer, workload, benchmarkCase));
            System.out.println();
            System.out.println("CPU API misst nur Workload-Aufbau und drawSprite-Aufrufe.");
            System.out.println("CPU Submit umfasst beginFrame/endFrame, wartet aber nicht auf die GPU.");
            System.out.println("GPU ist GL_TIME_ELAPSED ohne Swap, Swing und Desktop-Compositor.");
            System.out.println("Die Picking-Faelle schreiben zusaetzlich den ID- und Picking-Depth-Pass.");
        } finally {
            GL3 gl = drawable.getGL().getGL3();
            if (renderer != null)
                renderer.dispose(gl);
            nativeContext.release();
            drawable.destroy();
        }
    }

    private static List<Case> cases() {
        List<Case> result = new ArrayList<>();
        for (SpriteBenchmarkWorkload.Channels channels
                : SpriteBenchmarkWorkload.Channels.values())
            for (int count : COUNTS)
                result.add(new Case(channels.name().toLowerCase(Locale.ROOT),
                        count, channels, GLAlphaMode.CUTOUT, false, false));

        result.add(new Case("lit static", 10_000,
                SpriteBenchmarkWorkload.Channels.LIT,
                GLAlphaMode.CUTOUT, false, false));
        result.add(new Case("lit moving", 10_000,
                SpriteBenchmarkWorkload.Channels.LIT,
                GLAlphaMode.CUTOUT, true, false));
        for (GLAlphaMode alphaMode : GLAlphaMode.values())
            result.add(new Case("alpha " + alphaMode.name().toLowerCase(Locale.ROOT),
                    10_000, SpriteBenchmarkWorkload.Channels.LIT,
                    alphaMode, false, false));
        result.add(new Case("picking off", 10_000,
                SpriteBenchmarkWorkload.Channels.LIT,
                GLAlphaMode.CUTOUT, false, false));
        result.add(new Case("picking on", 10_000,
                SpriteBenchmarkWorkload.Channels.LIT,
                GLAlphaMode.CUTOUT, false, true));
        return result;
    }

    private static Result runCase(GL3 gl, GLRenderer2D renderer,
            SpriteBenchmarkWorkload workload, Case benchmarkCase) {
        for (int frame = 0; frame < WARMUP_FRAMES; frame++)
            drawFrame(gl, renderer, workload, benchmarkCase, frame);
        gl.glFinish();

        int[] queries = new int[MEASURED_FRAMES];
        gl.glGenQueries(queries.length, queries, 0);
        double[] apiMilliseconds = new double[MEASURED_FRAMES];
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
                long apiStart = System.nanoTime();
                workload.draw(context, benchmarkCase.objectCount,
                        benchmarkCase.channels, benchmarkCase.alphaMode,
                        benchmarkCase.moving, benchmarkCase.picking,
                        frame / 60f, 0, 0, WIDTH, HEIGHT, false);
                long apiEnd = System.nanoTime();
                renderer.endFrame();
                long submitEnd = System.nanoTime();
                gl.glEndQuery(GL2ES2.GL_TIME_ELAPSED);

                apiMilliseconds[frame] = millis(apiEnd - apiStart);
                submitMilliseconds[frame] = millis(submitEnd - submitStart);
                drawCalls = renderer.drawCallCount();
            }
            gl.glFinish();
            long[] elapsedNanoseconds = new long[1];
            for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
                gl.glGetQueryObjectui64v(queries[frame], GL2ES2.GL_QUERY_RESULT,
                        elapsedNanoseconds, 0);
                gpuMilliseconds[frame] = millis(elapsedNanoseconds[0]);
            }
        } finally {
            gl.glDeleteQueries(queries.length, queries, 0);
        }
        return new Result(benchmarkCase, drawCalls,
                Statistics.of(apiMilliseconds), Statistics.of(submitMilliseconds),
                Statistics.of(gpuMilliseconds));
    }

    private static void drawFrame(GL3 gl, GLRenderer2D renderer,
            SpriteBenchmarkWorkload workload, Case benchmarkCase, int frame) {
        GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
                Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
        context.clear(BACKGROUND);
        workload.draw(context, benchmarkCase.objectCount, benchmarkCase.channels,
                benchmarkCase.alphaMode, benchmarkCase.moving,
                benchmarkCase.picking, frame / 60f, 0, 0, WIDTH, HEIGHT, false);
        renderer.endFrame();
    }

    private static void printHeader() {
        System.out.printf("%-16s %8s %9s %7s | %17s | %22s | %17s%n",
                "case", "objects", "picking", "draws", "CPU API p50/p95",
                "CPU submit p50/p95/max", "GPU p50/p95");
        System.out.println("-".repeat(116));
    }

    private static void print(Result result) {
        Case benchmarkCase = result.benchmarkCase;
        System.out.printf("%-16s %,8d %9s %,7d | %7.3f / %7.3f | %7.3f / %7.3f / %7.3f | %7.3f / %7.3f ms%n",
                benchmarkCase.label, benchmarkCase.objectCount,
                benchmarkCase.picking ? "on" : "off", result.drawCalls,
                result.api.p50, result.api.p95,
                result.submit.p50, result.submit.p95, result.submit.maximum,
                result.gpu.p50, result.gpu.p95);
    }

    private static double millis(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private record Case(String label, int objectCount,
            SpriteBenchmarkWorkload.Channels channels,
            GLAlphaMode alphaMode, boolean moving, boolean picking) { }

    private record Result(Case benchmarkCase, int drawCalls,
            Statistics api, Statistics submit, Statistics gpu) { }

    private record Statistics(double p50, double p95, double maximum) {
        static Statistics of(double[] samples) {
            double[] sorted = samples.clone();
            Arrays.sort(sorted);
            return new Statistics(percentile(sorted, 0.50),
                    percentile(sorted, 0.95), sorted[sorted.length - 1]);
        }

        private static double percentile(double[] sorted, double percentile) {
            double position = percentile * (sorted.length - 1);
            int lower = (int) Math.floor(position);
            int upper = (int) Math.ceil(position);
            if (lower == upper)
                return sorted[lower];
            double fraction = position - lower;
            return sorted[lower] * (1 - fraction)
                    + sorted[upper] * fraction;
        }
    }
}
