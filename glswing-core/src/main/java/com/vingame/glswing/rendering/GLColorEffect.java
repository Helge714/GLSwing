package com.vingame.glswing.rendering;

import java.awt.Color;

/** Combined one-sample brightness, contrast, saturation, gamma, invert and tint effect. */
public final class GLColorEffect implements GLLayerEffect {

	private final float brightness;
	private final float contrast;
	private final float saturation;
	private final float gamma;
	private final float invert;
	private final Color tint;
	private final float tintStrength;

	public GLColorEffect() {
		this(1, 1, 1, 1, 0, Color.WHITE, 0);
	}

	private GLColorEffect(float brightness, float contrast, float saturation,
			float gamma, float invert, Color tint, float tintStrength) {
		this.brightness = requireNonNegative(brightness, "Brightness");
		this.contrast = requireNonNegative(contrast, "Contrast");
		this.saturation = requireNonNegative(saturation, "Saturation");
		if (!Float.isFinite(gamma) || gamma <= 0)
			throw new IllegalArgumentException("Gamma must be finite and positive");
		this.gamma = gamma;
		this.invert = requireUnit(invert, "Invert strength");
		if (tint == null)
			throw new IllegalArgumentException("Tint color must not be null");
		this.tint = tint;
		this.tintStrength = requireUnit(tintStrength, "Tint strength");
	}

	public float getBrightness() { return brightness; }
	public float getContrast() { return contrast; }
	public float getSaturation() { return saturation; }
	public float getGamma() { return gamma; }
	public float getInvert() { return invert; }
	public Color getTint() { return tint; }
	public float getTintStrength() { return tintStrength; }

	public GLColorEffect withBrightness(float value) {
		return new GLColorEffect(value, contrast, saturation, gamma, invert, tint, tintStrength);
	}

	public GLColorEffect withContrast(float value) {
		return new GLColorEffect(brightness, value, saturation, gamma, invert, tint, tintStrength);
	}

	public GLColorEffect withSaturation(float value) {
		return new GLColorEffect(brightness, contrast, value, gamma, invert, tint, tintStrength);
	}

	public GLColorEffect withGamma(float value) {
		return new GLColorEffect(brightness, contrast, saturation, value, invert, tint, tintStrength);
	}

	public GLColorEffect withInvert(float strength) {
		return new GLColorEffect(brightness, contrast, saturation, gamma,
				strength, tint, tintStrength);
	}

	public GLColorEffect withTint(Color color, float strength) {
		return new GLColorEffect(brightness, contrast, saturation, gamma,
				invert, color, strength);
	}

	@Override
	public float getPadding() {
		return 0;
	}

	private static float requireNonNegative(float value, String name) {
		if (!Float.isFinite(value) || value < 0)
			throw new IllegalArgumentException(name + " must be finite and non-negative");
		return value;
	}

	private static float requireUnit(float value, String name) {
		if (!Float.isFinite(value) || value < 0 || value > 1)
			throw new IllegalArgumentException(name + " must be between 0 and 1");
		return value;
	}
}
