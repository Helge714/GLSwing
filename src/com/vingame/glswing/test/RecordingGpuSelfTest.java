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
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** GPU integration checks for static recording, replay, batching and picking. */
public final class RecordingGpuSelfTest {

	private static final int WIDTH = 220;
	private static final int HEIGHT = 150;

	private RecordingGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();

		GLContext glContext = drawable.getContext();
		int current = glContext.makeCurrent();
		if (current == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make the offscreen OpenGL context current");

		GLRenderer2D renderer = null;
		GLTexture texture = null;
		GLFont font = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			texture = GLTexture.create(gl, solidImage());
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 18);

			testCompatibleBatchReplay(gl, renderer);
			testTextureAndTextCoalescing(gl, renderer, texture, font);
			testMixedRecording(gl, renderer, texture, font);
			testRecordedScreenStrokeIgnoresReplayScale(gl, renderer);
			testRenderOutputsAreAmbient(gl, renderer);
			System.out.println("Recording GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (font != null)
				font.dispose(gl);
			if (texture != null)
				texture.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void testCompatibleBatchReplay(GL3 gl, GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillRect(10, 10, 20, 20, Color.RED);

		context.beginRecording();
		context.fillRect(40, 10, 20, 20, Color.GREEN);
		context.fillRect(70, 10, 20, 20, Color.BLUE);
		GLRecording recording = context.endRecording();
		if (recording.getCommandCount() != 1)
			throw new AssertionError("Expected one coalesced filled command");
		if (renderer.drawCallCount() != 0)
			throw new AssertionError("Recording unexpectedly submitted a draw call");

		context.draw(recording);
		renderer.endFrame();
		if (renderer.drawCallCount() != 1)
			throw new AssertionError(
					"Compatible recording boundary split the filled batch: got "
							+ renderer.drawCallCount() + " draw calls");
		checkColor(gl, 20, 20, Color.RED, "immediate filled geometry");
		checkColor(gl, 50, 20, Color.GREEN, "first recorded filled geometry");
		checkColor(gl, 80, 20, Color.BLUE, "second recorded filled geometry");
	}

	private static void testTextureAndTextCoalescing(GL3 gl,
			GLRenderer2D renderer, GLTexture texture, GLFont font) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.beginRecording();
		context.drawTexture(texture, 10, 10, 20, 20);
		context.drawTexture(texture, 35, 10, 20, 20);
		context.drawText(font, "A", 10, 60, Color.WHITE);
		context.drawText(font, "B", 35, 60, Color.WHITE);
		GLRecording recording = context.endRecording();
		if (recording.getCommandCount() != 2)
			throw new AssertionError(
					"Expected one coalesced texture and one coalesced text command, got "
							+ recording.getCommandCount());

		context.draw(recording);
		renderer.endFrame();
		if (renderer.drawCallCount() != 2)
			throw new AssertionError(
					"Coalesced texture/text replay used "
							+ renderer.drawCallCount() + " draw calls instead of two");
		checkColor(gl, 20, 20, Color.WHITE, "first coalesced texture");
		checkColor(gl, 45, 20, Color.WHITE, "second coalesced texture");
	}

