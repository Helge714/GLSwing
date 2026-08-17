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
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;

/** GPU integration checks for integer IDs, ordering and texture alpha picking. */
public final class PickingGpuSelfTest {

	private static final int WIDTH = 200;
	private static final int HEIGHT = 160;

	private PickingGpuSelfTest() {
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
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			texture = GLTexture.create(gl, alphaTexture());
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);

			context.setPickId(101);
			context.fillRect(10, 10, 60, 60, Color.RED);
			context.fillRect(30, 30, 60, 60, Color.BLUE, 202);
			context.clearPickId();
			context.drawTexture(texture, 100, 20, 64, 64, 303);
			renderer.endFrame();

			checkId(renderer, gl, 20, 20, 101, "context Pick-ID");
			checkId(renderer, gl, 40, 40, 202, "one-shot Pick-ID and draw order");
			checkId(renderer, gl, 108, 52, 303, "opaque texture pixel");
			checkId(renderer, gl, 132, 52, 303, "default texture alpha threshold");
			checkId(renderer, gl, 156, 52, GLContext2D.NO_PICK_ID, "transparent texture pixel");
			checkId(renderer, gl, 180, 140, GLContext2D.NO_PICK_ID, "empty background");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setTransform(new GLTransform()
					.translate(100, 80)
					.rotate((float) (Math.PI / 2))
					.scale(2));
			context.fillRect(10, 10, 20, 10, Color.GREEN, 505);
			context.drawTexture(texture, 10, 30, 20, 10, 515);
			renderer.endFrame();
			checkId(renderer, gl, 70, 120, 505, "translated, rotated and zoomed geometry");
			checkId(renderer, gl, 30, 104, 515,
					"translated, rotated and zoomed texture");
			checkId(renderer, gl, 30, 136, GLContext2D.NO_PICK_ID,
					"transformed transparent texture pixel");
			checkId(renderer, gl, 20, 20, GLContext2D.NO_PICK_ID, "picking buffer frame clear");

			GLMouseTarget target = new GLMouseTarget() {
			};
			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setTransform(new GLTransform()
					.translate(100, 80)
					.rotate((float) (Math.PI / 2))
					.scale(2));
			context.setMouseTarget(target);
			context.fillRect(10, 10, 20, 10, Color.GREEN);
			context.fillRect(25, 10, 10, 10, Color.YELLOW);
			renderer.endFrame();
			checkTarget(renderer, gl, 70, 120, target, 10, 5,
					"automatic rectangle-local coordinates");
			checkTarget(renderer, gl, 70, 144, target, 22, 5,
					"stable target origin across multiple draw commands");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.fillRect(10, 10, 40, 40, Color.RED, 606);
			context.clear(new Color(8, 12, 20));
			context.fillRect(100, 20, 40, 40, Color.CYAN, 707);
			renderer.endFrame();
			checkId(renderer, gl, 20, 20, GLContext2D.NO_PICK_ID,
					"context clear removes pending visible and pick geometry");
			checkId(renderer, gl, 110, 30, 707, "drawing after context clear");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.drawTexture(texture, 10, 20, 64, 64, 811);
			context.setTexturePickAlphaThreshold(0.5f);
			context.drawTexture(texture, 110, 20, 64, 64, 822);
			renderer.endFrame();
			checkId(renderer, gl, 42, 52, 811,
					"texture pixel above the default alpha threshold");
			checkId(renderer, gl, 142, 52, GLContext2D.NO_PICK_ID,
					"texture pixel below the changed alpha threshold");
			checkId(renderer, gl, 118, 52, 822,
					"opaque pixel above the changed alpha threshold");

			context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setPickingRenderingEnabled(false);
			context.fillRect(10, 10, 40, 40, Color.RED, 901);
			context.setRenderOutputs(false, true);
			context.fillRect(70, 10, 40, 40, Color.GREEN, 902);
			renderer.endFrame();
			checkId(renderer, gl, 20, 20, GLContext2D.NO_PICK_ID,
					"color-only geometry stays out of picking");
			checkId(renderer, gl, 80, 20, 902,
					"picking-only geometry writes an ID");
			checkColor(gl, 20, 20, Color.RED, "color-only geometry stays visible");
			checkColor(gl, 80, 20, Color.BLACK,
					"picking-only geometry stays out of the color target");

			testAsynchronousSnapshot(gl, renderer);
			testSnapshotBurstKeepsNewestGeneration(gl, renderer);

