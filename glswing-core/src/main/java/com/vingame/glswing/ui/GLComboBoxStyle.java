package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by combo boxes. */
public record GLComboBoxStyle(
		GLFontSpec font,
		Color background,
		Color hoverBackground,
		Color pressedBackground,
		Color pressedLightColor,
		Color pressedDarkColor,
		Color disabledBackground,
		Color textColor,
		Color disabledTextColor,
		Color borderColor,
		Color arrowColor,
		float cornerRadius,
		float borderWidth,
		float height,
		float minimumWidth,
		float arrowAreaWidth,
		GLInsets padding) {

	public static final GLComboBoxStyle DEFAULT = GLThemeDefaults.comboBoxStyle();

	public GLComboBoxStyle {
		if (font == null || background == null || hoverBackground == null
				|| pressedBackground == null || disabledBackground == null
				|| pressedLightColor == null || pressedDarkColor == null
				|| textColor == null || disabledTextColor == null
				|| borderColor == null || arrowColor == null || padding == null)
			throw new IllegalArgumentException(
					"Combo-box style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !positive(height) || !positive(minimumWidth)
				|| !positive(arrowAreaWidth))
			throw new IllegalArgumentException(
					"Combo-box geometry must be finite and valid");
	}

	public Color background(GLControlState state) {
		return switch (state) {
		case NORMAL -> background;
		case HOVERED -> hoverBackground;
		case PRESSED -> pressedBackground;
		case DISABLED -> disabledBackground;
		};
	}

	public Color text(GLControlState state) {
		return state == GLControlState.DISABLED ? disabledTextColor : textColor;
	}

	public GLComboBoxStyle withFont(GLFontSpec value) {
		return copy(value, background, hoverBackground, pressedBackground,
				pressedLightColor, pressedDarkColor, disabledBackground, textColor,
				disabledTextColor, borderColor,
				arrowColor, cornerRadius, borderWidth, height, minimumWidth,
				arrowAreaWidth, padding);
	}

	public GLComboBoxStyle withGeometry(float radius, float height,
			float minimumWidth, float arrowAreaWidth) {
		return copy(font, background, hoverBackground, pressedBackground,
				pressedLightColor, pressedDarkColor, disabledBackground, textColor,
				disabledTextColor, borderColor,
				arrowColor, radius, borderWidth, height, minimumWidth,
				arrowAreaWidth, padding);
	}

	public GLComboBoxStyle withColors(Color background, Color hover,
			Color pressed, Color disabled, Color text, Color disabledText,
			Color arrow) {
		return copy(font, background, hover, pressed, pressedLightColor,
				pressedDarkColor, disabled, text, disabledText, borderColor,
				arrow, cornerRadius, borderWidth,
				height, minimumWidth, arrowAreaWidth, padding);
	}

	public GLComboBoxStyle withPressedGradient(Color light, Color dark) {
		return copy(font, background, hoverBackground, pressedBackground,
				light, dark, disabledBackground, textColor, disabledTextColor,
				borderColor, arrowColor, cornerRadius, borderWidth, height,
				minimumWidth, arrowAreaWidth, padding);
	}

	public GLComboBoxStyle withBorder(float width, Color color) {
		return copy(font, background, hoverBackground, pressedBackground,
				pressedLightColor, pressedDarkColor, disabledBackground, textColor,
				disabledTextColor, color,
				arrowColor, cornerRadius, width, height, minimumWidth,
				arrowAreaWidth, padding);
	}

	public GLComboBoxStyle withPadding(GLInsets value) {
		return copy(font, background, hoverBackground, pressedBackground,
				pressedLightColor, pressedDarkColor, disabledBackground, textColor,
				disabledTextColor, borderColor,
				arrowColor, cornerRadius, borderWidth, height, minimumWidth,
				arrowAreaWidth, value);
	}

	private static GLComboBoxStyle copy(GLFontSpec font, Color background,
			Color hover, Color pressed, Color pressedLight, Color pressedDark,
			Color disabled, Color text,
			Color disabledText, Color border, Color arrow, float radius,
			float borderWidth, float height, float minimumWidth,
			float arrowAreaWidth, GLInsets padding) {
		return new GLComboBoxStyle(font, background, hover, pressed, pressedLight,
				pressedDark, disabled, text, disabledText, border, arrow, radius,
				borderWidth, height,
				minimumWidth, arrowAreaWidth, padding);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
