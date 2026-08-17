package com.vingame.glswing.rendering;

import java.awt.Color;

/** A soft, displaced shadow clipped to the inside of the layer alpha shape. */
public final class GLInnerShadowEffect implements GLLayerEffect {

	private final float offsetX;
	private final float offsetY;
	private final float radius;
	private final Color color;

	public GLInnerShadowEffect(float offsetX, float offsetY,
			float radius, Color color) {
		if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY))
			throw new IllegalArgumentException("Inner shadow offset must be finite");
		if (!Float.isFinite(radius) || radius < 0)
			throw new IllegalArgumentException(
					"Inner shadow radius must be finite and non-negative");
		if (color == null)
			throw new IllegalArgumentException("Inner shadow color must not be null");
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.radius = radius;
		this.color = color;
	}

	public float getOffsetX() {
		return offsetX;
	}

	public float getOffsetY() {
		return offsetY;
	}

	public float getRadius() {
		return radius;
	}

	public Color getColor() {
		return color;
	}

	@Override
	public float getPadding() {
		return 0;
	}
}
