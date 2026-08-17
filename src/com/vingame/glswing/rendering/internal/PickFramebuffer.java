package com.vingame.glswing.rendering.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GL3ES3;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;

/** Owns the integer ID render target and the three built-in picking shaders. */
public final class PickFramebuffer {

	private static final float DEFAULT_ALPHA_THRESHOLD = 0.1f;
	private static final int READBACK_SLOTS = 3;

	private final ShaderProgram filledProgram;
	private final ShaderProgram textureProgram;
	private final ShaderProgram textProgram;
	private final ShaderProgram uiProgram;
	private int framebuffer;
	private int idTexture;
	private int width;
	private int height;
	private int allocatedWidth;
	private int allocatedHeight;
	private int previousDrawFramebuffer;
	private final int[] previousViewport = new int[4];
	private boolean previousScissorEnabled;
	private final int[] previousScissor = new int[4];
	private final int[] clearValue = { 0 };
	private boolean fullClearPending = true;
	private final int[] readbackBuffers = new int[READBACK_SLOTS];
	private final int[] readbackCapacities = new int[READBACK_SLOTS];
	private final long[] readbackFences = new long[READBACK_SLOTS];
	private final long[] readbackSequences = new long[READBACK_SLOTS];
	private final int[] readbackWidths = new int[READBACK_SLOTS];
	private final int[] readbackHeights = new int[READBACK_SLOTS];
	private final PickFrameRecords[] readbackRecords =
			new PickFrameRecords[READBACK_SLOTS];
	private long nextReadbackSequence = 1;
	private volatile long latestRequestedSequence;
	private long pendingReadbackSequence;
	private int pendingReadbackWidth;
	private int pendingReadbackHeight;
	private PickFrameRecords pendingReadbackRecords;
	private volatile PickSnapshot completedSnapshot;
	private volatile boolean latestSnapshotInvalidated = true;

	private PickFramebuffer(ShaderProgram filledProgram, ShaderProgram textureProgram,
			ShaderProgram textProgram, ShaderProgram uiProgram,
			int framebuffer, int idTexture) {
		this.filledProgram = filledProgram;
		this.textureProgram = textureProgram;
		this.textProgram = textProgram;
		this.uiProgram = uiProgram;
		this.framebuffer = framebuffer;
		this.idTexture = idTexture;
	}

