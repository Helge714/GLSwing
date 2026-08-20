package com.vingame.glswing.rendering.internal;

import java.util.Map;

import com.vingame.glswing.rendering.GLLayerEffect;

/** Validated internal effect plus custom uniform data. */
public final class LayerEffectCommand {

	private final GLLayerEffect effect;
	private final Map<String, ShaderUniformValue> uniforms;
	private final float[] maskVertices;
	private final float[] maskProjection;

	public LayerEffectCommand(GLLayerEffect effect,
			Map<String, ShaderUniformValue> uniforms) {
		this(effect, uniforms, null, null);
	}

	public LayerEffectCommand(GLLayerEffect effect,
			Map<String, ShaderUniformValue> uniforms,
			float[] maskVertices, float[] maskProjection) {
		this.effect = effect;
		this.uniforms = uniforms;
		this.maskVertices = maskVertices;
		this.maskProjection = maskProjection;
	}

	public GLLayerEffect effect() {
		return effect;
	}

	public Map<String, ShaderUniformValue> uniforms() {
		return uniforms;
	}

	public boolean hasPathMask() {
		return maskVertices != null;
	}

	public float[] maskVertices() {
		return maskVertices;
	}

	public float[] maskProjection() {
		return maskProjection;
	}
}
