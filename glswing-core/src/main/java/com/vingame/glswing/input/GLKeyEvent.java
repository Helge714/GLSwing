package com.vingame.glswing.input;

import java.awt.event.KeyEvent;

/** Keyboard event delivered to the focused lightweight GL control. */
public final class GLKeyEvent {

	private final KeyEvent originalEvent;

	public GLKeyEvent(KeyEvent originalEvent) {
		if (originalEvent == null)
			throw new IllegalArgumentException("Key event must not be null");
		this.originalEvent = originalEvent;
	}

	public KeyEvent getOriginalEvent() {
		return originalEvent;
	}

	public int getKeyCode() {
		return originalEvent.getKeyCode();
	}

	public char getKeyChar() {
		return originalEvent.getKeyChar();
	}

	public int getModifiersEx() {
		return originalEvent.getModifiersEx();
	}

	public void consume() {
		originalEvent.consume();
	}

	public boolean isConsumed() {
		return originalEvent.isConsumed();
	}
}
