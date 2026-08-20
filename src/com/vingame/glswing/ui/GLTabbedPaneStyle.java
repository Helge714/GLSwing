package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable visual and geometry configuration for {@link GLTabbedPane}. */
public record GLTabbedPaneStyle(GLFontSpec font, Color background,
		Color tabBarBackground, Color tab, Color tabHover, Color tabPressed,
		Color tabSelected, Color tabDisabled, Color text, Color textDisabled,
		Color border, float borderWidth, float cornerRadius, float tabHeight,
		float minimumTabWidth, float maximumTabWidth, float tabGap,
		float tabHorizontalPadding, GLInsets contentPadding) {

	public GLTabbedPaneStyle {
		if (font == null || background == null || tabBarBackground == null
				|| tab == null || tabHover == null || tabPressed == null
				|| tabSelected == null || tabDisabled == null || text == null
				|| textDisabled == null || border == null || contentPadding == null)
			throw new IllegalArgumentException("Tabbed-pane style values must not be null");
		if (!nonNegative(borderWidth) || !nonNegative(cornerRadius)
				|| !positive(tabHeight) || !positive(minimumTabWidth)
				|| !positive(maximumTabWidth) || maximumTabWidth < minimumTabWidth
				|| !nonNegative(tabGap) || !nonNegative(tabHorizontalPadding))
			throw new IllegalArgumentException("Tabbed-pane geometry is invalid");
	}

	public static GLTabbedPaneStyle fromTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("Theme must not be null");
		GLThemeValues values = theme.values();
		return new GLTabbedPaneStyle(theme.buttonStyle().font(),
				values.getColor("tabbedPane.background", theme.menuStyle().background()),
				values.getColor("tabbedPane.tabBarBackground",
						theme.menuBarStyle().background()),
				values.getColor("tabbedPane.tab", theme.buttonStyle().background()),
				values.getColor("tabbedPane.tabHover",
						theme.buttonStyle().hoverBackground()),
				values.getColor("tabbedPane.tabPressed",
						theme.buttonStyle().pressedDarkColor()),
				values.getColor("tabbedPane.tabSelected",
						theme.menuBarStyle().activeBackground()),
				values.getColor("tabbedPane.tabDisabled",
						theme.buttonStyle().disabledBackground()),
				values.getColor("tabbedPane.text", theme.buttonStyle().textColor()),
				values.getColor("tabbedPane.textDisabled",
						theme.buttonStyle().disabledTextColor()),
				values.getColor("tabbedPane.border", theme.buttonStyle().borderColor()),
				values.getFloat("tabbedPane.borderWidth",
						theme.buttonStyle().borderWidth()),
				values.getFloat("tabbedPane.cornerRadius",
						theme.buttonStyle().cornerRadius()),
				values.getFloat("tabbedPane.tabHeight", 28),
				values.getFloat("tabbedPane.minimumTabWidth", 72),
				values.getFloat("tabbedPane.maximumTabWidth", 220),
				values.getFloat("tabbedPane.tabGap", 2),
				values.getFloat("tabbedPane.tabHorizontalPadding", 12),
				values.getInsets("tabbedPane.contentPadding",
						new GLInsets(8, 8, 8, 8)));
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}
}
