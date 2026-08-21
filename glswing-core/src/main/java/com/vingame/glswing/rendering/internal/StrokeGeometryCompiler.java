package com.vingame.glswing.rendering.internal;

import java.awt.Color;

import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;

/** Compiles stroked paths to the same normalized triangles used by filled shapes. */
public final class StrokeGeometryCompiler {

	private StrokeGeometryCompiler() {
	}

	public static float[] line(float x1, float y1, float x2, float y2,
			GLStroke stroke, Color color, GLTransform modelTransform,
			GLTransform worldToScreen, GLTransform screenToWorld) {
		if (stroke.getWidthSpace() == GLStroke.WidthSpace.WORLD) {
			float[] vertices = StrokeTessellator.tessellateLine(
					x1, y1, x2, y2,
					strokeForTessellation(stroke, modelTransform, worldToScreen), color);
			modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
			return vertices;
		}

		float[] points = transformPointsToScreen(
				new float[] { x1, y1, x2, y2 }, modelTransform, worldToScreen);
		float[] vertices = StrokeTessellator.tessellateLine(
				points[0], points[1], points[2], points[3], stroke, color);
		screenToWorld.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	public static float[] line(float x1, float y1, Color color1,
			float x2, float y2, Color color2, GLStroke stroke,
			GLTransform modelTransform, GLTransform worldToScreen,
			GLTransform screenToWorld) {
		if (stroke.getWidthSpace() == GLStroke.WidthSpace.WORLD) {
			float[] vertices = StrokeTessellator.tessellateLine(
					x1, y1, color1, x2, y2, color2,
					strokeForTessellation(stroke, modelTransform, worldToScreen));
			modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
			return vertices;
		}

		float[] points = transformPointsToScreen(
				new float[] { x1, y1, x2, y2 }, modelTransform, worldToScreen);
		float[] vertices = StrokeTessellator.tessellateLine(
				points[0], points[1], color1, points[2], points[3], color2, stroke);
		screenToWorld.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	public static float[] path(float[] points, boolean closed, GLStroke stroke,
			Color color, GLTransform modelTransform, GLTransform worldToScreen,
			GLTransform screenToWorld) {
		if (stroke.getWidthSpace() == GLStroke.WidthSpace.WORLD) {
			GLStroke compiledStroke = strokeForTessellation(
					stroke, modelTransform, worldToScreen);
			float[] vertices = closed
					? StrokeTessellator.tessellatePolygon(points, compiledStroke, color)
					: StrokeTessellator.tessellatePolyline(points, compiledStroke, color);
			modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
			return vertices;
		}

		float[] screenPoints = transformPointsToScreen(points, modelTransform, worldToScreen);
		float[] vertices = closed
				? StrokeTessellator.tessellatePolygon(screenPoints, stroke, color)
				: StrokeTessellator.tessellatePolyline(screenPoints, stroke, color);
		screenToWorld.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	public static float[] path(float[] points, boolean closed, GLStroke stroke,
			Color[] colors, GLTransform modelTransform, GLTransform worldToScreen,
			GLTransform screenToWorld) {
		if (stroke.getWidthSpace() == GLStroke.WidthSpace.WORLD) {
			GLStroke compiledStroke = strokeForTessellation(
					stroke, modelTransform, worldToScreen);
			float[] vertices = closed
					? StrokeTessellator.tessellatePolygon(points, compiledStroke, colors)
					: StrokeTessellator.tessellatePolyline(points, compiledStroke, colors);
			modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
			return vertices;
		}

		float[] screenPoints = transformPointsToScreen(points, modelTransform, worldToScreen);
		float[] vertices = closed
				? StrokeTessellator.tessellatePolygon(screenPoints, stroke, colors)
				: StrokeTessellator.tessellatePolyline(screenPoints, stroke, colors);
		screenToWorld.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	private static float[] transformPointsToScreen(float[] points,
			GLTransform modelTransform, GLTransform worldToScreen) {
		if (points == null)
			throw new IllegalArgumentException("Stroke points must not be null");
		float[] screenPoints = points.clone();
		worldToScreen.copy().concatenate(modelTransform)
				.transform(screenPoints, 0, screenPoints.length / 2);
		return screenPoints;
	}

	private static GLStroke strokeForTessellation(GLStroke stroke,
			GLTransform modelTransform, GLTransform worldToScreen) {
		if (stroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN
				|| stroke.getCap() != GLStroke.Cap.ROUND
						&& stroke.getJoin() != GLStroke.Join.ROUND)
			return stroke;

		float maximumScale = worldToScreen.copy().concatenate(modelTransform)
				.getMaximumScale();
		float worldTolerance;
		if (!(maximumScale > 0)) {
			worldTolerance = Float.MAX_VALUE;
		} else {
			worldTolerance = stroke.getCurveTolerance() / maximumScale;
			if (!(worldTolerance > 0))
				worldTolerance = Float.MIN_NORMAL;
		}
		return stroke.withCurveTolerance(worldTolerance);
	}
}
