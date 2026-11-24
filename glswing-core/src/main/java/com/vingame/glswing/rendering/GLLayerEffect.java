package com.vingame.glswing.rendering;

/** Immutable description of one ordered layer post-processing stage. */
public sealed interface GLLayerEffect permits GLBlurEffect, GLDropShadowEffect,
		GLOutlineEffect, GLInnerOutlineEffect, GLInnerShadowEffect,
		GLColorEffect, GLRoundedClipEffect, GLPathClipEffect, GLShaderEffect {

	/** Extra logical pixels potentially produced outside the source geometry. */
	float getPadding();
}
