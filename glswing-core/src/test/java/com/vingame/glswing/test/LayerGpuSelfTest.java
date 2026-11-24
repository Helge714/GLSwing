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
import com.vingame.glswing.rendering.GLBlendMode;
import com.vingame.glswing.rendering.GLComposite;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLLayerEffects;
import com.vingame.glswing.rendering.GLShape2D;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** GPU checks for group opacity, nested layers and picking through layers. */
public final class LayerGpuSelfTest {

	private static final int LOGICAL_WIDTH = 320;
	private static final int LOGICAL_HEIGHT = 350;
	private static final int DEVICE_SCALE = 2;
	private static final int SURFACE_WIDTH = LOGICAL_WIDTH * DEVICE_SCALE;
	private static final int SURFACE_HEIGHT = LOGICAL_HEIGHT * DEVICE_SCALE;
	private static final String CUSTOM_LAYER_FRAGMENT_SHADER = """
			#version 330 core
			in vec2 vTexCoord;
			uniform sampler2D uTexture;
			uniform float uOpacity;
			uniform vec2 uResolution;
			uniform vec2 uTexelSize;
			uniform float uGain;
			out vec4 fragColor;
			void main() {
			    vec4 source = texture(uTexture, vTexCoord);
			    float resolutionCheck = (uResolution * uTexelSize).x;
			    fragColor = vec4(source.rgb * uGain * resolutionCheck, source.a) * uOpacity;
			}
			""";

	private LayerGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		capabilities.setSampleBuffers(true);
		capabilities.setNumSamples(4);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(
						null, capabilities, null, SURFACE_WIDTH, SURFACE_HEIGHT);
		drawable.display();

		GLContext glContext = drawable.getContext();
		if (glContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make the offscreen OpenGL context current");

		GLRenderer2D renderer = null;
		ShaderProgram customLayerShader = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT);
			renderer = GLRenderer2D.create(gl);
			customLayerShader = ShaderProgram.fromLayerFragmentSource(
					gl, CUSTOM_LAYER_FRAGMENT_SHADER);
			GLContext2D context = renderer.beginFrame(gl, LOGICAL_WIDTH, LOGICAL_HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(LOGICAL_WIDTH, LOGICAL_HEIGHT));
			context.clear(Color.BLACK);

			context.beginLayer();
			context.fillRect(10, 10, 60, 40, Color.WHITE);
			context.fillRect(40, 10, 60, 40, Color.WHITE);
			context.endLayer(0.5f);

			context.beginLayer();
			context.fillRect(10, 70, 50, 40, Color.RED);
			context.beginLayer();
			context.fillRect(80, 70, 50, 40, Color.GREEN);
			context.endLayer(0.5f);
			context.endLayer(0.5f);

			context.beginLayer();
			context.fillRect(140, 20, 25, 25, Color.YELLOW, 77);
			context.endLayer(0);

			// Half a logical pixel is exactly one physical pixel at 200 % scaling.
			context.beginLayer();
			context.fillRect(120, 120, 0.5f, 10, Color.WHITE);
			context.endLayer();

			Color backdrop = new Color(100, 150, 200);
			Color source = new Color(128, 64, 255);
			drawBlendSample(context, 10, 150, backdrop, source, GLBlendMode.NORMAL);
			drawBlendSample(context, 40, 150, backdrop, source, GLBlendMode.ADD);
			drawBlendSample(context, 70, 150, backdrop, source, GLBlendMode.MULTIPLY);
			drawBlendSample(context, 100, 150, backdrop, source, GLBlendMode.SCREEN);
			drawBlendSample(context, 130, 150, backdrop, source, GLBlendMode.SUBTRACT);
			drawBlendSample(context, 160, 150, backdrop, source, GLBlendMode.ERASE);

			context.beginLayer();
			context.fillRect(10, 200, 30, 30, Color.WHITE);
			GLComposite invalidUniform = new GLComposite(0.5f)
					.withEffect(GLLayerEffects.custom(customLayerShader)
							.withUniform("uGain", 1));
			GLContext2D customContext = context;
			expectFailure(() -> customContext.endLayer(invalidUniform),
					"wrong custom layer uniform type");
			context.endLayer(new GLComposite(0.5f)
					.withEffect(GLLayerEffects.custom(customLayerShader)
							.withUniform("uGain", 0.5f)));

