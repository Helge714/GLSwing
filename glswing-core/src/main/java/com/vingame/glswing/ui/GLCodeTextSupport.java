package com.vingame.glswing.ui;
import java.util.*;
import java.net.URI;
/** Stateless source-text operations shared by code-editor features. */
final class GLCodeTextSupport {
 private GLCodeTextSupport(){}
	static List<Integer> identifierOccurrences(String text, String identifier) {
		if (identifier.isEmpty()) return List.of();
		ArrayList<Integer> result = new ArrayList<>();
		for (int from = 0; from <= text.length() - identifier.length();) {
			int index = text.indexOf(identifier, from);
			if (index < 0) break;
			int end = index + identifier.length();
			boolean leftBoundary = index == 0
					|| !Character.isJavaIdentifierPart(text.charAt(index - 1));
			boolean rightBoundary = end == text.length()
					|| !Character.isJavaIdentifierPart(text.charAt(end));
			if (leftBoundary && rightBoundary) result.add(index);
			from = Math.max(index + 1, end);
		}
		return List.copyOf(result);
	}

	static boolean sameDocumentUri(URI first, URI second) {
		if (first.equals(second)) return true;
		if (!"file".equalsIgnoreCase(first.getScheme())
				|| !"file".equalsIgnoreCase(second.getScheme())) return false;
		try {
			return java.nio.file.Path.of(first).toAbsolutePath().normalize().equals(
					java.nio.file.Path.of(second).toAbsolutePath().normalize());
		} catch (RuntimeException ignored) { return false; }
	}
	static void trimTrailingSpace(StringBuilder value) {
		while (!value.isEmpty() && value.charAt(value.length() - 1) == ' ')
			value.setLength(value.length() - 1);
	}

	static int lineCommentStart(String line) {
		boolean quoted = false; char quote = 0;
		for (int index = 0; index + 1 < line.length(); index++) {
			char value = line.charAt(index);
			if (quoted) {
				if (value == '\\') index++;
				else if (value == quote) quoted = false;
			} else if (value == '"' || value == '\'') { quoted = true; quote = value; }
			else if (value == '/' && line.charAt(index + 1) == '/') return index;
		}
		return -1;
	}
	static String javaParameterName(String label) {
		if (label == null || label.isBlank()) return null;
		java.util.regex.Matcher identifiers = java.util.regex.Pattern.compile(
				"[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*").matcher(label);
		String last = null;
		int count = 0;
		while (identifiers.find()) { last = identifiers.group(); count++; }
		if (last == null || (count == 1 && Character.isUpperCase(last.codePointAt(0))))
			return null;
		return switch (last) {
		case "byte", "short", "int", "long", "float", "double", "boolean", "char",
				"void", "this", "super" -> null;
		default -> last;
		};
	}
	static boolean validSignatureHintAnchor(String source, int offset) {
		if (offset < 0 || offset > source.length()) return false;
		int previous = offset - 1;
		while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
		return previous >= 0 && (source.charAt(previous) == '(' || source.charAt(previous) == ',');
	}
}
