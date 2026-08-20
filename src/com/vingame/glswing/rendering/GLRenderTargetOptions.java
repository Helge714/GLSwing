package com.vingame.glswing.rendering;

/** Sampling options for one persistent RGBA8 render target. */
public record GLRenderTargetOptions(Filter filter, Wrap wrap) {

	public enum Filter { LINEAR, NEAREST }
	public enum Wrap { CLAMP, REPEAT }

	public static final GLRenderTargetOptions DEFAULT =
			new GLRenderTargetOptions(Filter.LINEAR, Wrap.CLAMP);

	public GLRenderTargetOptions {
		if (filter == null || wrap == null)
			throw new IllegalArgumentException(
					"Render-target options must not contain null");
	}

	public static GLRenderTargetOptions pixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.CLAMP);
	}

	public static GLRenderTargetOptions repeatingPixelArt() {
		return new GLRenderTargetOptions(Filter.NEAREST, Wrap.REPEAT);
	}

	public GLRenderTargetOptions withFilter(Filter value) {
		return new GLRenderTargetOptions(value, wrap);
	}

	public GLRenderTargetOptions withWrap(Wrap value) {
		return new GLRenderTargetOptions(filter, value);
	}
}
