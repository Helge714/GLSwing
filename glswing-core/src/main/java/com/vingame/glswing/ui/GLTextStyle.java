package com.vingame.glswing.ui;

/** Immutable character formatting stored by {@link GLTextDocument}. */
public record GLTextStyle(boolean bold, boolean italic) {
	public static final GLTextStyle PLAIN = new GLTextStyle(false, false);

	public GLTextStyle withBold(boolean value) { return new GLTextStyle(value, italic); }
	public GLTextStyle withItalic(boolean value) { return new GLTextStyle(bold, value); }
}
