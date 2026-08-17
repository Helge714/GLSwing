package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable visual and spacing values for labels. */
public record GLLabelStyle(GLFontSpec font, Color textColor, GLInsets padding) {

	public static final GLLabelStyle DEFAULT = GLThemeDefaults.labelStyle();

	public GLLabelStyle {
		if (font == null || textColor == null || padding == null)
			throw new IllegalArgumentException("Label style values must not be null");
	}

	public GLLabelStyle withFont(GLFontSpec font) {
		return new GLLabelStyle(font, textColor, padding);
	}

	public GLLabelStyle withTextColor(Color color) {
		return new GLLabelStyle(font, color, padding);
	}

	public GLLabelStyle withPadding(GLInsets padding) {
		return new GLLabelStyle(font, textColor, padding);
	}
}
