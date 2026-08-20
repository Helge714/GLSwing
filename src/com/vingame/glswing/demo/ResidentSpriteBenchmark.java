package com.vingame.glswing.demo;

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
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLSpriteBuffer;
import com.vingame.glswing.rendering.Projection2D;

/** Offscreen A/B benchmark: per-frame submission versus GPU-resident instances. */
public final class ResidentSpriteBenchmark {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int WARMUP_FRAMES = 20;
    private static final int MEASURED_FRAMES = 60;
    private static final Color BACKGROUND = new Color(12, 17, 27);
    private static final Case[] CASES = {
            new Case("dynamic", 10_000, false, false),
            new Case("resident", 10_000, false, true),
            new Case("dynamic", 50_000, false, false),
            new Case("resident", 50_000, false, true),
            new Case("dynamic pick", 10_000, true, false),
            new Case("resident pick", 10_000, true, true)
    };

    private ResidentSpriteBenchmark() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        GLProfile profile = GLProfile.get(GLProfile.GL3);
        GLCapabilities capabilities = new GLCapabilities(profile);
        capabilities.setOnscreen(false);
        capabilities.setDepthBits(24);
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
            SpriteBenchmarkWorkload workload = new SpriteBenchmarkWorkload();
            System.out.println("GLSwing dynamic/resident sprite benchmark");
            System.out.println("GPU: " + gl.glGetString(GL.GL_RENDERER));
            System.out.printf("Surface: %dx%d, warm-up: %d, samples: %d%n%n",
                    WIDTH, HEIGHT, WARMUP_FRAMES, MEASURED_FRAMES);
            System.out.printf("%-15s %8s %8s %7s | %18s | %18s%n",
                    "case", "objects", "picking", "draws",
                    "CPU API p50/p95", "GPU p50/p95");
            System.out.println("-".repeat(88));
            for (Case value : CASES)
                print(run(gl, renderer, workload, value));
            System.out.println();
            System.out.println("Resident schliesst den einmaligen Aufnahme-/Upload-Frame aus.");
            System.out.println("Resident Picking aktualisiert weiterhin frame-lokale Pick-IDs.");
        } finally {
            GL3 gl = drawable.getGL().getGL3();
            if (renderer != null)
                renderer.dispose(gl);
            nativeContext.release();
            drawable.destroy();
        }
    }

    private static Result run(GL3 gl, GLRenderer2D renderer,
            SpriteBenchmarkWorkload workload, Case value) {
        GLSpriteBuffer buffer = value.resident
                ? record(renderer, gl, workload, value) : null;
        try {
            for (int frame = 0; frame < WARMUP_FRAMES; frame++)
                draw(renderer, gl, workload, value, buffer, false, null, frame);
            gl.glFinish();
            int[] queries = new int[MEASURED_FRAMES];
            gl.glGenQueries(queries.length, queries, 0);
            double[] cpu = new double[MEASURED_FRAMES];
            double[] gpu = new double[MEASURED_FRAMES];
            int[] calls = new int[1];
            try {
                for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
                    gl.glBeginQuery(GL2ES2.GL_TIME_ELAPSED, queries[frame]);
                    draw(renderer, gl, workload, value, buffer, true,
                            new Measurement(cpu, frame), frame);
                    gl.glEndQuery(GL2ES2.GL_TIME_ELAPSED);
                    calls[0] = renderer.drawCallCount();
                }
                gl.glFinish();
                long[] elapsed = new long[1];
                for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
                    gl.glGetQueryObjectui64v(queries[frame], GL2ES2.GL_QUERY_RESULT,
                            elapsed, 0);
                    gpu[frame] = elapsed[0] / 1_000_000.0;
                }
            } finally {
                gl.glDeleteQueries(queries.length, queries, 0);
            }
            return new Result(value, calls[0], Stats.of(cpu), Stats.of(gpu));
        } finally {
            if (buffer != null)
                buffer.close();
        }
    }

    private static void draw(GLRenderer2D renderer, GL3 gl,
            SpriteBenchmarkWorkload workload, Case value, GLSpriteBuffer buffer,
            boolean measure, Measurement measurement, int frame) {
        GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
                Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
        context.clear(BACKGROUND);
        long start = System.nanoTime();
        if (buffer == null)
            workload.draw(context, value.count,
                    SpriteBenchmarkWorkload.Channels.LIT, GLAlphaMode.CUTOUT,
                    false, value.picking, frame / 60f,
                    0, 0, WIDTH, HEIGHT, false);
        else
            workload.draw(context, buffer, value.picking, false);
        if (measure)
            measurement.samples[measurement.index] =
                    (System.nanoTime() - start) / 1_000_000.0;
        renderer.endFrame();
    }

    private static GLSpriteBuffer record(GLRenderer2D renderer, GL3 gl,
            SpriteBenchmarkWorkload workload, Case value) {
        GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
                Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
        GLSpriteBuffer result = workload.recordBuffer(context, value.count,
                SpriteBenchmarkWorkload.Channels.LIT, GLAlphaMode.CUTOUT,
                value.picking, 0, 0, WIDTH, HEIGHT);
        renderer.endFrame();
        return result;
    }

    private static void print(Result result) {
        System.out.printf("%-15s %,8d %8s %,7d | %7.3f / %7.3f | %7.3f / %7.3f ms%n",
                result.value.label, result.value.count,
                result.value.picking ? "on" : "off", result.drawCalls,
                result.cpu.p50, result.cpu.p95, result.gpu.p50, result.gpu.p95);
    }

    private record Case(String label, int count, boolean picking,
            boolean resident) { }
    private record Measurement(double[] samples, int index) { }
    private record Result(Case value, int drawCalls, Stats cpu, Stats gpu) { }
    private record Stats(double p50, double p95) {
        static Stats of(double[] source) {
            double[] values = source.clone();
            Arrays.sort(values);
            return new Stats(percentile(values, 0.50), percentile(values, 0.95));
        }

        private static double percentile(double[] values, double percentile) {
            double position = percentile * (values.length - 1);
            int low = (int) Math.floor(position);
            int high = (int) Math.ceil(position);
            float fraction = (float) (position - low);
            return values[low] * (1 - fraction) + values[high] * fraction;
        }
    }
}
