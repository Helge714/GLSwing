package com.vingame.glswing.rendering.internal;

import com.vingame.glswing.rendering.GLCornerRadii;

/** CPU checks for adaptive curved-shape polygon generation. */
public final class CurvedShapeBuilderSelfTest {

	private static final float EPSILON = 0.001f;

	private CurvedShapeBuilderSelfTest() {
	}

	public static void main(String[] args) {
		float[] ellipse = CurvedShapeBuilder.ellipse(10, 20, 80, 40, 24);
		check(ellipse.length == 48, "Ellipse segment count is wrong");
		assertBounds(ellipse, 10, 20, 90, 60, "ellipse");

		float[] rounded = CurvedShapeBuilder.roundedRectangle(0, 0, 40, 20,
				new GLCornerRadii(30, 30, 30, 30), 24);
		check(rounded.length >= 24, "Rounded rectangle has too few points");
		assertBounds(rounded, 0, 0, 40, 20, "normalized rounded rectangle");

		check(CurvedShapeBuilder.ellipse(0, 0, 0, 10, 8).length == 0,
				"Degenerate ellipse should be empty");
		System.out.println("Curved shape builder self-test passed.");
	}

	private static void assertBounds(float[] points, float minX, float minY,
			float maxX, float maxY, String description) {
		float actualMinX = Float.POSITIVE_INFINITY;
		float actualMinY = Float.POSITIVE_INFINITY;
		float actualMaxX = Float.NEGATIVE_INFINITY;
		float actualMaxY = Float.NEGATIVE_INFINITY;
		for (int index = 0; index < points.length; index += 2) {
			actualMinX = Math.min(actualMinX, points[index]);
			actualMinY = Math.min(actualMinY, points[index + 1]);
			actualMaxX = Math.max(actualMaxX, points[index]);
			actualMaxY = Math.max(actualMaxY, points[index + 1]);
		}
		check(Math.abs(actualMinX - minX) < EPSILON
				&& Math.abs(actualMinY - minY) < EPSILON
				&& Math.abs(actualMaxX - maxX) < EPSILON
				&& Math.abs(actualMaxY - maxY) < EPSILON,
				description + " bounds are wrong");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
