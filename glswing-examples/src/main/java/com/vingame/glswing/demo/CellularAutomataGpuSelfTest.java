package com.vingame.glswing.demo;

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
import com.vingame.glswing.rendering.GLFeedbackBuffer;
import com.vingame.glswing.rendering.GLRenderTargetOptions;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** Demo-local GPU checks for the Conway and Bosco example rules. */
public final class CellularAutomataGpuSelfTest {

	private static final int SIZE = 64;
	private static final int GRID_SIZE = 16;
	private static final String VERTEX_SHADER =
			"/com/vingame/glswing/shaders/texture.vert";
	private static final String DEMO_SHADER_ROOT =
			"/com/vingame/glswing/demo/shaders/";

	private CellularAutomataGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, SIZE, SIZE);
		drawable.display();
		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");
		GL3 gl = drawable.getGL().getGL3();
		GLRenderer2D renderer = null;
		ShaderProgram conway = null;
		ShaderProgram bosco = null;
		GLFeedbackBuffer feedback = new GLFeedbackBuffer(GRID_SIZE, GRID_SIZE,
				GLRenderTargetOptions.repeatingPixelArt());
		try {
			renderer = GLRenderer2D.create(gl);
			conway = ShaderProgram.fromResources(gl, VERTEX_SHADER,
					DEMO_SHADER_ROOT + "game_of_life.frag");
			bosco = ShaderProgram.fromResources(gl, VERTEX_SHADER,
					DEMO_SHADER_ROOT + "larger_than_life.frag");
			testConway(gl, renderer, feedback, conway);
			testBosco(gl, renderer, feedback, bosco);
			System.out.println("Cellular-automata demo GPU self-test passed.");
		} finally {
			feedback.close();
			if (conway != null)
				conway.dispose(gl);
			if (bosco != null)
				bosco.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testConway(GL3 gl, GLRenderer2D renderer,
			GLFeedbackBuffer feedback, ShaderProgram shader) {
		GLContext2D context = begin(gl, renderer);
		feedback.clear(context, Color.BLACK);
		context.withRenderTarget(feedback.current(), () -> {
			context.fillRect(7, 7, 1, 1, Color.WHITE);
			context.fillRect(8, 7, 1, 1, Color.WHITE);
			context.fillRect(7, 8, 1, 1, Color.WHITE);
			context.fillRect(8, 8, 1, 1, Color.WHITE);
		});
		feedback.update(context, () -> {
			context.setTextureShader(shader);
			context.setTextureUniform("uTexelSize",
					1f / GRID_SIZE, 1f / GRID_SIZE);
			feedback.drawCurrent(context, 0, 0, GRID_SIZE, GRID_SIZE);
		});
		show(context, feedback);
		renderer.endFrame();
		checkCell(gl, 7, 7, Color.WHITE, "stable Conway block");
		checkCell(gl, 6, 6, Color.BLACK, "dead cell beside Conway block");
	}

	private static void testBosco(GL3 gl, GLRenderer2D renderer,
			GLFeedbackBuffer feedback, ShaderProgram shader) {
		GLContext2D context = begin(gl, renderer);
		feedback.clear(context, Color.BLACK);
		context.withRenderTarget(feedback.current(), () -> {
			for (int y = 5; y < 11; y++)
				for (int x = 5; x < 11; x++)
					context.fillRect(x, y, 1, 1, Color.WHITE);
		});
		feedback.update(context, () -> {
			context.setTextureShader(shader);
			CellularRule.BOSCO.applyUniforms(context, GRID_SIZE, GRID_SIZE);
			feedback.drawCurrent(context, 0, 0, GRID_SIZE, GRID_SIZE);
		});
		show(context, feedback);
		renderer.endFrame();
		checkCell(gl, 7, 7, Color.WHITE,
				"Bosco cell with 36 cells in its neighborhood");
	}

	private static GLContext2D begin(GL3 gl, GLRenderer2D renderer) {
		gl.glViewport(0, 0, SIZE, SIZE);
		return renderer.beginFrame(gl, SIZE, SIZE,
				Projection2D.TOP_LEFT_PIXELS.create(SIZE, SIZE));
	}

	private static void show(GLContext2D context, GLFeedbackBuffer feedback) {
		context.clear(Color.MAGENTA);
		feedback.drawCurrent(context, 0, 0, SIZE, SIZE);
	}

	private static void checkCell(GL3 gl, int cellX, int cellY,
			Color expected, String description) {
		int scale = SIZE / GRID_SIZE;
		int x = cellX * scale + scale / 2;
		int y = cellY * scale + scale / 2;
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, SIZE - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (Math.abs(red - expected.getRed()) >= 12
				|| Math.abs(green - expected.getGreen()) >= 12
				|| Math.abs(blue - expected.getBlue()) >= 12)
			throw new AssertionError(description + " had color "
					+ red + "/" + green + "/" + blue);
	}
}
