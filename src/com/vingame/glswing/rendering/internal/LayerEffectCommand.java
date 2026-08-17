package com.vingame.glswing.rendering.internal;

import java.util.Map;

import com.vingame.glswing.rendering.GLLayerEffect;

/** Validated internal effect plus custom uniform data. */
public final class LayerEffectCommand {

	private final GLLayerEffect effect;
	private final Map<String, ShaderUniformValue> uniforms;

	public LayerEffectCommand(GLLayerEffect effect,
			Map<String, ShaderUniformValue> uniforms) {
		this.effect = effect;
		this.uniforms = uniforms;
	}

	public GLLayerEffect effect() {
		return effect;
	}

	public Map<String, ShaderUniformValue> uniforms() {
		return uniforms;
	}
}
