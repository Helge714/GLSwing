package com.vingame.glswing.ui;

/** Transient syntax token using UTF-16 document offsets. */
public record GLCodeToken(int start, int end, Type type) {
	public enum Type { KEYWORD, STRING, NUMBER, COMMENT, TYPE, PLAIN }
	public GLCodeToken {
		if (start < 0 || end < start || type == null)
			throw new IllegalArgumentException("Invalid code token");
	}
}