	public static PickFramebuffer create(GL3 gl) {
		ShaderProgram filled = ShaderProgram.fromBuiltInResources(
				gl, "pick_filled.vert", "pick_filled.frag");
		ShaderProgram texture = null;
		ShaderProgram text = null;
		ShaderProgram ui = null;
		int framebuffer = 0;
		int idTexture = 0;
		try {
			texture = ShaderProgram.fromBuiltInResources(
					gl, "pick_texture.vert", "pick_texture.frag");
			text = ShaderProgram.fromBuiltInResources(
					gl, "pick_text.vert", "pick_text.frag");
			ui = ShaderProgram.fromBuiltInResources(
					gl, "pick_ui_quad.vert", "pick_ui_quad.frag");
			int[] id = new int[1];
			gl.glGenFramebuffers(1, id, 0);
			framebuffer = id[0];
			gl.glGenTextures(1, id, 0);
			idTexture = id[0];
			return new PickFramebuffer(filled, texture, text, ui, framebuffer, idTexture);
		} catch (RuntimeException error) {
			if (idTexture != 0)
				gl.glDeleteTextures(1, new int[] { idTexture }, 0);
			if (framebuffer != 0)
				gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);
			if (text != null)
				text.dispose(gl);
			if (ui != null)
				ui.dispose(gl);
			if (texture != null)
				texture.dispose(gl);
			filled.dispose(gl);
			throw error;
		}
	}

	public void beginFrame(GL3 gl, int width, int height) {
		beginFrame(gl, width, height, true);
	}

	/** Updates logical dimensions while optionally retaining IDs from the last frame. */
	public void beginFrame(GL3 gl, int width, int height, boolean rebuild) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Picking dimensions must be positive");
		boolean resized = this.width != width || this.height != height;
		this.width = width;
		this.height = height;
		// Clearing is deferred until the first actual picking draw. Frames without
		// pickable geometry therefore neither resize nor clear the integer FBO.
		if (rebuild || resized) {
			// The preceding CPU snapshot stops being authoritative as soon as a new
			// ID frame begins. Waiting until requestSnapshot() leaves a race window:
			// damage has already been consumed, but the old snapshot still appears
			// current while the render thread is building the replacement frame.
			latestSnapshotInvalidated = true;
			fullClearPending = true;
		}
	}

	/** Clears all IDs while preserving the visible draw target and viewport. */
	public void clear(GL3 gl) {
		if (fullClearPending)
			return;
		captureCurrentTarget(gl);
		bind(gl);
		applyScaledScissor(gl);
		gl.glClearBufferuiv(GL3.GL_COLOR, 0, clearValue, 0);
		restorePreviousTarget(gl);
	}

	public void drawFilled(GL3 gl, int vao, int vertexCount, float[] projection) {
		ensureSize(gl, width, height);
		captureCurrentTarget(gl);
		bindForPicking(gl);
		clearPendingFrame(gl);
		filledProgram.use(gl);
		filledProgram.setMatrix4(gl, "uProjection", projection);
		filledProgram.setFloat(gl, "uAlphaThreshold", DEFAULT_ALPHA_THRESHOLD);
		draw(gl, vao, vertexCount);
		restoreAfterPicking(gl);
	}

	public void drawTexture(GL3 gl, int vao, int vertexCount, float[] projection,
			GLTexture[] textures, float alphaThreshold) {
		ensureSize(gl, width, height);
		captureCurrentTarget(gl);
		bindForPicking(gl);
		clearPendingFrame(gl);
		textureProgram.use(gl);
		textureProgram.setMatrix4(gl, "uProjection", projection);
		setTextureSamplers(gl, textureProgram);
		textureProgram.setFloat(gl, "uAlphaThreshold", alphaThreshold);
		bindTextures(gl, textures);
		draw(gl, vao, vertexCount);
		unbindTextures(gl, textures);
		restoreAfterPicking(gl);
	}

	public void drawText(GL3 gl, int vao, int vertexCount, float[] projection,
			GLTexture atlas, float msdfPixelRange) {
		ensureSize(gl, width, height);
		captureCurrentTarget(gl);
		bindForPicking(gl);
		clearPendingFrame(gl);
		textProgram.use(gl);
		textProgram.setMatrix4(gl, "uProjection", projection);
		textProgram.setInteger(gl, "uTexture", 0);
		textProgram.setFloat(gl, "uMsdfPixelRange", msdfPixelRange);
		textProgram.setFloat(gl, "uAlphaThreshold", DEFAULT_ALPHA_THRESHOLD);
		atlas.bind(gl, 0);
		draw(gl, vao, vertexCount);
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		restoreAfterPicking(gl);
	}

	public void drawUI(GL3 gl, int vao, int instanceCount, float[] projection,
			GLTexture[] textures, float textAlphaThreshold,
			float textureAlphaThreshold) {
		ensureSize(gl, width, height);
		captureCurrentTarget(gl);
		bindForPicking(gl);
		clearPendingFrame(gl);
		uiProgram.use(gl);
		uiProgram.setMatrix4(gl, "uProjection", projection);
		setTextureSamplers(gl, uiProgram);
		uiProgram.setFloat(gl, "uTextAlphaThreshold", textAlphaThreshold);
		uiProgram.setFloat(gl, "uTextureAlphaThreshold", textureAlphaThreshold);
		// Picking is rendered into a one-pixel-per-logical-pixel framebuffer.
		uiProgram.setVector2(gl, "uDeviceScale", 1, 1);
		bindTextures(gl, textures);
		gl.glBindVertexArray(vao);
		gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, instanceCount);
		unbindTextures(gl, textures);
		restoreAfterPicking(gl);
	}

	public int readId(GL3 gl, int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height)
			return 0;
		if (fullClearPending)
			return 0;
		int[] previousReadFramebuffer = new int[1];
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, previousReadFramebuffer, 0);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, framebuffer);
		gl.glReadBuffer(GL3.GL_COLOR_ATTACHMENT0);
		IntBuffer result = Buffers.newDirectIntBuffer(1);
		gl.glReadPixels(x, height - 1 - y, 1, 1,
				GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, result);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, previousReadFramebuffer[0]);
		return result.get(0);
	}

	/**
	 * Starts a complete asynchronous ID-buffer transfer. If all ring slots are
	 * still occupied, the newest generation is retained and submitted as soon as
	 * one becomes available. This never waits for the GPU.
	 */
	public void requestSnapshot(GL3 gl, PickFrameRecords records) {
		if (records == null)
			throw new IllegalArgumentException("Pick records must not be null");
		// The ID surface already contains this generation. Replace any older
		// unsent request before polling: submitting old records now would pair
		// them with the pixels of this newer frame.
		pendingReadbackSequence = nextReadbackSequence++;
		latestRequestedSequence = pendingReadbackSequence;
		pendingReadbackWidth = width;
		pendingReadbackHeight = height;
		pendingReadbackRecords = records.snapshot();
		// latestRequestedSequence now names the replacement generation, so the
		// old completed snapshot remains non-current until this transfer publishes.
		latestSnapshotInvalidated = false;
		pollSnapshots(gl);
		submitPendingSnapshot(gl);
	}

	private void submitPendingSnapshot(GL3 gl) {
		if (pendingReadbackRecords == null)
			return;
		int slot = freeReadbackSlot();
		if (slot < 0)
			return;
		// pollSnapshots is called before beginFrame may resize or redraw the ID
		// surface. Therefore a retained request still describes its contents here.
		if (width != pendingReadbackWidth || height != pendingReadbackHeight) {
			clearPendingReadback();
			return;
		}
		ensureReadableSurface(gl);
		ensureReadbackBuffers(gl);

		int bytes = Math.multiplyExact(Math.multiplyExact(width, height), Integer.BYTES);
		int[] previousReadFramebuffer = new int[1];
		int[] previousPackBuffer = new int[1];
		gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING,
				previousReadFramebuffer, 0);
		gl.glGetIntegerv(GL2ES3.GL_PIXEL_PACK_BUFFER_BINDING,
				previousPackBuffer, 0);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, framebuffer);
		gl.glReadBuffer(GL3.GL_COLOR_ATTACHMENT0);
		gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, readbackBuffers[slot]);
		if (readbackCapacities[slot] != bytes) {
			gl.glBufferData(GL2ES3.GL_PIXEL_PACK_BUFFER, bytes, null,
					GL2ES3.GL_STREAM_READ);
			readbackCapacities[slot] = bytes;
		}
		gl.glReadPixels(0, 0, width, height, GL3.GL_RED_INTEGER,
				GL.GL_UNSIGNED_INT, 0L);
		readbackFences[slot] = gl.glFenceSync(
				GL3ES3.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		readbackSequences[slot] = pendingReadbackSequence;
		readbackWidths[slot] = pendingReadbackWidth;
		readbackHeights[slot] = pendingReadbackHeight;
		readbackRecords[slot] = pendingReadbackRecords;
		clearPendingReadback();
		gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, previousPackBuffer[0]);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER,
				previousReadFramebuffer[0]);
	}

	/** Publishes every transfer whose GPU fence has completed, without waiting. */
	public void pollSnapshots(GL3 gl) {
		if (readbackBuffers[0] == 0)
			return;
		for (int slot = 0; slot < READBACK_SLOTS; slot++) {
			long fence = readbackFences[slot];
			if (fence == 0)
				continue;
			int status = gl.glClientWaitSync(fence,
					GL3ES3.GL_SYNC_FLUSH_COMMANDS_BIT, 0);
			if (status == GL3ES3.GL_TIMEOUT_EXPIRED)
				continue;
			if (status == GL3ES3.GL_WAIT_FAILED) {
				releaseReadbackSlot(gl, slot);
				continue;
			}
			if (status != GL3ES3.GL_ALREADY_SIGNALED
					&& status != GL3ES3.GL_CONDITION_SATISFIED)
				continue;
			publishSnapshot(gl, slot);
		}
		submitPendingSnapshot(gl);
	}

	/** Returns null until a dimension-compatible completed snapshot exists. */
	public GLPickResult readSnapshot(int x, int y,
			int expectedWidth, int expectedHeight) {
		PickSnapshot snapshot = completedSnapshot;
		if (snapshot == null || snapshot.width != expectedWidth
				|| snapshot.height != expectedHeight)
			return null;
		if (x < 0 || y < 0 || x >= snapshot.width || y >= snapshot.height)
			return GLPickResult.none();
		int record = snapshot.ids[(snapshot.height - 1 - y) * snapshot.width + x];
		return snapshot.records.resolve(record, x, y);
	}

	/** Whether the published CPU image belongs to the newest requested ID frame. */
	public boolean isLatestSnapshotReady() {
		PickSnapshot snapshot = completedSnapshot;
		return !latestSnapshotInvalidated && snapshot != null
				&& latestRequestedSequence != 0
				&& snapshot.sequence == latestRequestedSequence;
	}

	public void dispose(GL3 gl) {
		for (int slot = 0; slot < READBACK_SLOTS; slot++)
			releaseReadbackSlot(gl, slot);
		if (readbackBuffers[0] != 0) {
			gl.glDeleteBuffers(READBACK_SLOTS, readbackBuffers, 0);
			java.util.Arrays.fill(readbackBuffers, 0);
		}
		clearPendingReadback();
		completedSnapshot = null;
		latestRequestedSequence = 0;
		latestSnapshotInvalidated = true;
		if (idTexture != 0) {
			gl.glDeleteTextures(1, new int[] { idTexture }, 0);
			idTexture = 0;
		}
		if (framebuffer != 0) {
			gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);
			framebuffer = 0;
		}
		filledProgram.dispose(gl);
		textureProgram.dispose(gl);
		textProgram.dispose(gl);
		uiProgram.dispose(gl);
	}

	private void ensureReadableSurface(GL3 gl) {
		ensureSize(gl, width, height);
		if (!fullClearPending)
			return;
		captureCurrentTarget(gl);
		bind(gl);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		gl.glClearBufferuiv(GL3.GL_COLOR, 0, clearValue, 0);
		fullClearPending = false;
		restorePreviousTarget(gl);
	}

	private void ensureReadbackBuffers(GL3 gl) {
		if (readbackBuffers[0] != 0)
			return;
		gl.glGenBuffers(READBACK_SLOTS, readbackBuffers, 0);
		for (int buffer : readbackBuffers)
			if (buffer == 0)
				throw new IllegalStateException("Could not create picking readback PBOs");
	}

	private int freeReadbackSlot() {
		for (int slot = 0; slot < READBACK_SLOTS; slot++)
			if (readbackFences[slot] == 0)
				return slot;
		return -1;
	}

	private void publishSnapshot(GL3 gl, int slot) {
		int bytes = Math.multiplyExact(
				Math.multiplyExact(readbackWidths[slot], readbackHeights[slot]),
				Integer.BYTES);
		int[] previousPackBuffer = new int[1];
		gl.glGetIntegerv(GL2ES3.GL_PIXEL_PACK_BUFFER_BINDING,
				previousPackBuffer, 0);
		gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, readbackBuffers[slot]);
		ByteBuffer mapped = gl.glMapBufferRange(GL2ES3.GL_PIXEL_PACK_BUFFER,
				0, bytes, GL.GL_MAP_READ_BIT);
		if (mapped != null) {
			int[] ids = new int[bytes / Integer.BYTES];
			mapped.order(ByteOrder.nativeOrder()).asIntBuffer().get(ids);
			gl.glUnmapBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER);
			PickSnapshot previous = completedSnapshot;
			if (previous == null || readbackSequences[slot] > previous.sequence)
				completedSnapshot = new PickSnapshot(readbackSequences[slot],
						readbackWidths[slot], readbackHeights[slot], ids,
						readbackRecords[slot]);
		}
		gl.glBindBuffer(GL2ES3.GL_PIXEL_PACK_BUFFER, previousPackBuffer[0]);
		releaseReadbackSlot(gl, slot);
	}

	private void releaseReadbackSlot(GL3 gl, int slot) {
		if (readbackFences[slot] != 0) {
			gl.glDeleteSync(readbackFences[slot]);
			readbackFences[slot] = 0;
		}
		readbackSequences[slot] = 0;
		readbackWidths[slot] = 0;
		readbackHeights[slot] = 0;
		readbackRecords[slot] = null;
	}

	private void clearPendingReadback() {
		pendingReadbackSequence = 0;
		pendingReadbackWidth = 0;
		pendingReadbackHeight = 0;
		pendingReadbackRecords = null;
	}

	private record PickSnapshot(long sequence, int width, int height,
			int[] ids, PickFrameRecords records) {
	}

	private void ensureSize(GL3 gl, int requestedWidth, int requestedHeight) {
		if (allocatedWidth == requestedWidth && allocatedHeight == requestedHeight)
			return;
		allocatedWidth = requestedWidth;
		allocatedHeight = requestedHeight;
		captureCurrentTarget(gl);
		int[] previousTexture = new int[1];
		gl.glGetIntegerv(GL.GL_TEXTURE_BINDING_2D, previousTexture, 0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, idTexture);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
		gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL3.GL_R32UI,
				allocatedWidth, allocatedHeight,
				0, GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, null);
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
		gl.glFramebufferTexture2D(GL3.GL_DRAW_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0,
				GL.GL_TEXTURE_2D, idTexture, 0);
		gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
		int status = gl.glCheckFramebufferStatus(GL3.GL_DRAW_FRAMEBUFFER);
		gl.glBindTexture(GL.GL_TEXTURE_2D, previousTexture[0]);
		restorePreviousTarget(gl);
		if (status != GL3.GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException(
					"Picking framebuffer is incomplete: 0x" + Integer.toHexString(status));
	}

	private void captureCurrentTarget(GL3 gl) {
		int[] framebuffer = new int[1];
		gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, framebuffer, 0);
		previousDrawFramebuffer = framebuffer[0];
		gl.glGetIntegerv(GL.GL_VIEWPORT, previousViewport, 0);
		previousScissorEnabled = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		gl.glGetIntegerv(GL.GL_SCISSOR_BOX, previousScissor, 0);
	}

	private void bindForPicking(GL3 gl) {
		bind(gl);
		applyScaledScissor(gl);
		gl.glDisable(GL.GL_BLEND);
		gl.glDisable(GL.GL_MULTISAMPLE);
	}

	private void clearPendingFrame(GL3 gl) {
		if (!fullClearPending)
			return;
		// A frame starts with a logically empty picking target. Clear the entire
		// physical texture once, even if its first draw happens inside a bounded
		// layer, then restore that draw's scaled scissor rectangle.
		gl.glDisable(GL.GL_SCISSOR_TEST);
		gl.glClearBufferuiv(GL3.GL_COLOR, 0, clearValue, 0);
		fullClearPending = false;
		applyScaledScissor(gl);
	}

	private void restoreAfterPicking(GL3 gl) {
		gl.glUseProgram(0);
		gl.glBindVertexArray(0);
		restorePreviousTarget(gl);
		gl.glEnable(GL.GL_MULTISAMPLE);
		gl.glEnable(GL.GL_BLEND);
	}

	private void bind(GL3 gl) {
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
		gl.glViewport(0, 0, width, height);
	}

	private void restorePreviousTarget(GL3 gl) {
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
		gl.glViewport(previousViewport[0], previousViewport[1],
				previousViewport[2], previousViewport[3]);
		if (previousScissorEnabled) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			gl.glScissor(previousScissor[0], previousScissor[1],
					previousScissor[2], previousScissor[3]);
		} else {
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
	}

	private void applyScaledScissor(GL3 gl) {
		if (!previousScissorEnabled) {
			gl.glDisable(GL.GL_SCISSOR_TEST);
			return;
		}
		float scaleX = width / (float) Math.max(1, previousViewport[2]);
		float scaleY = height / (float) Math.max(1, previousViewport[3]);
		int minimumX = (int) Math.floor(
				(previousScissor[0] - previousViewport[0]) * scaleX);
		int minimumY = (int) Math.floor(
				(previousScissor[1] - previousViewport[1]) * scaleY);
		int maximumX = (int) Math.ceil(
				(previousScissor[0] + previousScissor[2] - previousViewport[0]) * scaleX);
		int maximumY = (int) Math.ceil(
				(previousScissor[1] + previousScissor[3] - previousViewport[1]) * scaleY);
		minimumX = Math.max(0, Math.min(width, minimumX));
		minimumY = Math.max(0, Math.min(height, minimumY));
		maximumX = Math.max(0, Math.min(width, maximumX));
		maximumY = Math.max(0, Math.min(height, maximumY));
		gl.glEnable(GL.GL_SCISSOR_TEST);
		gl.glScissor(minimumX, minimumY,
				Math.max(0, maximumX - minimumX),
				Math.max(0, maximumY - minimumY));
	}

	private static void draw(GL3 gl, int vao, int vertexCount) {
		gl.glBindVertexArray(vao);
		gl.glDrawArrays(GL.GL_TRIANGLES, 0, vertexCount);
	}

	private static void setTextureSamplers(GL3 gl, ShaderProgram program) {
		for (int slot = 0; slot < 16; slot++)
			program.setInteger(gl, "uTextures[" + slot + "]", slot);
	}

	private static void bindTextures(GL3 gl, GLTexture[] textures) {
		for (int slot = 0; slot < textures.length; slot++)
			textures[slot].bind(gl, slot);
	}

	private static void unbindTextures(GL3 gl, GLTexture[] textures) {
		for (int slot = 0; slot < textures.length; slot++) {
			gl.glActiveTexture(GL.GL_TEXTURE0 + slot);
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		}
		gl.glActiveTexture(GL.GL_TEXTURE0);
	}
}
