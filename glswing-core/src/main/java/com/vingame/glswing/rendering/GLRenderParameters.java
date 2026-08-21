package com.vingame.glswing.rendering;

import java.awt.Color;

/**
 * Mutable replay values for a complete recording or one recorded region. A
 * recording keeps the parameter object itself, so changing a value affects the
 * next replay without rebuilding geometry.
 */
public final class GLRenderParameters {

	private volatile GLTransform transform = new GLTransform();
	private volatile Color tint = Color.WHITE;
	private volatile Color color;
	private volatile float opacity = 1;

	public GLTransform getTransform() {
		return transform.copy();
	}

	public GLRenderParameters setTransform(GLTransform transform) {
		if (transform == null)
			throw new IllegalArgumentException("Parameter transform must not be null");
		this.transform = transform.copy();
		return this;
	}

	public GLRenderParameters resetTransform() {
		transform = new GLTransform();
		return this;
	}

	/** Replaces the parameter transform with a pure translation. */
	public GLRenderParameters setTranslation(float x, float y) {
		transform = new GLTransform().translate(x, y);
		return this;
	}

	/** Appends a translation to the current parameter transform. */
	public GLRenderParameters translate(float x, float y) {
		transform = transform.copy().translate(x, y);
		return this;
	}

	/** Appends a clockwise-positive rotation, measured in radians. */
	public GLRenderParameters rotate(float radians) {
		transform = transform.copy().rotate(radians);
		return this;
	}

	/** Appends a rotation around an anchor in the parameter's local space. */
	public GLRenderParameters rotate(float radians, float anchorX, float anchorY) {
		transform = transform.copy().rotate(radians, anchorX, anchorY);
		return this;
	}

	public GLRenderParameters scale(float scale) {
		return scale(scale, scale);
	}

	/** Appends a scale to the current parameter transform. */
	public GLRenderParameters scale(float scaleX, float scaleY) {
		transform = transform.copy().scale(scaleX, scaleY);
		return this;
	}

	public Color getTint() {
		return tint;
	}

	/** Multiplies the colors originally stored in the recording. */
	public GLRenderParameters setTint(Color tint) {
		if (tint == null)
			throw new IllegalArgumentException("Parameter tint must not be null");
		this.tint = tint;
		return this;
	}

	public GLRenderParameters resetTint() {
		tint = Color.WHITE;
		return this;
	}

	/**
	 * Replaces recorded vertex colors in this region. Texture and glyph alpha are
	 * still supplied by their textures. Pass {@link #clearColor()} to restore the
	 * original recorded colors.
	 */
	public GLRenderParameters setColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Parameter color must not be null");
		this.color = color;
		return this;
	}

	public GLRenderParameters clearColor() {
		color = null;
		return this;
	}

	public boolean hasColor() {
		return color != null;
	}

	public Color getColor() {
		return color;
	}

	public float getOpacity() {
		return opacity;
	}

	/** Multiplies vertex alpha; this is not group/layer opacity. */
	public GLRenderParameters setOpacity(float opacity) {
		if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1)
			throw new IllegalArgumentException("Parameter opacity must be from zero to one");
		this.opacity = opacity;
		return this;
	}

	public GLRenderParameters reset() {
		transform = new GLTransform();
		tint = Color.WHITE;
		color = null;
		opacity = 1;
		return this;
	}

	GLTransform transformValue() {
		return transform;
	}
}
