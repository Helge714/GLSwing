package com.vingame.glswing.rendering;

/** Controls storage and sampling when a {@link GLImage} first reaches a renderer. */
public record GLImageOptions(Storage storage, Filter filter, Wrap wrap,
		Usage usage) {

	public enum Storage { AUTO, ATLAS, STANDALONE }
	public enum Filter { LINEAR, NEAREST }
	public enum Wrap { CLAMP, REPEAT }
	public enum Usage { STATIC, DYNAMIC }

	public static final GLImageOptions DEFAULT = new GLImageOptions(
			Storage.AUTO, Filter.LINEAR, Wrap.CLAMP, Usage.STATIC);

	public GLImageOptions {
		if (storage == null || filter == null || wrap == null || usage == null)
			throw new IllegalArgumentException("Image options must not contain null");
		if (storage == Storage.ATLAS && (wrap == Wrap.REPEAT || usage == Usage.DYNAMIC))
			throw new IllegalArgumentException(
					"Atlas images must be static and clamp to their edges");
	}

	public static GLImageOptions atlas() {
		return DEFAULT.withStorage(Storage.ATLAS);
	}

	public static GLImageOptions standalone() {
		return DEFAULT.withStorage(Storage.STANDALONE);
	}

	public static GLImageOptions pixelArt() {
		return DEFAULT.withFilter(Filter.NEAREST);
	}

	public static GLImageOptions dynamic() {
		return DEFAULT.withUsage(Usage.DYNAMIC);
	}

	public static GLImageOptions repeating() {
		return DEFAULT.withStorage(Storage.STANDALONE).withWrap(Wrap.REPEAT);
	}

	public GLImageOptions withStorage(Storage value) {
		return new GLImageOptions(value, filter, wrap, usage);
	}

	public GLImageOptions withFilter(Filter value) {
		return new GLImageOptions(storage, value, wrap, usage);
	}

	public GLImageOptions withWrap(Wrap value) {
		return new GLImageOptions(storage, filter, value, usage);
	}

	public GLImageOptions withUsage(Usage value) {
		Storage resolved = value == Usage.DYNAMIC && storage == Storage.ATLAS
				? Storage.STANDALONE : storage;
		return new GLImageOptions(resolved, filter, wrap, value);
	}
}
