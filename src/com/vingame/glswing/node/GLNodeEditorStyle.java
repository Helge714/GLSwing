package com.vingame.glswing.node;

import java.awt.Color;

import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeValues;

/** Immutable appearance and interaction geometry for a {@link GLNodeEditor}. */
public record GLNodeEditorStyle(Color background,
		Color connectionBorder, Color selectedConnectionBorder,
		Color marqueeFill, Color marqueeBorder,
		float connectionSnapDistance, float connectionCurveTolerance,
		float connectionHandleFactor, float minimumConnectionHandleLength,
		float connectionWidth, float connectionBorderWidth,
		float marqueeBorderWidth) {

	public GLNodeEditorStyle {
		if (background == null || connectionBorder == null
				|| selectedConnectionBorder == null || marqueeFill == null
				|| marqueeBorder == null)
			throw new IllegalArgumentException(
					"Node editor colors must not be null");
		if (!nonNegative(connectionSnapDistance)
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
				values.getColor("nodeEditor.connectionBorder",
						values.getColor("palette.border", Color.DARK_GRAY)),
				values.getColor("nodeEditor.selectedConnectionBorder",
						values.getColor("palette.accent", Color.WHITE)),
				values.getColor("nodeEditor.marqueeFill",
						values.getColor("palette.selection",
								new Color(80, 120, 190, 70))),
				values.getColor("nodeEditor.marqueeBorder",
						values.getColor("palette.accent", Color.WHITE)),
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
		return new GLNodeEditorStyle(color, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, connectionWidth,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionGeometry(float snapDistance,
			float curveTolerance, float handleFactor, float minimumHandleLength,
			float width) {
		return new GLNodeEditorStyle(background, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				snapDistance, curveTolerance,
				handleFactor, minimumHandleLength, width,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionWidth(float width) {
		return new GLNodeEditorStyle(background, connectionBorder,
				selectedConnectionBorder, marqueeFill, marqueeBorder,
				connectionSnapDistance,
				connectionCurveTolerance, connectionHandleFactor,
				minimumConnectionHandleLength, width,
				connectionBorderWidth, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withConnectionBorders(Color border,
			Color selectedBorder, float width) {
		return new GLNodeEditorStyle(background, border, selectedBorder,
				marqueeFill, marqueeBorder, connectionSnapDistance,
				connectionCurveTolerance,
				connectionHandleFactor, minimumConnectionHandleLength,
				connectionWidth, width, marqueeBorderWidth);
	}

	public GLNodeEditorStyle withMarquee(Color fill, Color border,
			float borderWidth) {
		return new GLNodeEditorStyle(background, connectionBorder,
				selectedConnectionBorder, fill, border,
				connectionSnapDistance, connectionCurveTolerance,
				connectionHandleFactor, minimumConnectionHandleLength,
				connectionWidth, connectionBorderWidth, borderWidth);
	}

	private static boolean positive(float value) {
		return Float.isFinite(value) && value > 0;
	}

	private static boolean nonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}
