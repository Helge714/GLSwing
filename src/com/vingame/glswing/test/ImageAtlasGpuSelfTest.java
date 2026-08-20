package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.vingame.glswing.rendering.GLIconOptions;
import com.vingame.glswing.rendering.GLPath2D;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLSvgDocument;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** GPU regression tests for automatic atlases and multi-texture batches. */
public final class ImageAtlasGpuSelfTest {

	private static final int WIDTH = 256;
	private static final int HEIGHT = 128;

	private ImageAtlasGpuSelfTest() {
	}

	public static void main(String[] args) {
		testMipmappingOptions();
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
			testPathIconInUIAndTextureBatch(gl, renderer);
			testMipmappedAtlasIsolation(gl, renderer);
			testRecordedAdaptiveIcon(gl, renderer);
			System.out.println("Image atlas GPU self-test passed.");
		} finally {
			if (renderer != null)
				renderer.dispose(drawable.getGL().getGL3());
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testPathIconInUIAndTextureBatch(GL3 gl,
			GLRenderer2D renderer) {
		GLPath2D path = new GLPath2D()
				.moveTo(0, 0)
				.lineTo(32, 0)
				.lineTo(16, 32)
				.closePath();
		GLIcon icon = GLIcon.fromPath(path);
		check(icon.getFormat() == GLIcon.Format.MSDF,
				"Path icon should default to tintable MSDF");

		ShaderProgram shader = ShaderProgram.fromSource(gl, """
				#version 330 core
				layout(location = 0) in vec2 aPosition;
				layout(location = 1) in vec2 aTexCoord;
				layout(location = 2) in vec4 aColor;
				uniform mat4 uProjection;
				out vec2 vTexCoord;
				out vec4 vColor;
				void main() {
				    vTexCoord = aTexCoord;
				    vColor = aColor;
				    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
				}
				""", """
				#version 330 core
				in vec2 vTexCoord;
				in vec4 vColor;
				uniform sampler2D uTexture;
				out vec4 fragColor;
				void main() {
				    float alpha = texture(uTexture, vTexCoord).a * vColor.a;
				    fragColor = vec4(0.0, alpha, 0.0, alpha);
				}
				""");
		try {
			GLContext2D context = begin(gl, renderer);
			context.beginRecording();
			context.setTextureShader(shader);
			context.drawIcon(icon, 8, 8, 48, 48, Color.MAGENTA);
			context.useDefaultTextureShader();
			GLRecording textureRecording = context.endRecording();
			context.clear(Color.BLACK);
			context.draw(textureRecording);
			context.drawUIIcon(icon, 72, 8, 48, 48, Color.CYAN);
			renderer.endFrame();

			check(renderer.drawCallCount() == 2,
					"Texture and UI path icons should use their two respective batches");
			checkDominant(gl, 32, 24, Color.GREEN,
					"custom-shaded texture-batch path icon");
			checkVisible(gl, 72, 8, 48, 48, "UI-batch path icon");
			check(gl.glGetError() == GL.GL_NO_ERROR,
					"Path icon rendering produced an OpenGL error");
		} finally {
			shader.dispose(gl);
		}
	}

	private static void testMipmappingOptions() {
		check(GLImageOptions.DEFAULT.mipmapping()
				== GLImageOptions.Mipmapping.AUTO,
				"Default images should choose mipmapping automatically");
		check(GLImageOptions.pixelArt().mipmapping()
				== GLImageOptions.Mipmapping.NONE,
				"Pixel-art images must not use conventional mipmaps");
		check(GLImageOptions.dynamic().mipmapping()
				== GLImageOptions.Mipmapping.NONE,
				"Dynamic images should not regenerate mipmaps implicitly");
		boolean rejected = false;
		try {
			GLImageOptions.pixelArt().withMipmapping(
					GLImageOptions.Mipmapping.ENABLED);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		check(rejected, "Explicit mipmapping should reject nearest filtering");
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
		context.drawUIIcon(msdf, 8, 8, 62, 62, Color.CYAN);
		context.drawUIIcon(rgba, 90, 4, 70, 70);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"RGBA and MSDF atlas icons should remain in one UI draw call");
		checkVisible(gl, 8, 8, 62, 62, "MSDF icon");
		checkVisible(gl, 90, 4, 70, 70, "RGBA icon");
	}

	private static void testMipmappedAtlasIsolation(GL3 gl,
			GLRenderer2D renderer) {
		GLImage red = colorImage(64, 64, Color.RED, GLImageOptions.DEFAULT);
		GLImage green = colorImage(64, 64, Color.GREEN, GLImageOptions.DEFAULT);
		GLContext2D context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.drawUIImage(red, 10, 90, 4, 4);
		context.drawUIImage(green, 30, 90, 4, 4);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"Mipmapped atlas images should share one UI draw call");
		checkDominant(gl, 12, 92, Color.RED, "red downscaled atlas image");
		checkDominant(gl, 32, 92, Color.GREEN, "green downscaled atlas image");
		check(gl.glGetError() == GL.GL_NO_ERROR,
				"Mipmapped atlas rendering produced an OpenGL error");
	}

