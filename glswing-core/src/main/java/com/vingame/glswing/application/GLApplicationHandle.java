package com.vingame.glswing.application;

import java.util.concurrent.CompletableFuture;

import javax.swing.JFrame;

/** Asynchronous lifetime handle returned immediately by {@link GLApplication#launch()}. */
public final class GLApplicationHandle implements AutoCloseable {
	private final CompletableFuture<GLApplicationContext> ready =
			new CompletableFuture<>();
	private final CompletableFuture<Void> closed = new CompletableFuture<>();
	private volatile Runnable closeAction;
	private volatile JFrame window;

	GLApplicationHandle() { }
	void window(JFrame value) { window = value; }
	void closeAction(Runnable value) { closeAction = value; }
	CompletableFuture<GLApplicationContext> mutableReadyFuture() { return ready; }
	CompletableFuture<Void> mutableClosedFuture() { return closed; }

	public CompletableFuture<GLApplicationContext> readyFuture() { return ready; }
	public CompletableFuture<Void> closedFuture() { return closed; }
	public JFrame window() { return window; }

	@Override public void close() {
		Runnable action = closeAction;
		if (action != null) action.run();
	}
}
