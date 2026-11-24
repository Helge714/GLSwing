package com.vingame.glswing.rendering;

/** Produces the complete world-to-clip transform for a 2D viewport. */
@FunctionalInterface
public interface GLView2D {

	GLTransform createViewProjection(float width, float height);
}
