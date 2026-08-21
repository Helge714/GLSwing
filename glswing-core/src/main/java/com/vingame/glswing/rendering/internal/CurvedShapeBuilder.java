package com.vingame.glswing.rendering.internal;

import java.util.Arrays;

import com.vingame.glswing.rendering.GLCornerRadii;

/** Builds clockwise polygon boundaries for common curved 2D shapes. */
public final class CurvedShapeBuilder {

	private CurvedShapeBuilder() {
	}

	public static float[] ellipse(float x, float y, float width, float height,
			int segments) {
		validateBounds(x, y, width, height);
		if (segments < 8)
			throw new IllegalArgumentException("Ellipse requires at least eight segments");
		float left = Math.min(x, x + width);
		float top = Math.min(y, y + height);
		float radiusX = Math.abs(width) / 2;
		float radiusY = Math.abs(height) / 2;
		if (radiusX == 0 || radiusY == 0)
			return new float[0];
		float centerX = left + radiusX;
		float centerY = top + radiusY;
		float[] points = new float[segments * 2];
		for (int point = 0; point < segments; point++) {
			double angle = point * Math.PI * 2 / segments;
			points[point * 2] = centerX + (float) Math.cos(angle) * radiusX;
			points[point * 2 + 1] = centerY + (float) Math.sin(angle) * radiusY;
		}
		return points;
	}

	public static float[] roundedRectangle(float x, float y,
			float width, float height, GLCornerRadii radii, int fullCircleSegments) {
		validateBounds(x, y, width, height);
		if (radii == null)
			throw new IllegalArgumentException("Corner radii must not be null");
		if (fullCircleSegments < 8)
			throw new IllegalArgumentException("Rounded rectangle requires at least eight segments");
		float left = Math.min(x, x + width);
		float right = Math.max(x, x + width);
		float top = Math.min(y, y + height);
		float bottom = Math.max(y, y + height);
		float actualWidth = right - left;
		float actualHeight = bottom - top;
		if (actualWidth == 0 || actualHeight == 0)
			return new float[0];

		float[] normalized = normalizeRadii(actualWidth, actualHeight, radii);
		float topLeft = normalized[0];
		float topRight = normalized[1];
		float bottomRight = normalized[2];
		float bottomLeft = normalized[3];
		int quarterSegments = Math.max(2, (int) Math.ceil(fullCircleSegments / 4f));
		PointBuffer points = new PointBuffer(quarterSegments * 8 + 8);
		points.add(left + topLeft, top);
		points.add(right - topRight, top);
		appendArc(points, right - topRight, top + topRight, topRight,
				(float) (-Math.PI / 2), 0, quarterSegments);
		points.add(right, bottom - bottomRight);
		appendArc(points, right - bottomRight, bottom - bottomRight, bottomRight,
				0, (float) (Math.PI / 2), quarterSegments);
		points.add(left + bottomLeft, bottom);
		appendArc(points, left + bottomLeft, bottom - bottomLeft, bottomLeft,
				(float) (Math.PI / 2), (float) Math.PI, quarterSegments);
		points.add(left, top + topLeft);
		appendArc(points, left + topLeft, top + topLeft, topLeft,
				(float) Math.PI, (float) (Math.PI * 1.5), quarterSegments);
		return points.toArrayWithoutClosingDuplicate();
	}

	private static float[] normalizeRadii(float width, float height,
			GLCornerRadii radii) {
		float topLeft = radii.topLeft();
		float topRight = radii.topRight();
		float bottomRight = radii.bottomRight();
		float bottomLeft = radii.bottomLeft();
		float scale = 1;
		scale = Math.min(scale, ratio(width, topLeft + topRight));
		scale = Math.min(scale, ratio(width, bottomLeft + bottomRight));
		scale = Math.min(scale, ratio(height, topLeft + bottomLeft));
		scale = Math.min(scale, ratio(height, topRight + bottomRight));
		return new float[] { topLeft * scale, topRight * scale,
				bottomRight * scale, bottomLeft * scale };
	}

	private static float ratio(float available, float requested) {
		return requested == 0 ? 1 : Math.min(1, available / requested);
	}

	private static void appendArc(PointBuffer points, float centerX, float centerY,
			float radius, float startAngle, float endAngle, int segments) {
		if (radius == 0) {
			points.add(centerX, centerY);
			return;
		}
		for (int segment = 1; segment <= segments; segment++) {
			float progress = segment / (float) segments;
			float angle = startAngle + (endAngle - startAngle) * progress;
			points.add(centerX + (float) Math.cos(angle) * radius,
					centerY + (float) Math.sin(angle) * radius);
		}
	}

	private static void validateBounds(float x, float y, float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height))
			throw new IllegalArgumentException("Shape bounds must be finite");
	}

	private static final class PointBuffer {
		private float[] values;
		private int size;

		private PointBuffer(int pointCapacity) {
			values = new float[Math.max(16, pointCapacity * 2)];
		}

		private void add(float x, float y) {
			if (size >= 2 && values[size - 2] == x && values[size - 1] == y)
				return;
			if (size + 2 > values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = x;
			values[size++] = y;
		}

		private float[] toArrayWithoutClosingDuplicate() {
			if (size >= 4 && values[0] == values[size - 2]
					&& values[1] == values[size - 1])
				size -= 2;
			return Arrays.copyOf(values, size);
		}
	}
}
