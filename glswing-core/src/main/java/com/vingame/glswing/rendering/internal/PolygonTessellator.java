package com.vingame.glswing.rendering.internal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import com.vingame.glswing.rendering.GLPath2D;

/** Converts possibly concave or self-intersecting polygons to colored triangles. */
public final class PolygonTessellator {

	private PolygonTessellator() {
	}

	public static float[] tessellate(float[] points, Color color) {
		if (color == null)
			throw new IllegalArgumentException("Polygon color must not be null");
		validatePoints(points);
		Color[] colors = new Color[points.length / 2];
		java.util.Arrays.fill(colors, color);
		return tessellateValidated(points, colors);
	}

	public static float[] tessellate(float[] points, Color[] colors) {
		validatePoints(points);
		validateColors(colors, points.length / 2);
		return tessellateValidated(points, colors);
	}

	/** Tessellates multiple uniformly colored contours using the non-zero winding rule. */
	public static float[] tessellateContours(List<float[]> contours, Color color) {
		return tessellateContours(contours, color, GLPath2D.FillRule.NON_ZERO);
	}

	/** Tessellates uniformly colored contours using the selected winding rule. */
	public static float[] tessellateContours(List<float[]> contours, Color color,
			GLPath2D.FillRule fillRule) {
		if (contours == null || contours.isEmpty())
			throw new IllegalArgumentException("Polygon contours must not be empty");
		if (color == null)
			throw new IllegalArgumentException("Polygon color must not be null");
		if (fillRule == null)
			throw new IllegalArgumentException("Polygon fill rule must not be null");

		List<Vertex[]> vertexContours = new ArrayList<>(contours.size());
		for (float[] contour : contours) {
			validatePoints(contour);
			Vertex[] vertices = new Vertex[contour.length / 2];
			for (int i = 0; i < vertices.length; i++)
				vertices[i] = new Vertex(contour[i * 2], contour[i * 2 + 1], color);
			vertexContours.add(vertices);
		}
		return tessellateVertexContours(vertexContours, windingRule(fillRule));
	}

	/** Tessellates multiple contours with one interpolated color per contour point. */
	public static float[] tessellateContours(List<float[]> contours, List<Color[]> colors) {
		if (contours == null || contours.isEmpty())
			throw new IllegalArgumentException("Polygon contours must not be empty");
		if (colors == null || colors.size() != contours.size())
			throw new IllegalArgumentException("Polygon contours require one color array per contour");

		List<Vertex[]> vertexContours = new ArrayList<>(contours.size());
		for (int contourIndex = 0; contourIndex < contours.size(); contourIndex++) {
			float[] contour = contours.get(contourIndex);
			Color[] contourColors = colors.get(contourIndex);
			validatePoints(contour);
			validateColors(contourColors, contour.length / 2);
			Vertex[] vertices = new Vertex[contourColors.length];
			for (int i = 0; i < vertices.length; i++)
				vertices[i] = new Vertex(contour[i * 2], contour[i * 2 + 1], contourColors[i]);
			vertexContours.add(vertices);
		}
		return tessellateVertexContours(vertexContours, GLU.GLU_TESS_WINDING_NONZERO);
	}

	private static float[] tessellateValidated(float[] points, Color[] colors) {
		Vertex[] vertices = new Vertex[colors.length];
		for (int i = 0; i < colors.length; i++)
			vertices[i] = new Vertex(points[i * 2], points[i * 2 + 1], colors[i]);
		return tessellateVertexContours(List.<Vertex[]>of(vertices), GLU.GLU_TESS_WINDING_ODD);
	}

	private static int windingRule(GLPath2D.FillRule fillRule) {
		return fillRule == GLPath2D.FillRule.EVEN_ODD
				? GLU.GLU_TESS_WINDING_ODD : GLU.GLU_TESS_WINDING_NONZERO;
	}

	private static float[] tessellateVertexContours(List<Vertex[]> contours, int windingRule) {
		TessellationCallback callback = new TessellationCallback();
		GLUtessellator tessellator = GLU.gluNewTess();
		if (tessellator == null)
			throw new IllegalStateException("Could not create the GLU polygon tessellator");

		try {
			GLU.gluTessCallback(tessellator, GLU.GLU_TESS_BEGIN, callback);
			GLU.gluTessCallback(tessellator, GLU.GLU_TESS_VERTEX, callback);
			GLU.gluTessCallback(tessellator, GLU.GLU_TESS_END, callback);
			GLU.gluTessCallback(tessellator, GLU.GLU_TESS_ERROR, callback);
			GLU.gluTessCallback(tessellator, GLU.GLU_TESS_COMBINE, callback);
			GLU.gluTessProperty(tessellator, GLU.GLU_TESS_WINDING_RULE, windingRule);
			GLU.gluTessNormal(tessellator, 0, 0, 1);

			GLU.gluTessBeginPolygon(tessellator, null);
			for (Vertex[] contour : contours) {
				GLU.gluTessBeginContour(tessellator);
				for (Vertex vertex : contour)
					GLU.gluTessVertex(tessellator, vertex.coordinates, 0, vertex);
				GLU.gluTessEndContour(tessellator);
			}
			GLU.gluTessEndPolygon(tessellator);
		} finally {
			GLU.gluDeleteTess(tessellator);
		}

		float[] result = callback.triangleData();
		if (result.length == 0)
			throw new IllegalArgumentException("Polygon has no filled area");
		return result;
	}

