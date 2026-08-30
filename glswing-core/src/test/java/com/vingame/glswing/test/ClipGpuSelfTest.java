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
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** GPU checks for nested and recorded color/picking scissor clips. */
public final class ClipGpuSelfTest {
	private static final int WIDTH = 120;
	private static final int HEIGHT = 90;

	private ClipGpuSelfTest() { }

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();
		GLContext glContext = drawable.getContext();
		if (glContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make GL context current");
		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.beginClip(20, 15, 55, 45);
			context.fillRect(0, 0, 100, 80, Color.RED, 11);
			context.beginClip(35, 25, 15, 12);
			context.fillRect(0, 0, 100, 80, Color.BLUE, 22);
			context.endClip();
			context.endClip();
			renderer.endFrame();
			checkColor(gl, 10, 10, Color.BLACK, "outside color clip");
			checkId(renderer, gl, 10, 10, 0, "outside picking clip");
			checkColor(gl, 25, 20, Color.RED, "outer clip");
			checkId(renderer, gl, 25, 20, 11, "outer pick clip");
			checkColor(gl, 40, 30, Color.BLUE, "nested clip");
			checkId(renderer, gl, 40, 30, 22, "nested pick clip");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.beginClip(20, 15, 55, 45);
			context.setPickId(44);
			context.fillUIRoundRect(0, 0, 100, 80, 0, Color.RED);
			context.beginClip(35, 25, 15, 12);
			context.setPickId(55);
			context.fillUIRoundRect(0, 0, 100, 80, 0, Color.BLUE);
			context.endClip();
			context.endClip();
			renderer.endFrame();
			if (renderer.drawCallCount() != 2)
				throw new AssertionError("Nested built-in UI clips split the batch into "
						+ renderer.drawCallCount()
						+ " calls instead of one color and one picking call");
			if (renderer.drawCallProfile().ui() != 2
					|| renderer.drawCallProfile().other() != 0)
				throw new AssertionError("Nested UI clip calls were assigned to the wrong pipeline: "
						+ renderer.drawCallProfile());
			if (renderer.drawCallProfile().events().size() != 1
					|| !"UI".equals(renderer.drawCallProfile().events().get(0).pipeline())
					|| !"frame end".equals(
							renderer.drawCallProfile().events().get(0).reason()))
				throw new AssertionError("Nested UI clip trace is incomplete: "
						+ renderer.drawCallProfile().events());
			checkColor(gl, 10, 10, Color.BLACK, "outside UI clip");
			checkId(renderer, gl, 10, 10, 0, "outside UI pick clip");
			checkColor(gl, 25, 20, Color.RED, "outer UI clip");
			checkId(renderer, gl, 25, 20, 44, "outer UI pick clip");
			checkColor(gl, 40, 30, Color.BLUE, "nested UI clip");
			checkId(renderer, gl, 40, 30, 55, "nested UI pick clip");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setPickId(66);
			context.setStroke(new GLStroke(5)
					.withCap(GLStroke.Cap.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawUILine(15, 70, 105, 20, Color.YELLOW);
			renderer.endFrame();
			if (renderer.drawCallProfile().ui() != 2
					|| renderer.drawCallProfile().filled() != 0)
				throw new AssertionError("Analytic UI line left the UI batch: "
						+ renderer.drawCallProfile());
			checkColor(gl, 60, 45, Color.YELLOW, "diagonal UI line");
			checkId(renderer, gl, 60, 45, 66, "diagonal UI line picking");
			checkColor(gl, 60, 60, Color.BLACK, "outside diagonal UI line");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.beginRecording();
			context.beginClip(0, 0, 20, 20);
			context.fillRect(0, 0, 40, 40, Color.GREEN, 33);
			context.endClip();
			GLRecording recording = context.endRecording();
			context.setTransform(new GLTransform().translate(60, 35));
			context.draw(recording);
			renderer.endFrame();
			checkColor(gl, 65, 40, Color.GREEN, "recorded translated clip");
			checkId(renderer, gl, 65, 40, 33, "recorded translated pick clip");
			checkColor(gl, 85, 40, Color.BLACK, "recorded clip outside");
			checkId(renderer, gl, 85, 40, 0, "recorded pick clip outside");
			System.out.println("Clip GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null) renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void checkId(GLRenderer2D renderer, GL3 gl,
			int x, int y, int expected, String description) {
		int actual = renderer.readPickId(gl, x, y);
		if (actual != expected)
			throw new AssertionError(description + ": expected " + expected
					+ ", got " + actual);
	}

	private static void checkColor(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (Math.abs(red - expected.getRed()) > 2
				|| Math.abs(green - expected.getGreen()) > 2
				|| Math.abs(blue - expected.getBlue()) > 2)
			throw new AssertionError(description + ": got rgb("
					+ red + "," + green + "," + blue + ")");
	}
}
