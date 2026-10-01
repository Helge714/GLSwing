package com.vingame.glswing.node;

import java.awt.Color;

import com.vingame.glswing.rendering.GLColorInterpolation;
import com.vingame.glswing.rendering.GLColorInterpolator;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeValues;

/** Immutable appearance and interaction geometry for a {@link GLNodeEditor}. */
public record GLNodeEditorStyle(Color background, Color minorGridColor,
		Color majorGridColor, boolean gridVisible, float gridSpacing,
		int majorGridInterval, float gridLineWidth,
		Color connectionBorder, Color selectedConnectionBorder,
		Color marqueeFill, Color marqueeBorder,
		GLColorInterpolator connectionColorInterpolator,
		float connectionSnapDistance, float connectionCurveTolerance,
		float connectionHandleFactor, float minimumConnectionHandleLength,
		float connectionWidth, float connectionBorderWidth,
		float marqueeBorderWidth) {

	public GLNodeEditorStyle {
		if (background == null || minorGridColor == null || majorGridColor == null
				|| connectionBorder == null
				|| selectedConnectionBorder == null || marqueeFill == null
				|| marqueeBorder == null || connectionColorInterpolator == null)
			throw new IllegalArgumentException(
					"Node editor colors and interpolation must not be null");
		if (!positive(gridSpacing) || majorGridInterval < 1
				|| !positive(gridLineWidth)
				|| !nonNegative(connectionSnapDistance)
				|| !positive(connectionCurveTolerance)
				|| !nonNegative(connectionHandleFactor)
				|| !nonNegative(minimumConnectionHandleLength)
				|| !positive(connectionWidth)
				|| !nonNegative(connectionBorderWidth)
				|| !positive(marqueeBorderWidth))
			throw new IllegalArgumentException(
					"Node editor connection geometry must be finite and valid");
	}

	public static GLNodeEditorStyle fromTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("Theme must not be null");
		GLThemeValues values = theme.values();
		Color fallback = values.getColor("palette.surface",
				theme.menuStyle().background());
		return new GLNodeEditorStyle(
				values.getColor("nodeEditor.background", fallback),
				values.getColor("nodeEditor.gridMinor"),
				values.getColor("nodeEditor.gridMajor"),
				values.getBoolean("nodeEditor.gridVisible", true),
				values.getFloat("nodeEditor.gridSpacing", 24),
				Math.max(1, Math.round(values.getFloat("nodeEditor.gridMajorInterval", 4))),
				values.getFloat("nodeEditor.gridLineWidth", 1),
				values.getColor("nodeEditor.connectionBorder",
						values.getColor("palette.border")),
				values.getColor("nodeEditor.selectedConnectionBorder",
						values.getColor("palette.accent")),
				values.getColor("nodeEditor.marqueeFill",
						values.getColor("palette.selection")),
				values.getColor("nodeEditor.marqueeBorder",
						values.getColor("palette.accent")),
				GLColorInterpolation.parse(values.get(
						"nodeEditor.connectionColorInterpolation", "srgb")),
				values.getFloat("nodeEditor.connectionSnapDistance", 18),
				values.getFloat("nodeEditor.connectionCurveTolerance",
						GLStroke.CURVE_TOLERANCE_NORMAL),
				values.getFloat("nodeEditor.connectionHandleFactor", 0.5f),
				values.getFloat("nodeEditor.minimumConnectionHandleLength", 45),
				values.getFloat("nodeEditor.connectionWidth", 3),
				values.getFloat("nodeEditor.connectionBorderWidth", 1),
				values.getFloat("nodeEditor.marqueeBorderWidth", 1));
	}

	public GLNodeEditorStyle withBackground(Color color) {
		return new GLNodeEditorStyle(color, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionColorInterpolator,
				connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, connectionWidth,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionGeometry(float snapDistance,
			float curveTolerance, float handleFactor, float minimumHandleLength,
			float width) {
		return new GLNodeEditorStyle(background, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionColorInterpolator,
				snapDistance, curveTolerance,
				handleFactor, minimumHandleLength, width,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionWidth(float width) {
		return new GLNodeEditorStyle(background, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionColorInterpolator,
				connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, width,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionBorders(Color border,
			Color selectedBorder, float width) {
		return new GLNodeEditorStyle(background, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth,
				border, selectedBorder,
				marqueeFill, marqueeBorder, connectionColorInterpolator,
				connectionSnapDistance,
				connectionCurveTolerance,
				connectionHandleFactor, minimumConnectionHandleLength,
				connectionWidth, width, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withMarquee(Color fill, Color border,
			float borderWidth) {
		return new GLNodeEditorStyle(background, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth, connectionBorder,
				selectedConnectionBorder, fill, border,
				connectionColorInterpolator,
				connectionSnapDistance, connectionCurveTolerance,
				connectionHandleFactor, minimumConnectionHandleLength,
				connectionWidth, connectionBorderWidth, borderWidth);
	}

	public GLNodeEditorStyle withConnectionColorInterpolator(
			GLColorInterpolator interpolation) {
		return new GLNodeEditorStyle(background, minorGridColor, majorGridColor,
				gridVisible, gridSpacing, majorGridInterval, gridLineWidth, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				interpolation, connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, connectionWidth,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withGrid(boolean visible, float spacing,
			int majorInterval, float lineWidth, Color minorColor, Color majorColor) {
		return new GLNodeEditorStyle(background, minorColor, majorColor, visible,
				spacing, majorInterval, lineWidth, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionColorInterpolator, connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, connectionWidth,
				connectionBorderWidth, marqueeBorderWidth);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
