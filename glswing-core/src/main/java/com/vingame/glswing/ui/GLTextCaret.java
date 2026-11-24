package com.vingame.glswing.ui;

/** One caret and its optional anchored selection in a text area. */
public record GLTextCaret(int anchor, int position) {
	public GLTextCaret {
		if (anchor < 0 || position < 0)
			throw new IllegalArgumentException("Caret offsets must not be negative");
	}
	public int start() { return Math.min(anchor, position); }
	public int end() { return Math.max(anchor, position); }
}
