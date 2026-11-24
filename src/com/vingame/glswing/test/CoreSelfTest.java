package com.vingame.glswing.test;

import java.util.List;

import com.vingame.glswing.rendering.context.GLProjection;
import com.vingame.glswing.rendering.context.GLStroke;
import com.vingame.glswing.rendering.context.Mesh;
import com.vingame.glswing.rendering.context.PolygonTriangulator;

/** Dependency-free smoke tests. Run with assertions enabled (-ea). */
public final class CoreSelfTest {

	private CoreSelfTest() {
	}

	public static void main(String[] args) {
		testConvexPolygonTriangulation();
		testConcavePolygonTriangulation();
		testPerVertexColors();
		testProjectionIsImmutable();
		testLineMeshSize();
		System.out.println("GLSwing core self-tests passed.");
	}

	private static void testConvexPolygonTriangulation() {
		List<float[]> triangles = PolygonTriangulator.triangulate(new float[] { 0, 0, 10, 0, 10, 10, 0, 10 });
		check(triangles.size() == 2, "a rectangle must produce two triangles");
	}

	private static void testConcavePolygonTriangulation() {
		float[] arrow = { 0, 0, 10, 5, 0, 10, 3, 5 };
		check(PolygonTriangulator.triangulate(arrow).size() == 2,
				"a concave four-vertex polygon must produce two triangles");
	}

	private static void testPerVertexColors() {
		float[] square = { 0, 0, 1, 0, 1, 1, 0, 1 };
		float[] colors = { 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1 };
		List<float[]> result = PolygonTriangulator.triangulateColors(square, colors);
		check(result.size() == 2 && result.get(0).length == 12,
				"triangulated vertex colors must match the generated triangles");
	}

	private static void testProjectionIsImmutable() {
		GLProjection identity = GLProjection.DEFAULT_PROJECTION;
		GLProjection translated = identity.translate(4, 7);
		check(identity != translated, "projection operations must return a new value");
		check(identity.projection[12] == 0 && identity.projection[13] == 0,
				"the shared identity projection must remain unchanged");
	}

	private static void testLineMeshSize() {
		Mesh mesh = GLStroke.buildMesh(new float[] { 0, 0, 10, 0, 10, 10 }, false);
		check(mesh.vertices.length == 56, "two line segments need eight vertices with seven values each");
		check(mesh.indices.length == 12, "two line segments need four triangles");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
