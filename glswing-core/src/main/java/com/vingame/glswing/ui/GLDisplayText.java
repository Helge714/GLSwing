package com.vingame.glswing.ui;

/** Internal normalization for developer-supplied, visible UI labels. */
final class GLDisplayText {
	private GLDisplayText() {
	}

	static String normalizeEllipses(String text) {
		return text == null ? null : text.replace("...", "\u2026");
	}
}
