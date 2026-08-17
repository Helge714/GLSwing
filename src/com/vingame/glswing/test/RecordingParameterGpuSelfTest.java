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
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** GPU integration checks for mutable and nested recording parameters. */
public final class RecordingParameterGpuSelfTest {

	private static final int WIDTH = 180;
	private static final int HEIGHT = 120;

	private RecordingParameterGpuSelfTest() {
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
		GLTexture texture = null;
		GLFont font = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			texture = GLTexture.create(gl, solidImage());
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 20);
			testMutableNestedParameters(gl, renderer, texture, font);
			System.out.println("Recording parameter GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (font != null)
				font.dispose(gl);
			if (texture != null)
				texture.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testMutableNestedParameters(GL3 gl,
			GLRenderer2D renderer, GLTexture texture, GLFont font) {
		GLRenderParameters outer = new GLRenderParameters()
				.setTranslation(20, 10)
				.setColor(Color.RED);
		GLRenderParameters inner = new GLRenderParameters()
				.setTranslation(20, 10)
				.setColor(Color.BLUE);
		GLMouseTarget target = new GLMouseTarget() {
		};

		GLContext2D firstContext = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		firstContext.beginRecording();
		firstContext.fillRect(0, 0, 12, 12, Color.WHITE);
		firstContext.withParameters(inner, () -> {
			firstContext.fillRect(15, 0, 12, 12, Color.WHITE, target);
			firstContext.drawTexture(texture, 30, 0, 12, 12);
			firstContext.drawText(font, "H", 47, 17, Color.WHITE);
		});
		GLRecording recording = firstContext.endRecording();
		firstContext.clear(Color.BLACK);
		firstContext.setTransform(new GLTransform().translate(5, 5));
		firstContext.draw(recording, outer);
		renderer.endFrame();

		checkColor(gl, 27, 17, Color.RED, "outer parameter color");
		checkColor(gl, 62, 27, Color.BLUE, "nested color override");
		checkColor(gl, 77, 27, Color.BLUE, "textured parameter color");
		checkPick(renderer.readPickResult(gl, 62, 27), target, 2, 2);

		// Mutating the same objects must affect the next frame without re-recording.
		outer.setTranslation(40, 25).setColor(Color.GREEN).setOpacity(0.5f);
		inner.setTranslation(10, 5).setColor(Color.YELLOW);
		GLContext2D secondContext = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		secondContext.clear(Color.BLACK);
		secondContext.draw(recording, outer);
		renderer.endFrame();

		checkColor(gl, 46, 31, new Color(0, 128, 0), "updated color and opacity");
		checkColor(gl, 71, 36, new Color(128, 128, 0), "updated nested parameters");
		checkColor(gl, 27, 17, Color.BLACK, "old parameter position");
	}

	private static void checkPick(GLPickResult result, GLMouseTarget target,
			float expectedX, float expectedY) {
		if (result.getTarget() != target)
			throw new AssertionError("Parameterized mouse target was not picked");
		if (!result.hasLocalPosition()
				|| Math.abs(result.getLocalX() - expectedX) > 0.01f
				|| Math.abs(result.getLocalY() - expectedY) > 0.01f)
			throw new AssertionError("Expected local pick " + expectedX + "/" + expectedY
					+ ", got " + result.getLocalX() + "/" + result.getLocalY());
	}

	private static void checkColor(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (Math.abs(red - expected.getRed()) > 3
				|| Math.abs(green - expected.getGreen()) > 3
				|| Math.abs(blue - expected.getBlue()) > 3)
			throw new AssertionError(description + ": expected "
					+ expected.getRed() + "/" + expected.getGreen() + "/"
					+ expected.getBlue() + ", got " + red + "/" + green + "/" + blue);
	}

	private static BufferedImage solidImage() {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				image.setRGB(x, y, Color.WHITE.getRGB());
		return image;
	}
}
