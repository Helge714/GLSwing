package com.vingame.glswing.test;

import java.awt.Color;

import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.internal.StrokeTessellator;

/** Small dependency-free smoke test for styled stroke geometry. */
public final class StrokeTessellatorSelfTest {

	private static final int FLOATS_PER_VERTEX = 6;
	private static final float EPSILON = 0.01f;

	private StrokeTessellatorSelfTest() {
	}

	public static void main(String[] args) {
		assertImmutableStrokeStyle();
		assertCurveToleranceControlsTriangleCount();
		assertCapBounds();
		assertAllJoinsProduceFiniteTriangles();
		assertCenterOffset();
		assertOffsetChangesRoundJoinRadius();
		assertDashPatternAndPhase();
		assertOddDashPatternRepeats();
		assertDashCaps();
		assertDashColorsAreInterpolated();
		assertClosedDashedStroke();
		assertClosedStroke();
		assertCollapsedInnerContourIsAccepted();
		assertPerPointColorsReachVertices();
		assertInvalidColorCountIsRejected();
		assertInvalidDashPatternIsRejected();

		System.out.println("Stroke tessellator self-test passed.");
	}

	private static void assertCapBounds() {
		Bounds butt = bounds(line(GLStroke.Cap.BUTT, 0));
		Bounds square = bounds(line(GLStroke.Cap.SQUARE, 0));
		Bounds round = bounds(line(GLStroke.Cap.ROUND, 0));
		assertNear("butt start", 0, butt.minimumX);
		assertNear("butt end", 100, butt.maximumX);
		assertNear("square start", -5, square.minimumX);
		assertNear("square end", 105, square.maximumX);
		assertNear("round start", -5, round.minimumX);
		assertNear("round end", 105, round.maximumX);
	}

	private static void assertAllJoinsProduceFiniteTriangles() {
		float[] points = { 0, 100, 60, 0, 120, 100 };
		for (GLStroke.Join join : GLStroke.Join.values()) {
			GLStroke stroke = new GLStroke(18).withCap(GLStroke.Cap.BUTT).withJoin(join);
			float[] vertices = StrokeTessellator.tessellatePolyline(
					points, stroke, new Color(255, 255, 255, 120));
			assertCompleteFiniteTriangles(join.name(), vertices);
		}
	}

	private static void assertCenterOffset() {
		Bounds centered = bounds(line(GLStroke.Cap.BUTT, 0));
		Bounds shifted = bounds(line(GLStroke.Cap.BUTT, 7));
		assertNear("centered minimum y", -5, centered.minimumY);
		assertNear("centered maximum y", 5, centered.maximumY);
		assertNear("shifted minimum y", 2, shifted.minimumY);
		assertNear("shifted maximum y", 12, shifted.maximumY);
	}

	private static void assertOffsetChangesRoundJoinRadius() {
		float[] corner = { 0, 100, 100, 100, 100, 0 };
		GLStroke enlarged = new GLStroke(10)
				.withJoin(GLStroke.Join.ROUND)
				.withOffset(20)
				.withCurveTolerance(0.05f);
		assertContainsArcVertex("enlarged outer round join",
				StrokeTessellator.tessellatePolyline(corner, enlarged, Color.WHITE),
				100, 100, 25, 0.05f);

		GLStroke reduced = enlarged.withOffset(-3);
		assertContainsArcVertex("reduced outer round join",
				StrokeTessellator.tessellatePolyline(corner, reduced, Color.WHITE),
				100, 100, 2, 0.05f);
	}

	private static void assertClosedStroke() {
		GLStroke stroke = new GLStroke(12)
				.withCap(GLStroke.Cap.ROUND)
				.withJoin(GLStroke.Join.ROUND)
				.withOffset(8);
		float[] vertices = StrokeTessellator.tessellatePolygon(new float[] {
				0, 0, 100, 0, 100, 100, 0, 100
		}, stroke, Color.BLUE);
		assertCompleteFiniteTriangles("closed offset stroke", vertices);
	}

	private static void assertCollapsedInnerContourIsAccepted() {
		GLStroke stroke = new GLStroke(20)
				.withJoin(GLStroke.Join.ROUND)
				.withOffset(40);
		float[] vertices = StrokeTessellator.tessellatePolygon(new float[] {
				0, 0, 100, 0, 100, 100, 0, 100
		}, stroke, Color.WHITE);
		assertCompleteFiniteTriangles("collapsed inner contour", vertices);
	}

	private static float[] line(GLStroke.Cap cap, float offset) {
		GLStroke stroke = new GLStroke(10).withCap(cap).withOffset(offset);
		return StrokeTessellator.tessellateLine(0, 0, 100, 0, stroke, Color.WHITE);
	}

