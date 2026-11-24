package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Built-in editing support for declarative {@code .glschema} files. */
final class GLThemeSchemaCodeSupport {
	private static final Set<String> TYPES = Set.of(
			"TEXT", "NUMBER", "BOOLEAN", "COLOR", "INSETS", "ENUM", "PATH");
	private static final Set<String> OPTIONS = Set.of(
			"description", "values", "min", "max", "integer", "fragment");
	private static final Pattern NUMBER = Pattern.compile(
			"(?<![\\w.])[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?");
	private static final GLCodeLanguage LANGUAGE = new SchemaLanguage();

	private GLThemeSchemaCodeSupport() { }

	static GLCodeLanguage language() { return LANGUAGE; }

	private static final class SchemaLanguage implements GLCodeLanguage {
		@Override public String id() { return "glschema"; }
		@Override public String lineCommentPrefix() { return "#"; }
		@Override public boolean supportsFormatting() { return false; }

		@Override
		public List<GLCodeToken> tokenize(String source) {
			List<GLCodeToken> result = new ArrayList<>();
			forEachLine(source, (line, start, end) -> {
				int content = firstNonWhitespace(line);
				if (content >= line.length()) return;
				if (line.charAt(content) == '#' || line.charAt(content) == '!') {
					result.add(new GLCodeToken(start + content, end,
							GLCodeToken.Type.COMMENT));
					return;
				}
				int separator = line.indexOf('=', content);
				if (separator <= content) return;
				int keyEnd = trimEnd(line, content, separator);
				String key = line.substring(content, keyEnd);
				result.add(new GLCodeToken(start + content, start + keyEnd,
						key.startsWith("schema.") ? GLCodeToken.Type.KEYWORD
								: GLCodeToken.Type.TYPE));
				int valueStart = skipWhitespace(line, separator + 1);
				int typeEnd = identifierEnd(line, valueStart);
				if (!key.startsWith("schema.") && typeEnd > valueStart)
					result.add(new GLCodeToken(start + valueStart, start + typeEnd,
							GLCodeToken.Type.KEYWORD));
				boolean quoted = false;
				int quoteStart = -1;
				for (int index = valueStart; index < line.length(); index++) {
					char value = line.charAt(index);
					if (value == '"') {
						if (!quoted) quoteStart = index;
						else result.add(new GLCodeToken(start + quoteStart,
								start + index + 1, GLCodeToken.Type.STRING));
						quoted = !quoted;
					}
				}
				Matcher numbers = NUMBER.matcher(line);
				numbers.region(valueStart, line.length());
				while (numbers.find()) {
					if (!insideQuotes(line, numbers.start()))
						result.add(new GLCodeToken(start + numbers.start(),
								start + numbers.end(), GLCodeToken.Type.NUMBER));
				}
			});
			return List.copyOf(result);
		}

		@Override
		public List<GLCodeDiagnostic> diagnose(String source) {
			List<GLCodeDiagnostic> result = new ArrayList<>();
			Set<String> declarations = new HashSet<>();
			forEachLine(source, (line, start, end) -> diagnoseLine(line, start, end,
					declarations, result));
			return List.copyOf(result);
		}
	}

	private static void diagnoseLine(String line, int start, int end,
			Set<String> declarations, List<GLCodeDiagnostic> result) {
		int content = firstNonWhitespace(line);
		if (content >= line.length() || line.charAt(content) == '#'
				|| line.charAt(content) == '!') return;
		int separator = line.indexOf('=', content);
		if (separator <= content) {
			result.add(error(start + content, Math.max(start + content + 1, end),
					"Schema declarations require key=value syntax"));
			return;
		}
		int keyEnd = trimEnd(line, content, separator);
		String key = line.substring(content, keyEnd);
		if (key.isBlank()) {
			result.add(error(start + content, start + separator + 1,
					"Schema property key is missing"));
			return;
		}
		if (!declarations.add(key))
			result.add(error(start + content, start + keyEnd,
					"Duplicate schema declaration '" + key + "'"));
		String descriptor = line.substring(separator + 1).trim();
		if (key.startsWith("schema.")) {
			if (descriptor.isEmpty()) result.add(error(start + separator + 1,
					Math.max(start + separator + 2, end), "Schema metadata value is missing"));
			return;
		}
		List<String> fields;
		try { fields = fields(descriptor); }
		catch (IllegalArgumentException error) {
			result.add(error(start + separator + 1, Math.max(start + separator + 2, end),
					error.getMessage()));
			return;
		}
		if (fields.isEmpty() || fields.get(0).isBlank()) {
			result.add(error(start + separator + 1, Math.max(start + separator + 2, end),
					"Schema property type is missing"));
			return;
		}
		String type = fields.get(0).trim().toUpperCase(Locale.ROOT);
		if (!TYPES.contains(type)) {
			int valueStart = skipWhitespace(line, separator + 1);
			result.add(error(start + valueStart,
					start + Math.max(valueStart + 1, identifierEnd(line, valueStart)),
					"Unknown schema property type '" + fields.get(0).trim() + "'"));
		}
		boolean choices = false;
		for (int index = 1; index < fields.size(); index++) {
			String field = fields.get(index).trim();
			if (field.isEmpty()) continue;
			int equals = field.indexOf('=');
			if (equals < 0) continue;
			String option = field.substring(0, equals).trim().toLowerCase(Locale.ROOT);
			if (!OPTIONS.contains(option))
				result.add(error(start + separator + 1, Math.max(start + separator + 2, end),
						"Unknown schema option '" + option + "'"));
			if ("values".equals(option) && !field.substring(equals + 1).trim().isEmpty())
				choices = true;
		}
		if ("ENUM".equals(type) && !choices)
			result.add(error(start + separator + 1, Math.max(start + separator + 2, end),
					"ENUM properties require values=a|b"));
	}

	private static List<String> fields(String value) {
		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"') quoted = !quoted;
			if (character == ',' && !quoted) {
				result.add(field.toString().trim());
				field.setLength(0);
			} else field.append(character);
		}
		if (quoted) throw new IllegalArgumentException("Unclosed schema description quote");
		result.add(field.toString().trim());
		return result;
	}

	private static GLCodeDiagnostic error(int start, int end, String message) {
		return new GLCodeDiagnostic(start, end, GLCodeDiagnostic.Severity.ERROR, message);
	}

	private interface LineConsumer { void accept(String line, int start, int end); }
	private static void forEachLine(String source, LineConsumer consumer) {
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			String line = source.substring(start, end);
			if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
			consumer.accept(line, start, start + line.length());
			if (newline < 0) break;
			start = newline + 1;
		}
	}

	private static int firstNonWhitespace(String value) { return skipWhitespace(value, 0); }
	private static int skipWhitespace(String value, int start) {
		int result = start;
		while (result < value.length() && Character.isWhitespace(value.charAt(result))) result++;
		return result;
	}
	private static int trimEnd(String value, int start, int end) {
		while (end > start && Character.isWhitespace(value.charAt(end - 1))) end--;
		return end;
	}
	private static int identifierEnd(String value, int start) {
		int result = start;
		while (result < value.length() && (Character.isLetterOrDigit(value.charAt(result))
				|| value.charAt(result) == '_')) result++;
		return result;
	}
	private static boolean insideQuotes(String value, int offset) {
		boolean quoted = false;
		for (int index = 0; index < offset; index++)
			if (value.charAt(index) == '"') quoted = !quoted;
		return quoted;
	}
}
