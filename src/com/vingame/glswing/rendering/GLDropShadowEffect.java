package com.vingame.glswing.rendering;

import java.awt.Color;

/** A soft shadow generated from a displaced copy of the layer alpha. */
public final class GLDropShadowEffect implements GLLayerEffect {

	private final float offsetX;
	private final float offsetY;
	private final float radius;
	private final Color color;

	public GLDropShadowEffect(float offsetX, float offsetY, float radius, Color color) {
		if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY))
			throw new IllegalArgumentException("Shadow offset must be finite");
		if (!Float.isFinite(radius) || radius < 0)
			throw new IllegalArgumentException("Shadow radius must be finite and non-negative");
		if (color == null)
			throw new IllegalArgumentException("Shadow color must not be null");
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
		return radius + Math.max(Math.abs(offsetX), Math.abs(offsetY));
	}
}
