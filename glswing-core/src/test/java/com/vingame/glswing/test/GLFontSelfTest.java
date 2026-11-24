package com.vingame.glswing.test;

import com.vingame.glswing.rendering.GLFont;

/** CPU-only checks for the AWT-free public font API and lazy lifecycle. */
public final class GLFontSelfTest {

	private GLFontSelfTest() {
	}

	public static void main(String[] args) {
		GLFont font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 18);
		check(font.getStyle() == GLFont.BOLD, "Font style must be retained");
		check(font.getSize() == 18, "Font size must be retained");
		check(font.measureText("GLSwing") > 0, "Text must be measurable before GL initialization");
		"Δ–—…›←→✓".codePoints().forEach(codePoint -> check(font.canRender(codePoint),
				"Built-in UI symbol is missing from the default atlas: U+"
						+ Integer.toHexString(codePoint).toUpperCase()));
		check(!font.isInitialized(), "Constructor must not require a GL context");
		check(!font.isDisposed(), "New font must be live");

		GLFont defaultSize = new GLFont(GLFont.MONOSPACED, GLFont.PLAIN);
		check(defaultSize.getSize() == GLFont.DEFAULT_SIZE, "Two-argument constructor uses default size");

		expectFailure(() -> new GLFont(GLFont.SERIF, 16, 12),
				"Unsupported style must fail");
		expectFailure(() -> new GLFont(GLFont.SERIF, GLFont.PLAIN, 0),
				"Zero font size must fail");
		expectFailure(() -> font.canRender(-1),
				"Invalid Unicode code point must fail");

		font.dispose(null); // Safe because a lazy font has no GPU resource yet.
		check(font.isDisposed(), "Disposed font must report its state");
		expectFailure(() -> font.measureText("disposed"),
				"Disposed font must reject further use");
		System.out.println("GLFont self-test passed.");
	}

	private static void expectFailure(Runnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException | IllegalStateException expected) {
			// Expected.
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
