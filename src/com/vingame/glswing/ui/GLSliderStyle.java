package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by sliders. */
public record GLSliderStyle(
		GLFontSpec font,
		Color background,
		Color hoverBackground,
		Color pressedBackground,
		Color disabledBackground,
		Color textColor,
		Color disabledTextColor,
		Color borderColor,
		Color progressTopColor,
		Color progressBottomColor,
		Color arrowBackground,
		Color arrowHoverBackground,
		Color arrowPressedBackground,
		Color arrowDisabledBackground,
		Color arrowColor,
		Color disabledArrowColor,
		float cornerRadius,
		float borderWidth,
		float height,
		float preferredWidth,
		float arrowButtonWidth,
		float arrowInset,
		float arrowStrokeWidth,
		GLInsets padding) {

	public static final GLSliderStyle DEFAULT = GLThemeDefaults.sliderStyle();

	public GLSliderStyle {
		if (font == null || background == null || hoverBackground == null
				|| pressedBackground == null || disabledBackground == null
				|| textColor == null || disabledTextColor == null
				|| borderColor == null || progressTopColor == null
				|| progressBottomColor == null || arrowBackground == null
				|| arrowHoverBackground == null || arrowPressedBackground == null
				|| arrowDisabledBackground == null || arrowColor == null
				|| disabledArrowColor == null || padding == null)
			throw new IllegalArgumentException("Slider style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !positive(height) || !positive(preferredWidth)
				|| !positive(arrowButtonWidth) || !nonNegative(arrowInset)
				|| !positive(arrowStrokeWidth))
			throw new IllegalArgumentException(
					"Slider geometry must be finite and non-negative");
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

	public GLSliderStyle withFont(GLFontSpec value) {
		return copy(value, background, hoverBackground, pressedBackground,
				disabledBackground, textColor, disabledTextColor, borderColor,
				progressTopColor, progressBottomColor, arrowBackground,
				arrowHoverBackground, arrowPressedBackground,
				arrowDisabledBackground, arrowColor, disabledArrowColor,
				cornerRadius, borderWidth, height, preferredWidth,
				arrowButtonWidth, arrowInset, arrowStrokeWidth, padding);
	}

	public GLSliderStyle withProgressColors(Color top, Color bottom) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, textColor, disabledTextColor, borderColor,
				top, bottom, arrowBackground, arrowHoverBackground,
				arrowPressedBackground, arrowDisabledBackground, arrowColor,
				disabledArrowColor, cornerRadius, borderWidth, height,
				preferredWidth, arrowButtonWidth, arrowInset, arrowStrokeWidth, padding);
	}

	public GLSliderStyle withBorder(float width, Color color) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, textColor, disabledTextColor, color,
				progressTopColor, progressBottomColor, arrowBackground,
				arrowHoverBackground, arrowPressedBackground,
				arrowDisabledBackground, arrowColor, disabledArrowColor,
				cornerRadius, width, height, preferredWidth, arrowButtonWidth,
				arrowInset, arrowStrokeWidth, padding);
	}

	public GLSliderStyle withGeometry(float radius, float height, float width) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, textColor, disabledTextColor, borderColor,
				progressTopColor, progressBottomColor, arrowBackground,
				arrowHoverBackground, arrowPressedBackground,
				arrowDisabledBackground, arrowColor, disabledArrowColor,
				radius, borderWidth, height, width, arrowButtonWidth,
				arrowInset, arrowStrokeWidth, padding);
	}

	public GLSliderStyle withPadding(GLInsets value) {
		return copy(font, background, hoverBackground, pressedBackground,
				disabledBackground, textColor, disabledTextColor, borderColor,
				progressTopColor, progressBottomColor, arrowBackground,
				arrowHoverBackground, arrowPressedBackground,
				arrowDisabledBackground, arrowColor, disabledArrowColor,
				cornerRadius, borderWidth, height, preferredWidth,
				arrowButtonWidth, arrowInset, arrowStrokeWidth, value);
	}

	private static GLSliderStyle copy(GLFontSpec font,
			Color background, Color hoverBackground, Color pressedBackground,
			Color disabledBackground, Color textColor, Color disabledTextColor,
			Color borderColor, Color progressTopColor, Color progressBottomColor,
			Color arrowBackground, Color arrowHoverBackground,
			Color arrowPressedBackground, Color arrowDisabledBackground,
			Color arrowColor, Color disabledArrowColor, float cornerRadius,
			float borderWidth, float height, float preferredWidth,
			float arrowButtonWidth, float arrowInset, float arrowStrokeWidth,
			GLInsets padding) {
		return new GLSliderStyle(font, background, hoverBackground,
				pressedBackground, disabledBackground, textColor, disabledTextColor,
				borderColor, progressTopColor, progressBottomColor, arrowBackground,
				arrowHoverBackground, arrowPressedBackground,
				arrowDisabledBackground, arrowColor, disabledArrowColor,
				cornerRadius, borderWidth, height, preferredWidth,
				arrowButtonWidth, arrowInset, arrowStrokeWidth, padding);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
