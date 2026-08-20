package com.vingame.glswing.rendering;

/** Sampling and optional inner-picking options for one persistent render target. */
public record GLRenderTargetOptions(Filter filter, Wrap wrap, boolean picking) {

	public enum Filter { LINEAR, NEAREST }
	public enum Wrap { CLAMP, REPEAT }

	public static final GLRenderTargetOptions DEFAULT =
			new GLRenderTargetOptions(Filter.LINEAR, Wrap.CLAMP, false);

	public GLRenderTargetOptions(Filter filter, Wrap wrap) {
		this(filter, wrap, false);
	}

	public GLRenderTargetOptions {
		if (filter == null || wrap == null)
			throw new IllegalArgumentException(
					"Render-target options must not contain null");
	}

	public static GLRenderTargetOptions pixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.CLAMP, false);
	}

	public static GLRenderTargetOptions repeatingPixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.REPEAT, false);
	}

	public GLRenderTargetOptions withFilter(Filter value) {
		return new GLRenderTargetOptions(value, wrap, picking);
	}

	public GLRenderTargetOptions withWrap(Wrap value) {
		return new GLRenderTargetOptions(filter, value, picking);
	}

	/** Enables or disables a persistent target-local integer picking surface. */
	public GLRenderTargetOptions withPicking(boolean value) {
		return new GLRenderTargetOptions(filter, wrap, value);
	}
}
