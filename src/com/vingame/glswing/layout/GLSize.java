package com.vingame.glswing.layout;

/** Immutable logical size measured in GLSwing user-space units. */
public record GLSize(float width, float height) {

	public static final GLSize ZERO = new GLSize(0, 0);
	public static final GLSize INFINITE =
			new GLSize(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

	public GLSize {
		if (Float.isNaN(width) || Float.isNaN(height) || width < 0 || height < 0)
			throw new IllegalArgumentException("GL size must not be negative or NaN");
	}

	public GLSize clamp(GLSize minimum, GLSize maximum) {
		if (minimum == null || maximum == null)
			throw new IllegalArgumentException("Size limits must not be null");
		return new GLSize(
				Math.max(minimum.width, Math.min(width, maximum.width)),
				Math.max(minimum.height, Math.min(height, maximum.height)));
	}
}