	private static void validatePoints(float[] points) {
		if (points == null)
			throw new IllegalArgumentException("Polygon points must not be null");
		if (points.length < 6 || points.length % 2 != 0)
			throw new IllegalArgumentException("A polygon requires at least three x/y coordinate pairs");
		for (float value : points) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Polygon coordinates must be finite");
		}

		int last = points.length - 2;
		if (points[0] == points[last] && points[1] == points[last + 1])
			throw new IllegalArgumentException("Do not repeat the first polygon point at the end");
	}

	private static void validateColors(Color[] colors, int pointCount) {
		if (colors == null)
			throw new IllegalArgumentException("Polygon colors must not be null");
		if (colors.length != pointCount)
			throw new IllegalArgumentException("A polygon requires exactly one color per point");
		for (Color color : colors) {
			if (color == null)
				throw new IllegalArgumentException("Polygon colors must not contain null");
		}
	}

	private static final class TessellationCallback extends GLUtessellatorCallbackAdapter {
		private final List<Vertex> triangles = new ArrayList<>();
		private final List<Vertex> primitive = new ArrayList<>();
		private int primitiveType;

		@Override
		public void begin(int type) {
			primitiveType = type;
			primitive.clear();
		}

		@Override
		public void vertex(Object vertexData) {
			primitive.add((Vertex) vertexData);
		}

		@Override
		public void end() {
			switch (primitiveType) {
			case GL.GL_TRIANGLES -> emitTriangles();
			case GL.GL_TRIANGLE_FAN -> emitTriangleFan();
			case GL.GL_TRIANGLE_STRIP -> emitTriangleStrip();
			default -> throw new IllegalStateException("Unsupported tessellator primitive: " + primitiveType);
			}
		}

		@Override
		public void combine(double[] coordinates, Object[] inputData, float[] weights, Object[] outputData) {
			float red = 0;
			float green = 0;
			float blue = 0;
			float alpha = 0;
			for (int i = 0; i < inputData.length; i++) {
				if (inputData[i] instanceof Vertex vertex) {
					red += weights[i] * vertex.red;
					green += weights[i] * vertex.green;
					blue += weights[i] * vertex.blue;
					alpha += weights[i] * vertex.alpha;
				}
			}
			outputData[0] = new Vertex(coordinates[0], coordinates[1], red, green, blue, alpha);
		}

		@Override
		public void error(int errorCode) {
			throw new IllegalArgumentException("Polygon tessellation failed with GLU error " + errorCode);
		}

		float[] triangleData() {
			float[] data = new float[triangles.size()
					* FilledVertexFormat.FLOATS_PER_VERTEX];
			int offset = 0;
			for (Vertex vertex : triangles) {
				data[offset++] = (float) vertex.coordinates[0];
				data[offset++] = (float) vertex.coordinates[1];
				data[offset++] = vertex.red;
				data[offset++] = vertex.green;
				data[offset++] = vertex.blue;
				data[offset++] = vertex.alpha;
			}
			return data;
		}

		private void emitTriangles() {
			if (primitive.size() % 3 != 0)
				throw new IllegalStateException("Tessellator returned incomplete triangles");
			triangles.addAll(primitive);
		}

		private void emitTriangleFan() {
			for (int i = 1; i + 1 < primitive.size(); i++) {
				emit(primitive.get(0), primitive.get(i), primitive.get(i + 1));
			}
		}

		private void emitTriangleStrip() {
			for (int i = 2; i < primitive.size(); i++) {
				if ((i & 1) == 0)
					emit(primitive.get(i - 2), primitive.get(i - 1), primitive.get(i));
				else
					emit(primitive.get(i - 1), primitive.get(i - 2), primitive.get(i));
			}
		}

		private void emit(Vertex first, Vertex second, Vertex third) {
			triangles.add(first);
			triangles.add(second);
			triangles.add(third);
		}
	}

	private static final class Vertex {
		private final double[] coordinates;
		private final float red;
		private final float green;
		private final float blue;
		private final float alpha;

		Vertex(double x, double y, Color color) {
			this(x, y, color.getRed() / 255f, color.getGreen() / 255f,
					color.getBlue() / 255f, color.getAlpha() / 255f);
		}

		Vertex(double x, double y, float red, float green, float blue, float alpha) {
			coordinates = new double[] { x, y, 0 };
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.alpha = alpha;
		}
	}
}
