package com.vingame.glswing.test;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** GPU checks for the shared instanced standard-control batch. */
public final class UIQuadBatchGpuSelfTest {

	private static final int WIDTH = 180;
	private static final int HEIGHT = 90;

	private UIQuadBatchGpuSelfTest() {
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
		GLFont font = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 24);
			try {
				testRoundedControlMatrix(gl, renderer);
				testSliderValueMatrix(gl, renderer);
				testHiDpiAndTransparency(gl, renderer);
				testGroupedContours(gl, renderer);
				testMixedRecordedInstances(gl, renderer, font);
				testAsymmetricRoundedFill(gl, renderer);
				testScreenSpaceBorderUnderZoom(gl, renderer);
				testMinimumProgressInsideBorder(gl, renderer);
				testSliderPrimitives(gl, renderer);
				testCustomUIShader(gl, renderer);
				testThousandRecordedControls(gl, renderer, font);
			} catch (RuntimeException | Error failure) {
				try {
					renderer.endFrame();
				} catch (RuntimeException cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
				throw failure;
			}
			System.out.println("UI quad batch GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (font != null)
				font.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testRoundedControlMatrix(GL3 gl,
			GLRenderer2D renderer) {
		float[] radii = { 0, 0.5f, 1, 2, 4, 10 };
		float[] borders = { 1, 2, 4 };
		for (float radius : radii) {
			for (float border : borders) {
				GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
						Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
				context.clear(Color.BLACK);
				context.fillAndDrawUIRoundRect(20, 20, 80, 40, radius, border,
						Color.RED, Color.BLUE, GLStroke.WidthSpace.SCREEN);
				renderer.endFrame();

				String caseName = "radius=" + radius + ", border=" + border;
				if (radius <= 1) {
					checkRegionsEqual(gl, 20, 20, 97, 20, 3, 3, 3,
							caseName + " top-corner symmetry");
					checkRegionsEqual(gl, 20, 57, 97, 57, 3, 3, 3,
							caseName + " bottom-corner symmetry");
					checkRegionsEqual(gl, 20, 20, 20, 57, 3, 3, 3,
							caseName + " left-corner symmetry");
				}
				checkDominantColor(gl, 20, 40, Color.BLUE,
						caseName + " left border");
				checkDominantColor(gl, 60, 40, Color.RED,
						caseName + " fill");
			}
		}
	}

	private static void testSliderValueMatrix(GL3 gl,
			GLRenderer2D renderer) {
		float[] values = { 0, 0.03f, 0.5f, 0.97f, 1 };
		for (float value : values) {
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.fillUISlider(20, 20, 120, 32, 4, value, 2,
					new Color(40, 40, 40), Color.RED, new Color(120, 0, 0),
					Color.BLUE, GLStroke.WidthSpace.SCREEN);
			renderer.endFrame();

			checkDominantColor(gl, 20, 36, Color.BLUE,
					"slider " + value + " left border");
			checkDominantColor(gl, 139, 36, Color.BLUE,
					"slider " + value + " right border");
			if (value >= 0.5f)
				checkDominantColor(gl, 50, 36, Color.RED,
						"slider " + value + " progress interior");
			else
				checkColor(gl, 50, 36, new Color(40, 40, 40),
						"slider " + value + " background interior");
		}
	}

	private static void testHiDpiAndTransparency(GL3 gl,
			GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH / 2f, HEIGHT / 2f,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH / 2f, HEIGHT / 2f));
		context.clear(new Color(20, 80, 20));
		context.fillAndDrawUIRoundRect(10, 8, 60, 28, 6, 1,
				new Color(255, 0, 0, 128), Color.BLUE,
				GLStroke.WidthSpace.SCREEN);
		renderer.endFrame();

		checkDominantColor(gl, 20, 44, Color.BLUE,
				"2x screen-space border");
	}

