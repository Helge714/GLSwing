package com.vingame.glswing.rendering;

/** Resolves a frame-size-dependent 2D projection into an affine transform. */
@FunctionalInterface
public interface Projection2D extends GLView2D {

	/** Swing-style pixel coordinates: origin top-left, positive y downwards. */
	Projection2D TOP_LEFT_PIXELS = Projection2D::topLeftPixels;

	/** Pixel coordinates centered in the viewport with positive y upwards. */
	Projection2D CENTERED_PIXELS = Projection2D::centeredPixels;

	/**
	 * Aspect-correct normalized coordinates centered in the viewport. Visible y
	 * is {@code -1..1}; visible x is {@code -aspect..aspect}.
	 */
	Projection2D CENTERED_NORMALIZED = Projection2D::centeredNormalized;

	/** Raw OpenGL clip space: both axes use {@code -1..1} without aspect correction. */
	Projection2D CLIP_SPACE = Projection2D::clipSpace;

	/** Creates the concrete world-to-clip transform for the current frame size. */
	GLTransform create(float width, float height);

	@Override
	default GLTransform createViewProjection(float width, float height) {
		return create(width, height);
	}

	static GLTransform topLeftPixels(float width, float height) {
		requirePositiveSize(width, height);
		return new GLTransform(
				2f / width, 0,
				0, -2f / height,
				-1, 1);
	}

	static GLTransform centeredPixels(float width, float height) {
		requirePositiveSize(width, height);
		return new GLTransform(
				2f / width, 0,
				0, 2f / height,
				0, 0);
	}

	static GLTransform centeredNormalized(float width, float height) {
		requirePositiveSize(width, height);
		float aspect = width / height;
		return new GLTransform(
				1f / aspect, 0,
				0, 1,
				0, 0);
	}

	static GLTransform clipSpace(float width, float height) {
		requirePositiveSize(width, height);
		return new GLTransform();
	}

	/** Creates a size-independent provider from a concrete projection. */
	static Projection2D fixed(GLTransform projection) {
		if (projection == null)
			throw new IllegalArgumentException("Projection must not be null");
		GLTransform copy = projection.copy();
		return (width, height) -> copy.copy();
	}

	private static void requirePositiveSize(float width, float height) {
		if (!(width > 0) || !(height > 0)
				|| !Float.isFinite(width) || !Float.isFinite(height))
			throw new IllegalArgumentException("Projection dimensions must be finite and positive");
	}
}
