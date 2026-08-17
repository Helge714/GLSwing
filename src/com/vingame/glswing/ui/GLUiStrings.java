package com.vingame.glswing.ui;

import java.util.Objects;

/** Legacy fixed text bundle; new code should use {@link GLLocalizer}. */
public final class GLUiStrings {

	public static final GLUiStrings ENGLISH = new GLUiStrings("Reset");
	public static final GLUiStrings GERMAN = new GLUiStrings("Zurücksetzen");

	private final String reset;

	public GLUiStrings(String reset) {
		this.reset = requireText(reset, "Reset text");
	}

	public String reset() {
		return reset;
	}

	public GLUiStrings withReset(String text) {
		return new GLUiStrings(text);
	}

	@Override
	public boolean equals(Object object) {
		return object instanceof GLUiStrings other
				&& reset.equals(other.reset);
	}

	@Override
	public int hashCode() {
		return Objects.hash(reset);
	}

	@Override
	public String toString() {
		return "GLUiStrings[reset=" + reset + "]";
	}

	private static String requireText(String text, String description) {
		if (text == null || text.isBlank())
			throw new IllegalArgumentException(description + " must not be blank");
		return text;
	}
}
