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
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDepthState;
import com.vingame.glswing.rendering.GLImage;
import com.vingame.glswing.rendering.GLLighting2D;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLRenderTarget;
import com.vingame.glswing.rendering.GLRenderTargetOptions;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLSprite;
import com.vingame.glswing.rendering.Projection2D;

/** GPU regression test for cutout, per-pixel depth, lighting and picking. */
public final class DepthSpriteGpuSelfTest {
	private static final int WIDTH = 96;
	private static final int HEIGHT = 64;

	private DepthSpriteGpuSelfTest() { }

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		capabilities.setDepthBits(24);
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
			testDepthCutoutAndPicking(gl, renderer);
			testRecordedSpriteInDepthRenderTarget(gl, renderer);
			testLargeInstancedBatch(gl, renderer);
			System.out.println("Depth sprite GPU self-test passed.");
		} finally {
			if (renderer != null)
				renderer.dispose(drawable.getGL().getGL3());
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testLargeInstancedBatch(GL3 gl,
			GLRenderer2D renderer) {
		GLSprite sprite = GLSprite.builder(solid(Color.RED, false))
				.alphaMode(GLAlphaMode.OPAQUE)
				.depthState(GLDepthState.DISABLED)
				.build();
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.setPickingRenderingEnabled(false);
		for (int index = 0; index < 50_000; index++)
			context.drawSprite(sprite, index % WIDTH,
					(index / WIDTH) % HEIGHT, 1, 1, Color.WHITE, 0.5f);
		renderer.endFrame();
		if (renderer.drawCallCount() != 1)
			throw new AssertionError("50k compatible sprites required "
					+ renderer.drawCallCount() + " draw calls instead of one");
		assertDominant(gl, 20, 20, Color.RED, "large instanced sprite batch");
		if (gl.glGetError() != GL.GL_NO_ERROR)
			throw new AssertionError(
					"Large instanced sprite batch produced an OpenGL error");
	}

	private static void testRecordedSpriteInDepthRenderTarget(GL3 gl,
			GLRenderer2D renderer) {
		GLSprite sprite = GLSprite.builder(solid(Color.RED, false))
				.alphaMode(GLAlphaMode.OPAQUE).build();
		GLMouseTarget target = new GLMouseTarget() { };
		GLRenderTarget surface = new GLRenderTarget(48, 32,
				GLRenderTargetOptions.DEFAULT.withDepth(true).withPicking(true));
		try {
			GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
					Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
			context.beginRecording();
			context.setMouseTarget(target, 0, 0);
			context.drawSprite(sprite, 0, 0, 48, 32, Color.WHITE, 0.65f);
			context.clearMouseTarget();
			GLRecording recording = context.endRecording();

			context.beginRenderTarget(surface);
			context.clear(Color.BLACK);
			context.clearDepth();
			context.draw(recording);
			context.endRenderTarget();
			context.clear(Color.BLACK);
			context.drawRenderTarget(surface, 20, 15, 48, 32);
			renderer.endFrame();

			assertDominant(gl, 30, 25, Color.RED,
					"recorded sprite in depth render target");
			if (renderer.readPickResult(gl, 30, 25).getTarget() != target)
				throw new AssertionError(
						"Render target did not preserve recorded sprite picking");
		} finally {
			surface.close();
		}
	}

	private static void testDepthCutoutAndPicking(GL3 gl,
			GLRenderer2D renderer) {
		GLSprite back = GLSprite.builder(solid(Color.BLUE, false))
				.alphaMode(GLAlphaMode.OPAQUE).build();
		GLSprite front = GLSprite.builder(solid(Color.RED, true))
				.depthMap(solidGray(255))
				.normalMap(normalMap())
				.alphaMode(GLAlphaMode.CUTOUT)
				.alphaCutoff(0.5f)
				.depthRange(-0.05f, 0.05f)
				.lit(true).build();
		GLMouseTarget backTarget = new GLMouseTarget() { };
		GLMouseTarget frontTarget = new GLMouseTarget() { };

		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		context.clear(Color.BLACK);
		context.clearDepth();
		context.setLighting(GLLighting2D.DEFAULT.withoutDirectionalLight()
				.withAmbient(Color.WHITE, 1));
		context.setMouseTarget(frontTarget, 16, 8);
		context.drawSprite(front, 16, 8, 64, 48, Color.WHITE, 0.75f);
		context.setMouseTarget(backTarget, 16, 8);
		context.drawSprite(back, 16, 8, 64, 48, Color.WHITE, 0.2f);
		context.clearMouseTarget();
		renderer.endFrame();

		assertDominant(gl, 24, 20, Color.RED, "front cutout sprite");
		assertDominant(gl, 48, 32, Color.BLUE, "transparent cutout hole");
		if (renderer.readPickResult(gl, 24, 20).getTarget() != frontTarget)
			throw new AssertionError("Front sprite did not win depth-aware picking");
		if (renderer.readPickResult(gl, 48, 32).getTarget() != backTarget)
			throw new AssertionError("Back sprite was not pickable through cutout hole");
		if (gl.glGetError() != GL.GL_NO_ERROR)
			throw new AssertionError("Depth sprite rendering produced an OpenGL error");
	}

	private static GLImage solid(Color color, boolean hole) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++)
			for (int x = 0; x < 16; x++) {
				boolean transparent = hole && x >= 6 && x <= 9 && y >= 6 && y <= 9;
				image.setRGB(x, y, transparent ? 0 : color.getRGB());
			}
		return GLImage.of(image);
	}

	private static GLImage solidGray(int value) {
		return solid(new Color(value, value, value), false);
	}

	private static GLImage normalMap() {
		return solid(new Color(128, 128, 255), false);
	}

	private static void assertDominant(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		int expectedValue = expected == Color.RED ? red : blue;
		int other = expected == Color.RED ? Math.max(green, blue)
				: Math.max(red, green);
		if (expectedValue < 180 || expectedValue <= other)
			throw new AssertionError(description + ": got "
					+ red + "/" + green + "/" + blue);
	}
}
