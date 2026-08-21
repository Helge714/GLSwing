package com.vingame.glswing.ui;

import com.vingame.glswing.rendering.GLFont;

/** Small shared text fitting helpers used by lightweight controls. */
public final class GLTextFitter {
	private static final String ELLIPSIS = "...";

	private GLTextFitter() {
	}

	public static String ellipsize(GLFont font, String text, float maximumWidth) {
		if (text == null || text.isEmpty() || maximumWidth <= 0)
			return "";
		if (font.measureText(text) <= maximumWidth)
			return text;
		String suffix = fittingDots(font, maximumWidth);
		if (suffix.isEmpty())
			return "";
		float suffixWidth = font.measureText(suffix);
		int codePoints = text.codePointCount(0, text.length());
		int low = 0;
		int high = codePoints;
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			int end = text.offsetByCodePoints(0, middle);
			if (font.measureText(text.substring(0, end)) + suffixWidth
					<= maximumWidth)
				low = middle;
			else
				high = middle - 1;
		}
		return text.substring(0, text.offsetByCodePoints(0, low)) + suffix;
	}

	private static String fittingDots(GLFont font, float maximumWidth) {
		for (int count = ELLIPSIS.length(); count > 0; count--) {
			String dots = ELLIPSIS.substring(0, count);
			if (font.measureText(dots) <= maximumWidth)
				return dots;
		}
		return "";
	}
}