	private static void testGroupedContours(GL3 gl,
			GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillUIRoundRect(20, 20, 60, 32,
				new GLCornerRadii(6, 0, 0, 6), Color.RED);
		context.fillUIRoundRect(80, 20, 60, 32,
				new GLCornerRadii(0, 6, 6, 0), Color.RED);
		context.drawUIRoundRect(20, 20, 120, 32, 6, 2, Color.BLUE,
				GLStroke.WidthSpace.SCREEN);
		renderer.endFrame();

		checkDominantColor(gl, 79, 36, Color.RED, "grouped left seam");
		checkDominantColor(gl, 80, 36, Color.RED, "grouped right seam");
		checkDominantColor(gl, 20, 36, Color.BLUE, "grouped outer border");
		checkDominantColor(gl, 139, 36, Color.BLUE, "grouped outer border");
	}

	private static void testCustomUIShader(GL3 gl,
			GLRenderer2D renderer) {
		String fragment = loadResource(
				"/com/vingame/glswing/shaders/ui_quad.frag")
				.replace("out vec4 fragColor;",
						"uniform float uTestAlpha;\nout vec4 fragColor;")
				.replace("fragColor = vec4(color.rgb, color.a * coverage * clipCoverage);",
						"fragColor = vec4(color.rgb, color.a * coverage * clipCoverage * uTestAlpha);");
		ShaderProgram shader = ShaderProgram.fromUIFragmentSource(gl, fragment);
		try {
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.clear(Color.BLACK);
			context.setUIShader(shader);
			context.setUIUniform("uTestAlpha", 0.25f);
			context.fillUIRoundRect(10, 10, 50, 40, 4, Color.RED);
			renderer.endFrame();

			if (renderer.drawCallCount() != 1)
				throw new AssertionError("Custom UI shader used "
						+ renderer.drawCallCount() + " draw calls instead of one");
			ByteBuffer pixel = readPixel(gl, 30, 30);
			int red = pixel.get(0) & 0xff;
			if (red < 45 || red > 85)
				throw new AssertionError(
						"Custom UI uniform did not affect output: red=" + red);
			if (gl.glGetError() != GL.GL_NO_ERROR)
				throw new AssertionError("Custom UI shader produced an OpenGL error");
		} finally {
			shader.dispose(gl);
		}
	}

	private static String loadResource(String path) {
		try (InputStream stream = UIQuadBatchGpuSelfTest.class
				.getResourceAsStream(path)) {
			if (stream == null)
				throw new IllegalArgumentException("Resource not found: " + path);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException error) {
			throw new IllegalStateException("Could not read resource: " + path,
					error);
		}
	}

	private static void testAsymmetricRoundedFill(GL3 gl,
			GLRenderer2D renderer) {
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.setMouseTarget(target, 10, 10);
		context.fillUIRoundRect(10, 10, 60, 40,
				new GLCornerRadii(12, 0, 12, 0), Color.RED);
		context.fillAndDrawUIRoundRect(90, 10, 60, 40,
				new GLCornerRadii(10, 0, 10, 0), 2, Color.RED, Color.BLUE,
				GLStroke.WidthSpace.SCREEN);
		context.drawUIRoundRect(10, 60, 60, 20,
				new GLCornerRadii(0.5f, 0.5f, 0.5f, 0.5f), 2,
				Color.BLUE, GLStroke.WidthSpace.SCREEN);
		context.clearMouseTarget();
		renderer.endFrame();

		checkColor(gl, 10, 10, Color.BLACK,
				"asymmetric rounded top-left corner");
		checkColor(gl, 68, 11, Color.RED,
				"asymmetric square top-right corner");
		checkColor(gl, 68, 48, Color.BLACK,
				"asymmetric rounded bottom-right corner");
		checkColor(gl, 11, 48, Color.RED,
				"asymmetric square bottom-left corner");
		checkDominantColor(gl, 96, 11, Color.BLUE,
				"combined asymmetric rounded border");
		checkDominantColor(gl, 148, 11, Color.BLUE,
				"combined asymmetric square border");
		checkDominantColor(gl, 91, 48, Color.BLUE,
				"combined asymmetric square bottom-left border");
		checkDominantColor(gl, 120, 30, Color.RED,
				"combined asymmetric fill");
		checkDominantColor(gl, 10, 60, Color.BLUE,
				"subpixel asymmetric stroke top-left corner");
		checkDominantColor(gl, 68, 60, Color.BLUE,
				"subpixel asymmetric stroke top-right corner");
		checkDominantColor(gl, 10, 78, Color.BLUE,
				"subpixel asymmetric stroke bottom-left corner");
		checkDominantColor(gl, 68, 78, Color.BLUE,
				"subpixel asymmetric stroke bottom-right corner");
		if (renderer.readPickResult(gl, 10, 10).isHit())
			throw new AssertionError(
					"Transparent asymmetric UI corner was pickable");
		if (renderer.readPickResult(gl, 68, 11).getTarget() != target)
			throw new AssertionError(
					"Square asymmetric UI corner was not pickable");
	}

