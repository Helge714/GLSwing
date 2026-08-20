package com.vingame.glswing.rendering;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vingame.glswing.rendering.internal.ShaderUniformValue;

/**
 * Immutable sprite material. Color, depth and normal images are resolved lazily
 * by the owning renderer and may independently live in atlas pages.
 */
public final class GLMaterial2D {
	private final GLImage colorMap;
	private final GLImage depthMap;
	private final GLImage normalMap;
	private final GLAlphaMode alphaMode;
	private final float alphaCutoff;
	private final float depthBackOffset;
	private final float depthFrontOffset;
	private final boolean lit;
	private final GLDepthState depthState;
	private final ShaderProgram shader;
	private final Map<String, ShaderUniformValue> uniforms;

	private GLMaterial2D(Builder builder) {
		colorMap = builder.colorMap;
		depthMap = builder.depthMap;
		normalMap = builder.normalMap;
		alphaMode = builder.alphaMode;
		alphaCutoff = builder.alphaCutoff;
		depthBackOffset = builder.depthBackOffset;
		depthFrontOffset = builder.depthFrontOffset;
		lit = builder.lit;
		depthState = builder.depthState != null ? builder.depthState
				: defaultDepthState(alphaMode);
		shader = builder.shader;
		uniforms = Map.copyOf(builder.uniforms);
	}

	public static Builder builder(GLImage colorMap) {
		return new Builder(colorMap);
	}

	public GLImage getColorMap() { return colorMap; }
	public GLImage getDepthMap() { return depthMap; }
	public GLImage getNormalMap() { return normalMap; }
	public GLAlphaMode getAlphaMode() { return alphaMode; }
	public float getAlphaCutoff() { return alphaCutoff; }
	public float getDepthBackOffset() { return depthBackOffset; }
	public float getDepthFrontOffset() { return depthFrontOffset; }
	public boolean isLit() { return lit; }
	public GLDepthState getDepthState() { return depthState; }
	public ShaderProgram getShader() { return shader; }
	Map<String, ShaderUniformValue> uniformValues() { return uniforms; }

	public boolean hasDepthMap() { return depthMap != null; }
	public boolean hasNormalMap() { return normalMap != null; }

	private static GLDepthState defaultDepthState(GLAlphaMode mode) {
		return mode == GLAlphaMode.BLEND
				? GLDepthState.READ_ONLY : GLDepthState.READ_WRITE;
	}

	public static final class Builder {
		private final GLImage colorMap;
		private GLImage depthMap;
		private GLImage normalMap;
		private GLAlphaMode alphaMode = GLAlphaMode.BLEND;
		private float alphaCutoff = 0.05f;
		private float depthBackOffset;
		private float depthFrontOffset;
		private boolean lit;
		private GLDepthState depthState;
		private ShaderProgram shader;
		private final Map<String, ShaderUniformValue> uniforms =
				new LinkedHashMap<>();

		private Builder(GLImage colorMap) {
			if (colorMap == null)
				throw new IllegalArgumentException("Material color map must not be null");
			this.colorMap = colorMap;
		}

		public Builder depthMap(GLImage image) {
			depthMap = image;
			return this;
		}

		public Builder normalMap(GLImage image) {
			normalMap = image;
			return this;
		}

		public Builder alphaMode(GLAlphaMode mode) {
			if (mode == null)
				throw new IllegalArgumentException("Alpha mode must not be null");
			alphaMode = mode;
			return this;
		}

		public Builder alphaCutoff(float cutoff) {
			if (!Float.isFinite(cutoff) || cutoff < 0 || cutoff > 1)
				throw new IllegalArgumentException(
						"Alpha cutoff must be between zero and one");
			alphaCutoff = cutoff;
			return this;
		}

		/** Relative public depth range around the draw call's base depth. */
		public Builder depthRange(float backOffset, float frontOffset) {
			if (!Float.isFinite(backOffset) || !Float.isFinite(frontOffset)
					|| backOffset > frontOffset)
				throw new IllegalArgumentException(
						"Depth range must be finite and ordered back to front");
			depthBackOffset = backOffset;
			depthFrontOffset = frontOffset;
			return this;
		}

		public Builder lit(boolean value) {
			lit = value;
			return this;
		}

		public Builder depthState(GLDepthState state) {
			if (state == null)
				throw new IllegalArgumentException("Depth state must not be null");
			depthState = state;
			return this;
		}

		public Builder shader(ShaderProgram program) {
			if (program != null && program.isDisposed())
				throw new IllegalArgumentException("Material shader is disposed");
			shader = program;
			return this;
		}

		public Builder uniform(String name, float value) {
			return uniformValue(name, ShaderUniformValue.of(value));
		}

		public Builder uniform(String name, float x, float y) {
			return uniformValue(name, ShaderUniformValue.of(x, y));
		}

		public Builder uniform(String name, float x, float y, float z) {
			return uniformValue(name, ShaderUniformValue.of(x, y, z));
		}

		public Builder uniform(String name, float x, float y, float z, float w) {
			return uniformValue(name, ShaderUniformValue.of(x, y, z, w));
		}

		public Builder uniform(String name, int value) {
			return uniformValue(name, ShaderUniformValue.of(value));
		}

		public Builder uniformMatrix4(String name, float[] value) {
			return uniformValue(name, ShaderUniformValue.matrix4(value));
		}

		private Builder uniformValue(String name, ShaderUniformValue value) {
			if (name == null || name.isBlank())
				throw new IllegalArgumentException("Uniform name must not be blank");
			uniforms.put(name, value);
			return this;
		}

		public GLMaterial2D build() {
			if (lit && normalMap == null)
				throw new IllegalStateException(
						"A lit material requires a normal map");
			if (shader == null && !uniforms.isEmpty())
				throw new IllegalStateException(
						"Custom uniforms require a custom material shader");
			return new GLMaterial2D(this);
		}
	}
}

