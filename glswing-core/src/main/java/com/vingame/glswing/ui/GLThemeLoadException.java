package com.vingame.glswing.ui;

/** Describes a missing, unreadable or invalid external theme. */
public final class GLThemeLoadException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public GLThemeLoadException(String message) {
		super(message);
	}

	public GLThemeLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
