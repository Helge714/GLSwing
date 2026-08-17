package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLImage;
import com.vingame.glswing.rendering.GLImageOptions;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;

/** GPU regression tests for automatic atlases and multi-texture batches. */
public final class ImageAtlasGpuSelfTest {

	private static final int WIDTH = 256;
	private static final int HEIGHT = 128;

	private ImageAtlasGpuSelfTest() {
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
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			testAutomaticAtlas(gl, renderer);
			testTextureSlotLimit(gl, renderer);
			testMixedUiTexturesAndPicking(gl, renderer);
			testRgbaAndMsdfIcons(gl, renderer);
			System.out.println("Image atlas GPU self-test passed.");
		} finally {
			if (renderer != null)
				renderer.dispose(drawable.getGL().getGL3());
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testAutomaticAtlas(GL3 gl, GLRenderer2D renderer) {
		GLContext2D context = begin(gl, renderer);
		context.clear(Color.BLACK);
		for (int index = 0; index < 40; index++) {
			GLImage image = colorImage(new Color(index * 47 % 255,
					index * 83 % 255, index * 131 % 255), GLImageOptions.DEFAULT);
			context.drawImage(image, (index % 20) * 10, (index / 20) * 10, 8, 8);
		}
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"Forty automatic atlas images should use one color draw call, got "
						+ renderer.drawCallCount());
	}

	private static void testTextureSlotLimit(GL3 gl, GLRenderer2D renderer) {
		GLContext2D context = begin(gl, renderer);
		context.clear(Color.BLACK);
		for (int index = 0; index < 18; index++) {
			GLImage image = colorImage(new Color(index * 13 % 255,
					index * 29 % 255, index * 61 % 255), GLImageOptions.standalone());
			context.drawImage(image, index * 10, 30, 8, 8);
		}
		renderer.endFrame();
		check(renderer.drawCallCount() == 2,
				"Eighteen standalone images should split at sixteen slots, got "
						+ renderer.drawCallCount());
	}

	private static void testMixedUiTexturesAndPicking(GL3 gl,
			GLRenderer2D renderer) {
		GLImage red = colorImage(Color.RED, GLImageOptions.standalone());
		GLImage green = colorImage(Color.GREEN, GLImageOptions.standalone());
		GLMouseTarget redTarget = new GLMouseTarget() { };
		GLMouseTarget greenTarget = new GLMouseTarget() { };
		GLContext2D context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.fillUIRoundRect(4, 4, 20, 20, 4, Color.BLUE);
		context.setMouseTarget(redTarget, 30, 4);
		context.drawUIImage(red, 30, 4, 20, 20);
		context.setMouseTarget(greenTarget, 56, 4);
		context.drawUIImage(green, 56, 4, 20, 20);
		context.clearMouseTarget();
		renderer.endFrame();
		check(renderer.drawCallCount() == 2,
				"Mixed UI shapes and two textures should use one color and one pick draw");
		check(renderer.readPickResult(gl, 40, 14).getTarget() == redTarget,
				"First UI texture picked the wrong target");
		check(renderer.readPickResult(gl, 66, 14).getTarget() == greenTarget,
				"Second UI texture picked the wrong target");
	}

	private static void testRgbaAndMsdfIcons(GL3 gl, GLRenderer2D renderer) {
		GLIcon msdf = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/link_icon.svg");
		GLIcon rgba = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/test_icon.svg", GLIcon.Format.RGBA);
		check(msdf.getFormat() == GLIcon.Format.MSDF,
				"Monochrome SVG was not automatically classified as MSDF");
		GLContext2D context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.fillUIRoundRect(4, 4, 70, 70, 8, new Color(30, 40, 55));
		context.drawIcon(msdf, 8, 8, 62, 62, Color.CYAN);
		context.drawIcon(rgba, 90, 4, 70, 70);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"RGBA and MSDF atlas icons should remain in one UI draw call");
		checkVisible(gl, 8, 8, 62, 62, "MSDF icon");
		checkVisible(gl, 90, 4, 70, 70, "RGBA icon");
	}

	private static GLContext2D begin(GL3 gl, GLRenderer2D renderer) {
		return renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
	}

	private static GLImage colorImage(Color color, GLImageOptions options) {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 8; y++)
			for (int x = 0; x < 8; x++)
				image.setRGB(x, y, color.getRGB());
		return GLImage.of(image, options);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static void checkVisible(GL3 gl, int x, int y,
			int width, int height, String description) {
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(x, HEIGHT - y - height, width, height,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		for (int index = 0; index < width * height; index++) {
			int offset = index * 4;
			if ((pixels.get(offset) & 0xff) > 25
					|| (pixels.get(offset + 1) & 0xff) > 25
					|| (pixels.get(offset + 2) & 0xff) > 25)
				return;
		}
		throw new AssertionError(description + " produced no visible pixels");
	}
}