	private static void testMixedRecording(GL3 gl, GLRenderer2D renderer,
			GLTexture texture, GLFont font) {
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		GLTransform buildOuter = new GLTransform().translate(7, 9);
		context.setTransform(buildOuter);
		context.beginRecording();
		if (!context.getTransform().isIdentity())
			throw new AssertionError("A recording did not start in local coordinates");
		context.setTransform(new GLTransform().translate(50, 10));
		context.setMouseTarget(target);
		context.fillRect(0, 0, 20, 20, Color.CYAN);
		context.drawTexture(texture, 25, 0, 16, 16);
		context.drawText(font, "R", 48, 18, Color.WHITE);
		context.beginLayer(0, 30, 70, 25);
		context.fillRect(0, 30, 70, 25, new Color(255, 128, 0));
		context.endLayer(0.75f);
		context.flush();
		GLRecording recording = context.endRecording();
		if (recording.getCommandCount() != 8)
			throw new AssertionError("Expected eight mixed recording commands, got "
					+ recording.getCommandCount());
		if (renderer.drawCallCount() != 0)
			throw new AssertionError("Mixed recording unexpectedly submitted a draw call");
		if (!context.getTransform().equals(buildOuter))
			throw new AssertionError("endRecording() did not restore the outer transform");

		GLTransform replayOuter = new GLTransform().translate(20, 15);
		context.setTransform(replayOuter);
		context.clearMouseTarget();
		context.draw(recording);
		if (!context.getTransform().equals(replayOuter))
			throw new AssertionError("Recording replay did not restore the parent transform");
		renderer.endFrame();

		checkColor(gl, 75, 30, Color.CYAN, "relative recorded filled geometry");
		checkColor(gl, 100, 30, Color.WHITE, "relative recorded texture");
		GLPickResult result = renderer.readPickResult(gl, 75, 30);
		if (result.getTarget() != target)
			throw new AssertionError("Recorded mouse target was not restored");
		if (!result.hasLocalPosition()
				|| Math.abs(result.getLocalX() - 5) > 0.01f
				|| Math.abs(result.getLocalY() - 5) > 0.01f)
			throw new AssertionError("Recorded local pick position is incorrect: "
					+ result.getLocalX() + "/" + result.getLocalY());
	}

	private static void testRenderOutputsAreAmbient(GL3 gl,
			GLRenderer2D renderer) {
		GLMouseTarget ambientTarget = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT), false);
		context.setRenderOutputs(true, false);
		context.beginRecording();
		context.setMouseTarget(ambientTarget);
		context.fillRect(20, 20, 40, 30, Color.MAGENTA);
		GLRecording ambientRecording = context.endRecording();

		context.beginRecording();
		context.setRenderOutputs(true, false);
		context.setMouseTarget(new GLMouseTarget() {
		});
		context.fillRect(90, 20, 40, 30, Color.YELLOW);
		GLRecording explicitColorOnlyRecording = context.endRecording();
		renderer.endFrame();

		context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.draw(ambientRecording);
		context.draw(explicitColorOnlyRecording);
		renderer.endFrame();

		GLPickResult ambient = renderer.readPickResult(gl, 30, 30);
		if (ambient.getTarget() != ambientTarget)
			throw new AssertionError(
					"Recording captured the color-only build frame and disabled later picking");
		if (renderer.readPickResult(gl, 100, 30).isHit())
			throw new AssertionError(
					"Explicit setRenderOutputs command inside a recording was not preserved");
	}

	private static void testRecordedScreenStrokeIgnoresReplayScale(
			GL3 gl, GLRenderer2D renderer) {
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.beginRecording();
		context.setStroke(new GLStroke(6)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN)
				.withCap(GLStroke.Cap.BUTT)
				.withDash(10, 10));
		context.drawLine(10, 20, 50, 20, Color.WHITE, target);
		GLRecording recording = context.endRecording();

		context.clear(Color.BLACK);
		context.setTransform(new GLTransform().translate(20, 20).scale(3));
		context.draw(recording,
				new GLRenderParameters().setColor(Color.CYAN).setOpacity(0.75f));
		renderer.endFrame();

		checkColor(gl, 55, 80, new Color(0, 191, 191),
				"recorded SCREEN dash visible run");
		checkColor(gl, 65, 80, Color.BLACK,
				"recorded SCREEN dash gap");
		checkColor(gl, 55, 72, Color.BLACK,
				"recorded SCREEN width scaled with its parent");
		GLPickResult pick = renderer.readPickResult(gl, 55, 80);
		if (pick.getTarget() != target || !pick.hasLocalPosition()
				|| Math.abs(pick.getLocalX() - 1.666667f) > 0.02f
				|| Math.abs(pick.getLocalY()) > 0.02f)
			throw new AssertionError("Recorded SCREEN stroke picking is incorrect: "
					+ pick.getLocalX() + "/" + pick.getLocalY());
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
					+ expected.getRed() + "/" + expected.getGreen() + "/" + expected.getBlue()
					+ ", got " + red + "/" + green + "/" + blue);
	}

	private static BufferedImage solidImage() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				image.setRGB(x, y, Color.WHITE.getRGB());
		return image;
	}
}
