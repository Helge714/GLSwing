package com.vingame.glswing.test;

import java.awt.Color;

import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLStroke.WidthSpace;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.internal.FilledVertexFormat;
import com.vingame.glswing.rendering.internal.StrokeGeometryCompiler;

/** Regression tests for compiling WORLD and SCREEN strokes to filled triangles. */
public final class StrokeGeometryCompilerSelfTest {

	private static final float EPSILON = 0.0001f;

	private StrokeGeometryCompilerSelfTest() {
	}

	public static void main(String[] args) {
		testWorldWidthFollowsModelScale();
		testScreenWidthIgnoresModelScale();
		System.out.println("Stroke geometry compiler self-test passed.");
	}

	private static void testWorldWidthFollowsModelScale() {
		float[] vertices = compileLine(WidthSpace.WORLD);
		assertBounds(vertices, 0, -12, 40, 12);
	}

	private static void testScreenWidthIgnoresModelScale() {
		float[] vertices = compileLine(WidthSpace.SCREEN);
		assertBounds(vertices, 0, -3, 40, 3);
	}

	private static float[] compileLine(WidthSpace widthSpace) {
		GLStroke stroke = new GLStroke(6).withWidthSpace(widthSpace);
		GLTransform modelTransform = new GLTransform().scale(4);
		GLTransform identity = new GLTransform();
		return StrokeGeometryCompiler.line(
				0, 0, 10, 0, stroke, Color.WHITE,
				modelTransform, identity, identity);
	}

	private static void assertBounds(float[] vertices,
			float expectedMinX, float expectedMinY,
			float expectedMaxX, float expectedMaxY) {
		if (vertices.length == 0)
			throw new AssertionError("Expected generated stroke vertices");
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int index = 0; index < vertices.length;
				index += FilledVertexFormat.FLOATS_PER_VERTEX) {
			minX = Math.min(minX, vertices[index]);
			minY = Math.min(minY, vertices[index + 1]);
			maxX = Math.max(maxX, vertices[index]);
			maxY = Math.max(maxY, vertices[index + 1]);
		}
		assertNear(expectedMinX, minX, "minimum x");
		assertNear(expectedMinY, minY, "minimum y");
		assertNear(expectedMaxX, maxX, "maximum x");
		assertNear(expectedMaxY, maxY, "maximum y");
	}

	private static void assertNear(float expected, float actual, String label) {
		if (Math.abs(expected - actual) > EPSILON)
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
	}
}
