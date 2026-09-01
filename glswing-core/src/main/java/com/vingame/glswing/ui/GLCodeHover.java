package com.vingame.glswing.ui;

/** Hover information covering a half-open source range. */
public record GLCodeHover(int start, int end, String contents) {
	public GLCodeHover {
		if (start < 0 || end <= start || contents == null || contents.isBlank())
			throw new IllegalArgumentException("Invalid code hover");
	}
}
