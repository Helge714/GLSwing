package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

/**
 * Persistent, lazily allocated RGBA8 framebuffer texture. A target belongs to
 * the first live renderer that uses it and is released with that renderer.
 */
public final class GLRenderTarget implements AutoCloseable {

	private final int width;
	private final int height;
	private final GLRenderTargetOptions options;
	private GLRenderer2D owner;
	private GLTexture texture;
	private int framebuffer;
	private volatile boolean closed;

	public GLRenderTarget(int width, int height) {
		this(width, height, GLRenderTargetOptions.DEFAULT);
	}

	public GLRenderTarget(int width, int height,
			GLRenderTargetOptions options) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"Render-target dimensions must be positive");
		if (options == null)
			throw new IllegalArgumentException(
					"Render-target options must not be null");
		this.width = width;
		this.height = height;
		this.options = options;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public GLRenderTargetOptions getOptions() {
		return options;
	}

	public boolean isClosed() {
		return closed;
	}

	@Override
	public void close() {
		closed = true;
	}

	synchronized void ensure(GLRenderer2D renderer, GL3 gl) {
		if (closed)
			throw new IllegalStateException("Render target has already been closed");
		if (owner != null && owner != renderer)
			throw new IllegalStateException(
					"A render target cannot be shared by independent renderers");
		if (texture != null)
			return;

		boolean linear = options.filter()
				== GLRenderTargetOptions.Filter.LINEAR;
		boolean repeating = options.wrap()
				== GLRenderTargetOptions.Wrap.REPEAT;
		GLTexture createdTexture = GLTexture.createEmpty(gl, width, height,
				linear, repeating, false);
		int[] createdFramebuffer = new int[1];
		int[] previousDraw = new int[1];
		int[] previousRead = new int[1];
		int[] previousViewport = new int[4];
		float[] previousClearColor = new float[4];
		boolean previousScissor = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, previousDraw, 0);
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, previousRead, 0);
		gl.glGetIntegerv(GL.GL_VIEWPORT, previousViewport, 0);
		gl.glGetFloatv(GL.GL_COLOR_CLEAR_VALUE, previousClearColor, 0);
		try {
			gl.glGenFramebuffers(1, createdFramebuffer, 0);
			if (createdFramebuffer[0] == 0)
				throw new IllegalStateException(
						"Could not create the render-target framebuffer");
			gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, createdFramebuffer[0]);
			gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,
					GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D,
					createdTexture.textureId(), 0);
			int status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
			if (status != GL.GL_FRAMEBUFFER_COMPLETE)
				throw new IllegalStateException(
						"Render-target framebuffer is incomplete: 0x"
								+ Integer.toHexString(status));
			gl.glViewport(0, 0, width, height);
			gl.glDisable(GL.GL_SCISSOR_TEST);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);
			owner = renderer;
			texture = createdTexture;
			framebuffer = createdFramebuffer[0];
		} catch (RuntimeException error) {
			if (createdFramebuffer[0] != 0)
				gl.glDeleteFramebuffers(1, createdFramebuffer, 0);
			createdTexture.dispose(gl);
			throw error;
		} finally {
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, previousDraw[0]);
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, previousRead[0]);
			gl.glViewport(previousViewport[0], previousViewport[1],
					previousViewport[2], previousViewport[3]);
			gl.glClearColor(previousClearColor[0], previousClearColor[1],
					previousClearColor[2], previousClearColor[3]);
			if (previousScissor)
				gl.glEnable(GL.GL_SCISSOR_TEST);
			else
				gl.glDisable(GL.GL_SCISSOR_TEST);
		}
	}

	synchronized int framebuffer(GLRenderer2D renderer, GL3 gl) {
		ensure(renderer, gl);
		return framebuffer;
	}

	synchronized GLTexture texture(GLRenderer2D renderer, GL3 gl) {
		ensure(renderer, gl);
		return texture;
	}

	synchronized boolean belongsTo(GLRenderer2D renderer) {
		return owner == renderer;
	}

	synchronized void release(GLRenderer2D renderer, GL3 gl) {
		if (owner != renderer)
			return;
		if (framebuffer != 0)
			gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);
		if (texture != null && !texture.isDisposed())
			texture.dispose(gl);
		framebuffer = 0;
		texture = null;
		owner = null;
	}
}
