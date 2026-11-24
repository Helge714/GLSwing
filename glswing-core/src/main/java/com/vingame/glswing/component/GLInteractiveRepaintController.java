package com.vingame.glswing.component;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Timer;

/** Coalesces picking rebuilds while a retained subtree changes continuously. */
final class GLInteractiveRepaintController {

	private final Timer settleTimer;
	private final Runnable repaintPicking;
	private final Map<Object, Runnable> settledCallbacks = new LinkedHashMap<>();
	private boolean pending;

	GLInteractiveRepaintController(Runnable repaintPicking) {
		this.repaintPicking = repaintPicking;
		settleTimer = new Timer(75, event -> settle());
		settleTimer.setRepeats(false);
	}

	void request(Object source, int delayMillis, Runnable callback) {
		settleTimer.setInitialDelay(Math.max(0, delayMillis));
		settleTimer.setDelay(Math.max(0, delayMillis));
		if (callback != null)
			settledCallbacks.put(source, callback);
		pending = true;
		settleTimer.restart();
	}

	boolean settle() {
		if (!pending)
			return false;
		settleTimer.stop();
		pending = false;
		runCallbacks();
		repaintPicking.run();
		return true;
	}

	void cancel() {
		settleTimer.stop();
		pending = false;
		runCallbacks();
	}

	private void runCallbacks() {
		Runnable[] callbacks = settledCallbacks.values().toArray(Runnable[]::new);
		settledCallbacks.clear();
		for (Runnable callback : callbacks)
			callback.run();
	}
}
