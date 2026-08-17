package com.vingame.glswing.rendering;

import java.awt.Color;

/** Convenience factories for built-in and custom layer effects. */
public final class GLLayerEffects {

	private GLLayerEffects() {
	}

	public static GLBlurEffect blur(float radius) {
		return new GLBlurEffect(radius);
	}

	public static GLDropShadowEffect dropShadow(
			float offsetX, float offsetY, float radius, Color color) {
		return new GLDropShadowEffect(offsetX, offsetY, radius, color);
	}

	public static GLDropShadowEffect outerGlow(float radius, Color color) {
		return new GLDropShadowEffect(0, 0, radius, color);
	}

	public static GLOutlineEffect outline(float width, Color color) {
		return new GLOutlineEffect(width, color);
	}

	public static GLInnerOutlineEffect innerOutline(float width, Color color) {
		return new GLInnerOutlineEffect(width, color);
	}

	public static GLInnerShadowEffect innerShadow(
			float offsetX, float offsetY, float radius, Color color) {
		return new GLInnerShadowEffect(offsetX, offsetY, radius, color);
	}

	public static GLColorEffect colorAdjust() {
		return new GLColorEffect();
	}

	public static GLColorEffect grayscale(float strength) {
		if (!Float.isFinite(strength) || strength < 0 || strength > 1)
			throw new IllegalArgumentException("Grayscale strength must be between 0 and 1");
		return new GLColorEffect().withSaturation(1 - strength);
	}

	public static GLColorEffect invert(float strength) {
		return new GLColorEffect().withInvert(strength);
	}

	/** Clips a layer to its bounds using independent antialiased corner radii. */
	public static GLRoundedClipEffect roundedClip(GLCornerRadii radii) {
		return new GLRoundedClipEffect(radii);
	}

	public static GLShaderEffect custom(ShaderProgram shader) {
		return new GLShaderEffect(shader);
	}
}
