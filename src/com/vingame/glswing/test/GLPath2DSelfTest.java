package com.vingame.glswing.test;

import java.awt.Color;
import java.util.List;

import com.vingame.glswing.rendering.GLPath2D;
import com.vingame.glswing.rendering.internal.FilledVertexFormat;
import com.vingame.glswing.rendering.internal.PolygonTessellator;

/** Dependency-free checks for path construction, bounds and fill rules. */
public final class GLPath2DSelfTest {

	private static final float EPSILON = 0.001f;

	private GLPath2DSelfTest() {
	}

	public static void main(String[] args) {
		assertQuadraticBounds();
		assertCubicBounds();
		assertCopyIsIndependent();
		assertPathMustBeginWithMove();
		assertFillRulesProduceDifferentAreas();
		System.out.println("GLPath2D self-test passed.");
	}

	private static void assertQuadraticBounds() {
		GLPath2D path = new GLPath2D()
				.moveTo(0, 0)
				.quadTo(50, 100, 100, 0);
		assertBounds("quadratic", path.getBounds(), 0, 0, 100, 50);
	}

	private static void assertCubicBounds() {
		GLPath2D path = new GLPath2D()
				.moveTo(0, 0)
				.cubicTo(0, 100, 100, 100, 100, 0);
		assertBounds("cubic", path.getBounds(), 0, 0, 100, 75);
	}

	private static void assertCopyIsIndependent() {
		GLPath2D original = new GLPath2D(GLPath2D.FillRule.EVEN_ODD)
				.moveTo(0, 0).lineTo(10, 0).lineTo(10, 10).closePath();
		GLPath2D copy = original.copy().lineTo(20, 20);
		if (original.getCommandCount() != 4 || copy.getCommandCount() != 5)
			throw new AssertionError("Path copy shared mutable command storage");
		if (copy.getFillRule() != GLPath2D.FillRule.EVEN_ODD)
			throw new AssertionError("Path copy lost its fill rule");
		original.reset().moveTo(5, 5).lineTo(6, 6);
		if (copy.getCommandCount() != 5)
			throw new AssertionError("Resetting a path changed its copy");
	}

	private static void assertPathMustBeginWithMove() {
		try {
			new GLPath2D().lineTo(1, 1);
			throw new AssertionError("Path accepted lineTo() before moveTo()");
		} catch (IllegalStateException expected) {
			// Expected.
		}
	}

	private static void assertFillRulesProduceDifferentAreas() {
		List<float[]> contours = List.of(
				new float[] { 0, 0, 100, 0, 100, 100, 0, 100 },
				new float[] { 25, 25, 75, 25, 75, 75, 25, 75 });
		float[] evenOdd = PolygonTessellator.tessellateContours(
				contours, Color.WHITE, GLPath2D.FillRule.EVEN_ODD);
		float[] nonZero = PolygonTessellator.tessellateContours(
				contours, Color.WHITE, GLPath2D.FillRule.NON_ZERO);
		assertNear("even-odd area", 7_500, triangleArea(evenOdd));
		assertNear("non-zero area", 10_000, triangleArea(nonZero));
	}

	private static float triangleArea(float[] vertices) {
		double area = 0;
		int stride = FilledVertexFormat.FLOATS_PER_VERTEX;
		for (int vertex = 0; vertex < vertices.length; vertex += stride * 3) {
			float x0 = vertices[vertex];
			float y0 = vertices[vertex + 1];
			float x1 = vertices[vertex + stride];
			float y1 = vertices[vertex + stride + 1];
			float x2 = vertices[vertex + stride * 2];
			float y2 = vertices[vertex + stride * 2 + 1];
			area += Math.abs((x1 - x0) * (y2 - y0)
					- (y1 - y0) * (x2 - x0)) * 0.5;
		}
		return (float) area;
	}

	private static void assertBounds(String name, GLPath2D.Bounds bounds,
			float x, float y, float width, float height) {
		assertNear(name + " x", x, bounds.x());
		assertNear(name + " y", y, bounds.y());
		assertNear(name + " width", width, bounds.width());
		assertNear(name + " height", height, bounds.height());
	}

	private static void assertNear(String name, float expected, float actual) {
		if (Math.abs(expected - actual) > EPSILON)
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
	}
}
