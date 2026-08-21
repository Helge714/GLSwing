package com.vingame.glswing.rendering;

/** Clips a layer to one antialiased rounded rectangle at its layer bounds. */
public record GLRoundedClipEffect(GLCornerRadii radii) implements GLLayerEffect {

	public GLRoundedClipEffect {
		if (radii == null)
			throw new IllegalArgumentException("Clip radii must not be null");
	}

	@Override
	public float getPadding() {
		return 0;
	}
}