			System.out.println("Picking GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (texture != null)
				texture.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void testAsynchronousSnapshot(GL3 gl,
			GLRenderer2D renderer) {
		GLMouseTarget first = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.setMouseTarget(first);
		context.fillRect(20, 20, 50, 40, Color.RED);
		renderer.endFrame();

		GLPickResult firstSnapshot = awaitSnapshot(renderer, gl,
				25, 25, result -> result != null && result.getTarget() == first);
		if (Math.abs(firstSnapshot.getLocalX() - 5) > 0.01f
				|| Math.abs(firstSnapshot.getLocalY() - 5) > 0.01f)
			throw new AssertionError("Async snapshot lost its paired transform records");

		GLMouseTarget second = new GLMouseTarget() {
		};
		context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		if (renderer.isLatestPickSnapshotReady())
			throw new AssertionError(
					"Previous snapshot remained current while a replacement ID frame was active");
		context.clear(Color.BLACK);
		context.setMouseTarget(second);
		context.fillRect(110, 90, 50, 40, Color.BLUE);
		renderer.endFrame();

		// Publishing is deliberately fence-driven. Until the new PBO completes,
		// the previous internally consistent snapshot remains immediately usable.
		GLPickResult stale = renderer.readPickSnapshot(25, 25, WIDTH, HEIGHT);
		if (stale == null || stale.getTarget() != first)
			throw new AssertionError("Pending transfer discarded the previous snapshot");
		awaitSnapshot(renderer, gl, 115, 95,
				result -> result != null && result.getTarget() == second);
		GLPickResult replaced = renderer.readPickSnapshot(25, 25, WIDTH, HEIGHT);
		if (replaced == null || replaced.isHit())
			throw new AssertionError("Completed snapshot did not replace stale IDs");
		if (renderer.readPickSnapshot(25, 25, WIDTH + 1, HEIGHT) != null)
			throw new AssertionError("Snapshot accepted incompatible dimensions");
	}

	private static void testSnapshotBurstKeepsNewestGeneration(GL3 gl,
			GLRenderer2D renderer) {
		GLMouseTarget newest = null;
		for (int generation = 0; generation < 12; generation++) {
			GLMouseTarget target = new GLMouseTarget() {
			};
			newest = target;
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setMouseTarget(target);
			context.fillRect(0, 0, WIDTH, HEIGHT, Color.WHITE);
			renderer.endFrame();
		}
		GLMouseTarget expected = newest;
		awaitSnapshot(renderer, gl, WIDTH / 2, HEIGHT / 2,
				result -> result != null && result.getTarget() == expected);
	}

	private static GLPickResult awaitSnapshot(GLRenderer2D renderer, GL3 gl,
			int x, int y, java.util.function.Predicate<GLPickResult> accepted) {
		long deadline = System.nanoTime() + 2_000_000_000L;
		gl.glFlush();
		do {
			renderer.pollPickSnapshots(gl);
			GLPickResult result = renderer.readPickSnapshot(x, y, WIDTH, HEIGHT);
			if (accepted.test(result))
				return result;
			Thread.onSpinWait();
		} while (System.nanoTime() < deadline);
		throw new AssertionError("Asynchronous picking snapshot did not complete");
	}

	private static void checkTarget(GLRenderer2D renderer, GL3 gl, int x, int y,
			GLMouseTarget expectedTarget, float expectedLocalX, float expectedLocalY,
			String description) {
		GLPickResult result = renderer.readPickResult(gl, x, y);
		if (result.getTarget() != expectedTarget)
			throw new AssertionError(description + ": wrong target");
		if (!result.hasLocalPosition()
				|| Math.abs(result.getLocalX() - expectedLocalX) > 0.01f
				|| Math.abs(result.getLocalY() - expectedLocalY) > 0.01f)
			throw new AssertionError(description + ": expected local " + expectedLocalX + "/"
					+ expectedLocalY + ", got " + result.getLocalX() + "/" + result.getLocalY());
		if (result.getId() != GLContext2D.NO_PICK_ID)
			throw new AssertionError(description + ": target-only hit exposed an integer ID");
	}

	private static void checkId(GLRenderer2D renderer, GL3 gl,
			int x, int y, int expected, String description) {
		int actual = renderer.readPickId(gl, x, y);
		if (actual != expected)
			throw new AssertionError(description + ": expected " + expected + ", got " + actual);
	}

	private static void checkColor(GL3 gl, int x, int y, Color expected,
			String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (Math.abs(red - expected.getRed()) > 2
				|| Math.abs(green - expected.getGreen()) > 2
				|| Math.abs(blue - expected.getBlue()) > 2)
			throw new AssertionError(description + ": expected " + expected.getRed() + "/"
					+ expected.getGreen() + "/" + expected.getBlue() + ", got "
					+ red + "/" + green + "/" + blue);
	}

	private static BufferedImage alphaTexture() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = x < image.getWidth() / 3 ? 255
						: x < image.getWidth() * 2 / 3 ? 64 : 0;
				image.setRGB(x, y, new Color(255, 255, 255, alpha).getRGB());
			}
		}
		return image;
	}
}
