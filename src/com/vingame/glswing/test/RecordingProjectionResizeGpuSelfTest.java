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
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;

/** Ensures cached local recordings inherit the current frame projection. */
public final class RecordingProjectionResizeGpuSelfTest {

	private static final int SURFACE_WIDTH = 360;
	private static final int SURFACE_HEIGHT = 120;

	private RecordingProjectionResizeGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null,
						SURFACE_WIDTH, SURFACE_HEIGHT);
		drawable.display();
		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");

		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			renderer = GLRenderer2D.create(gl);
			GLRecording recording = createRecording(gl, renderer);
			drawAfterAspectChange(gl, renderer, recording);
			System.out.println("Recording projection resize GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static GLRecording createRecording(GL3 gl, GLRenderer2D renderer) {
		gl.glViewport(0, 0, 180, SURFACE_HEIGHT);
		GLContext2D context = renderer.beginFrame(gl, 180, SURFACE_HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(180, SURFACE_HEIGHT));
		context.beginRecording();
		context.fillUIRoundRect(20, 20, 80, 80, 40, Color.GREEN);
		GLRecording recording = context.endRecording();
		context.clear(Color.BLACK);
		context.draw(recording);
		renderer.endFrame();
		return recording;
	}

	private static void drawAfterAspectChange(GL3 gl, GLRenderer2D renderer,
			GLRecording recording) {
		gl.glViewport(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT);
		GLContext2D context = renderer.beginFrame(gl, SURFACE_WIDTH, SURFACE_HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(SURFACE_WIDTH, SURFACE_HEIGHT));
		context.clear(Color.BLACK);
		context.draw(recording);
		renderer.endFrame();

		ByteBuffer pixels = ByteBuffer.allocateDirect(
				SURFACE_WIDTH * SURFACE_HEIGHT * 4);
		gl.glReadPixels(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
		int minimumX = SURFACE_WIDTH;
		int minimumY = SURFACE_HEIGHT;
		int maximumX = -1;
		int maximumY = -1;
		for (int y = 0; y < SURFACE_HEIGHT; y++) {
			for (int x = 0; x < SURFACE_WIDTH; x++) {
				int offset = (y * SURFACE_WIDTH + x) * 4;
				int red = pixels.get(offset) & 0xff;
				int green = pixels.get(offset + 1) & 0xff;
				int blue = pixels.get(offset + 2) & 0xff;
				if (green < 160 || red > 80 || blue > 80)
					continue;
				minimumX = Math.min(minimumX, x);
				minimumY = Math.min(minimumY, y);
				maximumX = Math.max(maximumX, x);
				maximumY = Math.max(maximumY, y);
			}
		}
		if (maximumX < 0)
			throw new AssertionError("Recorded circle was not visible after resize");
		int width = maximumX - minimumX + 1;
		int height = maximumY - minimumY + 1;
		if (Math.abs(width - 80) > 3 || Math.abs(height - 80) > 3)
			throw new AssertionError("Recorded 80x80 circle was stretched to "
					+ width + "x" + height + " after the aspect change");
	}
}
