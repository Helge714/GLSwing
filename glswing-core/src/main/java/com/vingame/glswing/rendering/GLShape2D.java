package com.vingame.glswing.rendering;

/**
 * Lightweight vector-shape contract. Shapes can be converted to an independent
 * mutable path for drawing, clipping or icon generation.
 */
@FunctionalInterface
public interface GLShape2D {

	/** Returns an independent mutable path representation of this shape. */
	GLPath2D toPath();

	static GLShape2D rectangle(float x, float y, float width, float height) {
		return fixed(GLPath2D.rectangle(x, y, width, height));
	}

	static GLShape2D roundedRectangle(float x, float y, float width,
			float height, float radius) {
		return roundedRectangle(x, y, width, height, GLCornerRadii.all(radius));
	}

	static GLShape2D roundedRectangle(float x, float y, float width,
			float height, GLCornerRadii radii) {
		return fixed(GLPath2D.roundedRectangle(x, y, width, height, radii));
	}

	static GLShape2D ellipse(float x, float y, float width, float height) {
		return fixed(GLPath2D.ellipse(x, y, width, height));
	}

	static GLShape2D circle(float centerX, float centerY, float radius) {
		return fixed(GLPath2D.circle(centerX, centerY, radius));
	}

	static GLShape2D polygon(float[] points) {
		return fixed(GLPath2D.polygon(points));
	}

	private static GLShape2D fixed(GLPath2D path) {
		return path::copy;
	}
}