	private static void testSliderPrimitives(GL3 gl, GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillUIProgressRoundRect(10, 10, 100, 32, 8, 0.5f,
				new Color(120, 170, 235), new Color(45, 80, 145));
		context.fillUIProgressRoundRect(10, 50, 100, 30, 0, 8, 0.5f,
				new Color(120, 170, 235), new Color(45, 80, 145));
		context.drawUIChevron(125, 10, 18, 32, false, 2, Color.WHITE,
				GLStroke.WidthSpace.SCREEN);
		context.drawUIChevron(150, 10, 18, 32, true, 2, Color.WHITE,
				GLStroke.WidthSpace.SCREEN);
		renderer.endFrame();

		checkGradient(gl, 25, 15, 25, 36);
		checkColor(gl, 80, 26, Color.BLACK, "progress clipping");
		checkColor(gl, 10, 79, Color.BLACK,
				"vertical-corner progress bottom rounding");
		checkVisibleBrightPixels(gl, 125, 10, 18, 32, "left chevron");
		checkVisibleBrightPixels(gl, 150, 10, 18, 32, "right chevron");
	}

	private static void testMinimumProgressInsideBorder(GL3 gl,
			GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillUISlider(10, 10, 100, 32, 2, 0.03f, 2,
				new Color(45, 45, 45), Color.RED, new Color(120, 0, 0),
				Color.BLUE, GLStroke.WidthSpace.SCREEN);
		renderer.endFrame();

		checkDominantColor(gl, 11, 37, Color.BLUE,
				"minimum slider progress behind bottom-left border");
		checkDominantColor(gl, 11, 25, Color.BLUE,
				"minimum slider progress behind left border");
	}

	private static void testScreenSpaceBorderUnderZoom(GL3 gl,
			GLRenderer2D renderer) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillAndDrawUIRoundRect(10, 10, 30, 20, 0.5f, 2,
				Color.RED, Color.BLUE, GLStroke.WidthSpace.SCREEN);
		context.setTransform(new GLTransform().translate(70, 10).scale(3));
		context.fillAndDrawUIRoundRect(0, 0, 20, 20, 0, 2,
				Color.RED, Color.BLUE, GLStroke.WidthSpace.SCREEN);
		renderer.endFrame();

