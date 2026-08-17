package com.vingame.glswing.test;

import com.vingame.glswing.rendering.GLPath2D;
import com.vingame.glswing.rendering.GLSvgPathParser;

/** Dependency-free regression tests for the complete SVG path grammar. */
public final class GLSvgPathParserSelfTest {

	private static final float EPSILON = 0.01f;

	private GLSvgPathParserSelfTest() {
	}

	public static void main(String[] args) {
		assertMoveRepetitionAndClose();
		assertRelativeLines();
		assertSmoothCubicReflection();
		assertSmoothQuadraticReflection();
		assertSvgNumberSyntax();
		assertCircularArc();
		assertRotatedAndDegenerateArcs();
		assertFactoryAndFillRule();
		assertMalformedPathsAreRejected();
		System.out.println("GLSvgPathParser self-test passed.");
	}

	private static void assertMoveRepetitionAndClose() {
		GLPath2D path = GLSvgPathParser.parse("M0,0 40,0 40,30 0,30z");
		assertBounds("implicit lines", path.getBounds(), 0, 0, 40, 30);
		assertEquals("implicit command count", 5, path.getCommandCount());
		assertEquals("implicit segment count", 3, path.getSegmentCount());
	}

	private static void assertRelativeLines() {
		GLPath2D path = GLSvgPathParser.parse("m10 20 l30 0 v20 h-30 z");
		assertBounds("relative lines", path.getBounds(), 10, 20, 30, 20);
	}

	private static void assertSmoothCubicReflection() {
		GLPath2D parsed = GLSvgPathParser.parse(
				"M0 0 C0 100 100 100 100 0 S200 -100 200 0");
		GLPath2D expected = new GLPath2D().moveTo(0, 0)
				.cubicTo(0, 100, 100, 100, 100, 0)
				.cubicTo(100, -100, 200, -100, 200, 0);
		assertSameBounds("smooth cubic", expected, parsed);
	}

	private static void assertSmoothQuadraticReflection() {
		GLPath2D parsed = GLSvgPathParser.parse("M0 0 Q50 100 100 0 T200 0");
		GLPath2D expected = new GLPath2D().moveTo(0, 0)
				.quadTo(50, 100, 100, 0)
				.quadTo(150, -100, 200, 0);
		assertSameBounds("smooth quadratic", expected, parsed);
	}

	private static void assertSvgNumberSyntax() {
		GLPath2D path = GLSvgPathParser.parse("M.5-.5L1e2,2E1 l-5. 2.5e+1");
		assertBounds("number syntax", path.getBounds(), 0.5f, -0.5f, 99.5f, 45.5f);
	}

	private static void assertCircularArc() {
		GLPath2D arc = GLSvgPathParser.parse("M0 0 A50 50 0 0 1 100 0");
		assertEquals("semicircle cubic pieces", 2, arc.getSegmentCount());
		GLPath2D.Bounds bounds = arc.getBounds();
		assertNear("semicircle x", 0, bounds.x());
		assertNear("semicircle width", 100, bounds.width());
		assertNear("semicircle height", 50, bounds.height());

		GLPath2D compactFlags = GLSvgPathParser.parse("M0 0 A50 50 0 01100 0");
		assertSameBounds("compact arc flags", arc, compactFlags);
	}

	private static void assertRotatedAndDegenerateArcs() {
		GLPath2D rotated = GLSvgPathParser.parse("M10 20 a80 30 37 1 0 140 65");
		GLPath2D.Bounds bounds = rotated.getBounds();
		assertFinite("rotated arc bounds", bounds.x(), bounds.y(),
				bounds.width(), bounds.height());
		if (bounds.width() <= 0 || bounds.height() <= 0)
			throw new AssertionError("Rotated arc has empty bounds");

		GLPath2D line = GLSvgPathParser.parse("M5 7 A0 20 0 0 1 25 17");
		assertEquals("zero-radius arc is a line", 1, line.getSegmentCount());
		assertBounds("zero-radius arc", line.getBounds(), 5, 7, 20, 10);

		GLPath2D omitted = GLSvgPathParser.parse("M5 7 A20 20 0 1 1 5 7");
		assertEquals("coincident arc is omitted", 0, omitted.getSegmentCount());
		assertEquals("coincident arc command count", 1, omitted.getCommandCount());
	}

	private static void assertFactoryAndFillRule() {
		GLPath2D path = GLPath2D.fromSvgPath("M0 0h10v10z",
				GLPath2D.FillRule.EVEN_ODD);
		if (path.getFillRule() != GLPath2D.FillRule.EVEN_ODD)
			throw new AssertionError("SVG factory lost the requested fill rule");
	}

	private static void assertMalformedPathsAreRejected() {
		assertRejected("line before move", "L0 0");
		assertRejected("unknown command", "M0 0 X10 10");
		assertRejected("missing coordinate", "M0 0 L10");
		assertRejected("malformed exponent", "M0 0 L1e 2");
		assertRejected("invalid arc flag", "M0 0 A5 5 0 2 0 10 10");
		assertRejected("numbers after close", "M0 0z 10 10");
	}

	private static void assertRejected(String name, String data) {
		try {
			GLSvgPathParser.parse(data);
			throw new AssertionError(name + " was accepted");
		} catch (GLSvgPathParser.ParseException expected) {
			if (expected.getIndex() < 0 || !expected.getMessage().contains("index"))
				throw new AssertionError(name + " has no useful error position");
		}
	}

	private static void assertSameBounds(String name,
			GLPath2D expected, GLPath2D actual) {
		GLPath2D.Bounds bounds = expected.getBounds();
		assertBounds(name, actual.getBounds(), bounds.x(), bounds.y(),
				bounds.width(), bounds.height());
	}

	private static void assertBounds(String name, GLPath2D.Bounds bounds,
			float x, float y, float width, float height) {
		assertNear(name + " x", x, bounds.x());
		assertNear(name + " y", y, bounds.y());
		assertNear(name + " width", width, bounds.width());
		assertNear(name + " height", height, bounds.height());
	}

	private static void assertFinite(String name, float... values) {
		for (float value : values)
			if (!Float.isFinite(value))
				throw new AssertionError(name + " contains " + value);
	}

	private static void assertEquals(String name, int expected, int actual) {
		if (expected != actual)
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
	}

	private static void assertNear(String name, float expected, float actual) {
		if (Math.abs(expected - actual) > EPSILON)
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
	}
}
