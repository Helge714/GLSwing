package com.vingame.glswing.ui;

import java.awt.Color;

/** Resolved color associated with one half-open UTF-16 source range. */
public record GLCodeColor(int start, int end, Color color) {
	public GLCodeColor {
		if (start < 0 || end <= start || color == null)
			throw new IllegalArgumentException("Invalid code color");
	}
}
