package com.vingame.glswing.rendering;

import java.awt.Color;

/** Two persistent render targets with safe read/write swapping. */
public final class GLFeedbackBuffer implements AutoCloseable {

	private final GLRenderTarget first;
	private final GLRenderTarget second;
	private GLRenderTarget current;
	private GLRenderTarget next;
	private GLContext2D updatingContext;

	public GLFeedbackBuffer(int width, int height) {
		this(width, height, GLRenderTargetOptions.DEFAULT);
	}

	public GLFeedbackBuffer(int width, int height,
			GLRenderTargetOptions options) {
		first = new GLRenderTarget(width, height, options);
		second = new GLRenderTarget(width, height, options);
		current = first;
		next = second;
	}

	/** Target containing the latest completed state. */
	public GLRenderTarget current() {
		return current;
	}

	/** Target that receives the next state. */
	public GLRenderTarget next() {
		return next;
	}

	public int getWidth() {
		return first.getWidth();
	}

	public int getHeight() {
		return first.getHeight();
	}

	/** Starts writing the next state while {@link #current()} remains readable. */
	public void beginUpdate(GLContext2D context) {
		if (context == null)
			throw new IllegalArgumentException("Rendering context must not be null");
		if (updatingContext != null)
			throw new IllegalStateException("A feedback update is already active");
		context.beginRenderTarget(next);
		updatingContext = context;
	}

	/** Finishes the update and makes the newly rendered target current. */
	public void endUpdate(GLContext2D context) {
		requireUpdatingContext(context);
		try {
			context.endRenderTarget();
		} finally {
			updatingContext = null;
		}
		swap();
	}

	/** Ends an unsuccessful update without swapping the readable target. */
	public void cancelUpdate(GLContext2D context) {
		requireUpdatingContext(context);
		try {
			context.endRenderTarget();
		} finally {
			updatingContext = null;
		}
	}

	/** Runs one exception-safe update and swaps only after successful drawing. */
	public void update(GLContext2D context, Runnable drawing) {
		if (drawing == null)
			throw new IllegalArgumentException("Feedback drawing must not be null");
		beginUpdate(context);
		boolean successful = false;
		try {
			drawing.run();
			successful = true;
		} finally {
			if (successful)
				endUpdate(context);
			else
				cancelUpdate(context);
		}
	}

	/** Clears both states without changing which target is current. */
	public void clear(GLContext2D context, Color color) {
		if (updatingContext != null)
			throw new IllegalStateException(
					"Cannot clear a feedback buffer during an update");
		context.withRenderTarget(first, () -> context.clear(color));
		context.withRenderTarget(second, () -> context.clear(color));
	}

	public void drawCurrent(GLContext2D context, float x, float y,
			float width, float height) {
		context.drawRenderTarget(current, x, y, width, height);
	}

	@Override
	public void close() {
		if (updatingContext != null)
			throw new IllegalStateException(
					"Cannot close a feedback buffer during an update");
		first.close();
		second.close();
	}

	private void swap() {
		GLRenderTarget formerCurrent = current;
		current = next;
		next = formerCurrent;
	}

	private void requireUpdatingContext(GLContext2D context) {
		if (context == null || updatingContext != context)
			throw new IllegalArgumentException(
					"Feedback update must end on the context that began it");
	}
}