			context.beginLayer();
			context.fillRect(60, 205, 20, 20, Color.WHITE);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.blur(3.5f))
					.withEffect(GLLayerEffects.colorAdjust()));

			context.fillRect(100, 195, 70, 40, Color.WHITE);
			context.beginLayer();
			context.fillRect(110, 200, 20, 20, Color.RED);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.dropShadow(
							9, 9, 3.5f, new Color(0, 0, 0, 204)))
					.withEffect(GLLayerEffects.colorAdjust()));

			context.beginLayer();
			context.fillRect(195, 205, 20, 20, Color.RED);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.outline(3, Color.WHITE)));

			context.beginLayer();
			context.fillRect(235, 205, 20, 20, Color.RED);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.grayscale(1)));

			context.beginLayer();
			context.fillRect(275, 205, 20, 20, Color.WHITE);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.custom(customLayerShader)
							.withUniform("uGain", 0.5f))
					.withEffect(GLLayerEffects.custom(customLayerShader)
							.withUniform("uGain", 0.5f))
					.withEffect(GLLayerEffects.custom(customLayerShader)
							.withUniform("uGain", 0.5f)));

			context.beginLayer();
			context.fillRect(20, 250, 30, 30, Color.RED);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.innerOutline(3, Color.WHITE)));

			Color innerShadowBackdrop = new Color(40, 80, 140);
			context.fillRect(70, 245, 50, 40, innerShadowBackdrop);
			context.beginLayer();
			context.fillRect(75, 250, 40, 30, Color.WHITE);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.innerShadow(
							4, 4, 4, new Color(0, 0, 0, 230)))
					.withEffect(GLLayerEffects.colorAdjust()));

			// Reuse one bounded layer target at adjacent positions. The invisible
			// first layer must not leak stale blue pixels into the following blur.
			context.beginLayer(150, 250, 20, 20);
			context.fillRect(145, 245, 30, 30, Color.BLUE);
			context.endLayer(0);
			context.beginLayer(170, 250, 20, 20);
			context.fillRect(160, 245, 40, 30, Color.RED);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.blur(3)));

			context.beginLayer(230, 250, 30, 30);
			context.beginLayer(225, 245, 40, 40);
			context.fillRect(225, 245, 40, 40, Color.GREEN);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.outline(4, Color.WHITE)));
			context.endLayer();

			context.beginLayer(280, 250, 20, 20);
			context.fillRect(270, 245, 40, 30, Color.YELLOW, 88);
			context.endLayer();

			context.beginLayer(10, 300, 40, 30);
			context.fillRect(15, 305, 30, 20, Color.WHITE);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.blur(10)));

			context.beginLayer(70, 300, 80, 40);
			context.fillRect(70, 300, 80, 40, Color.WHITE);
			context.endLayer(new GLComposite()
					.withEffect(GLLayerEffects.innerShadow(
							0, 0, 25, new Color(0, 0, 0, 230))));

			context.beginLayer(180, 300, 40, 40);
			context.fillRect(180, 300, 40, 40, Color.WHITE);
			context.endLayer(new GLComposite().withEffect(
					GLLayerEffects.roundedClip(
							new GLCornerRadii(10, 0, 10, 0))));

			context.beginLayer();
			context.fillRect(240, 300, 60, 40, Color.WHITE);
			context.endLayer(new GLComposite().withEffect(
					GLLayerEffects.pathClip(GLShape2D.circle(270, 320, 18))));
			renderer.endFrame();
			drawable.swapBuffers();
			int glError = gl.glGetError();
			if (glError != GL.GL_NO_ERROR)
				throw new AssertionError("OpenGL error after layer rendering: 0x"
						+ Integer.toHexString(glError));

			Color singleCoverage = readColor(gl, 20, 25);
			Color doubleCoverage = readColor(gl, 50, 25);
			checkNear(singleCoverage, doubleCoverage, 2,
					"group opacity must not accumulate at overlap");
			checkNear(singleCoverage, new Color(128, 128, 128), 3,
					"outer group opacity");
			checkNear(readColor(gl, 25, 85), new Color(128, 0, 0), 3,
					"outer nested layer opacity");
			checkNear(readColor(gl, 95, 85), new Color(0, 64, 0), 3,
					"inner and outer layer opacity multiplication");
			checkNear(readColor(gl, 150, 30), Color.BLACK, 2,
					"zero-opacity layer is invisible");
			checkNear(readPhysicalColor(gl, 240, 245), Color.WHITE, 3,
					"layer uses physical HiDPI resolution");
			checkNear(readPhysicalColor(gl, 241, 245), Color.BLACK, 3,
					"one-physical-pixel layer edge stays sharp");
			int pickId = renderer.readPickId(gl, 150, 30);
			if (pickId != 77)
				throw new AssertionError(
						"Picking must remain independent of layer opacity: expected 77, got " + pickId);
			checkNear(readColor(gl, 20, 160), source, 3, "normal layer blend");
			checkNear(readColor(gl, 50, 160), new Color(228, 214, 255), 3,
					"add layer blend");
			checkNear(readColor(gl, 80, 160), new Color(50, 38, 200), 3,
					"multiply layer blend");
			checkNear(readColor(gl, 110, 160), new Color(178, 176, 255), 3,
					"screen layer blend");
			checkNear(readColor(gl, 140, 160), new Color(0, 86, 0), 3,
					"subtract layer blend");
			checkNear(readColor(gl, 170, 160), Color.BLACK, 3,
					"erase layer blend");
			checkNear(readColor(gl, 20, 215), new Color(64, 64, 64), 3,
					"custom layer shader, uniforms and opacity");
			checkNear(readColor(gl, 70, 215), Color.WHITE, 3,
					"blur shader keeps the interior opaque");
			Color blurredEdge = readColor(gl, 57, 215);
			if (blurredEdge.getRed() < 12 || blurredEdge.getRed() > 60)
				throw new AssertionError("Blur shader did not spread a soft edge: got "
						+ color(blurredEdge));
			checkNear(readColor(gl, 115, 210), Color.RED, 3,
					"shadow shader keeps the source in front");
			Color shadowPixel = readColor(gl, 136, 210);
			if (shadowPixel.getRed() >= 210 || shadowPixel.getGreen() >= 210
					|| shadowPixel.getBlue() >= 210)
				throw new AssertionError("Shadow shader did not darken the offset backdrop: got "
						+ color(shadowPixel));
			Color lowerShadowPixel = readColor(gl, 120, 228);
			if (lowerShadowPixel.getRed() >= 210
					|| lowerShadowPixel.getGreen() >= 210
					|| lowerShadowPixel.getBlue() >= 210)
				throw new AssertionError("positive shadow Y offset does not point down: got "
						+ color(lowerShadowPixel));
			checkNear(readColor(gl, 120, 196), Color.WHITE, 5,
					"positive shadow Y offset must not point up");
			checkNear(readColor(gl, 205, 215), Color.RED, 3,
					"outline effect keeps the source in front");
			checkNear(readColor(gl, 192, 215), Color.WHITE, 8,
					"outline effect expands beyond the source");
			checkNear(readColor(gl, 193, 203), Color.WHITE, 12,
					"circular outline kernel covers the near corner");
			checkNear(readColor(gl, 192, 202), Color.BLACK, 8,
					"circular outline kernel excludes the far corner");
			Color outlineAntialias = readPhysicalColor(gl, 384, 431);
			if (outlineAntialias.getRed() < 90 || outlineAntialias.getRed() > 170)
				throw new AssertionError("outline radius has no smooth one-pixel transition: got "
						+ color(outlineAntialias));
			checkNear(readColor(gl, 245, 215), new Color(54, 54, 54), 4,
					"color effect converts red to luminance");
			checkNear(readColor(gl, 285, 215), new Color(32, 32, 32), 4,
					"three ordered effects reuse both ping-pong targets");
			checkNear(readColor(gl, 35, 265), Color.RED, 3,
					"inner outline preserves the center");
			checkNear(readColor(gl, 21, 265), Color.WHITE, 12,
					"inner outline colors the inside edge");
			checkNear(readColor(gl, 18, 265), Color.BLACK, 3,
					"inner outline does not expand the layer alpha");
			checkNear(readColor(gl, 80, 255), Color.WHITE, 12,
					"inner shadow preserves the lit interior");
			Color innerShadowPixel = readColor(gl, 112, 277);
			if (innerShadowPixel.getRed() > 120
					|| innerShadowPixel.getGreen() > 120
					|| innerShadowPixel.getBlue() > 120)
				throw new AssertionError("inner shadow did not darken the inside edge: got "
						+ color(innerShadowPixel));
			checkNear(readColor(gl, 72, 265), innerShadowBackdrop, 3,
					"inner shadow does not affect pixels outside the layer");
			checkNear(readColor(gl, 180, 260), Color.RED, 3,
					"bounded layer draws inside its region");
			Color boundedBlur = readColor(gl, 168, 260);
			if (boundedBlur.getRed() < 10 || boundedBlur.getBlue() > 5)
				throw new AssertionError("reused bounded target leaked stale pixels: got "
						+ color(boundedBlur));
			checkNear(readColor(gl, 166, 260), Color.BLACK, 3,
					"effect padding limits bounded layer processing");
			checkNear(readColor(gl, 240, 265), Color.GREEN, 3,
					"nested bounded layer draws inside its parent");
			checkNear(readColor(gl, 225, 265), Color.BLACK, 3,
					"parent layer clips nested effects");
			int boundedPickInside = renderer.readPickId(gl, 285, 260);
			if (boundedPickInside != 88)
				throw new AssertionError("bounded layer lost picking inside its region: got "
						+ boundedPickInside + ", mirrored position "
						+ renderer.readPickId(gl, 285, 30));
			int boundedPickOutside = renderer.readPickId(gl, 275, 260);
			if (boundedPickOutside != 0)
				throw new AssertionError("bounded layer did not clip picking outside its region: got "
						+ boundedPickOutside);
			Color halfResolutionBlur = readColor(gl, 10, 315);
			if (halfResolutionBlur.getRed() < 15 || halfResolutionBlur.getRed() > 180)
				throw new AssertionError("half-resolution blur did not produce a soft edge: got "
						+ color(halfResolutionBlur));
			Color quarterShadowCenter = readColor(gl, 110, 320);
			Color quarterShadowEdge = readColor(gl, 72, 320);
			if (quarterShadowCenter.getRed() <= quarterShadowEdge.getRed() + 15)
				throw new AssertionError("quarter-resolution inner shadow lost its gradient: center "
						+ color(quarterShadowCenter) + ", edge " + color(quarterShadowEdge));
			checkNear(readColor(gl, 181, 301), Color.BLACK, 5,
					"rounded layer clip excludes its top-left corner");
			checkNear(readColor(gl, 218, 301), Color.WHITE, 5,
					"rounded layer clip preserves its square top-right corner");
			checkNear(readColor(gl, 218, 338), Color.BLACK, 5,
					"rounded layer clip excludes its bottom-right corner");
			checkNear(readColor(gl, 181, 338), Color.WHITE, 5,
					"rounded layer clip preserves its square bottom-left corner");
			checkNear(readColor(gl, 270, 320), Color.WHITE, 5,
					"path layer clip preserves its interior");
			checkNear(readColor(gl, 241, 301), Color.BLACK, 5,
					"path layer clip excludes pixels outside the shape");

			System.out.println("Layer GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (customLayerShader != null)
				customLayerShader.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void drawBlendSample(GLContext2D context, float x, float y,
			Color backdrop, Color color, GLBlendMode blendMode) {
		context.fillRect(x, y, 20, 20, backdrop);
		context.beginLayer();
		context.fillRect(x, y, 20, 20, color);
		context.endLayer(blendMode);
	}

	private static Color readColor(GL3 gl, int x, int y) {
		int physicalX = Math.round((x + 0.5f) * DEVICE_SCALE - 0.5f);
		int physicalY = Math.round((y + 0.5f) * DEVICE_SCALE - 0.5f);
		return readPhysicalColor(gl, physicalX, physicalY);
	}

	private static Color readPhysicalColor(GL3 gl, int x, int topLeftY) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, SURFACE_HEIGHT - 1 - topLeftY, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		return new Color(pixel.get(0) & 0xff, pixel.get(1) & 0xff, pixel.get(2) & 0xff);
	}

	private static void checkNear(Color actual, Color expected, int tolerance,
			String description) {
		if (Math.abs(actual.getRed() - expected.getRed()) > tolerance
				|| Math.abs(actual.getGreen() - expected.getGreen()) > tolerance
				|| Math.abs(actual.getBlue() - expected.getBlue()) > tolerance)
			throw new AssertionError(description + ": expected " + color(expected)
					+ ", got " + color(actual));
	}

	private static String color(Color color) {
		return color.getRed() + "/" + color.getGreen() + "/" + color.getBlue();
	}

	private static void expectFailure(Runnable action, String description) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("Expected rejection of " + description);
	}
}
