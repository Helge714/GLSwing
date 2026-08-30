package com.vingame.glswing.rendering;

import java.awt.Color;

/** Immutable MSDF text-shadow color, logical-pixel offset, and softness factor. */
public record GLTextShadow(Color color, float offsetX, float offsetY,
		float softness, OffsetSpace offsetSpace) {
	public enum OffsetSpace {
		/** Offset is applied after the current transform. */
		SCREEN,
		/** Offset is part of the text's local coordinates and is transformed. */
		LOCAL
	}

	public static final GLTextShadow NONE =
			new GLTextShadow(new Color(0, 0, 0, 0), 0, 0, 0,
					OffsetSpace.SCREEN);

	/** Creates a screen-space shadow. */
	public GLTextShadow(Color color, float offsetX, float offsetY,
			float softness) {
		this(color, offsetX, offsetY, softness, OffsetSpace.SCREEN);
	}

	public GLTextShadow {
		if (color == null)
			throw new IllegalArgumentException("Text shadow color must not be null");
		if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY))
			throw new IllegalArgumentException("Text shadow offsets must be finite");
		if (!Float.isFinite(softness) || softness < 0)
			throw new IllegalArgumentException(
					"Text shadow softness must be finite and non-negative");
		if (offsetSpace == null)
			throw new IllegalArgumentException(
					"Text shadow offset space must not be null");
	}

	public boolean isVisible() {
		return color.getAlpha() > 0;
	}
}
