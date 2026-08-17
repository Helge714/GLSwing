package com.vingame.glswing.test;

import java.awt.Color;
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.Projection2D;

/** Offscreen GPU checks for circles, ellipses, rounded rectangles and picking. */
public final class CurvedShapesGpuSelfTest {

	private static final int WIDTH = 220;
	private static final int HEIGHT = 80;

	private CurvedShapesGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();
		GLContext glContext = drawable.getContext();
		if (glContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");
		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setColor(Color.RED);
			context.fillCircle(30, 30, 20, 101);
			context.setColor(Color.GREEN);
			context.fillRoundRect(70, 10, 50, 40, 15, 102);
			context.fillEllipse(145, 10, 60, 40, Color.BLUE);
			context.setStroke(new GLStroke(5).withCap(GLStroke.Cap.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawCubicBezier(125, 65, 135, 5, 190, 5,
					205, 65, Color.YELLOW, Color.CYAN, 103);
			renderer.endFrame();

			checkColor(gl, 30, 30, Color.RED, "circle center");
			checkColor(gl, 72, 12, Color.BLACK, "rounded corner exclusion");
			checkColor(gl, 95, 30, Color.GREEN, "rounded rectangle center");
			checkColor(gl, 175, 30, Color.BLUE, "ellipse center");
			checkColor(gl, 163, 20, new Color(128, 255, 128),
					"cubic Bezier gradient center");
			check(renderer.readPickResult(gl, 30, 30).getId() == 101,
					"Circle picking failed");
			check(renderer.readPickResult(gl, 72, 12).getId() == GLContext2D.NO_PICK_ID,
					"Transparent rounded corner was pickable");
			check(renderer.readPickResult(gl, 95, 30).getId() == 102,
					"Rounded rectangle picking failed");
			check(renderer.readPickResult(gl, 163, 20).getId() == 103,
					"Cubic Bezier picking failed");
			System.out.println("Curved shapes GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null)
				renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void checkColor(GL3 gl, int x, int y, Color expected,
			String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		Color actual = new Color(pixel.get(0) & 0xff, pixel.get(1) & 0xff,
				pixel.get(2) & 0xff);
		check(Math.abs(actual.getRed() - expected.getRed()) <= 2
				&& Math.abs(actual.getGreen() - expected.getGreen()) <= 2
				&& Math.abs(actual.getBlue() - expected.getBlue()) <= 2,
				description + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
