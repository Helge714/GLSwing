package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by popup menus. */
public record GLMenuStyle(
		GLFontSpec font,
		Color background,
		Color hoverBackground,
		Color pressedBackground,
		Color textColor,
		Color disabledTextColor,
		Color acceleratorColor,
		Color borderColor,
		Color separatorColor,
		Color arrowColor,
		float cornerRadius,
		float borderWidth,
		float itemHeight,
		float separatorHeight,
		float minimumWidth,
		float itemHorizontalPadding,
		float columnGap,
		GLInsets padding) {

	public static final GLMenuStyle DEFAULT = GLThemeDefaults.menuStyle();

	public GLMenuStyle {
		if (font == null || background == null || hoverBackground == null
				|| pressedBackground == null || textColor == null
				|| disabledTextColor == null || acceleratorColor == null
				|| borderColor == null || separatorColor == null
				|| arrowColor == null || padding == null)
			throw new IllegalArgumentException("Menu style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !positive(itemHeight) || !nonNegative(separatorHeight)
				|| !positive(minimumWidth) || !nonNegative(itemHorizontalPadding)
				|| !nonNegative(columnGap))
			throw new IllegalArgumentException(
					"Menu geometry must be finite and non-negative");
	}

	public GLMenuStyle withFont(GLFontSpec value) {
		return copy(value, background, hoverBackground, pressedBackground,
				textColor, disabledTextColor, acceleratorColor, borderColor,
				separatorColor, arrowColor, cornerRadius, borderWidth, itemHeight,
				separatorHeight, minimumWidth, itemHorizontalPadding, columnGap,
				padding);
	}

	public GLMenuStyle withBackground(Color value) {
		return copy(font, value, hoverBackground, pressedBackground,
				textColor, disabledTextColor, acceleratorColor, borderColor,
				separatorColor, arrowColor, cornerRadius, borderWidth, itemHeight,
				separatorHeight, minimumWidth, itemHorizontalPadding, columnGap,
				padding);
	}

	public GLMenuStyle withSelectionColors(Color hover, Color pressed) {
		return copy(font, background, hover, pressed, textColor,
				disabledTextColor, acceleratorColor, borderColor, separatorColor,
				arrowColor, cornerRadius, borderWidth, itemHeight, separatorHeight,
				minimumWidth, itemHorizontalPadding, columnGap, padding);
	}

	public GLMenuStyle withTextColors(Color text, Color disabled,
			Color accelerator, Color arrow) {
		return copy(font, background, hoverBackground, pressedBackground,
				text, disabled, accelerator, borderColor, separatorColor, arrow,
				cornerRadius, borderWidth, itemHeight, separatorHeight, minimumWidth,
				itemHorizontalPadding, columnGap, padding);
	}

	public GLMenuStyle withBorder(float width, Color color) {
		return copy(font, background, hoverBackground, pressedBackground,
				textColor, disabledTextColor, acceleratorColor, color,
				separatorColor, arrowColor, cornerRadius, width, itemHeight,
				separatorHeight, minimumWidth, itemHorizontalPadding, columnGap,
				padding);
	}

	public GLMenuStyle withGeometry(float radius, float itemHeight,
			float minimumWidth) {
		return copy(font, background, hoverBackground, pressedBackground,
				textColor, disabledTextColor, acceleratorColor, borderColor,
				separatorColor, arrowColor, radius, borderWidth, itemHeight,
				separatorHeight, minimumWidth, itemHorizontalPadding, columnGap,
				padding);
	}

	public GLMenuStyle withPadding(GLInsets value) {
		return copy(font, background, hoverBackground, pressedBackground,
				textColor, disabledTextColor, acceleratorColor, borderColor,
				separatorColor, arrowColor, cornerRadius, borderWidth, itemHeight,
				separatorHeight, minimumWidth, itemHorizontalPadding, columnGap,
				value);
	}

	private static GLMenuStyle copy(GLFontSpec font, Color background,
			Color hoverBackground, Color pressedBackground, Color textColor,
			Color disabledTextColor, Color acceleratorColor, Color borderColor,
			Color separatorColor, Color arrowColor, float cornerRadius,
			float borderWidth, float itemHeight, float separatorHeight,
			float minimumWidth, float itemHorizontalPadding, float columnGap,
			GLInsets padding) {
		return new GLMenuStyle(font, background, hoverBackground,
				pressedBackground, textColor, disabledTextColor, acceleratorColor,
				borderColor, separatorColor, arrowColor, cornerRadius, borderWidth,
				itemHeight, separatorHeight, minimumWidth, itemHorizontalPadding,
				columnGap, padding);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