	private static void testRecordedAdaptiveIcon(GL3 gl,
			GLRenderer2D renderer) {
		String source = """
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
				  <path fill="#ff4030" d="M 0 0 L 64 0 L 64 32 L 0 32 Z"/>
				  <path fill="#30a0ff" d="M 0 32 L 64 32 L 64 64 L 0 64 Z"/>
				</svg>
				""";
		GLSvgDocument document = GLSvgDocument.parse(new ByteArrayInputStream(
				source.getBytes(StandardCharsets.UTF_8)));
		GLIcon icon = GLIcon.fromSvg(document, GLIcon.Format.RGBA,
				GLIconOptions.DEFAULT.withResolutionRange(32, 512)
						.withRasterizationDelayMillis(0));
		check(icon.getCachedVariantCount() == 1,
				"Adaptive icon should initially retain one warm variant");
		AtomicInteger repaintRequests = new AtomicInteger();
		renderer.setAsynchronousResourceRepaint(repaintRequests::incrementAndGet);

		GLContext2D context = begin(gl, renderer);
		context.beginRecording();
		context.drawUIIcon(icon, 0, 0, 24, 24);
		GLRecording recording = context.endRecording();
		context.clear(Color.BLACK);
		context.draw(recording);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"Recorded adaptive icon left the UI batch at unit scale");
		awaitVariantCount(icon, 2,
				"Unit replay did not generate its 32px icon bucket asynchronously");
		awaitRepaintRequest(repaintRequests);

		context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.setTransform(new GLTransform().scale(8));
		context.draw(recording);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"Zoomed recorded icon left the UI batch");
		awaitVariantCount(icon, 3,
				"Zoomed replay did not generate its 256px icon bucket asynchronously");

		context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.setTransform(new GLTransform().scale(8));
		context.draw(recording);
		renderer.endFrame();
		check(renderer.drawCallCount() == 1,
				"Uploaded adaptive icon left the UI batch");
		checkVisible(gl, 0, 0, 64, 64, "zoomed recorded adaptive icon");
		renderer.setAsynchronousResourceRepaint(null);
	}

	private static void awaitVariantCount(GLIcon icon, int expected,
			String failureMessage) {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (icon.getCachedVariantCount() < expected
				&& System.nanoTime() < deadline) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while awaiting SVG rasterization",
						error);
			}
		}
		check(icon.getCachedVariantCount() >= expected, failureMessage);
	}

	private static void awaitRepaintRequest(AtomicInteger requests) {
		long deadline = System.nanoTime() + 2_000_000_000L;
		while (requests.get() == 0 && System.nanoTime() < deadline) {
			try {
				Thread.sleep(2);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new AssertionError(
						"Interrupted while awaiting adaptive-icon repaint", error);
			}
		}
		check(requests.get() > 0,
				"Completed adaptive icon did not request a repaint");
	}

	private static GLContext2D begin(GL3 gl, GLRenderer2D renderer) {
		return renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
	}

	private static GLImage colorImage(Color color, GLImageOptions options) {
		return colorImage(8, 8, color, options);
	}

	private static GLImage colorImage(int width, int height,
			Color color, GLImageOptions options) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
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

	private static void checkDominant(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		int wanted = expected == Color.RED ? red : green;
		int unwanted = expected == Color.RED ? Math.max(green, blue)
				: Math.max(red, blue);
		check(wanted > 180 && wanted > unwanted * 3,
				description + " bled into a neighboring atlas entry: "
						+ red + "/" + green + "/" + blue);
	}
}
