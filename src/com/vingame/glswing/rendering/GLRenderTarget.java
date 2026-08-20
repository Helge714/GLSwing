package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.internal.PersistentPickRecords;

/**
 * Persistent, lazily allocated RGBA8 framebuffer texture with optional
 * target-local integer picking surface. A target belongs to the first live
 * renderer that uses it and is released with that renderer.
 */
public final class GLRenderTarget implements AutoCloseable {

	private final int width;
	private final int height;
	private final GLRenderTargetOptions options;
	private GLRenderer2D owner;
	private GLTexture texture;
	private int framebuffer;
	private int pickingTexture;
	private int pickingFramebuffer;
	private final PersistentPickRecords pickingRecords =
			new PersistentPickRecords();
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

	public boolean isPickingEnabled() {
		return options.picking();
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
		int[] createdPickingFramebuffer = new int[1];
		int[] createdPickingTexture = new int[1];
		int[] previousDraw = new int[1];
		int[] previousRead = new int[1];
		int[] previousViewport = new int[4];
		int[] previousTexture = new int[1];
		float[] previousClearColor = new float[4];
		boolean previousScissor = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, previousDraw, 0);
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, previousRead, 0);
		gl.glGetIntegerv(GL.GL_VIEWPORT, previousViewport, 0);
		gl.glGetIntegerv(GL.GL_TEXTURE_BINDING_2D, previousTexture, 0);
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
			if (options.picking()) {
				gl.glGenTextures(1, createdPickingTexture, 0);
				gl.glGenFramebuffers(1, createdPickingFramebuffer, 0);
				if (createdPickingTexture[0] == 0
						|| createdPickingFramebuffer[0] == 0)
					throw new IllegalStateException(
							"Could not create the render-target picking surface");
				gl.glBindTexture(GL.GL_TEXTURE_2D, createdPickingTexture[0]);
				gl.glTexParameteri(GL.GL_TEXTURE_2D,
						GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
				gl.glTexParameteri(GL.GL_TEXTURE_2D,
						GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
				gl.glTexParameteri(GL.GL_TEXTURE_2D,
						GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
				gl.glTexParameteri(GL.GL_TEXTURE_2D,
						GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
				gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL3.GL_R32UI,
						width, height, 0, GL3.GL_RED_INTEGER,
						GL.GL_UNSIGNED_INT, null);
				gl.glBindFramebuffer(GL.GL_FRAMEBUFFER,
						createdPickingFramebuffer[0]);
				gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,
						GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D,
						createdPickingTexture[0], 0);
				gl.glDrawBuffers(1,
						new int[] { GL.GL_COLOR_ATTACHMENT0 }, 0);
				status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
				if (status != GL.GL_FRAMEBUFFER_COMPLETE)
					throw new IllegalStateException(
							"Render-target picking framebuffer is incomplete: 0x"
									+ Integer.toHexString(status));
				gl.glClearBufferuiv(GL3.GL_COLOR, 0, new int[] { 0 }, 0);
			}
			owner = renderer;
			texture = createdTexture;
			framebuffer = createdFramebuffer[0];
			pickingTexture = createdPickingTexture[0];
			pickingFramebuffer = createdPickingFramebuffer[0];
		} catch (RuntimeException error) {
			if (createdPickingFramebuffer[0] != 0)
				gl.glDeleteFramebuffers(1, createdPickingFramebuffer, 0);
			if (createdPickingTexture[0] != 0)
				gl.glDeleteTextures(1, createdPickingTexture, 0);
			if (createdFramebuffer[0] != 0)
				gl.glDeleteFramebuffers(1, createdFramebuffer, 0);
			createdTexture.dispose(gl);
			throw error;
		} finally {
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, previousDraw[0]);
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, previousRead[0]);
			gl.glViewport(previousViewport[0], previousViewport[1],
					previousViewport[2], previousViewport[3]);
			gl.glBindTexture(GL.GL_TEXTURE_2D, previousTexture[0]);
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

	synchronized int pickingFramebuffer(GLRenderer2D renderer, GL3 gl) {
		if (!options.picking())
			throw new IllegalStateException(
					"Render target was created without picking support");
		ensure(renderer, gl);
		return pickingFramebuffer;
	}

	synchronized int pickingTexture(GLRenderer2D renderer, GL3 gl) {
		if (!options.picking())
			return 0;
		ensure(renderer, gl);
		return pickingTexture;
	}

	PersistentPickRecords pickingRecords() {
		return pickingRecords;
	}

	synchronized void clearPicking(GLRenderer2D renderer, GL3 gl) {
		if (!options.picking())
			return;
		ensure(renderer, gl);
		int[] previousDraw = new int[1];
		int[] previousViewport = new int[4];
		int[] previousScissor = new int[4];
		boolean scissorEnabled = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, previousDraw, 0);
		gl.glGetIntegerv(GL.GL_VIEWPORT, previousViewport, 0);
		gl.glGetIntegerv(GL.GL_SCISSOR_BOX, previousScissor, 0);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, pickingFramebuffer);
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		gl.glClearBufferuiv(GL3.GL_COLOR, 0, new int[] { 0 }, 0);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, previousDraw[0]);
		gl.glViewport(previousViewport[0], previousViewport[1],
				previousViewport[2], previousViewport[3]);
		if (scissorEnabled) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			gl.glScissor(previousScissor[0], previousScissor[1],
					previousScissor[2], previousScissor[3]);
		} else {
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
		pickingRecords.reset();
	}

	synchronized boolean belongsTo(GLRenderer2D renderer) {
		return owner == renderer;
	}

	synchronized void release(GLRenderer2D renderer, GL3 gl) {
		if (owner != renderer)
			return;
		if (framebuffer != 0)
			gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);
		if (pickingFramebuffer != 0)
			gl.glDeleteFramebuffers(1, new int[] { pickingFramebuffer }, 0);
		if (pickingTexture != 0)
			gl.glDeleteTextures(1, new int[] { pickingTexture }, 0);
		if (texture != null && !texture.isDisposed())
			texture.dispose(gl);
		framebuffer = 0;
		pickingFramebuffer = 0;
		pickingTexture = 0;
		texture = null;
		pickingRecords.reset();
		owner = null;
	}
}
