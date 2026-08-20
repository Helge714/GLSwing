package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by themed buttons. */
public record GLButtonStyle(
		GLFontSpec font,
		Color background,
		Color hoverBackground,
		Color pressedLightColor,
		Color pressedDarkColor,
		Color disabledBackground,
		Color textColor,
		Color disabledTextColor,
		Color borderColor,
		Color focusColor,
		float cornerRadius,
		float borderWidth,
		float height,
		GLInsets padding) {

	public static final GLButtonStyle DEFAULT = GLThemeDefaults.buttonStyle();

	public GLButtonStyle {
		if (font == null || background == null || hoverBackground == null
				|| pressedLightColor == null || pressedDarkColor == null
				|| disabledBackground == null
				|| textColor == null || disabledTextColor == null
				|| borderColor == null || focusColor == null || padding == null)
			throw new IllegalArgumentException("Button style values must not be null");
		if (!Float.isFinite(cornerRadius) || cornerRadius < 0
				|| !Float.isFinite(borderWidth) || borderWidth < 0
				|| !Float.isFinite(height) || height <= 0)
			throw new IllegalArgumentException(
					"Button geometry must be finite and non-negative");
	}

	public Color background(GLControlState state) {
		return switch (state) {
		case NORMAL -> background;
		case HOVERED -> hoverBackground;
		case PRESSED -> pressedDarkColor;
		case DISABLED -> disabledBackground;
		};
	}

	public Color text(GLControlState state) {
		return state == GLControlState.DISABLED ? disabledTextColor : textColor;
	}

	public GLButtonStyle withFont(GLFontSpec value) {
		return copy(value, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withBackground(Color value) {
		return copy(font, value, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withHoverBackground(Color value) {
		return copy(font, background, value, pressedLightColor, pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withPressedBackground(Color value) {
		return copy(font, background, hoverBackground, value, value,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withPressedGradient(Color light, Color dark) {
		return copy(font, background, hoverBackground, light, dark,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withDisabledBackground(Color value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				value, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withTextColor(Color value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, value, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withDisabledTextColor(Color value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, value, borderColor,
				focusColor, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withCornerRadius(float value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, value, borderWidth, height, padding);
	}

	public GLButtonStyle withBorder(float width, Color color) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, color,
				focusColor, cornerRadius, width, height, padding);
	}

	public GLButtonStyle withFocusColor(Color value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				value, cornerRadius, borderWidth, height, padding);
	}

	public GLButtonStyle withHeight(float value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, value, padding);
	}

	public GLButtonStyle withPadding(GLInsets value) {
		return copy(font, background, hoverBackground, pressedLightColor,
				pressedDarkColor,
				disabledBackground, textColor, disabledTextColor, borderColor,
				focusColor, cornerRadius, borderWidth, height, value);
	}

	private static GLButtonStyle copy(GLFontSpec font,
			Color background, Color hoverBackground, Color pressedLightColor,
			Color pressedDarkColor, Color disabledBackground, Color textColor,
			Color disabledTextColor,
			Color borderColor, Color focusColor, float cornerRadius,
			float borderWidth, float height, GLInsets padding) {
		return new GLButtonStyle(font, background, hoverBackground,
				pressedLightColor, pressedDarkColor, disabledBackground, textColor,
				disabledTextColor, borderColor, focusColor, cornerRadius,
				borderWidth, height, padding);
	}
}
