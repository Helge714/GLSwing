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
import com.vingame.glswing.rendering.GLFeedbackBuffer;
import com.vingame.glswing.rendering.GLRenderTarget;
import com.vingame.glswing.rendering.GLRenderTargetOptions;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** GPU regressions for persistent targets, orientation and ping-pong feedback. */
public final class RenderTargetGpuSelfTest {

	private static final int WIDTH = 160;
	private static final int HEIGHT = 120;

	private static final String TEXTURE_VERTEX = """
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
			""";

	private static final String INVERT_FRAGMENT = """
			#version 330 core
			in vec2 vTexCoord;
			in vec4 vColor;
			uniform sampler2D uTexture;
			out vec4 fragColor;
			void main() {
			    vec4 value = texture(uTexture, vTexCoord);
			    fragColor = vec4(vec3(1.0) - value.rgb, value.a) * vColor;
			}
			""";

	private RenderTargetGpuSelfTest() {
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
		GL3 gl = drawable.getGL().getGL3();
		GLRenderer2D renderer = null;
		ShaderProgram invert = null;
		GLRenderTarget target = new GLRenderTarget(32, 32,
				GLRenderTargetOptions.pixelArt());
		GLRenderTarget copy = new GLRenderTarget(32, 32,
				GLRenderTargetOptions.pixelArt());
		GLRenderTarget pickable = new GLRenderTarget(40, 30,
				GLRenderTargetOptions.pixelArt().withPicking(true));
		GLRenderTarget nestedPickable = new GLRenderTarget(50, 40,
				GLRenderTargetOptions.pixelArt().withPicking(true));
		GLFeedbackBuffer feedback = new GLFeedbackBuffer(16, 16,
				GLRenderTargetOptions.repeatingPixelArt());
		try {
			renderer = GLRenderer2D.create(gl);
			invert = ShaderProgram.fromSource(gl, TEXTURE_VERTEX, INVERT_FRAGMENT);
			testPersistentTarget(gl, renderer, target, copy);
			testPersistentPicking(gl, renderer, pickable, nestedPickable);
			testFeedback(gl, renderer, feedback, invert);
			System.out.println("Render target GPU self-test passed.");
		} finally {
			target.close();
			copy.close();
			pickable.close();
			nestedPickable.close();
			feedback.close();
			if (invert != null)
				invert.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testPersistentPicking(GL3 gl, GLRenderer2D renderer,
			GLRenderTarget pickable, GLRenderTarget nestedPickable) {
		GLMouseTarget rectangle = new GLMouseTarget() { };
		GLMouseTarget invisibleArea = new GLMouseTarget() { };
		GLMouseTarget fallback = new GLMouseTarget() { };

		GLContext2D context = begin(gl, renderer);
		context.beginRenderTarget(pickable);
		check(context.isPickingRenderingEnabled(),
				"Picking target did not enable its local ID output");
		context.clear(new Color(0, 0, 0, 0));
		context.fillRect(4, 5, 10, 8, Color.RED, rectangle);
		context.setMouseTarget(invisibleArea);
		context.addUIHitArea(16, 16, 4, 4);
		context.clearMouseTarget();
		context.fillRect(20, 5, 8, 8, Color.BLUE);
		context.endRenderTarget();

		// The second target verifies that ID textures and record transforms can be
		// nested before they are finally presented on the main framebuffer.
		context.beginRenderTarget(nestedPickable);
		context.clear(new Color(0, 0, 0, 0));
		context.drawRenderTarget(pickable, 5, 5, 40, 30);
		context.endRenderTarget();

		context.clear(Color.BLACK);
		context.drawRenderTarget(nestedPickable, 60, 40, 75, 60, fallback);
		renderer.endFrame();

		checkTarget(renderer.readPickResult(gl, 81, 61), rectangle, 5, 4,
				"nested and scaled target-local rectangle");
		checkTarget(renderer.readPickResult(gl, 95, 75), invisibleArea, 2.33f, 2.33f,
				"invisible target-local hit area");
		checkTarget(renderer.readPickResult(gl, 104, 60), fallback, 44, 20,
				"whole-target fallback");

		// Persistent ID contents and weak target mappings survive a frame in which
		// the target is only sampled, not redrawn.
		context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.drawRenderTarget(nestedPickable, 10, 10, 50, 40);
		renderer.endFrame();
		checkTarget(renderer.readPickResult(gl, 24, 24), rectangle, 5, 4,
				"persistent target picking across frames");

		context = begin(gl, renderer);
		context.clear(Color.BLACK);
		context.translate(120, 20);
		context.rotate((float) (Math.PI * 0.5));
		context.drawRenderTarget(nestedPickable, 0, 0);
		renderer.endFrame();
		checkTarget(renderer.readPickResult(gl, 106, 34), rectangle, 5, 4,
				"rotated target picking");

		context = begin(gl, renderer);
		context.beginRenderTarget(nestedPickable);
		context.clear(new Color(0, 0, 0, 0));
		context.endRenderTarget();
		context.clear(Color.BLACK);
		context.drawRenderTarget(nestedPickable, 10, 10);
		renderer.endFrame();
		check(!renderer.readPickResult(gl, 24, 24).isHit(),
				"Clearing a picking target retained stale IDs");
	}

	private static void testPersistentTarget(GL3 gl, GLRenderer2D renderer,
			GLRenderTarget target, GLRenderTarget copy) {
		GLContext2D context = begin(gl, renderer);
		context.beginRenderTarget(target);
		check(context.getRenderTargetDepth() == 1,
				"Render-target depth was not incremented");
		context.clear(Color.RED);
		context.fillRect(0, 0, 32, 16, Color.GREEN);
		expectFailure(() -> context.setPickingRenderingEnabled(true),
				"Picking was enabled inside a persistent render target");
		expectFailure(() -> context.drawRenderTarget(target, 0, 0, 32, 32),
				"A target sampled from itself while it was being written");
		context.endRenderTarget();

		context.beginRenderTarget(copy);
		context.clear(Color.BLUE);
		context.drawRenderTarget(target, 0, 0, 32, 32);
		context.endRenderTarget();
		check(context.getRenderTargetDepth() == 0,
				"Render-target depth was not restored");

		context.clear(Color.BLACK);
		context.drawRenderTarget(copy, 16, 12, 64, 64);
		renderer.endFrame();
		checkColor(gl, 40, 28, Color.GREEN, "top target half");
		checkColor(gl, 40, 60, Color.RED, "bottom target half");
	}

	private static void testFeedback(GL3 gl, GLRenderer2D renderer,
			GLFeedbackBuffer feedback, ShaderProgram invert) {
		GLContext2D context = begin(gl, renderer);
		feedback.clear(context, Color.RED);
		feedback.update(context, () -> {
			context.setTextureShader(invert);
			context.drawRenderTarget(feedback.current(), 0, 0,
					feedback.getWidth(), feedback.getHeight());
		});
		context.clear(Color.BLACK);
		feedback.drawCurrent(context, 88, 20, 48, 48);
		renderer.endFrame();
		checkColor(gl, 104, 36, Color.CYAN, "feedback inversion");
		check(gl.glGetError() == GL.GL_NO_ERROR,
				"Render-target rendering produced an OpenGL error");
	}

	private static GLContext2D begin(GL3 gl, GLRenderer2D renderer) {
		gl.glViewport(0, 0, WIDTH, HEIGHT);
		return renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
	}

	private static void checkColor(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		check(Math.abs(red - expected.getRed()) < 12
				&& Math.abs(green - expected.getGreen()) < 12
				&& Math.abs(blue - expected.getBlue()) < 12,
				description + " had color " + red + "/" + green + "/" + blue);
	}

	private static void checkTarget(GLPickResult result, GLMouseTarget expected,
			float localX, float localY, String description) {
		check(result.getTarget() == expected, description + " picked the wrong target");
		check(result.hasLocalPosition()
				&& Math.abs(result.getLocalX() - localX) < 0.75f
				&& Math.abs(result.getLocalY() - localY) < 0.75f,
				description + " had local coordinates "
						+ result.getLocalX() + "/" + result.getLocalY());
	}

	private static void expectFailure(Runnable action, String message) {
		boolean failed = false;
		try {
			action.run();
		} catch (IllegalStateException expected) {
			failed = true;
		}
		check(failed, message);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
