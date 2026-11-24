package com.vingame.glswing.ui;

import java.awt.Color;


/** Immutable appearance and geometry of a {@link GLMenuBar}. */
public record GLMenuBarStyle(
		GLFontSpec font,
		Color background,
		Color textColor,
		Color disabledTextColor,
		Color hoverBackground,
		Color pressedBackground,
		Color activeBackground,
		Color borderColor,
		float height,
		float itemHorizontalPadding,
		float itemGap,
		float borderWidth,
		float itemCornerRadius) {

	public static final GLMenuBarStyle DEFAULT = GLThemeDefaults.menuBarStyle();

	public GLMenuBarStyle {
		if (font == null || background == null || textColor == null
				|| disabledTextColor == null || hoverBackground == null
				|| pressedBackground == null || activeBackground == null
				|| borderColor == null)
			throw new IllegalArgumentException("Menu-bar style values must not be null");
		if (!positive(height) || !nonNegative(itemHorizontalPadding)
				|| !nonNegative(itemGap) || !nonNegative(borderWidth)
				|| !nonNegative(itemCornerRadius))
			throw new IllegalArgumentException(
					"Menu-bar geometry must be finite and non-negative");
	}

	public GLMenuBarStyle withFont(GLFontSpec value) {
		return copy(value, background, textColor, disabledTextColor,
				hoverBackground, pressedBackground, activeBackground, borderColor,
				height, itemHorizontalPadding, itemGap, borderWidth, itemCornerRadius);
	}

	public GLMenuBarStyle withBackground(Color value, Color border) {
		return copy(font, value, textColor, disabledTextColor, hoverBackground,
				pressedBackground, activeBackground, border, height,
				itemHorizontalPadding, itemGap, borderWidth, itemCornerRadius);
	}

	public GLMenuBarStyle withSelectionColors(Color hover, Color pressed,
			Color active) {
		return copy(font, background, textColor, disabledTextColor, hover,
				pressed, active, borderColor, height, itemHorizontalPadding,
				itemGap, borderWidth, itemCornerRadius);
	}

	public GLMenuBarStyle withTextColors(Color text, Color disabled) {
		return copy(font, background, text, disabled, hoverBackground,
				pressedBackground, activeBackground, borderColor, height,
				itemHorizontalPadding, itemGap, borderWidth, itemCornerRadius);
	}

	public GLMenuBarStyle withGeometry(float height, float horizontalPadding,
			float gap, float borderWidth, float cornerRadius) {
		return copy(font, background, textColor, disabledTextColor,
				hoverBackground, pressedBackground, activeBackground, borderColor,
				height, horizontalPadding, gap, borderWidth, cornerRadius);
	}

	private static GLMenuBarStyle copy(GLFontSpec font, Color background,
			Color textColor, Color disabledTextColor, Color hoverBackground,
			Color pressedBackground, Color activeBackground, Color borderColor,
			float height, float itemHorizontalPadding, float itemGap,
			float borderWidth, float itemCornerRadius) {
		return new GLMenuBarStyle(font, background, textColor, disabledTextColor,
				hoverBackground, pressedBackground, activeBackground, borderColor,
				height, itemHorizontalPadding, itemGap, borderWidth,
				itemCornerRadius);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
