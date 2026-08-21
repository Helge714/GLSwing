package com.vingame.glswing.rendering;

/** Immutable clockwise corner radii for a rounded rectangle. */
public record GLCornerRadii(float topLeft, float topRight,
		float bottomRight, float bottomLeft) {

	public GLCornerRadii {
		if (!Float.isFinite(topLeft) || !Float.isFinite(topRight)
				|| !Float.isFinite(bottomRight) || !Float.isFinite(bottomLeft)
				|| topLeft < 0 || topRight < 0 || bottomRight < 0 || bottomLeft < 0)
			throw new IllegalArgumentException(
					"Corner radii must be finite and non-negative");
	}

	public static GLCornerRadii all(float radius) {
		return new GLCornerRadii(radius, radius, radius, radius);
	}
}
