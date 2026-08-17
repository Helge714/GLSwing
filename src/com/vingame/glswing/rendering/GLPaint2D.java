package com.vingame.glswing.rendering;

import java.awt.Color;

/** Supplies a fill color at a point in the local coordinate system. */
@FunctionalInterface
public interface GLPaint2D {

	Color colorAt(float x, float y);

	static GLPaint2D solid(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Paint color must not be null");
		return (x, y) -> color;
	}
}
