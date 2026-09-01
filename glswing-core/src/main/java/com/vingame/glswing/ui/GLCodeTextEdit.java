package com.vingame.glswing.ui;

/** One source range replacement associated with a completion proposal. */
public record GLCodeTextEdit(int start, int end, String text) {
	public GLCodeTextEdit {
		if (start < 0 || end < start || text == null)
			throw new IllegalArgumentException("Invalid code text edit");
	}
}