	private static void assertImmutableStrokeStyle() {
		GLStroke original = new GLStroke(4);
		float[] dash = { 9, 3 };
		GLStroke styled = original.withWidth(12)
				.withCap(GLStroke.Cap.ROUND)
				.withJoin(GLStroke.Join.BEVEL)
				.withMiterLimit(5)
				.withOffset(3)
				.withCurveTolerance(0.4f)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN)
				.withDash(dash, 2);
		dash[0] = 999;
		if (original.getWidth() != 4 || original.getCap() != GLStroke.Cap.BUTT
				|| original.getJoin() != GLStroke.Join.MITER || original.getOffset() != 0
				|| original.getWidthSpace() != GLStroke.WidthSpace.WORLD)
			throw new AssertionError("GLStroke mutation changed the original value");
		if (styled.getWidth() != 12 || styled.getCap() != GLStroke.Cap.ROUND
				|| styled.getJoin() != GLStroke.Join.BEVEL || styled.getMiterLimit() != 5
				|| styled.getOffset() != 3 || styled.getCurveTolerance() != 0.4f
				|| styled.getWidthSpace() != GLStroke.WidthSpace.SCREEN
				|| styled.getDashPhase() != 2 || styled.getDashArray()[0] != 9)
			throw new AssertionError("GLStroke fluent copy lost style values");
		float[] returnedDash = styled.getDashArray();
		returnedDash[0] = 777;
		if (styled.getDashArray()[0] != 9)
			throw new AssertionError("GLStroke exposed its internal dash array");
		if (styled.withCap(GLStroke.Cap.SQUARE).getDashPhase() != 2)
			throw new AssertionError("GLStroke copy lost its dash pattern");
		if (styled.withoutDash().isDashed())
			throw new AssertionError("GLStroke.withoutDash() kept a dash pattern");
	}

	private static void assertDashPatternAndPhase() {
		GLStroke dashed = new GLStroke(10).withDash(new float[] { 20, 10 }, 0);
		float[] vertices = StrokeTessellator.tessellateLine(
				0, 0, 100, 0, dashed, Color.WHITE);
		assertCompleteFiniteTriangles("dashed line", vertices);
		assertContainsX("first dash end", vertices, 20);
		assertContainsX("second dash start", vertices, 30);
		assertNoXBetween("first dash gap", vertices, 20, 30);

		float[] phased = StrokeTessellator.tessellateLine(0, 0, 100, 0,
				dashed.withDashPhase(5), Color.WHITE);
		assertContainsX("phased first dash end", phased, 15);
		assertContainsX("phased second dash start", phased, 25);
		assertNoXBetween("phased first dash gap", phased, 15, 25);
	}

	private static void assertDashCaps() {
		GLStroke round = new GLStroke(10)
				.withCap(GLStroke.Cap.ROUND)
				.withDash(20, 20);
		float[] vertices = StrokeTessellator.tessellateLine(
				0, 0, 100, 0, round, Color.WHITE);
		Bounds bounds = bounds(vertices);
		assertNear("round dashed start", -5, bounds.minimumX);
		assertNear("round dashed end", 105, bounds.maximumX);
		assertContainsX("round cap at first dash end", vertices, 25);
		assertContainsX("round cap at second dash start", vertices, 35);
	}

	private static void assertOddDashPatternRepeats() {
		float[] vertices = StrokeTessellator.tessellateLine(0, 0, 60, 0,
				new GLStroke(4).withDash(10, 5, 2), Color.WHITE);
		assertContainsX("odd pattern first run end", vertices, 10);
		assertContainsX("odd pattern second run start", vertices, 15);
		assertContainsX("odd pattern second run end", vertices, 17);
		assertContainsX("repeated odd pattern third run start", vertices, 27);
	}

	private static void assertDashColorsAreInterpolated() {
		float[] vertices = StrokeTessellator.tessellateLine(
				0, 0, Color.RED, 100, 0, Color.BLUE,
				new GLStroke(10).withDash(20, 10));
		for (int index = 0; index < vertices.length; index += FLOATS_PER_VERTEX) {
			if (Math.abs(vertices[index] - 20) <= EPSILON
					&& Math.abs(vertices[index + 2] - 0.8f) <= EPSILON
					&& Math.abs(vertices[index + 4] - 0.2f) <= EPSILON)
				return;
		}
		throw new AssertionError("Dash boundary did not interpolate its point color");
	}

	private static void assertClosedDashedStroke() {
		float[] vertices = StrokeTessellator.tessellatePolygon(new float[] {
				0, 0, 100, 0, 100, 100, 0, 100
		}, new GLStroke(8).withCap(GLStroke.Cap.ROUND).withDash(27, 11),
				Color.WHITE);
		assertCompleteFiniteTriangles("closed dashed stroke", vertices);
	}

	private static void assertCurveToleranceControlsTriangleCount() {
		GLStroke base = new GLStroke(100).withCap(GLStroke.Cap.ROUND);
		float[] coarse = StrokeTessellator.tessellateLine(0, 0, 200, 0,
				base.withCurveTolerance(GLStroke.CURVE_TOLERANCE_COARSE), Color.WHITE);
		float[] fine = StrokeTessellator.tessellateLine(0, 0, 200, 0,
				base.withCurveTolerance(GLStroke.CURVE_TOLERANCE_FINE), Color.WHITE);
		if (fine.length <= coarse.length)
			throw new AssertionError("FINE tolerance should produce more geometry than COARSE");
	}

	private static void assertPerPointColorsReachVertices() {
		float[] vertices = StrokeTessellator.tessellateLine(
				0, 0, Color.RED, 100, 0, Color.BLUE, new GLStroke(10));
		boolean foundRedStart = false;
		boolean foundBlueEnd = false;
		for (int i = 0; i < vertices.length; i += FLOATS_PER_VERTEX) {
			float x = vertices[i];
			float red = vertices[i + 2];
			float green = vertices[i + 3];
			float blue = vertices[i + 4];
			if (Math.abs(x) <= EPSILON && red > 0.99f && green < EPSILON && blue < EPSILON)
				foundRedStart = true;
			if (Math.abs(x - 100) <= EPSILON && red < EPSILON && green < EPSILON && blue > 0.99f)
				foundBlueEnd = true;
		}
		if (!foundRedStart || !foundBlueEnd)
			throw new AssertionError("Colored line did not preserve both endpoint colors");
	}

	private static void assertInvalidColorCountIsRejected() {
		try {
			StrokeTessellator.tessellatePolyline(new float[] { 0, 0, 50, 50, 100, 0 },
					new GLStroke(8), new Color[] { Color.RED, Color.BLUE });
			throw new AssertionError("Stroke accepted a mismatched color count");
		} catch (IllegalArgumentException expected) {
			// Expected: every point requires exactly one color.
		}
	}

	private static void assertInvalidDashPatternIsRejected() {
		try {
			new GLStroke(4).withDash(10, 0);
			throw new AssertionError("Stroke accepted a zero dash length");
		} catch (IllegalArgumentException expected) {
			// Zero-length runs are intentionally unsupported; use a short round run for dots.
		}
		try {
			new GLStroke(4).withDash(-1, 3);
			throw new AssertionError("Stroke accepted a negative dash length");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void assertCompleteFiniteTriangles(String name, float[] vertices) {
		if (vertices.length == 0 || vertices.length % (FLOATS_PER_VERTEX * 3) != 0)
			throw new AssertionError(name + " did not produce complete triangles");
		for (float value : vertices) {
			if (!Float.isFinite(value))
				throw new AssertionError(name + " produced non-finite vertex data");
		}
	}

	private static void assertContainsArcVertex(String name, float[] vertices,
			float centerX, float centerY, float radius, float tolerance) {
		assertCompleteFiniteTriangles(name, vertices);
		for (int index = 0; index < vertices.length; index += FLOATS_PER_VERTEX) {
			float deltaX = vertices[index] - centerX;
			float deltaY = vertices[index + 1] - centerY;
			float actualRadius = (float) Math.hypot(deltaX, deltaY);
			if (deltaX > radius * 0.1f && deltaY > radius * 0.1f
					&& Math.abs(actualRadius - radius) <= tolerance)
				return;
		}
		throw new AssertionError(name + " did not contain an intermediate point on radius "
				+ radius + " around (" + centerX + ", " + centerY + ")");
	}

	private static Bounds bounds(float[] vertices) {
		assertCompleteFiniteTriangles("bounds input", vertices);
		Bounds bounds = new Bounds();
		for (int i = 0; i < vertices.length; i += FLOATS_PER_VERTEX)
			bounds.include(vertices[i], vertices[i + 1]);
		return bounds;
	}

	private static void assertNear(String name, float expected, float actual) {
		if (Math.abs(expected - actual) > EPSILON)
			throw new AssertionError(name + ": expected " + expected + ", but got " + actual);
	}

	private static void assertContainsX(String name, float[] vertices, float expected) {
		for (int index = 0; index < vertices.length; index += FLOATS_PER_VERTEX)
			if (Math.abs(vertices[index] - expected) <= EPSILON)
				return;
		throw new AssertionError(name + ": no vertex at x=" + expected);
	}

	private static void assertNoXBetween(String name, float[] vertices,
			float minimum, float maximum) {
		for (int index = 0; index < vertices.length; index += FLOATS_PER_VERTEX) {
			float x = vertices[index];
			if (x > minimum + EPSILON && x < maximum - EPSILON)
				throw new AssertionError(name + ": unexpected vertex at x=" + x);
		}
	}

	private static final class Bounds {
		private float minimumX = Float.POSITIVE_INFINITY;
		private float minimumY = Float.POSITIVE_INFINITY;
		private float maximumX = Float.NEGATIVE_INFINITY;
		private float maximumY = Float.NEGATIVE_INFINITY;

		void include(float x, float y) {
			minimumX = Math.min(minimumX, x);
			minimumY = Math.min(minimumY, y);
			maximumX = Math.max(maximumX, x);
			maximumY = Math.max(maximumY, y);
		}
	}
}
