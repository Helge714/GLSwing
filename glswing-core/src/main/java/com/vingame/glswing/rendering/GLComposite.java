package com.vingame.glswing.rendering;

import java.util.ArrayList;
import java.util.List;

/** Immutable configuration used when compositing a completed layer. */
public final class GLComposite {

	public static final GLComposite DEFAULT = new GLComposite(
			1, GLBlendMode.NORMAL, List.of());
	public static final GLComposite NORMAL = DEFAULT;
	public static final GLComposite ADD = new GLComposite(GLBlendMode.ADD);
	public static final GLComposite MULTIPLY = new GLComposite(GLBlendMode.MULTIPLY);
	public static final GLComposite SCREEN = new GLComposite(GLBlendMode.SCREEN);
	public static final GLComposite SUBTRACT = new GLComposite(GLBlendMode.SUBTRACT);
	public static final GLComposite ERASE = new GLComposite(GLBlendMode.ERASE);

	private final float opacity;
	private final GLBlendMode blendMode;
	private final List<GLLayerEffect> effects;

	public GLComposite() {
		this(1, GLBlendMode.NORMAL, List.of());
	}

	public GLComposite(float opacity) {
		this(opacity, GLBlendMode.NORMAL, List.of());
	}

	public GLComposite(GLBlendMode blendMode) {
		this(1, requireBlendMode(blendMode), List.of());
	}

	public GLComposite(float opacity, GLBlendMode blendMode) {
		this(opacity, requireBlendMode(blendMode), List.of());
	}

	private GLComposite(float opacity, GLBlendMode blendMode,
			List<GLLayerEffect> effects) {
		this.opacity = requireOpacity(opacity);
		this.blendMode = requireBlendMode(blendMode);
		this.effects = effects;
	}

	public static GLComposite opacity(float opacity) {
		return new GLComposite(opacity);
	}

	public static GLComposite blend(GLBlendMode blendMode) {
		return new GLComposite(blendMode);
	}

	public float getOpacity() {
		return opacity;
	}

	public GLBlendMode getBlendMode() {
		return blendMode;
	}

	/** Returns the effects in their execution order. */
	public List<GLLayerEffect> getEffects() {
		return effects;
	}

	public GLComposite withOpacity(float opacity) {
		return new GLComposite(opacity, blendMode, effects);
	}

	public GLComposite withBlendMode(GLBlendMode blendMode) {
		return new GLComposite(opacity, requireBlendMode(blendMode), effects);
	}

	/** Appends one effect; effects execute in the order in which they are added. */
	public GLComposite withEffect(GLLayerEffect effect) {
		if (effect == null)
			throw new IllegalArgumentException("Layer effect must not be null");
		List<GLLayerEffect> changed = new ArrayList<>(effects);
		changed.add(effect);
		return new GLComposite(opacity, blendMode, List.copyOf(changed));
	}

	public GLComposite withoutEffects() {
		return effects.isEmpty() ? this : new GLComposite(opacity, blendMode, List.of());
	}

	/** Total logical padding required by the ordered effect chain. */
	public float getEffectPadding() {
		float padding = 0;
		for (GLLayerEffect effect : effects)
			padding += effect.getPadding();
		return padding;
	}

	private static float requireOpacity(float opacity) {
		if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1)
			throw new IllegalArgumentException("Layer opacity must be between 0 and 1");
		return opacity;
	}

	private static GLBlendMode requireBlendMode(GLBlendMode blendMode) {
		if (blendMode == null)
			throw new IllegalArgumentException("Layer blend mode must not be null");
		return blendMode;
	}
}
