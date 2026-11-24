package com.vingame.glswing.rendering.internal;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.ShaderProgram;

/** Persistent color surface used for damage-based scene updates. */
public final class SceneFramebuffer {

	private int framebuffer;
	private int colorBuffer;
	private int depthBuffer;
	private int resolveFramebuffer;
	private int resolveTexture;
	private int presentVao;
	private ShaderProgram presentProgram;
	private int width;
	private int height;
	private int samples = -1;
	private int targetDrawFramebuffer;
	private int targetReadFramebuffer;
	private final int[] targetViewport = new int[4];
	private boolean targetScissorEnabled;
	private final int[] targetScissor = new int[4];
	private boolean updating;

	public static SceneFramebuffer create(GL3 gl) {
		SceneFramebuffer result = new SceneFramebuffer();
		int[] ids = new int[2];
		gl.glGenFramebuffers(2, ids, 0);
		result.framebuffer = ids[0];
		result.resolveFramebuffer = ids[1];
		gl.glGenRenderbuffers(2, ids, 0);
		result.colorBuffer = ids[0];
		result.depthBuffer = ids[1];
		gl.glGenTextures(1, ids, 0);
		result.resolveTexture = ids[0];
		gl.glGenVertexArrays(1, ids, 0);
		result.presentVao = ids[0];
		if (result.framebuffer == 0 || result.resolveFramebuffer == 0
				|| result.colorBuffer == 0 || result.depthBuffer == 0
				|| result.resolveTexture == 0
				|| result.presentVao == 0) {
			result.dispose(gl);
			throw new IllegalStateException("Could not create the persistent scene framebuffer");
		}
		try {
			result.presentProgram = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer.frag");
		} catch (RuntimeException error) {
			result.dispose(gl);
			throw error;
		}
		return result;
	}

