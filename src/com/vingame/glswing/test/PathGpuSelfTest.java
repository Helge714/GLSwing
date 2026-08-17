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
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLPath2D;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLSvgPathParser;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.GLSvgDocument;
import com.vingame.glswing.rendering.Projection2D;

/** GPU integration checks for filled, curved and recorded stroked paths. */
public final class PathGpuSelfTest {

	private static final int WIDTH = 320;
	private static final int HEIGHT = 200;

	private PathGpuSelfTest() {
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
		Throwable failure = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			testFillRulesCurvesAndPicking(gl, renderer);
			testRecordedScreenStrokeAndSnapshot(gl, renderer);
			testImportedSvgDocument(gl, renderer);
			System.out.println("Path GPU self-test passed.");
		} catch (RuntimeException | Error exception) {
			failure = exception;
			throw exception;
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null) {
				try {
					renderer.dispose(gl);
				} catch (RuntimeException | Error cleanupFailure) {
					if (failure != null)
						failure.addSuppressed(cleanupFailure);
					else
						throw cleanupFailure;
				}
			}
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testImportedSvgDocument(GL3 gl, GLRenderer2D renderer) {
		GLSvgDocument document = GLSvgDocument.loadResource(
				"com/vingame/glswing/icons/test_icon.svg");
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.beginRecording();
		document.draw(context, 60, 10, 180, 180);
		GLRecording recording = context.endRecording();
		context.clear(Color.BLACK);
		context.draw(recording);
		renderer.endFrame();

		checkColor(gl, 62, 12, Color.BLACK, "rounded SVG corner clipping");
		checkNotBlack(gl, 70, 100, "SVG gradient background");
		checkNotBlack(gl, 150, 100, "SVG imported content");
		checkBrighter(gl, 68, 100, 232, 100, "SVG left-to-right gradient");
	}

	private static void testFillRulesCurvesAndPicking(GL3 gl,
			GLRenderer2D renderer) {
		GLMouseTarget target = new GLMouseTarget() {
		};
		GLPath2D ring = new GLPath2D(GLPath2D.FillRule.EVEN_ODD)
				.moveTo(10, 10).lineTo(110, 10).lineTo(110, 110)
				.lineTo(10, 110).closePath()
				.moveTo(40, 40).lineTo(80, 40).lineTo(80, 80)
				.lineTo(40, 80).closePath();
		GLPath2D curved = new GLPath2D()
				.moveTo(160, 130)
				.cubicTo(140, 80, 145, 30, 200, 30)
				.cubicTo(255, 30, 260, 80, 240, 130)
				.closePath();
		// SVG circles commonly return explicitly to their first point before Z.
		// The closing point must be normalized before polygon tessellation.
		GLPath2D svgCircle = GLSvgPathParser.parse(
				"M305 50 A25 25 0 1 1 255 50 A25 25 0 1 1 305 50 Z");

		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.fillPath(ring, Color.GREEN, target);
		context.fillPath(curved, Color.RED);
		context.fillPath(svgCircle, Color.BLUE);
		renderer.endFrame();

		checkColor(gl, 20, 20, Color.GREEN, "even-odd outer contour");
		checkColor(gl, 60, 60, Color.BLACK, "even-odd hole");
		checkColor(gl, 200, 80, Color.RED, "cubic filled path");
		checkColor(gl, 280, 50, Color.BLUE, "closed SVG arc path");
		GLPickResult pick = renderer.readPickResult(gl, 20, 20);
		if (pick.getTarget() != target || !pick.hasLocalPosition()
				|| Math.abs(pick.getLocalX() - 10) > 0.02f
				|| Math.abs(pick.getLocalY() - 10) > 0.02f)
			throw new AssertionError("Filled path local picking is incorrect");
		if (renderer.readPickResult(gl, 60, 60).isHit())
			throw new AssertionError("Even-odd path hole was pickable");
	}

	private static void testRecordedScreenStrokeAndSnapshot(GL3 gl,
			GLRenderer2D renderer) {
		GLPath2D source = new GLPath2D().moveTo(10, 20).lineTo(50, 20);
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.beginRecording();
		context.setStroke(new GLStroke(6)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN)
				.withDash(10, 10));
		context.drawPath(source, Color.WHITE);
		GLRecording recording = context.endRecording();
		source.reset().moveTo(200, 150).lineTo(300, 150);

		context.clear(Color.BLACK);
		context.setTransform(new GLTransform().translate(20, 20).scale(3));
		context.draw(recording);
		renderer.endFrame();

		checkColor(gl, 55, 80, Color.WHITE, "recorded path dash");
		checkColor(gl, 65, 80, Color.BLACK, "recorded path dash gap");
		checkColor(gl, 55, 72, Color.BLACK, "recorded path screen width");
		checkColor(gl, 250, 150, Color.BLACK, "recorded path snapshot");
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

	private static void checkNotBlack(GL3 gl, int x, int y, String description) {
		int[] color = readColor(gl, x, y);
		if (color[0] + color[1] + color[2] < 12)
			throw new AssertionError(description + " remained black");
	}

	private static void checkBrighter(GL3 gl, int firstX, int firstY,
			int secondX, int secondY, String description) {
		int[] first = readColor(gl, firstX, firstY);
		int[] second = readColor(gl, secondX, secondY);
		int firstBrightness = first[0] + first[1] + first[2];
		int secondBrightness = second[0] + second[1] + second[2];
		if (secondBrightness <= firstBrightness + 20)
			throw new AssertionError(description + " was not visibly increasing");
	}

	private static int[] readColor(GL3 gl, int x, int y) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		return new int[] {
				pixel.get(0) & 0xff,
				pixel.get(1) & 0xff,
				pixel.get(2) & 0xff,
				pixel.get(3) & 0xff
		};
	}
}
