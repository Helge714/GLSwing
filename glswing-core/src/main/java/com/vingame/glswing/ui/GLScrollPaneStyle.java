package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;

/** Immutable visual and spacing configuration for {@link GLScrollPane}. */
public record GLScrollPaneStyle(Color background, Color border,
		float borderWidth, float cornerRadius, float scrollBarGap,
		GLInsets padding, Color viewportBorder, float viewportBorderWidth) {

	public GLScrollPaneStyle {
		if (background == null || border == null || padding == null
				|| viewportBorder == null)
			throw new IllegalArgumentException("Scroll-pane style values must not be null");
		if (!finiteNonNegative(borderWidth) || !finiteNonNegative(cornerRadius)
				|| !finiteNonNegative(scrollBarGap)
				|| !finiteNonNegative(viewportBorderWidth))
			throw new IllegalArgumentException(
					"Scroll-pane geometry must be finite and non-negative");
	}

	/** Creates a style without a separate viewport border. */
	public GLScrollPaneStyle(Color background, Color border,
			float borderWidth, float cornerRadius, float scrollBarGap,
			GLInsets padding) {
		this(background, border, borderWidth, cornerRadius, scrollBarGap,
				padding, background, 0);
	}

	public static GLScrollPaneStyle fromTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("Theme must not be null");
		GLThemeValues values = theme.values();
		return new GLScrollPaneStyle(
				values.getColor("scrollPane.background", theme.menuStyle().background()),
				values.getColor("scrollPane.border", theme.menuStyle().borderColor()),
				values.getFloat("scrollPane.borderWidth",
						theme.menuStyle().borderWidth()),
				values.getFloat("scrollPane.cornerRadius",
						theme.menuStyle().cornerRadius()),
				values.getFloat("scrollPane.scrollBarGap", 2),
				values.getInsets("scrollPane.padding",
						new GLInsets(4, 4, 4, 4)),
				values.getColor("scrollPane.viewportBorder",
						values.getColor("list.background",
								theme.textEditorStyle().backgroundColor())),
				values.getFloat("scrollPane.viewportBorderWidth", 1));
	}

	private static boolean finiteNonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