	/**
	 * Binds the persistent surface and clips following drawing to logical top-left
	 * coordinates. A null damage rectangle updates the complete surface.
	 */
	public void beginUpdate(GL3 gl, int width, int height, int samples,
			float logicalWidth, float logicalHeight, float[] damage) {
		if (updating)
			throw new IllegalStateException("A scene update is already active");
		if (width <= 0 || height <= 0 || logicalWidth <= 0 || logicalHeight <= 0)
			throw new IllegalArgumentException("Scene dimensions must be positive");
		captureTarget(gl);
		try {
			ensureStorage(gl, width, height, Math.max(0, samples));
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, framebuffer);
			gl.glViewport(0, 0, width, height);
			if (damage == null) {
				gl.glDisable(GL.GL_SCISSOR_TEST);
			} else {
			float scaleX = width / logicalWidth;
			float scaleY = height / logicalHeight;
			int minimumX = clamp((int) Math.floor(damage[0] * scaleX), 0, width);
			int maximumX = clamp((int) Math.ceil((damage[0] + damage[2]) * scaleX),
					0, width);
			int minimumY = clamp((int) Math.floor(
					(logicalHeight - damage[1] - damage[3]) * scaleY), 0, height);
			int maximumY = clamp((int) Math.ceil(
					(logicalHeight - damage[1]) * scaleY), 0, height);
				gl.glEnable(GL.GL_SCISSOR_TEST);
				gl.glScissor(minimumX, minimumY,
					Math.max(0, maximumX - minimumX),
					Math.max(0, maximumY - minimumY));
			}
			updating = true;
		} catch (RuntimeException | Error error) {
			restoreTarget(gl);
			throw error;
		}
	}

	/** Restores the drawable target after a scene update. */
	public void endUpdate(GL3 gl) {
		if (!updating)
			throw new IllegalStateException("No scene update is active");
		restoreTarget(gl);
		updating = false;
	}

	/** Copies the retained image without changing its resolution. */
	public void present(GL3 gl) {
		present(gl, width, height);
	}

	/**
	 * Presents the retained image at the requested drawable resolution. Scaling
	 * uses a resolved texture because OpenGL cannot resolve a multisample buffer
	 * and resize it in the same framebuffer blit.
	 */
	public void present(GL3 gl, int targetWidth, int targetHeight) {
		if (updating)
			throw new IllegalStateException("Cannot present during a scene update");
		if (width == 0 || height == 0)
			return;
		if (targetWidth <= 0 || targetHeight <= 0)
			throw new IllegalArgumentException("Presentation dimensions must be positive");
		int[] readFramebuffer = new int[1];
		int[] drawFramebuffer = new int[1];
		int[] viewport = new int[4];
		boolean scissorEnabled = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		boolean blendEnabled = gl.glIsEnabled(GL.GL_BLEND);
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, readFramebuffer, 0);
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, drawFramebuffer, 0);
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		if (width == targetWidth && height == targetHeight) {
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, framebuffer);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer[0]);
			gl.glBlitFramebuffer(0, 0, width, height, 0, 0,
					targetWidth, targetHeight,
					GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
		} else {
			resolve(gl);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer[0]);
			gl.glViewport(0, 0, targetWidth, targetHeight);
			gl.glDisable(GL.GL_BLEND);
			presentProgram.use(gl);
			presentProgram.setInteger(gl, "uTexture", 0);
			presentProgram.setFloat(gl, "uOpacity", 1);
			gl.glActiveTexture(GL.GL_TEXTURE0);
			gl.glBindTexture(GL.GL_TEXTURE_2D, resolveTexture);
			gl.glBindVertexArray(presentVao);
			gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
			gl.glBindVertexArray(0);
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
			gl.glUseProgram(0);
		}
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, readFramebuffer[0]);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer[0]);
		gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		if (blendEnabled)
			gl.glEnable(GL.GL_BLEND);
		else
			gl.glDisable(GL.GL_BLEND);
		if (scissorEnabled)
			gl.glEnable(GL.GL_SCISSOR_TEST);
		else
			gl.glDisable(GL.GL_SCISSOR_TEST);
	}

	public boolean isAllocated() {
		return width > 0 && height > 0;
	}

	public boolean isUpdating() {
		return updating;
	}

	public void invalidate() {
		width = 0;
		height = 0;
		samples = -1;
	}

	public void dispose(GL3 gl) {
		if (presentProgram != null) {
			presentProgram.dispose(gl);
			presentProgram = null;
		}
		if (presentVao != 0) {
			gl.glDeleteVertexArrays(1, new int[] { presentVao }, 0);
			presentVao = 0;
		}
		if (resolveTexture != 0) {
			gl.glDeleteTextures(1, new int[] { resolveTexture }, 0);
			resolveTexture = 0;
		}
		if (colorBuffer != 0) {
			gl.glDeleteRenderbuffers(1, new int[] { colorBuffer }, 0);
			colorBuffer = 0;
		}
		if (depthBuffer != 0) {
			gl.glDeleteRenderbuffers(1, new int[] { depthBuffer }, 0);
			depthBuffer = 0;
		}
		if (framebuffer != 0 || resolveFramebuffer != 0) {
			gl.glDeleteFramebuffers(2,
					new int[] { framebuffer, resolveFramebuffer }, 0);
			framebuffer = 0;
			resolveFramebuffer = 0;
		}
		width = 0;
		height = 0;
	}

	private void ensureStorage(GL3 gl, int width, int height, int samples) {
		if (this.width == width && this.height == height && this.samples == samples)
			return;
		this.width = width;
		this.height = height;
		this.samples = samples;
		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, colorBuffer);
		if (samples > 1)
			gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, samples,
					GL.GL_RGBA8, width, height);
		else
			gl.glRenderbufferStorage(GL.GL_RENDERBUFFER, GL.GL_RGBA8, width, height);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
		gl.glFramebufferRenderbuffer(GL3.GL_DRAW_FRAMEBUFFER,
				GL.GL_COLOR_ATTACHMENT0, GL.GL_RENDERBUFFER, colorBuffer);
		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, depthBuffer);
		if (samples > 1)
			gl.glRenderbufferStorageMultisample(GL.GL_RENDERBUFFER, samples,
					GL3.GL_DEPTH_COMPONENT24, width, height);
		else
			gl.glRenderbufferStorage(GL.GL_RENDERBUFFER,
					GL3.GL_DEPTH_COMPONENT24, width, height);
		gl.glFramebufferRenderbuffer(GL3.GL_DRAW_FRAMEBUFFER,
				GL3.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, depthBuffer);
		int status = gl.glCheckFramebufferStatus(GL3.GL_DRAW_FRAMEBUFFER);
		if (status != GL3.GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException(
					"Persistent scene framebuffer is incomplete: 0x"
							+ Integer.toHexString(status));
		gl.glViewport(0, 0, width, height);

		gl.glBindTexture(GL.GL_TEXTURE_2D, resolveTexture);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
		gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height,
				0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
		gl.glFramebufferTexture2D(GL3.GL_DRAW_FRAMEBUFFER,
				GL3.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, resolveTexture, 0);
		gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
		status = gl.glCheckFramebufferStatus(GL3.GL_DRAW_FRAMEBUFFER);
		if (status != GL3.GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException(
					"Persistent scene resolve framebuffer is incomplete: 0x"
							+ Integer.toHexString(status));
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		gl.glClearColor(0, 0, 0, 0);
		gl.glClearDepth(0);
		int[] previousDepthMask = new int[1];
		gl.glGetIntegerv(GL.GL_DEPTH_WRITEMASK, previousDepthMask, 0);
		gl.glDepthMask(true);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		gl.glDepthMask(previousDepthMask[0] != 0);
	}

	private void resolve(GL3 gl) {
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, framebuffer);
		gl.glReadBuffer(GL3.GL_COLOR_ATTACHMENT0);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
		gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
		gl.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
				GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
	}

	private void captureTarget(GL3 gl) {
		int[] value = new int[1];
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, value, 0);
		targetDrawFramebuffer = value[0];
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, value, 0);
		targetReadFramebuffer = value[0];
		gl.glGetIntegerv(GL.GL_VIEWPORT, targetViewport, 0);
		targetScissorEnabled = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		gl.glGetIntegerv(GL.GL_SCISSOR_BOX, targetScissor, 0);
	}

	private void restoreTarget(GL3 gl) {
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, targetDrawFramebuffer);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, targetReadFramebuffer);
		gl.glViewport(targetViewport[0], targetViewport[1],
				targetViewport[2], targetViewport[3]);
		if (targetScissorEnabled) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			gl.glScissor(targetScissor[0], targetScissor[1],
					targetScissor[2], targetScissor[3]);
		} else {
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
