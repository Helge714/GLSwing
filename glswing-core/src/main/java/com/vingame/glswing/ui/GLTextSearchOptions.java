package com.vingame.glswing.ui;

/** Immutable matching policy for text search and replacement. */
public record GLTextSearchOptions(boolean matchCase, boolean wholeWord,
		boolean regularExpression) {
	public static final GLTextSearchOptions DEFAULT =
			new GLTextSearchOptions(false, false, false);
}
