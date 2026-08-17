package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable look-and-feel values for a single-line text editor. */
public record GLTextEditorStyle(
		GLFontSpec font,
		Color backgroundColor,
		Color textColor,
		Color disabledTextColor,
		Color selectionColor,
		Color caretColor,
		Color borderColor,
		Color focusedBorderColor,
		Color invalidBorderColor,
		float cornerRadius,
		float borderWidth,
		float height,
		float preferredWidth,
		float overflowFadeWidth,
		GLInsets padding) {

	public static final GLTextEditorStyle DEFAULT = GLThemeDefaults.textEditorStyle();

	public GLTextEditorStyle {
		if (font == null || backgroundColor == null || textColor == null
				|| disabledTextColor == null || selectionColor == null
				|| caretColor == null || borderColor == null
				|| focusedBorderColor == null || invalidBorderColor == null
				|| padding == null)
			throw new IllegalArgumentException("Text editor style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !nonNegative(overflowFadeWidth)
				|| !positive(height) || !positive(preferredWidth))
			throw new IllegalArgumentException(
					"Text editor geometry must be finite and non-negative");
	}

	public GLTextEditorStyle withFont(GLFontSpec value) {
		return new GLTextEditorStyle(value, backgroundColor, textColor,
				disabledTextColor, selectionColor, caretColor, borderColor,
				focusedBorderColor, invalidBorderColor, cornerRadius,
				borderWidth, height, preferredWidth, overflowFadeWidth, padding);
	}

	public GLTextEditorStyle withBorderColors(Color normal, Color focused,
			Color invalid) {
		return new GLTextEditorStyle(font, backgroundColor, textColor,
				disabledTextColor, selectionColor, caretColor, normal, focused,
				invalid, cornerRadius, borderWidth, height, preferredWidth,
				overflowFadeWidth, padding);
	}

	public GLTextEditorStyle withGeometry(float cornerRadius, float borderWidth,
			float height, float preferredWidth, GLInsets padding) {
		return new GLTextEditorStyle(font, backgroundColor, textColor,
				disabledTextColor, selectionColor, caretColor, borderColor,
				focusedBorderColor, invalidBorderColor, cornerRadius, borderWidth,
				height, preferredWidth, overflowFadeWidth, padding);
	}

	public GLTextEditorStyle withOverflowFadeWidth(float width) {
		return new GLTextEditorStyle(font, backgroundColor, textColor,
				disabledTextColor, selectionColor, caretColor, borderColor,
				focusedBorderColor, invalidBorderColor, cornerRadius, borderWidth,
				height, preferredWidth, width, padding);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
