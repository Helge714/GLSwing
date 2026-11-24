package com.vingame.glswing.rendering;

/** Clips a completed layer against one antialiased vector path. */
public final class GLPathClipEffect implements GLLayerEffect {

	private final GLPath2D path;

	public GLPathClipEffect(GLShape2D shape) {
		if (shape == null)
			throw new IllegalArgumentException("Clip shape must not be null");
		GLPath2D converted = shape.toPath();
		if (converted == null || converted.isEmpty())
			throw new IllegalArgumentException(
					"Clip shape must produce a non-empty path");
		path = converted.copy();
	}

	/** Returns an independent mutable copy of the clip path. */
	public GLPath2D getPath() {
		return path.copy();
	}

	GLPath2D pathValue() {
		return path;
	}

	@Override
	public float getPadding() {
		return 0;
	}
}
