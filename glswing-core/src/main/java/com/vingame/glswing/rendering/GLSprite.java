package com.vingame.glswing.rendering;

/** Immutable convenience asset pairing a material with its natural dimensions. */
public final class GLSprite {
	private final GLMaterial2D material;

	public GLSprite(GLMaterial2D material) {
		if (material == null)
			throw new IllegalArgumentException("Sprite material must not be null");
		this.material = material;
	}

	public static GLSprite of(GLImage colorMap) {
		return new GLSprite(GLMaterial2D.builder(colorMap).build());
	}

	public static Builder builder(GLImage colorMap) {
		return new Builder(colorMap);
	}

	public GLMaterial2D getMaterial() { return material; }
	public int getWidth() { return material.getColorMap().getWidth(); }
	public int getHeight() { return material.getColorMap().getHeight(); }
	public int getNaturalWidth() { return getWidth(); }
	public int getNaturalHeight() { return getHeight(); }

	/** Convenience builder whose {@code build()} result is a complete sprite. */
	public static final class Builder {
		private final GLMaterial2D.Builder material;

		private Builder(GLImage colorMap) {
			material = GLMaterial2D.builder(colorMap);
		}

		public Builder depthMap(GLImage value) { material.depthMap(value); return this; }
		public Builder normalMap(GLImage value) { material.normalMap(value); return this; }
		public Builder alphaMode(GLAlphaMode value) { material.alphaMode(value); return this; }
		public Builder alphaCutoff(float value) { material.alphaCutoff(value); return this; }
		public Builder depthRange(float back, float front) {
			material.depthRange(back, front); return this;
		}
		public Builder lit(boolean value) { material.lit(value); return this; }
		public Builder depthState(GLDepthState value) { material.depthState(value); return this; }
		public Builder shader(ShaderProgram value) { material.shader(value); return this; }
		public Builder uniform(String name, float value) {
			material.uniform(name, value); return this;
		}
		public Builder uniform(String name, float x, float y) {
			material.uniform(name, x, y); return this;
		}
		public Builder uniform(String name, float x, float y, float z) {
			material.uniform(name, x, y, z); return this;
		}
		public Builder uniform(String name, float x, float y, float z, float w) {
			material.uniform(name, x, y, z, w); return this;
		}
		public Builder uniform(String name, int value) {
			material.uniform(name, value); return this;
		}
		public Builder uniformMatrix4(String name, float[] value) {
			material.uniformMatrix4(name, value); return this;
		}
		public GLSprite build() { return new GLSprite(material.build()); }
	}
}
