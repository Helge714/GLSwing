package com.vingame.glswing.rendering;

/** Immutable depth-test and depth-write state for 2D/2.5D rendering. */
public record GLDepthState(boolean testEnabled, boolean writeEnabled,
		GLDepthFunction function) {

	public static final GLDepthState DISABLED =
			new GLDepthState(false, false, GLDepthFunction.GREATER);
	/** Larger public depth values are in front and update the depth buffer. */
	public static final GLDepthState READ_WRITE =
			new GLDepthState(true, true, GLDepthFunction.GREATER);
	/** Tests against existing depth without occluding later transparent draws. */
	public static final GLDepthState READ_ONLY =
			new GLDepthState(true, false, GLDepthFunction.GREATER);

	public GLDepthState {
		if (function == null)
			throw new IllegalArgumentException("Depth function must not be null");
	}

	public GLDepthState withTestEnabled(boolean enabled) {
		return new GLDepthState(enabled, writeEnabled, function);
	}

	public GLDepthState withWriteEnabled(boolean enabled) {
		return new GLDepthState(testEnabled, enabled, function);
	}

	public GLDepthState withFunction(GLDepthFunction value) {
		return new GLDepthState(testEnabled, writeEnabled, value);
	}
}

