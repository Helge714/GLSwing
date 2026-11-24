package com.vingame.glswing.ui;

import com.vingame.glswing.rendering.GLFont;

/** Small shared text fitting helpers used by lightweight controls. */
public final class GLTextFitter {
	private static final String ELLIPSIS = "\u2026";

	private GLTextFitter() {
	}

	public static String ellipsize(GLFont font, String text, float maximumWidth) {
		if (text == null || text.isEmpty() || maximumWidth <= 0)
			return "";
		if (font.measureText(text) <= maximumWidth)
			return text;
		if (font.measureText(ELLIPSIS) > maximumWidth)
			return "";
		float suffixWidth = font.measureText(ELLIPSIS);
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
		return text.substring(0, text.offsetByCodePoints(0, low)) + ELLIPSIS;
	}

	/** Returns the original text only when fitting would replace it with an ellipsis. */
	public static String overflowToolTip(GLFont font, String text, float maximumWidth) {
		return text != null && !text.isEmpty() && font.measureText(text) > maximumWidth
				? text : null;
	}
}
