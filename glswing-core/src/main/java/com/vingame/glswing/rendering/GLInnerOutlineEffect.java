package com.vingame.glswing.rendering;

import java.awt.Color;

/** A colored outline drawn entirely inside the layer alpha shape. */
public final class GLInnerOutlineEffect implements GLLayerEffect {

	private final float width;
	private final Color color;

	public GLInnerOutlineEffect(float width, Color color) {
		if (!Float.isFinite(width) || width < 0)
			throw new IllegalArgumentException(
					"Inner outline width must be finite and non-negative");
		if (color == null)
			throw new IllegalArgumentException("Inner outline color must not be null");
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
		return 0;
	}
}
