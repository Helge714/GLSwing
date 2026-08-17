package com.vingame.glswing.component;

import com.jogamp.opengl.GL3;

/** Small asynchronous query ring; it never waits for a GPU result. */
final class GLGpuTimerQueries {
	static final int SCENE = 0;
	static final int PRESENT = 1;
	private static final int PHASES = 2;
	private static final int RING_SIZE = 8;

	private final int[][] ids = new int[PHASES][RING_SIZE];
	private final boolean[][] pending = new boolean[PHASES][RING_SIZE];
	private final int[] cursor = new int[PHASES];
	private final long[] accumulatedNanos = new long[PHASES];
	private final int[] resultCount = new int[PHASES];
	private final double[] averagesMillis = new double[PHASES];
	private int activePhase = -1;
	private int activeSlot = -1;
	private long windowStartNanos;

	void initialize(GL3 gl) {
		int[] generated = new int[PHASES * RING_SIZE];
		gl.glGenQueries(generated.length, generated, 0);
		for (int phase = 0, offset = 0; phase < PHASES; phase++)
			for (int slot = 0; slot < RING_SIZE; slot++)
				ids[phase][slot] = generated[offset++];
		windowStartNanos = System.nanoTime();
	}

	void begin(GL3 gl, int phase) {
		if (activePhase >= 0)
			throw new IllegalStateException("GPU timer queries must not overlap");
		int slot = cursor[phase];
		if (pending[phase][slot])
			return;
		activePhase = phase;
		activeSlot = slot;
		gl.glBeginQuery(GL3.GL_TIME_ELAPSED, ids[phase][slot]);
	}

	void end(GL3 gl) {
		if (activePhase < 0)
			return;
		gl.glEndQuery(GL3.GL_TIME_ELAPSED);
		pending[activePhase][activeSlot] = true;
		cursor[activePhase] = (activeSlot + 1) % RING_SIZE;
		activePhase = -1;
		activeSlot = -1;
	}

	void poll(GL3 gl) {
		int[] available = new int[1];
		long[] elapsed = new long[1];
		for (int phase = 0; phase < PHASES; phase++)
			for (int slot = 0; slot < RING_SIZE; slot++) {
				if (!pending[phase][slot])
					continue;
				gl.glGetQueryObjectiv(ids[phase][slot],
						GL3.GL_QUERY_RESULT_AVAILABLE, available, 0);
				if (available[0] == 0)
					continue;
				gl.glGetQueryObjectui64v(ids[phase][slot],
						GL3.GL_QUERY_RESULT, elapsed, 0);
				pending[phase][slot] = false;
				accumulatedNanos[phase] += elapsed[0];
				resultCount[phase]++;
			}
		long now = System.nanoTime();
		if (now - windowStartNanos < 500_000_000L)
			return;
		for (int phase = 0; phase < PHASES; phase++) {
			if (resultCount[phase] > 0)
				averagesMillis[phase] = accumulatedNanos[phase]
						/ (resultCount[phase] * 1_000_000.0);
			accumulatedNanos[phase] = 0;
			resultCount[phase] = 0;
		}
		windowStartNanos = now;
	}

	double averageMillis(int phase) {
		return averagesMillis[phase];
	}

	void dispose(GL3 gl) {
		if (activePhase >= 0) {
			gl.glEndQuery(GL3.GL_TIME_ELAPSED);
			activePhase = -1;
		}
		int[] all = new int[PHASES * RING_SIZE];
		for (int phase = 0, offset = 0; phase < PHASES; phase++)
			for (int slot = 0; slot < RING_SIZE; slot++)
				all[offset++] = ids[phase][slot];
		gl.glDeleteQueries(all.length, all, 0);
	}
}
