package com.vingame.glswing.rendering;

import java.awt.Color;

/** Produces a color between two endpoint colors for an amount from 0 to 1. */
@FunctionalInterface
public interface GLColorInterpolator {

	Color interpolate(Color start, Color end, float amount);
}
