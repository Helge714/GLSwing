package com.vingame.glswing.rendering;

/** A separable Gaussian blur measured in logical Swing pixels. */
public final class GLBlurEffect implements GLLayerEffect {

	private final float radius;

	public GLBlurEffect(float radius) {
		if (!Float.isFinite(radius) || radius < 0)
			throw new IllegalArgumentException("Blur radius must be finite and non-negative");
		this.radius = radius;
	}

	public float getRadius() {
		return radius;
	}

	@Override
	public float getPadding() {
		return radius;
	}
}
