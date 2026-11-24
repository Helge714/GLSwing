package com.vingame.glswing.ui;

/** Inclusive logical-line range whose first line remains visible when folded. */
public record GLCodeFoldRange(int startLine, int endLine) {
	public GLCodeFoldRange {
		if (startLine < 0 || endLine <= startLine)
			throw new IllegalArgumentException("A fold range must span at least two lines");
	}

	public boolean containsHiddenLine(int line) {
		return line > startLine && line <= endLine;
	}
}
