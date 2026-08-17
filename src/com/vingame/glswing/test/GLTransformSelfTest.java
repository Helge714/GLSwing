package com.vingame.glswing.test;

import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** CPU-only checks for affine composition and copy semantics. */
public final class GLTransformSelfTest {

	private static final float EPSILON = 0.0001f;

	private GLTransformSelfTest() {
	}

	public static void main(String[] args) {
		GLTransform identity = new GLTransform();
		check(identity.isIdentity(), "New transform must be identity");
		checkPoint(identity, 3, 4, 3, 4);

		GLTransform composed = new GLTransform()
				.translate(10, 20)
				.rotate((float) (Math.PI / 2))
				.scale(2, 3);
		checkPoint(composed, 1, 0, 10, 22);
		checkPoint(composed, 0, 1, 7, 20);

		GLTransform anchored = new GLTransform().rotate(0.75f, 15, 25);
		checkPoint(anchored, 15, 25, 15, 25);

		GLTransform copy = composed.copy();
		composed.translate(100, 200);
		checkPoint(copy, 1, 0, 10, 22);
		check(!copy.equals(composed), "Copy must not share mutable state");

		GLTransform invertible = new GLTransform()
				.translate(17, -9)
				.rotate(0.37f)
				.scale(2.5f, 0.75f);
		GLTransform inverse = invertible.createInverse();
		checkPoint(inverse.copy().concatenate(invertible), 8, -3, 8, -3);
		checkPoint(invertible.copy().concatenate(inverse), 8, -3, 8, -3);
		checkClose(invertible.determinant(), 1.875f, "Transform determinant");
		checkClose(new GLTransform().scale(2, 5).rotate(0.8f).getMaximumScale(),
				5, "Maximum transform scale");
		checkClose(new GLTransform(1, 0, 3, 1, 0, 0).getMaximumScale(),
				3.3027756f, "Maximum shear scale");
		try {
			new GLTransform().scale(0, 1).createInverse();
			throw new AssertionError("Singular transform must not have an inverse");
		} catch (IllegalStateException expected) {
			// Expected.
		}

		float[] points = { 1, 2, 3, 4 };
		new GLTransform().translate(5, 7).transform(points, 0, 2);
		checkClose(points[0], 6, "First transformed x");
		checkClose(points[1], 9, "First transformed y");
		checkClose(points[2], 8, "Second transformed x");
		checkClose(points[3], 11, "Second transformed y");

		GLTransform topLeft = Projection2D.topLeftPixels(800, 600);
		checkPoint(topLeft, 0, 0, -1, 1);
		checkPoint(topLeft, 800, 600, 1, -1);
		GLTransform centered = Projection2D.centeredPixels(800, 600);
		checkPoint(centered, -400, -300, -1, -1);
		checkPoint(centered, 400, 300, 1, 1);
		checkPoint(Projection2D.CENTERED_PIXELS.create(800, 600), 400, 300, 1, 1);
		checkPoint(Projection2D.TOP_LEFT_PIXELS.create(800, 400), 800, 400, 1, -1);
		checkPoint(Projection2D.CENTERED_PIXELS.create(800, 400), 400, 200, 1, 1);
		GLTransform normalized = Projection2D.CENTERED_NORMALIZED.create(800, 400);
		checkPoint(normalized, -2, -1, -1, -1);
		checkPoint(normalized, 2, 1, 1, 1);
		checkPoint(Projection2D.CLIP_SPACE.create(800, 400), 0.4f, -0.7f, 0.4f, -0.7f);

		GLTransform fixedSource = Projection2D.topLeftPixels(800, 600);
		Projection2D fixed = Projection2D.fixed(fixedSource);
		fixedSource.translate(100, 100);
		checkPoint(fixed.create(123, 456), 0, 0, -1, 1);

		Projection2D halfViewport = (width, height) ->
				Projection2D.topLeftPixels(width / 2, height / 2);
		checkPoint(halfViewport.create(800, 600), 400, 300, 1, -1);
		float[] matrix = topLeft.toMatrix4();
		checkClose(matrix[12], -1, "Projection matrix translation x");
		checkClose(matrix[13], 1, "Projection matrix translation y");
		System.out.println("GLTransform self-test passed.");
	}

	private static void checkPoint(GLTransform transform, float x, float y,
			float expectedX, float expectedY) {
		checkClose(transform.transformX(x, y), expectedX, "Transformed x");
		checkClose(transform.transformY(x, y), expectedY, "Transformed y");
	}

	private static void checkClose(float actual, float expected, String description) {
		if (Math.abs(actual - expected) > EPSILON)
			throw new AssertionError(description + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
