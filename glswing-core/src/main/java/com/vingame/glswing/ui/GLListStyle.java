package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable colors, typography and geometry shared by list controls. */
public record GLListStyle(GLFontSpec font, Color background,
		Color rowBackground, Color rowHoverBackground, Color rowPressedBackground,
		Color selectionBackground, Color selectionInactiveBackground,
		Color textColor, Color selectedTextColor, Color disabledTextColor,
		Color borderColor, Color focusColor, float cornerRadius,
		float borderWidth, float rowHeight, float preferredWidth,
		GLInsets cellPadding) {
	public static final GLListStyle DEFAULT = fromTheme(GLTheme.DEFAULT);
	public GLListStyle {
		if (font == null || background == null || rowBackground == null
				|| rowHoverBackground == null || rowPressedBackground == null
				|| selectionBackground == null || selectionInactiveBackground == null
				|| textColor == null || selectedTextColor == null
				|| disabledTextColor == null || borderColor == null
				|| focusColor == null || cellPadding == null)
			throw new IllegalArgumentException("List style values must not be null");
		if (!nonNegative(cornerRadius) || !nonNegative(borderWidth)
				|| !positive(rowHeight) || !positive(preferredWidth))
			throw new IllegalArgumentException("List geometry must be finite and valid");
	}
	public static GLListStyle fromTheme(GLTheme theme) {
		if (theme == null) throw new IllegalArgumentException("Theme must not be null");
		var values = theme.values();
		var label = theme.labelStyle();
		Color selection = values.getColor("list.selectionBackground",
				values.getColor("palette.selection", theme.buttonStyle().focusColor()));
		return new GLListStyle(label.font(),
				values.getColor("list.background", theme.textEditorStyle().backgroundColor()),
				values.getColor("list.rowBackground", new Color(0, 0, 0, 0)),
				values.getColor("list.rowHoverBackground", theme.buttonStyle().hoverBackground()),
				values.getColor("list.rowPressedBackground",
						theme.buttonStyle().background(GLControlState.PRESSED)),
				selection,
				values.getColor("list.selectionInactiveBackground",
						new Color(selection.getRed(), selection.getGreen(), selection.getBlue(),
								Math.max(1, selection.getAlpha() * 2 / 3))),
				values.getColor("list.text", label.textColor()),
				values.getColor("list.selectedText", label.textColor()),
				values.getColor("list.textDisabled", theme.buttonStyle().disabledTextColor()),
				values.getColor("list.border", theme.buttonStyle().borderColor()),
				values.getColor("list.focus", theme.buttonStyle().focusColor()),
				values.getFloat("list.cornerRadius", values.getFloat("metrics.cornerRadius", 4)),
				values.getFloat("list.borderWidth", values.getFloat("metrics.borderWidth", 1)),
				values.getFloat("list.rowHeight", 28),
				values.getFloat("list.preferredWidth", 180),
				values.getInsets("list.cellPadding", new GLInsets(3, 8, 3, 8)));
	}
	public GLListStyle withFont(GLFontSpec value) {
		return copy(value, background, rowBackground, rowHoverBackground,
				rowPressedBackground, selectionBackground,
				selectionInactiveBackground, textColor, selectedTextColor,
				disabledTextColor, borderColor, focusColor, cornerRadius,
				borderWidth, rowHeight, preferredWidth, cellPadding);
	}
	public GLListStyle withRowHeight(float value) {
		return copy(font, background, rowBackground, rowHoverBackground,
				rowPressedBackground, selectionBackground,
				selectionInactiveBackground, textColor, selectedTextColor,
				disabledTextColor, borderColor, focusColor, cornerRadius,
				borderWidth, value, preferredWidth, cellPadding);
	}
	public GLListStyle withGeometry(float radius, float border,
			float height, float width, GLInsets padding) {
		return copy(font, background, rowBackground, rowHoverBackground,
				rowPressedBackground, selectionBackground,
				selectionInactiveBackground, textColor, selectedTextColor,
				disabledTextColor, borderColor, focusColor, radius, border,
				height, width, padding);
	}
	private static GLListStyle copy(GLFontSpec font, Color background,
			Color rowBackground, Color rowHoverBackground,
			Color rowPressedBackground, Color selectionBackground,
			Color selectionInactiveBackground, Color textColor,
			Color selectedTextColor, Color disabledTextColor,
			Color borderColor, Color focusColor, float cornerRadius,
			float borderWidth, float rowHeight, float preferredWidth,
			GLInsets cellPadding) {
		return new GLListStyle(font, background, rowBackground,
				rowHoverBackground, rowPressedBackground, selectionBackground,
				selectionInactiveBackground, textColor, selectedTextColor,
				disabledTextColor, borderColor, focusColor, cornerRadius,
				borderWidth, rowHeight, preferredWidth, cellPadding);
	}
	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}
	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
