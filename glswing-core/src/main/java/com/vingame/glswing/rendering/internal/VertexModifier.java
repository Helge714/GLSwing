package com.vingame.glswing.rendering.internal;

import java.awt.Color;

import com.vingame.glswing.rendering.GLTransform;

/** Mutable renderer-owned replay modifier consumed synchronously by batches. */
public final class VertexModifier {
	private final GLTransform transform = new GLTransform();
	private boolean colorChanged;
	private boolean overrideColor;
	private float red;
	private float green;
	private float blue;
	private float alpha;
	private float tintRed = 1;
	private float tintGreen = 1;
	private float tintBlue = 1;
	private float tintAlpha = 1;

	public void set(GLTransform transform, Color tint, Color color, float opacity) {
		set(transform,
				tint.getRed() / 255f, tint.getGreen() / 255f,
				tint.getBlue() / 255f, tint.getAlpha() / 255f,
				color != null,
				color == null ? 0 : color.getRed() / 255f,
				color == null ? 0 : color.getGreen() / 255f,
				color == null ? 0 : color.getBlue() / 255f,
				color == null ? 0 : color.getAlpha() / 255f,
				opacity);
	}

	public void set(GLTransform transform,
			float tintRed, float tintGreen, float tintBlue, float tintAlpha,
			boolean overrideColor,
			float red, float green, float blue, float alpha, float opacity) {
		this.transform.setTransform(transform);
		this.overrideColor = overrideColor;
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
		this.tintRed = tintRed;
		this.tintGreen = tintGreen;
		this.tintBlue = tintBlue;
		this.tintAlpha = tintAlpha * opacity;
		colorChanged = overrideColor || tintRed != 1 || tintGreen != 1
				|| tintBlue != 1 || tintAlpha != 1 || opacity != 1;
	}

	public boolean isIdentity() {
		return transform.isIdentity() && !colorChanged;
	}

	public boolean isTranslation() {
		return transform.isTranslation();
	}

	public float transformX(float x, float y) {
		return transform.transformX(x, y);
	}

	public float transformY(float x, float y) {
		return transform.transformY(x, y);
	}

	public float getTranslateX() {
		return transform.getTranslateX();
	}

	public float getTranslateY() {
		return transform.getTranslateY();
	}

	public boolean changesColor() {
		return colorChanged;
	}

	public float red(float recorded) {
		return (overrideColor ? red : recorded) * tintRed;
	}

	public float green(float recorded) {
		return (overrideColor ? green : recorded) * tintGreen;
	}

	public float blue(float recorded) {
		return (overrideColor ? blue : recorded) * tintBlue;
	}

	public float alpha(float recorded) {
		return (overrideColor ? alpha : recorded) * tintAlpha;
	}

	/** Applies tint and opacity without applying the primary color override. */
	public float secondaryRed(float recorded) {
		return recorded * tintRed;
	}

	public float secondaryGreen(float recorded) {
		return recorded * tintGreen;
	}

	public float secondaryBlue(float recorded) {
		return recorded * tintBlue;
	}

	public float secondaryAlpha(float recorded) {
		return recorded * tintAlpha;
	}
}
