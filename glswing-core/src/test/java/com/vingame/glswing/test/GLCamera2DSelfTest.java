package com.vingame.glswing.test;

import com.vingame.glswing.rendering.GLCamera2D;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** CPU-only checks for camera pose, zoom and projection composition. */
public final class GLCamera2DSelfTest {

	private static final float EPSILON = 0.0001f;

	private GLCamera2DSelfTest() {
	}

	public static void main(String[] args) {
		GLCamera2D camera = new GLCamera2D(Projection2D.CENTERED_PIXELS);
		camera.setPosition(100, 50);
		GLTransform viewProjection = camera.createViewProjection(800, 600);
		checkPoint(viewProjection, 100, 50, 0, 0);
		checkPoint(viewProjection, 500, 350, 1, 1);

		camera.setZoom(2);
		viewProjection = camera.createViewProjection(800, 600);
		checkPoint(viewProjection, 100, 50, 0, 0);
		checkPoint(viewProjection, 300, 200, 1, 1);

		camera.setZoom(1);
		camera.setPosition(0, 0);
		camera.setRotation((float) (Math.PI / 2));
		viewProjection = camera.createViewProjection(800, 600);
		checkPoint(viewProjection, 0, 100, 0.25f, 0);

		camera.setProjection(Projection2D.TOP_LEFT_PIXELS);
		camera.setRotation(0);
		checkPoint(camera.createViewProjection(800, 600), 0, 0, -1, 1);

		System.out.println("GLCamera2D self-test passed.");
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
}
