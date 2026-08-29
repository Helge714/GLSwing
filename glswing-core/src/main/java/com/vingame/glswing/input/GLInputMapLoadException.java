package com.vingame.glswing.input;

/** Describes a missing, unreadable or invalid external input map. */
public final class GLInputMapLoadException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public GLInputMapLoadException(String message) { super(message); }
	public GLInputMapLoadException(String message, Throwable cause) { super(message, cause); }
}
