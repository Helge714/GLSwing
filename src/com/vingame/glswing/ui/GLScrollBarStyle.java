package com.vingame.glswing.ui;

import java.awt.Color;

/** Immutable visual configuration for horizontal and vertical scrollbars. */
public record GLScrollBarStyle(Color track, Color thumb, Color thumbHover,
		Color thumbPressed, Color thumbDisabled, float thickness,
		float minimumThumbLength, float trackPadding, float cornerRadius) {

	public GLScrollBarStyle {
		if (track == null || thumb == null || thumbHover == null
				|| thumbPressed == null || thumbDisabled == null)
			throw new IllegalArgumentException("Scrollbar colors must not be null");
		if (!finiteNonNegative(thickness) || thickness <= 0
				|| !finiteNonNegative(minimumThumbLength)
				|| !finiteNonNegative(trackPadding)
				|| !finiteNonNegative(cornerRadius))
			throw new IllegalArgumentException(
					"Scrollbar geometry must be finite and non-negative");
	}

	public static GLScrollBarStyle fromTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("Theme must not be null");
		GLThemeValues values = theme.values();
		return new GLScrollBarStyle(
				values.getColor("scrollBar.track", theme.menuStyle().separatorColor()),
				values.getColor("scrollBar.thumb", theme.menuStyle().acceleratorColor()),
				values.getColor("scrollBar.thumbHover", theme.menuStyle().textColor()),
				values.getColor("scrollBar.thumbPressed",
						theme.buttonStyle().pressedBackground()),
				values.getColor("scrollBar.thumbDisabled",
						theme.buttonStyle().disabledBackground()),
				values.getFloat("scrollBar.thickness", 9),
				values.getFloat("scrollBar.minimumThumbLength", 18),
				values.getFloat("scrollBar.trackPadding", 2),
				values.getFloat("scrollBar.cornerRadius", 4));
	}

	private static boolean finiteNonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
