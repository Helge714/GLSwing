package com.vingame.glswing.rendering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.vingame.glswing.rendering.internal.ShaderUniformValue;

/** One user-provided premultiplied-alpha layer shader stage. */
public final class GLShaderEffect implements GLLayerEffect {

	private final ShaderProgram shader;
	private final Map<String, ShaderUniformValue> uniforms;
	private final float padding;

	public GLShaderEffect(ShaderProgram shader) {
		this(requireShader(shader), Map.of(), 0);
	}

	private GLShaderEffect(ShaderProgram shader,
			Map<String, ShaderUniformValue> uniforms, float padding) {
		this.shader = shader;
		this.uniforms = uniforms;
		this.padding = padding;
	}

	public static GLShaderEffect of(ShaderProgram shader) {
		return new GLShaderEffect(shader);
	}

	public ShaderProgram getShader() {
		return shader;
	}

	public GLShaderEffect withUniform(String name, float value) {
		return withUniformValue(name, ShaderUniformValue.of(value));
	}

	public GLShaderEffect withUniform(String name, float x, float y) {
		return withUniformValue(name, ShaderUniformValue.of(x, y));
	}

	public GLShaderEffect withUniform(String name, float x, float y, float z) {
		return withUniformValue(name, ShaderUniformValue.of(x, y, z));
	}

	public GLShaderEffect withUniform(String name, float x, float y, float z, float w) {
		return withUniformValue(name, ShaderUniformValue.of(x, y, z, w));
	}

	public GLShaderEffect withUniform(String name, int value) {
		return withUniformValue(name, ShaderUniformValue.of(value));
	}

	public GLShaderEffect withUniformMatrix4(String name, float[] matrix) {
		return withUniformValue(name, ShaderUniformValue.matrix4(matrix));
	}

	public GLShaderEffect withPadding(float padding) {
		if (!Float.isFinite(padding) || padding < 0)
			throw new IllegalArgumentException("Shader effect padding must be finite and non-negative");
		return new GLShaderEffect(shader, uniforms, padding);
	}

	@Override
	public float getPadding() {
		return padding;
	}

	Map<String, ShaderUniformValue> uniformValues() {
		return uniforms;
	}

	private GLShaderEffect withUniformValue(String name, ShaderUniformValue value) {
		requireUserUniformName(name);
		Map<String, ShaderUniformValue> changed = new LinkedHashMap<>(uniforms);
		changed.put(name, value);
		return new GLShaderEffect(shader, Collections.unmodifiableMap(changed), padding);
	}

	private static ShaderProgram requireShader(ShaderProgram shader) {
		if (shader == null)
			throw new IllegalArgumentException("Layer effect shader must not be null");
		if (shader.isDisposed())
			throw new IllegalArgumentException("Layer effect shader has already been disposed");
		return shader;
	}

	private static void requireUserUniformName(String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Uniform name must not be blank");
		if (name.equals("uTexture") || name.equals("uOpacity")
				|| name.equals("uResolution") || name.equals("uTexelSize")
				|| name.equals("uLogicalResolution") || name.equals("uDeviceScale"))
			throw new IllegalArgumentException("Uniform " + name + " is managed by the renderer");
	}
}
