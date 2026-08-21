package com.vingame.glswing.rendering;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vingame.glswing.rendering.GLPath2D.Subpath;
import com.vingame.glswing.rendering.internal.FilledVertexFormat;
import com.vingame.glswing.rendering.internal.PolygonTessellator;
import com.vingame.glswing.rendering.internal.StrokeGeometryCompiler;

/** Shared view-aware compiler for filled and stroked paths. */
final class PathGeometryCompiler {

	private PathGeometryCompiler() {
	}

	static float[] fill(GLPath2D path, Color color, float curveTolerance,
			GLTransform modelTransform, GLTransform worldToScreen) {
		validate(path, color, curveTolerance, modelTransform, worldToScreen);
		float[] vertices = fillGeometry(path, curveTolerance,
				modelTransform, worldToScreen, color);
		modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	static float[] fill(GLPath2D path, GLPaint2D paint, float curveTolerance,
			GLTransform modelTransform, GLTransform worldToScreen) {
		if (paint == null)
			throw new IllegalArgumentException("Path paint must not be null");
		validate(path, Color.WHITE, curveTolerance, modelTransform, worldToScreen);
		float[] vertices = fillGeometry(path, curveTolerance,
				modelTransform, worldToScreen, Color.WHITE);
		for (int vertex = 0; vertex < vertices.length;
				vertex += FilledVertexFormat.FLOATS_PER_VERTEX) {
			Color color = paint.colorAt(vertices[vertex], vertices[vertex + 1]);
			if (color == null)
				throw new IllegalArgumentException("Path paint returned null");
			vertices[vertex + 2] = color.getRed() / 255f;
			vertices[vertex + 3] = color.getGreen() / 255f;
			vertices[vertex + 4] = color.getBlue() / 255f;
			vertices[vertex + 5] = color.getAlpha() / 255f;
		}
		modelTransform.transformVertices(vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		return vertices;
	}

	private static float[] fillGeometry(GLPath2D path, float curveTolerance,
			GLTransform modelTransform, GLTransform worldToScreen, Color color) {
		GLTransform localToScreen = worldToScreen.copy().concatenate(modelTransform);
		List<float[]> contours = new ArrayList<>();
		for (Subpath subpath : path.flatten(localToScreen, curveTolerance).subpaths()) {
			float[] contour = withoutRepeatedFillEndpoint(subpath.points());
			if (contour.length >= 6)
				contours.add(contour);
		}
		if (contours.isEmpty())
			return new float[0];
		float[] vertices = PolygonTessellator.tessellateContours(
				contours, color, path.getFillRule());
		return vertices;
	}

	private static float[] withoutRepeatedFillEndpoint(float[] points) {
		int last = points.length - 2;
		if (last >= 2 && points[0] == points[last]
				&& points[1] == points[last + 1])
			return Arrays.copyOf(points, last);
		return points;
	}

	static float[] stroke(GLPath2D path, GLStroke stroke, Color color,
			float curveTolerance, GLTransform modelTransform,
			GLTransform worldToScreen, GLTransform screenToWorld) {
		validate(path, color, curveTolerance, modelTransform, worldToScreen);
		if (stroke == null)
			throw new IllegalArgumentException("Path stroke must not be null");
		if (screenToWorld == null)
			throw new IllegalArgumentException("Path screen-to-world transform is missing");
		GLTransform localToScreen = worldToScreen.copy().concatenate(modelTransform);
		List<float[]> geometries = new ArrayList<>();
		int resultLength = 0;
		for (Subpath subpath : path.flatten(localToScreen, curveTolerance).subpaths()) {
			float[] points = subpath.points();
			if (points.length < 4)
				continue;
			boolean closed = subpath.closed() && points.length >= 6;
			float[] geometry = StrokeGeometryCompiler.path(points, closed,
					stroke, color, modelTransform, worldToScreen, screenToWorld);
			if (geometry.length == 0)
				continue;
			geometries.add(geometry);
			resultLength += geometry.length;
		}
		return concatenate(geometries, resultLength);
	}

	private static float[] concatenate(List<float[]> geometries, int resultLength) {
		float[] result = new float[resultLength];
		int offset = 0;
		for (float[] geometry : geometries) {
			System.arraycopy(geometry, 0, result, offset, geometry.length);
			offset += geometry.length;
		}
		return result;
	}

	private static void validate(GLPath2D path, Color color, float curveTolerance,
			GLTransform modelTransform, GLTransform worldToScreen) {
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");
		if (color == null)
			throw new IllegalArgumentException("Path color must not be null");
		if (!Float.isFinite(curveTolerance) || curveTolerance <= 0)
			throw new IllegalArgumentException("Path curve tolerance must be positive");
		if (modelTransform == null || worldToScreen == null)
			throw new IllegalArgumentException("Path transforms must not be null");
	}
}