		checkDominantColor(gl, 11, 20, Color.BLUE,
				"unscaled screen-space border");
		checkDominantColor(gl, 14, 20, Color.RED,
				"unscaled border interior");
		checkDominantColor(gl, 10, 29, Color.BLUE,
				"square bottom-left border corner");
		checkDominantColor(gl, 12, 12, Color.RED,
				"square top-left fill corner");
		checkDominantColor(gl, 39, 10, Color.BLUE,
				"square top-right border corner");
		checkDominantColor(gl, 71, 40, Color.BLUE,
				"zoomed screen-space border");
		checkDominantColor(gl, 74, 40, Color.RED,
				"zoomed border interior");
	}

	private static void testThousandRecordedControls(GL3 gl,
			GLRenderer2D renderer, GLFont font) {
		final int controlCount = 1_000;
		GLRecording[] recordings = new GLRecording[controlCount];
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.setMouseTarget(target, 0, 0);
		for (int index = 0; index < controlCount; index++) {
			context.beginRecording();
			context.fillUIRoundRect(0, 0, 28, 16, 3, Color.WHITE);
			context.drawUIText(font, Integer.toString(index % 100),
					3, 13, Color.BLACK);
			recordings[index] = context.endRecording();
		}
		context.clearMouseTarget();
		context.clear(Color.BLACK);
		for (int index = 0; index < controlCount; index++) {
			float x = (index % 50) * 3;
			float y = (index / 50) * 3;
			context.setTransform(new GLTransform().translate(x, y));
			context.draw(recordings[index]);
		}
		renderer.endFrame();

		if (renderer.drawCallCount() != 2)
			throw new AssertionError(controlCount + " recorded controls used "
					+ renderer.drawCallCount()
					+ " draw calls instead of one color and one picking call");
	}

	private static void testMixedRecordedInstances(GL3 gl,
			GLRenderer2D renderer, GLFont font) {
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.beginRecording();
		context.setMouseTarget(target);
		context.drawUIRoundRectShadow(10, 10, 40, 32, 9, 8, 0, 3,
				new Color(255, 0, 255, 210));
		context.fillAndDrawUIRoundRect(
				10, 10, 40, 32, 9, 3, Color.RED, Color.BLUE);
		context.drawUIText(font, "UI", 65, 36, Color.WHITE);
		context.fillUIRoundRect(120, 10, 40, 32, 9, Color.GREEN);
		context.drawUICheckMark(80, 54, 12, 12, 2, Color.CYAN);
		context.addUIHitArea(75, 50, 40, 22);
		GLRecording recording = context.endRecording();
		context.clearMouseTarget();
		context.clear(Color.BLACK);
		context.draw(recording);
		renderer.endFrame();

		if (renderer.drawCallCount() != 2)
			throw new AssertionError("Mixed UI shapes and text used "
					+ renderer.drawCallCount()
					+ " draw calls instead of one color and one picking call");
		checkColor(gl, 30, 26, Color.RED, "first rounded rectangle");
		checkColor(gl, 30, 11, Color.BLUE, "combined rounded border");
		checkColor(gl, 140, 26, Color.GREEN, "second rounded rectangle");
		checkVisibleMagentaShadow(gl, 10, 10,
				"shadow behind rounded transparent corner");
		checkVisibleMagentaShadow(gl, 30, 47, "analytic rounded shadow");
		checkVisibleText(gl, 60, 8, 50, 40);
		checkVisibleCheckMark(gl, 80, 54, 12, 12);

		GLPickResult pick = renderer.readPickResult(gl, 30, 26);
		if (pick.getTarget() != target)
			throw new AssertionError("UI instance did not preserve its mouse target");
		if (!pick.hasLocalPosition()
				|| Math.abs(pick.getLocalX() - 20) > 0.01f
				|| Math.abs(pick.getLocalY() - 16) > 0.01f)
			throw new AssertionError("Unexpected UI-local pick position: "
					+ pick.getLocalX() + "/" + pick.getLocalY());
		if (renderer.readPickResult(gl, 110, 68).getTarget() != target)
			throw new AssertionError("Invisible UI hit area was not pickable");
		if (renderer.readPickResult(gl, 30, 47).isHit())
			throw new AssertionError("Analytic UI shadow was pickable");
	}

	private static void checkVisibleCheckMark(GL3 gl, int x, int y,
			int width, int height) {
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(x, HEIGHT - y - height, width, height,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		for (int index = 0; index < width * height; index++) {
			int offset = index * 4;
			if ((pixels.get(offset) & 0xff) < 80
					&& (pixels.get(offset + 1) & 0xff) > 120
					&& (pixels.get(offset + 2) & 0xff) > 120)
				return;
		}
		throw new AssertionError("Analytic UI check mark produced no visible pixels");
	}

	private static void checkVisibleText(GL3 gl, int x, int y,
			int width, int height) {
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(x, HEIGHT - y - height, width, height,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		for (int index = 0; index < width * height; index++) {
			int offset = index * 4;
			if ((pixels.get(offset) & 0xff) > 80
					&& (pixels.get(offset + 1) & 0xff) > 80
					&& (pixels.get(offset + 2) & 0xff) > 80)
				return;
		}
		throw new AssertionError("MSDF text produced no visible pixels");
	}

	private static void checkVisibleBrightPixels(GL3 gl, int x, int y,
			int width, int height, String description) {
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(x, HEIGHT - y - height, width, height,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		for (int index = 0; index < width * height; index++) {
			int offset = index * 4;
			if ((pixels.get(offset) & 0xff) > 160
					&& (pixels.get(offset + 1) & 0xff) > 160
					&& (pixels.get(offset + 2) & 0xff) > 160)
				return;
		}
		throw new AssertionError(description + " produced no visible pixels");
	}

	private static void checkGradient(GL3 gl, int topX, int topY,
			int bottomX, int bottomY) {
		ByteBuffer top = readPixel(gl, topX, topY);
		ByteBuffer bottom = readPixel(gl, bottomX, bottomY);
		int topBlue = top.get(2) & 0xff;
		int bottomBlue = bottom.get(2) & 0xff;
		if (topBlue <= bottomBlue + 30)
			throw new AssertionError("Slider progress has no vertical gradient: "
					+ topBlue + " versus " + bottomBlue);
	}

	private static ByteBuffer readPixel(GL3 gl, int x, int y) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		return pixel;
	}

	private static void checkRegionsEqual(GL3 gl, int firstX, int firstY,
			int secondX, int secondY, int width, int height, int tolerance,
			String description) {
		long[] first = regionSums(gl, firstX, firstY, width, height);
		long[] second = regionSums(gl, secondX, secondY, width, height);
		int samples = width * height;
		for (int channel = 0; channel < 4; channel++) {
			long difference = Math.abs(first[channel] - second[channel]);
			if (difference > (long) tolerance * samples)
				throw new AssertionError(description + ": channel " + channel
						+ " average differs by " + difference / (float) samples);
		}
	}

	private static long[] regionSums(GL3 gl, int x, int y,
			int width, int height) {
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glReadPixels(x, HEIGHT - y - height, width, height,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		long[] sums = new long[4];
		for (int sample = 0; sample < width * height; sample++)
			for (int channel = 0; channel < 4; channel++)
				sums[channel] += pixels.get(sample * 4 + channel) & 0xff;
		return sums;
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

	private static void checkVisibleMagentaShadow(GL3 gl, int x, int y,
			String description) {
		ByteBuffer pixel = readPixel(gl, x, y);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (red < 10 || blue < 10 || green >= Math.min(red, blue))
			throw new AssertionError(description
					+ ": expected a visible magenta shadow, got "
					+ red + "/" + green + "/" + blue);
	}

	private static void checkDominantColor(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		int expectedChannel = expected.getRed() > 0 ? red
				: expected.getGreen() > 0 ? green : blue;
		int otherMaximum = expected.getRed() > 0 ? Math.max(green, blue)
				: expected.getGreen() > 0 ? Math.max(red, blue)
						: Math.max(red, green);
		if (expectedChannel < 160 || expectedChannel <= otherMaximum)
			throw new AssertionError(description + ": expected dominant "
					+ expected + ", got " + red + "/" + green + "/" + blue);
	}
}
