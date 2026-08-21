package com.vingame.glswing.layout;

/** Immutable top/left/bottom/right spacing in logical GLSwing units. */
public record GLInsets(float top, float left, float bottom, float right) {

	public static final GLInsets ZERO = new GLInsets(0, 0, 0, 0);

	public GLInsets {
		if (!Float.isFinite(top) || !Float.isFinite(left)
				|| !Float.isFinite(bottom) || !Float.isFinite(right)
				|| top < 0 || left < 0 || bottom < 0 || right < 0)
			throw new IllegalArgumentException("GL insets must be finite and non-negative");
	}

	public static GLInsets all(float value) {
		return new GLInsets(value, value, value, value);
	}

	public static GLInsets symmetric(float vertical, float horizontal) {
		return new GLInsets(vertical, horizontal, vertical, horizontal);
	}

	public float horizontal() {
		return left + right;
	}

	public float vertical() {
		return top + bottom;
	}
}
