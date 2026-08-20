package com.vingame.glswing.rendering;

/** Sampling plus optional depth and inner-picking surfaces for one render target. */
public record GLRenderTargetOptions(Filter filter, Wrap wrap, boolean picking,
		boolean depth) {

	public enum Filter { LINEAR, NEAREST }
	public enum Wrap { CLAMP, REPEAT }

	public static final GLRenderTargetOptions DEFAULT =
			new GLRenderTargetOptions(Filter.LINEAR, Wrap.CLAMP, false, false);

	public GLRenderTargetOptions(Filter filter, Wrap wrap) {
		this(filter, wrap, false, false);
	}

	public GLRenderTargetOptions(Filter filter, Wrap wrap, boolean picking) {
		this(filter, wrap, picking, false);
	}

	public GLRenderTargetOptions {
		if (filter == null || wrap == null)
			throw new IllegalArgumentException(
					"Render-target options must not contain null");
	}

	public static GLRenderTargetOptions pixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.CLAMP, false, false);
	}

	public static GLRenderTargetOptions repeatingPixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.REPEAT, false, false);
	}

	public GLRenderTargetOptions withFilter(Filter value) {
		return new GLRenderTargetOptions(value, wrap, picking, depth);
	}

	public GLRenderTargetOptions withWrap(Wrap value) {
		return new GLRenderTargetOptions(filter, value, picking, depth);
	}

	/** Enables or disables a persistent target-local integer picking surface. */
	public GLRenderTargetOptions withPicking(boolean value) {
		return new GLRenderTargetOptions(filter, wrap, value, depth);
	}

	/** Enables or disables a depth attachment for color and picking. */
	public GLRenderTargetOptions withDepth(boolean value) {
		return new GLRenderTargetOptions(filter, wrap, picking, value);
	}
}
