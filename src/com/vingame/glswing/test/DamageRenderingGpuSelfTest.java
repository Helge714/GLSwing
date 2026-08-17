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
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.internal.SceneFramebuffer;

/** GPU checks for retained color, partial clears and preserved picking. */
public final class DamageRenderingGpuSelfTest {

	private static final int WIDTH = 160;
	private static final int HEIGHT = 100;

	private DamageRenderingGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();
		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");

		GLRenderer2D renderer = null;
		SceneFramebuffer scene = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			scene = SceneFramebuffer.create(gl);
			int[] samples = new int[1];
			gl.glGetIntegerv(GL.GL_SAMPLES, samples, 0);
			int[] maximumSamples = new int[1];
			gl.glGetIntegerv(GL3.GL_MAX_SAMPLES, maximumSamples, 0);
			int scaledSceneSamples = Math.min(4, maximumSamples[0]);

			scene.beginUpdate(gl, WIDTH, HEIGHT, samples[0], WIDTH, HEIGHT, null);
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), true);
			context.clear(Color.BLACK);
			context.fillRect(10, 10, 40, 30, Color.RED, 77);
			renderer.endFrame();
			scene.endUpdate(gl);
			scene.present(gl);
			checkColor(gl, 20, 20, Color.RED, "initial retained scene");
			if (renderer.readPickId(gl, 20, 20) != 77)
				throw new AssertionError("Initial picking result was not written");

			float[] damage = { 100, 20, 30, 30 };
			scene.beginUpdate(gl, WIDTH, HEIGHT, samples[0], WIDTH, HEIGHT, damage);
			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
			context.setRenderOutputs(true, false);
			renderer.setRedrawBounds(damage);
			context.clear(Color.BLACK);
			context.fillRect(100, 20, 30, 30, Color.BLUE);
			renderer.endFrame();
			scene.endUpdate(gl);
			scene.present(gl);

			checkColor(gl, 20, 20, Color.RED,
					"partial update preserved pixels outside its damage");
			checkColor(gl, 110, 30, Color.BLUE, "partial update changed its damage");
			if (renderer.readPickId(gl, 20, 20) != 77)
				throw new AssertionError("Color-only update discarded retained picking");

			// A physical scissor must not extend beyond the logical bounds used by
			// CPU culling. Otherwise a repainted back object can overwrite a front
			// sibling that was correctly rejected as outside the damage rectangle.
			scene.beginUpdate(gl, WIDTH, HEIGHT, samples[0], WIDTH, HEIGHT, null);
			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
			context.setRenderOutputs(true, false);
			context.clear(Color.BLACK);
			context.fillRect(10, 60, 80, 20, Color.RED);
			context.fillRect(80, 60, 30, 20, Color.GREEN);
			renderer.endFrame();
			scene.endUpdate(gl);

			float[] touchingDamage = { 60, 60, 20, 20 };
			scene.beginUpdate(gl, WIDTH, HEIGHT, samples[0], WIDTH, HEIGHT,
					touchingDamage);
			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
			context.setRenderOutputs(true, false);
			renderer.setRedrawBounds(touchingDamage);
			context.clear(Color.BLACK);
			context.fillRect(10, 60, 80, 20, Color.BLUE);
			renderer.endFrame();
			scene.endUpdate(gl);
			scene.present(gl);
			checkColor(gl, 79, 70, Color.BLUE,
					"partial redraw reached its final included pixel");
			checkColor(gl, 80, 70, Color.GREEN,
					"partial redraw preserved a touching front sibling");

			// Render at half resolution and linearly present into the native target.
			// Logical coordinates remain unchanged, so this also verifies that the
			// low-resolution viewport does not alter layout coordinates.
			scene.beginUpdate(gl, WIDTH / 2, HEIGHT / 2, scaledSceneSamples,
					WIDTH, HEIGHT, null);
			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
			context.setRenderOutputs(true, false);
			context.clear(Color.BLACK);
			context.fillRect(0, 0, WIDTH / 2f, HEIGHT, Color.MAGENTA);
			context.fillRect(WIDTH / 2f, 0, WIDTH / 2f, HEIGHT, Color.CYAN);
			renderer.endFrame();
			scene.endUpdate(gl);
			scene.present(gl, WIDTH, HEIGHT);
			checkColor(gl, 20, 50, Color.MAGENTA,
					"scaled presentation preserved the left logical half");
			checkColor(gl, 140, 50, Color.CYAN,
					"scaled presentation preserved the right logical half");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
			float[] fractionalDamage = { 10.25f, 10.25f, 20.5f, 20.5f };
			renderer.setRedrawBounds(fractionalDamage);
			if (!context.intersectsRedrawBounds(30.75f, 12, 8, 8))
				throw new AssertionError(
						"Subpixel-touching front geometry was culled from redraw");
			if (context.intersectsRedrawBounds(32, 12, 8, 8))
				throw new AssertionError(
						"Geometry beyond one physical pixel escaped redraw culling");
			renderer.endFrame();
			System.out.println("Damage rendering GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (scene != null)
				scene.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
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
			throw new AssertionError(description + ": expected "
					+ expected.getRed() + "/" + expected.getGreen() + "/"
					+ expected.getBlue() + ", got " + red + "/" + green + "/" + blue);
	}
}
