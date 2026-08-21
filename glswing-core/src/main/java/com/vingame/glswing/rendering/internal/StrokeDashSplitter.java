package com.vingame.glswing.rendering.internal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vingame.glswing.rendering.GLStroke;

/** Splits a centerline into the visible runs of an immutable dash pattern. */
final class StrokeDashSplitter {

	private static final float EPSILON = 1e-5f;

	private StrokeDashSplitter() {
	}

	static List<DashRun> split(float[] points, Color[] colors,
			boolean closed, GLStroke stroke) {
		float[] dash = effectivePattern(stroke.getDashArray());
		DashState state = new DashState(dash, stroke.getDashPhase());
		List<DashRun> runs = new ArrayList<>();
		RunBuilder current = null;
		int pointCount = points.length / 2;
		int segmentCount = closed ? pointCount : pointCount - 1;

		for (int segment = 0; segment < segmentCount; segment++) {
			int next = (segment + 1) % pointCount;
			float startX = points[segment * 2];
			float startY = points[segment * 2 + 1];
			float endX = points[next * 2];
			float endY = points[next * 2 + 1];
			float deltaX = endX - startX;
			float deltaY = endY - startY;
			float length = (float) Math.hypot(deltaX, deltaY);
			if (length <= EPSILON)
				continue;

			float position = 0;
			while (position < length - EPSILON) {
				float chunk = Math.min(length - position, state.remaining);
				float nextPosition = position + chunk;
				if (state.visible) {
					if (current == null)
						current = new RunBuilder(colors != null);
					appendPosition(current, startX, startY, deltaX, deltaY,
							length, position, colors, segment, next);
					appendPosition(current, startX, startY, deltaX, deltaY,
							length, nextPosition, colors, segment, next);
				}
				position = nextPosition;
				state.remaining -= chunk;
				if (state.remaining <= EPSILON) {
					if (state.visible && current != null) {
						addRun(runs, current, false);
						current = null;
					}
					state.advance();
				}
			}
		}

		if (current != null)
			addRun(runs, current, false);
		if (closed)
			joinClosedSeam(runs, points[0], points[1]);
		return runs;
	}

	private static void appendPosition(RunBuilder run,
			float startX, float startY, float deltaX, float deltaY,
			float segmentLength, float position, Color[] colors,
			int startPoint, int endPoint) {
		float ratio = position / segmentLength;
		Color color = colors == null ? null
				: interpolate(colors[startPoint], colors[endPoint], ratio);
		run.add(startX + deltaX * ratio, startY + deltaY * ratio, color);
	}

	private static void joinClosedSeam(List<DashRun> runs, float seamX, float seamY) {
		if (runs.isEmpty())
			return;
		DashRun first = runs.get(0);
		DashRun last = runs.get(runs.size() - 1);
		if (!samePoint(first.startX(), first.startY(), seamX, seamY)
				|| !samePoint(last.endX(), last.endY(), seamX, seamY))
			return;

		if (runs.size() == 1) {
			runs.set(0, last.asClosed());
			return;
		}

		DashRun merged = last.append(first);
		runs.remove(runs.size() - 1);
		runs.set(0, merged);
	}

	private static boolean samePoint(float firstX, float firstY,
			float secondX, float secondY) {
		return Math.abs(firstX - secondX) <= EPSILON
				&& Math.abs(firstY - secondY) <= EPSILON;
	}

	private static void addRun(List<DashRun> runs, RunBuilder builder, boolean closed) {
		DashRun run = builder.build(closed);
		if (run.points.length >= (closed ? 6 : 4))
			runs.add(run);
	}

	private static float[] effectivePattern(float[] dash) {
		if (dash.length % 2 == 0)
			return dash;
		float[] repeated = Arrays.copyOf(dash, dash.length * 2);
		System.arraycopy(dash, 0, repeated, dash.length, dash.length);
		return repeated;
	}

	private static Color interpolate(Color first, Color second, float position) {
		float inverse = 1 - position;
		return new Color(
				(first.getRed() * inverse + second.getRed() * position) / 255f,
				(first.getGreen() * inverse + second.getGreen() * position) / 255f,
				(first.getBlue() * inverse + second.getBlue() * position) / 255f,
				(first.getAlpha() * inverse + second.getAlpha() * position) / 255f);
	}

	static final class DashRun {
		private final float[] points;
		private final Color[] colors;
		private final boolean closed;

		DashRun(float[] points, Color[] colors, boolean closed) {
			this.points = points;
			this.colors = colors;
			this.closed = closed;
		}

		float[] points() {
			return points;
		}

		Color[] colors() {
			return colors;
		}

		boolean closed() {
			return closed;
		}

		float startX() {
			return points[0];
		}

		float startY() {
			return points[1];
		}

		float endX() {
			return points[points.length - 2];
		}

		float endY() {
			return points[points.length - 1];
		}

		DashRun asClosed() {
			int pointLength = points.length - 2;
			float[] closedPoints = Arrays.copyOf(points, pointLength);
			Color[] closedColors = colors == null ? null
					: Arrays.copyOf(colors, colors.length - 1);
			return new DashRun(closedPoints, closedColors, true);
		}

		DashRun append(DashRun following) {
			float[] mergedPoints = Arrays.copyOf(points,
					points.length + following.points.length - 2);
			System.arraycopy(following.points, 2, mergedPoints, points.length,
					following.points.length - 2);
			Color[] mergedColors = null;
			if (colors != null) {
				mergedColors = Arrays.copyOf(colors,
						colors.length + following.colors.length - 1);
				System.arraycopy(following.colors, 1, mergedColors, colors.length,
						following.colors.length - 1);
			}
			return new DashRun(mergedPoints, mergedColors, false);
		}
	}

	private static final class DashState {
		private final float[] pattern;
		private int index;
		private boolean visible = true;
		private float remaining;

		DashState(float[] pattern, float phase) {
			this.pattern = pattern;
			remaining = pattern[0];
			float cycle = 0;
			for (float length : pattern)
				cycle += length;
			float normalizedPhase = phase % cycle;
			while (normalizedPhase >= remaining - EPSILON) {
				normalizedPhase -= remaining;
				advance();
			}
			remaining -= normalizedPhase;
		}

		void advance() {
			index = (index + 1) % pattern.length;
			visible = !visible;
			remaining = pattern[index];
		}
	}

	private static final class RunBuilder {
		private float[] points = new float[16];
		private Color[] colors;
		private int size;

		RunBuilder(boolean colored) {
			if (colored)
				colors = new Color[8];
		}

		void add(float x, float y, Color color) {
			if (size > 0 && samePoint(points[(size - 1) * 2],
					points[(size - 1) * 2 + 1], x, y)) {
				if (colors != null)
					colors[size - 1] = color;
				return;
			}
			if ((size + 1) * 2 > points.length)
				points = Arrays.copyOf(points, points.length * 2);
			if (colors != null && size == colors.length)
				colors = Arrays.copyOf(colors, colors.length * 2);
			points[size * 2] = x;
			points[size * 2 + 1] = y;
			if (colors != null)
				colors[size] = color;
			size++;
		}

		DashRun build(boolean closed) {
			return new DashRun(Arrays.copyOf(points, size * 2),
					colors == null ? null : Arrays.copyOf(colors, size), closed);
		}
	}
}
