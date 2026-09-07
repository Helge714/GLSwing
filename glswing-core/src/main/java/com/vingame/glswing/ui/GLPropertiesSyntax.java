package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared lexical rules for GLSwing's properties-based configuration files. */
final class GLPropertiesSyntax {
	private GLPropertiesSyntax() { }

	record TypeAnnotation(GLCodePropertySchema.ValueType type, List<String> choices,
			int start, int end) {
		TypeAnnotation {
			choices = List.copyOf(choices);
		}
	}

	/** Reads an optional {@code @TYPE } prefix and returns the following key start. */
	static TypeAnnotation typeAnnotation(CharSequence source, int start, int end) {
		if (start >= end || source.charAt(start) != '@') return null;
		int nameEnd = start + 1;
		while (nameEnd < end && (Character.isLetterOrDigit(source.charAt(nameEnd))
				|| source.charAt(nameEnd) == '_')) nameEnd++;
		if (nameEnd == start + 1)
			throw new IllegalArgumentException("Theme token annotation requires a type");
		GLCodePropertySchema.ValueType type;
		try {
			type = GLCodePropertySchema.ValueType.valueOf(
					source.subSequence(start + 1, nameEnd).toString()
							.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("Unknown theme token type "
					+ source.subSequence(start, nameEnd), error);
		}
		List<String> choices = List.of();
		int annotationEnd = nameEnd;
		if (nameEnd < end && source.charAt(nameEnd) == '<') {
			if (type != GLCodePropertySchema.ValueType.ENUM)
				throw new IllegalArgumentException("Only @ENUM accepts a value list");
			int close = -1;
			for (int index = nameEnd + 1; index < end; index++)
				if (source.charAt(index) == '>') { close = index; break; }
			if (close < 0)
				throw new IllegalArgumentException("@ENUM value list requires '>'");
			choices = enumChoices(source.subSequence(nameEnd + 1, close).toString());
			annotationEnd = close + 1;
		} else if (type == GLCodePropertySchema.ValueType.ENUM)
			throw new IllegalArgumentException(
					"@ENUM requires values, for example @ENUM<compact, comfortable>");
		if (annotationEnd >= end
				|| !Character.isWhitespace(source.charAt(annotationEnd)))
			throw new IllegalArgumentException("Theme token annotation must be followed by a property");
		int keyStart = annotationEnd;
		while (keyStart < end && Character.isWhitespace(source.charAt(keyStart))) keyStart++;
		if (keyStart >= end)
			throw new IllegalArgumentException("Theme token annotation must be followed by a property");
		return new TypeAnnotation(type, choices, start, keyStart);
	}

	private static List<String> enumChoices(String source) {
		ArrayList<String> result = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		for (String part : source.split(",", -1)) {
			String choice = part.trim();
			if (choice.isEmpty())
				throw new IllegalArgumentException("@ENUM values must not be empty");
			if (!unique.add(choice.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Duplicate @ENUM value '" + choice + "'");
			result.add(choice);
		}
		return List.copyOf(result);
	}

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

	/** Removes inline token annotations while preserving line and character offsets. */
	static String removeTypeAnnotations(String source) {
		StringBuilder result = new StringBuilder(source);
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			int physicalEnd = end > start && source.charAt(end - 1) == '\r' ? end - 1 : end;
			int contentStart = start;
			while (contentStart < physicalEnd
					&& Character.isWhitespace(source.charAt(contentStart))) contentStart++;
			TypeAnnotation annotation = typeAnnotation(source, contentStart, physicalEnd);
			if (annotation != null)
				for (int index = annotation.start(); index < annotation.end(); index++)
					result.setCharAt(index, ' ');
			if (newline < 0) break;
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
