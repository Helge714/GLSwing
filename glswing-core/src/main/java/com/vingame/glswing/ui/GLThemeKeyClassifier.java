package com.vingame.glswing.ui;

import java.util.Locale;

/** Shared classification for color/font and metrics theme-fragment keys. */
final class GLThemeKeyClassifier {
	private GLThemeKeyClassifier() { }

	static boolean isColorOrFontKey(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.startsWith("palette.") || lower.startsWith("state.")
				|| lower.startsWith("font.") || lower.contains(".font.")) return true;
		String leaf = lower.substring(lower.lastIndexOf('.') + 1);
		if (leaf.matches(".*(width|height|radius|padding|gap|size|length|distance|"
				+ "tolerance|factor|scale|inset|offset|softness|thickness)$")) return false;
		// Semantic-token names describe colors without conventional suffixes such
		// as 'color' or 'text'. The remaining code-editor keys are color roles.
		if (lower.startsWith("codeeditor.")) return true;
		return leaf.matches(".*(background|foreground|text|border|focus|selection|"
				+ "caret|invalid|separator|arrow|progress|track|thumb|shadow|accent|"
				+ "color|gradient|disabled|hover|pressed|normal|surface|active|port|"
				+ "fill|tab|check|accelerator).*" );
	}
}
