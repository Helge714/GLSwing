package com.vingame.glswing.rendering;

import java.awt.Color;

/** An outer outline generated from neighboring layer alpha. */
public final class GLOutlineEffect implements GLLayerEffect {

	private final float width;
	private final Color color;

	public GLOutlineEffect(float width, Color color) {
		if (!Float.isFinite(width) || width < 0)
			throw new IllegalArgumentException("Outline width must be finite and non-negative");
		if (color == null)
			throw new IllegalArgumentException("Outline color must not be null");
		this.width = width;
		this.color = color;
	}

	public float getWidth() {
		return width;
	}

	public Color getColor() {
		return color;
	}

	@Override
	public float getPadding() {
		return width;
	}
}
