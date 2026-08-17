package com.vingame.glswing.node;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeValues;

/** Immutable visual, spacing and interaction geometry for a {@link GLNode}. */
public record GLNodeStyle(GLFontSpec titleFont, Color background,
		Color titleBackground, Color titleGradientLight,
		Color titleGradientDark, Color titleText, Color border,
		Color selectedBorder, Color activeSelectedBorder, Color shadow, Color inputPort,
		Color outputPort, Color portBorder, Color portHover,
		float cornerRadius, float borderWidth, float titleHeight,
		float shadowSoftness, float shadowOffsetX, float shadowOffsetY,
		float portRadius, float rowGap, float minimumWidth,
		float resizeHandleSize, GLInsets contentPadding) {

	public GLNodeStyle {
		if (titleFont == null || background == null || titleBackground == null
				|| titleGradientLight == null || titleGradientDark == null
				|| titleText == null || border == null || selectedBorder == null
				|| activeSelectedBorder == null
				|| shadow == null || inputPort == null || outputPort == null
				|| portBorder == null || portHover == null || contentPadding == null)
			throw new IllegalArgumentException("Node style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !positive(titleHeight) || !nonNegative(shadowSoftness)
				|| !Float.isFinite(shadowOffsetX) || !Float.isFinite(shadowOffsetY)
				|| !positive(portRadius) || !nonNegative(rowGap)
				|| !positive(minimumWidth) || !positive(resizeHandleSize))
			throw new IllegalArgumentException(
					"Node style geometry must be finite and valid");
	}

	/** Source-compatible constructor for styles without an active border color. */
	public GLNodeStyle(GLFontSpec titleFont, Color background,
			Color titleBackground, Color titleGradientLight,
			Color titleGradientDark, Color titleText, Color border,
			Color selectedBorder, Color shadow, Color inputPort,
			Color outputPort, Color portBorder, Color portHover,
			float cornerRadius, float borderWidth, float titleHeight,
			float shadowSoftness, float shadowOffsetX, float shadowOffsetY,
			float portRadius, float rowGap, float minimumWidth,
			float resizeHandleSize, GLInsets contentPadding) {
		this(titleFont, background, titleBackground, titleGradientLight,
				titleGradientDark, titleText, border, selectedBorder,
				selectedBorder, shadow, inputPort, outputPort, portBorder,
				portHover, cornerRadius, borderWidth, titleHeight,
				shadowSoftness, shadowOffsetX, shadowOffsetY, portRadius,
				rowGap, minimumWidth, resizeHandleSize, contentPadding);
	}

	/** Source-compatible constructor for styles without title-gradient colors. */
	public GLNodeStyle(GLFontSpec titleFont, Color background,
			Color titleBackground, Color titleText, Color border,
			Color selectedBorder, Color shadow, Color inputPort,
			Color outputPort, Color portBorder, Color portHover,
			float cornerRadius, float borderWidth, float titleHeight,
			float shadowSoftness, float shadowOffsetX, float shadowOffsetY,
			float portRadius, float rowGap, float minimumWidth,
			float resizeHandleSize, GLInsets contentPadding) {
		this(titleFont, background, titleBackground, titleBackground,
				titleBackground, titleText, border, selectedBorder,
				selectedBorder, shadow,
				inputPort, outputPort, portBorder, portHover, cornerRadius,
				borderWidth, titleHeight, shadowSoftness, shadowOffsetX,
				shadowOffsetY, portRadius, rowGap, minimumWidth,
				resizeHandleSize, contentPadding);
	}

	public GLNodeStyle withTitleBackground(Color color) {
		return copy(color, titleGradientLight, titleGradientDark);
	}

	public GLNodeStyle withTitleGradient(Color light, Color dark) {
		return copy(titleBackground, light, dark);
	}

	public static GLNodeStyle fromTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("Theme must not be null");
		GLThemeValues values = theme.values();
		Color surface = values.getColor("palette.surface",
				theme.buttonStyle().background());
		Color normal = values.getColor("state.normal",
				theme.buttonStyle().background());
		Color text = values.getColor("palette.text",
				theme.buttonStyle().textColor());
		Color border = values.getColor("palette.border",
				theme.buttonStyle().borderColor());
		Color accent = values.getColor("palette.accent",
				theme.buttonStyle().focusColor());
		return new GLNodeStyle(theme.buttonStyle().font(),
				values.getColor("node.background", surface),
				values.getColor("node.titleBackground", normal),
				values.getColor("node.titleGradientLight",
						values.getColor("node.titleBackground", normal)),
				values.getColor("node.titleGradientDark",
						values.getColor("node.titleBackground", normal)),
				values.getColor("node.titleText", text),
				values.getColor("node.border", border),
				values.getColor("node.selectedBorder", accent),
				values.getColor("node.activeSelectedBorder",
						values.getColor("node.selectedBorder", accent)),
				values.getColor("node.shadow", new Color(0, 0, 0, 105)),
				values.getColor("node.inputPort", new Color(112, 174, 232)),
				values.getColor("node.outputPort", new Color(237, 157, 91)),
				values.getColor("node.portBorder", border),
				values.getColor("node.portHover", text),
				values.getFloat("node.cornerRadius", 9),
				values.getFloat("node.borderWidth", 1.5f),
				values.getFloat("node.titleHeight", 30),
				values.getFloat("node.shadowSoftness", 10),
				values.getFloat("node.shadowOffsetX", 0),
				values.getFloat("node.shadowOffsetY", 4),
				values.getFloat("node.portRadius", 5),
				values.getFloat("node.rowGap", 9),
				values.getFloat("node.minimumWidth", 190),
				values.getFloat("node.resizeHandleSize", 7),
				values.getInsets("node.contentPadding",
						new GLInsets(11, 12, 12, 12)));
	}

	private GLNodeStyle copy(Color titleBackground, Color titleGradientLight,
			Color titleGradientDark) {
		return new GLNodeStyle(titleFont, background, titleBackground,
				titleGradientLight, titleGradientDark, titleText, border,
				selectedBorder, activeSelectedBorder, shadow, inputPort,
				outputPort, portBorder,
				portHover, cornerRadius, borderWidth, titleHeight,
				shadowSoftness, shadowOffsetX, shadowOffsetY, portRadius,
				rowGap, minimumWidth, resizeHandleSize, contentPadding);
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}
}
