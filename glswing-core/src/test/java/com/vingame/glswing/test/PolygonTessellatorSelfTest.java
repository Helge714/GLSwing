package com.vingame.glswing.test;

import java.awt.Color;

import com.vingame.glswing.rendering.internal.PolygonTessellator;

/** Small dependency-free smoke test for polygon tessellation. */
public final class PolygonTessellatorSelfTest {

	private static final int FLOATS_PER_VERTEX = 6;

	private PolygonTessellatorSelfTest() {
	}

	public static void main(String[] args) {
		assertTriangleCount("square", new float[] {
				0, 0, 100, 0, 100, 100, 0, 100
		}, 2);
		assertTriangleCount("concave polygon", new float[] {
				0, 0, 100, 0, 50, 40, 100, 100, 0, 100
		}, 3);
		assertTriangleCount("reversed winding", new float[] {
				0, 100, 100, 100, 50, 40, 100, 0, 0, 0
		}, 3);
		assertColoredSelfIntersection();

		System.out.println("Polygon tessellator self-test passed.");
	}

	private static void assertTriangleCount(String name, float[] points, int expectedTriangles) {
		float[] vertices = PolygonTessellator.tessellate(points, Color.WHITE);
		int actualTriangles = vertices.length / FLOATS_PER_VERTEX / 3;
		if (actualTriangles != expectedTriangles) {
			throw new AssertionError(name + ": expected " + expectedTriangles
					+ " triangles, but got " + actualTriangles);
		}
	}

	private static void assertColoredSelfIntersection() {
		float[] vertices = PolygonTessellator.tessellate(new float[] {
				0, 0, 100, 100, 0, 100, 100, 0
		}, new Color[] {
				Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE
		});
		if (vertices.length == 0 || vertices.length % (FLOATS_PER_VERTEX * 3) != 0)
			throw new AssertionError("Self-intersecting polygon did not produce complete triangles");
		for (float value : vertices) {
			if (!Float.isFinite(value))
				throw new AssertionError("Self-intersecting polygon produced non-finite vertex data");
		}
	}
}
