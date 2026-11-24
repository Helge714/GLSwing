package com.vingame.glswing.ui;

/** A recoverable problem found while loading a theme. */
public record GLThemeLoadWarning(String source, String property, String message) {
	public GLThemeLoadWarning {
		if (source == null || source.isBlank() || property == null || property.isBlank()
				|| message == null || message.isBlank())
			throw new IllegalArgumentException("Invalid theme-load warning");
	}
}
