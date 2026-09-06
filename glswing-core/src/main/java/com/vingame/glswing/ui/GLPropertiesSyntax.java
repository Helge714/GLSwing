package com.vingame.glswing.ui;

/** Shared lexical rules for GLSwing's properties-based configuration files. */
final class GLPropertiesSyntax {
	private GLPropertiesSyntax() { }

	/**
	 * Finds a whitespace-delimited inline comment after a non-empty value.
	 * A leading hash therefore remains available for hexadecimal colors.
	 */
	static int inlineCommentStart(CharSequence source, int valueStart, int end) {
		boolean escaped = false;
		boolean hasValue = false;
		for (int index = valueStart; index < end; index++) {
			char value = source.charAt(index);
			if (!escaped && hasValue && (value == '#' || value == '!')
					&& index > valueStart
					&& Character.isWhitespace(source.charAt(index - 1))) return index;
			if (!Character.isWhitespace(value)) hasValue = true;
			if (escaped) escaped = false;
			else if (value == '\\') escaped = true;
		}
		return -1;
	}

	static String removeInlineComments(String source) {
		StringBuilder result = new StringBuilder(source.length());
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			int physicalEnd = end > start && source.charAt(end - 1) == '\r'
					? end - 1 : end;
			int contentStart = start;
			while (contentStart < physicalEnd
					&& Character.isWhitespace(source.charAt(contentStart))) contentStart++;
			int separator = propertySeparator(source, contentStart, physicalEnd);
			int comment = separator < 0 ? -1
					: inlineCommentStart(source, separator + 1, physicalEnd);
			result.append(source, start, comment < 0 ? end : comment);
			if (newline < 0) break;
			result.append('\n');
			start = newline + 1;
		}
		return result.toString();
	}

	private static int propertySeparator(CharSequence source, int start, int end) {
		boolean escaped = false;
		for (int index = start; index < end; index++) {
			char value = source.charAt(index);
			if (!escaped && (value == '=' || value == ':')) return index;
			if (escaped) escaped = false;
			else if (value == '\\') escaped = true;
		}
		return -1;
	}
}
