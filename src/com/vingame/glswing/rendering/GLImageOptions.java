package com.vingame.glswing.rendering;

/** Controls storage and sampling when a {@link GLImage} first reaches a renderer. */
public record GLImageOptions(Storage storage, Filter filter, Wrap wrap,
		Usage usage, Mipmapping mipmapping) {

	public enum Storage { AUTO, ATLAS, STANDALONE }
	public enum Filter { LINEAR, NEAREST }
	public enum Wrap { CLAMP, REPEAT }
	public enum Usage { STATIC, DYNAMIC }
	public enum Mipmapping { NONE, AUTO, ENABLED }

	public static final GLImageOptions DEFAULT = new GLImageOptions(
			Storage.AUTO, Filter.LINEAR, Wrap.CLAMP, Usage.STATIC, Mipmapping.AUTO);

	public GLImageOptions {
		if (storage == null || filter == null || wrap == null || usage == null
				|| mipmapping == null)
			throw new IllegalArgumentException("Image options must not contain null");
		if (storage == Storage.ATLAS && (wrap == Wrap.REPEAT || usage == Usage.DYNAMIC))
			throw new IllegalArgumentException(
					"Atlas images must be static and clamp to their edges");
		if (mipmapping == Mipmapping.ENABLED
				&& (filter != Filter.LINEAR || usage != Usage.STATIC))
			throw new IllegalArgumentException(
					"Explicit mipmapping requires a static, linearly filtered image");
	}

	public static GLImageOptions atlas() {
		return DEFAULT.withStorage(Storage.ATLAS);
	}

	public static GLImageOptions standalone() {
		return DEFAULT.withStorage(Storage.STANDALONE);
	}

	public static GLImageOptions pixelArt() {
		return DEFAULT.withFilter(Filter.NEAREST).withMipmapping(Mipmapping.NONE);
	}

	public static GLImageOptions dynamic() {
		return DEFAULT.withUsage(Usage.DYNAMIC);
	}

	public static GLImageOptions repeating() {
		return DEFAULT.withStorage(Storage.STANDALONE).withWrap(Wrap.REPEAT);
	}

	public GLImageOptions withStorage(Storage value) {
		return new GLImageOptions(value, filter, wrap, usage, mipmapping);
	}

	public GLImageOptions withFilter(Filter value) {
		Mipmapping resolved = value == Filter.NEAREST
				? Mipmapping.NONE : mipmapping;
		return new GLImageOptions(storage, value, wrap, usage, resolved);
	}

	public GLImageOptions withWrap(Wrap value) {
		return new GLImageOptions(storage, filter, value, usage, mipmapping);
	}

	public GLImageOptions withUsage(Usage value) {
		Storage resolved = value == Usage.DYNAMIC && storage == Storage.ATLAS
				? Storage.STANDALONE : storage;
		Mipmapping resolvedMipmapping = value == Usage.DYNAMIC
				? Mipmapping.NONE : mipmapping;
		return new GLImageOptions(resolved, filter, wrap, value, resolvedMipmapping);
	}

	public GLImageOptions withMipmapping(Mipmapping value) {
		return new GLImageOptions(storage, filter, wrap, usage, value);
	}
}
