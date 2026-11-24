package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by check boxes. */
public record GLCheckBoxStyle(
		GLFontSpec font,
		Color background,
		Color hoverBackground,
		Color pressedBackground,
		Color disabledBackground,
		Color checkColor,
		Color disabledCheckColor,
		Color textColor,
		Color disabledTextColor,
		Color borderColor,
		Color focusBorderColor,
		float boxSize,
		float gap,
		float cornerRadius,
		float borderWidth,
		float checkStrokeWidth,
		GLInsets padding) {

	public static final GLCheckBoxStyle DEFAULT = GLThemeDefaults.checkBoxStyle();

	public GLCheckBoxStyle {
		if (font == null || background == null || hoverBackground == null
				|| pressedBackground == null || disabledBackground == null
				|| checkColor == null || disabledCheckColor == null
				|| textColor == null || disabledTextColor == null
				|| borderColor == null || focusBorderColor == null || padding == null)
			throw new IllegalArgumentException("Check box style values must not be null");
		if (!positive(boxSize) || !nonNegative(gap) || !nonNegative(cornerRadius)
				|| !nonNegative(borderWidth) || !positive(checkStrokeWidth))
			throw new IllegalArgumentException(
					"Check box sizes must be finite and non-negative");
	}

	public Color background(GLControlState state) {
		return switch (state) {
		case NORMAL -> background;
		case HOVERED -> hoverBackground;
		case PRESSED -> pressedBackground;
		case DISABLED -> disabledBackground;
		};
	}

	public Color check(GLControlState state) {
		return state == GLControlState.DISABLED ? disabledCheckColor : checkColor;
	}

	public Color text(GLControlState state) {
		return state == GLControlState.DISABLED ? disabledTextColor : textColor;
	}

	public GLCheckBoxStyle withFont(GLFontSpec value) {
		return copy(value, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withBackground(Color value) {
		return copy(font, value, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withHoverBackground(Color value) {
		return copy(font, background, value, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withPressedBackground(Color value) {
		return copy(font, background, hoverBackground, value,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withDisabledBackground(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				value, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withCheckColor(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, value, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withDisabledCheckColor(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, value, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withTextColor(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, value,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withDisabledTextColor(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				value, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withBorder(float width, Color color) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, color, focusBorderColor, boxSize, gap,
				cornerRadius, width, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withFocusBorderColor(Color value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, value, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withBoxSize(float value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, value, gap,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withGap(float value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, value,
				cornerRadius, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withCornerRadius(float value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				value, borderWidth, checkStrokeWidth, padding);
	}

	public GLCheckBoxStyle withCheckStrokeWidth(float value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, value, padding);
	}

	public GLCheckBoxStyle withPadding(GLInsets value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, checkColor, disabledCheckColor, textColor,
				disabledTextColor, borderColor, focusBorderColor, boxSize, gap,
				cornerRadius, borderWidth, checkStrokeWidth, value);
	}

	private static GLCheckBoxStyle copy(GLFontSpec font,
			Color background, Color hoverBackground, Color pressedBackground,
			Color disabledBackground, Color checkColor, Color disabledCheckColor,
			Color textColor, Color disabledTextColor, Color borderColor,
			Color focusBorderColor, float boxSize, float gap, float cornerRadius,
			float borderWidth, float checkStrokeWidth, GLInsets padding) {
		return new GLCheckBoxStyle(font, background, hoverBackground,
				pressedBackground, disabledBackground, checkColor,
				disabledCheckColor, textColor, disabledTextColor, borderColor,
				focusBorderColor, boxSize, gap, cornerRadius, borderWidth,
				checkStrokeWidth, padding);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
