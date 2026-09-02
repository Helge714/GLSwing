package com.vingame.glswing.ui;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;

/**
 * Built-in editing support for UTF-8 Java localization bundles. Locale files
 * are compared with a sibling root bundle when one is available.
 */
public final class GLLocalizationCodeSupport {
	private static final String BUILT_IN_BUNDLE =
			"/com/vingame/glswing/languages/Messages.properties";
	private static final GLCodePropertySchema SCHEMA = createSchema();
	private static final GLPropertiesCodeLanguage DELEGATE =
			new GLPropertiesCodeLanguage(SCHEMA);
	private static final GLCodeLanguage LANGUAGE = new LocalizationLanguage();

	private GLLocalizationCodeSupport() { }

	/** Returns the schema containing GLSwing's stable localization keys. */
	public static GLCodePropertySchema schema() { return SCHEMA; }
	/** Returns the reusable language for localization {@code .properties} files. */
	public static GLCodeLanguage language() { return LANGUAGE; }

	private static GLCodePropertySchema createSchema() {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("properties")
				.allowUnknownKeys(true);
		Properties builtIn = new Properties();
		try (var stream = GLLocalizationCodeSupport.class
				.getResourceAsStream(BUILT_IN_BUNDLE)) {
			if (stream != null)
				builtIn.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		} catch (IOException failure) {
			throw new ExceptionInInitializerError(failure);
		}
		builtIn.stringPropertyNames().stream().sorted().forEach(key ->
				builder.property(key, ValueType.TEXT,
						"Localized text for GLSwing UI key '" + key + "'.",
						builtIn.getProperty(key)));
		return builder.build();
	}

	private static final class LocalizationLanguage implements GLCodeLanguage {
		private final GLCodeDiagnosticProvider provider = new LocalizationProvider();
		@Override public String id() { return "properties"; }
		@Override public List<GLCodeToken> tokenize(String source) {
			return DELEGATE.tokenize(source);
		}
		@Override public List<GLCodeDiagnostic> diagnose(String source) {
			List<GLCodeDiagnostic> result = new ArrayList<>(DELEGATE.diagnose(source));
			result.addAll(messageDiagnostics(source));
			result.sort(Comparator.comparingInt(GLCodeDiagnostic::start));
			return List.copyOf(result);
		}
		@Override public String lineCommentPrefix() { return "#"; }
		@Override public GLCodeDiagnosticProvider codeIntelligenceProvider() {
			return provider;
		}
	}

	private static final class LocalizationProvider implements GLCodeDiagnosticProvider {
		@Override public Session open(GLCodeDocument document, Consumer<Result> listener) {
			if (document == null || listener == null)
				throw new IllegalArgumentException("Document and listener are required");
			return new LocalizationSession(document, listener);
		}
	}

	private static final class LocalizationSession implements GLCodeDiagnosticProvider.Session {
		private GLCodeDocument current;
		private final Consumer<GLCodeDiagnosticProvider.Result> listener;
		private final GLCodeDiagnosticProvider.Session delegate;
		private boolean closed;

		LocalizationSession(GLCodeDocument document,
				Consumer<GLCodeDiagnosticProvider.Result> listener) {
			current = document;
			this.listener = listener;
			delegate = DELEGATE.codeIntelligenceProvider().open(document, ignored -> publish());
		}

		@Override public void update(GLCodeDocument document) {
			if (closed || document == null || !current.uri().equals(document.uri())) return;
			current = document;
			delegate.update(document);
		}

		private void publish() {
			if (closed) return;
			listener.accept(new GLCodeDiagnosticProvider.Result(current.version(),
					bundleDiagnostics(current)));
		}

		@Override public CompletionStage<List<GLCodeCompletion>> complete(
				GLCodeDocument document, int offset, Character trigger) {
			return delegate.complete(document, offset, trigger).thenApply(existing ->
					mergeCompletions(existing, rootCompletions(document, offset)));
		}

		@Override public CompletionStage<Optional<GLCodeHover>> hover(
				GLCodeDocument document, int offset) {
			Optional<GLCodeHover> root = rootHover(document, offset);
			return root.isPresent() ? completed(root) : delegate.hover(document, offset);
		}

		@Override public CompletionStage<List<GLCodeLocation>> definitions(
				GLCodeDocument document, int offset) {
			List<GLCodeLocation> root = rootDefinition(document, offset);
			return root.isEmpty() ? delegate.definitions(document, offset) : completed(root);
		}

		@Override public CompletionStage<List<GLCodeSemanticToken>> semanticTokens(
				GLCodeDocument document) {
			return delegate.semanticTokens(document);
		}

		@Override public void close() {
			closed = true;
			delegate.close();
		}
	}

	private static List<GLCodeDiagnostic> messageDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		for (Entry entry : entries(source).values()) {
			try {
				new MessageFormat(decoded(entry.value()));
			} catch (IllegalArgumentException failure) {
				result.add(diagnostic(entry.valueStart(), entry.valueEnd(),
						GLCodeDiagnostic.Severity.ERROR,
						"Invalid MessageFormat pattern: " + failure.getMessage()));
			}
		}
		return result;
	}

	private static List<GLCodeDiagnostic> bundleDiagnostics(GLCodeDocument document) {
		Path root = rootBundle(document.uri());
		if (root == null) return List.of();
		Properties base = load(root);
		Properties translated = load(document.text());
		if (base == null || translated == null) return List.of();
		Map<String, Entry> ranges = entries(document.text());
		List<GLCodeDiagnostic> result = new ArrayList<>();
		Set<String> missing = new LinkedHashSet<>(base.stringPropertyNames());
		missing.removeAll(translated.stringPropertyNames());
		if (!missing.isEmpty())
			result.add(diagnostic(0, Math.min(1, Math.max(1, document.text().length())),
					GLCodeDiagnostic.Severity.WARNING, missingMessage(missing, root)));
		for (String key : translated.stringPropertyNames()) {
			Entry entry = ranges.get(key);
			if (entry == null) continue;
			if (!base.containsKey(key)) {
				result.add(diagnostic(entry.keyStart(), entry.keyEnd(),
						GLCodeDiagnostic.Severity.WARNING,
						"Localization key is not declared in " + root.getFileName()));
				continue;
			}
			Set<Integer> expected = placeholders(base.getProperty(key));
			Set<Integer> actual = placeholders(translated.getProperty(key));
			if (!expected.equals(actual))
				result.add(diagnostic(entry.valueStart(), entry.valueEnd(),
						GLCodeDiagnostic.Severity.ERROR,
						"MessageFormat arguments differ from " + root.getFileName()
								+ ": expected " + expected + ", found " + actual));
		}
		result.sort(Comparator.comparingInt(GLCodeDiagnostic::start));
		return List.copyOf(result);
	}

	private static String missingMessage(Set<String> missing, Path root) {
		String names = missing.stream().limit(4).reduce((left, right) -> left + ", " + right)
				.orElse("");
		if (missing.size() > 4) names += ", …";
		return "Missing " + missing.size() + " translation"
				+ (missing.size() == 1 ? "" : "s") + " from " + root.getFileName()
				+ ": " + names;
	}

	private static List<GLCodeCompletion> rootCompletions(GLCodeDocument document,
			int offset) {
		Path root = rootBundle(document.uri());
		Properties base = root == null ? null : load(root);
		if (base == null || offset < 0 || offset > document.text().length()) return List.of();
		LineFragment line = lineAt(document.text(), offset);
		if (line == null || line.comment() || line.separator() >= 0
				&& offset > line.separator()) return List.of();
		String prefix = document.text().substring(line.contentStart(), offset).trim();
		int start = offset - prefix.length();
		boolean appendSeparator = line.separator() < 0;
		Set<String> declared = entries(document.text()).keySet();
		return base.stringPropertyNames().stream().filter(key -> !declared.contains(key))
				.filter(key -> key.regionMatches(true, 0, prefix, 0, prefix.length()))
				.sorted().map(key -> new GLCodeCompletion(key,
						"localization key · Root: " + singleLine(base.getProperty(key)),
						key + (appendSeparator ? "=" : ""), start,
						line.separator() >= 0 ? line.separator() : offset)).toList();
	}

	private static List<GLCodeCompletion> mergeCompletions(
			List<GLCodeCompletion> first, List<GLCodeCompletion> second) {
		Map<String, GLCodeCompletion> result = new LinkedHashMap<>();
		first.forEach(item -> result.put(item.label(), item));
		second.forEach(item -> result.putIfAbsent(item.label(), item));
		return List.copyOf(result.values());
	}

	private static Optional<GLCodeHover> rootHover(GLCodeDocument document, int offset) {
		Path root = rootBundle(document.uri());
		Properties base = root == null ? null : load(root);
		if (base == null) return Optional.empty();
		Entry entry = entryAt(document.text(), offset);
		if (entry == null || offset < entry.keyStart() || offset >= entry.keyEnd()
				|| !base.containsKey(entry.key())) return Optional.empty();
		return Optional.of(new GLCodeHover(entry.keyStart(), entry.keyEnd(),
				"`" + entry.key() + "`\nRoot message: " + base.getProperty(entry.key())
						+ "\nSource: `" + root.getFileName() + "`"));
	}

	private static List<GLCodeLocation> rootDefinition(GLCodeDocument document,
			int offset) {
		Path root = rootBundle(document.uri());
		if (root == null) return List.of();
		Entry current = entryAt(document.text(), offset);
		if (current == null || offset < current.keyStart() || offset >= current.keyEnd())
			return List.of();
		try {
			String source = Files.readString(root, StandardCharsets.UTF_8);
			Entry target = entries(source).get(current.key());
			if (target == null) return List.of();
			int line = 0, character = 0;
			for (int index = 0; index < target.keyStart(); index++) {
				if (source.charAt(index) == '\n') { line++; character = 0; }
				else character++;
			}
			return List.of(new GLCodeLocation(root.toUri(), line, character));
		} catch (IOException failure) {
			return List.of();
		}
	}

	private static Path rootBundle(URI uri) {
		Path file = filePath(uri);
		if (file == null || file.getParent() == null) return null;
		String name = file.getFileName().toString();
		if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".properties")) return null;
		String stem = name.substring(0, name.length() - ".properties".length());
		for (int separator = stem.lastIndexOf('_'); separator > 0;
				separator = stem.lastIndexOf('_', separator - 1)) {
			String suffix = stem.substring(separator + 1);
			if (!suffix.matches("[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})*")) continue;
			Path candidate = file.resolveSibling(stem.substring(0, separator) + ".properties")
					.toAbsolutePath().normalize();
			if (Files.isRegularFile(candidate)) return candidate;
		}
		return null;
	}

	private static Path filePath(URI uri) {
		try {
			return uri != null && "file".equalsIgnoreCase(uri.getScheme())
					? Path.of(uri).toAbsolutePath().normalize() : null;
		} catch (RuntimeException failure) {
			return null;
		}
	}

	private static Properties load(Path path) {
		try {
			return load(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException | IllegalArgumentException failure) {
			return null;
		}
	}

	private static Properties load(String source) {
		try {
			Properties result = new Properties();
			result.load(new StringReader(source));
			return result;
		} catch (IOException | IllegalArgumentException failure) {
			return null;
		}
	}

	private static String decoded(String value) {
		Properties result = load("value=" + value);
		return result == null ? value : result.getProperty("value", value);
	}

	private static Set<Integer> placeholders(String pattern) {
		Set<Integer> result = new LinkedHashSet<>();
		boolean quoted = false;
		for (int index = 0; index < pattern.length(); index++) {
			char value = pattern.charAt(index);
			if (value == '\'') {
				if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') index++;
				else quoted = !quoted;
				continue;
			}
			if (quoted || value != '{') continue;
			int cursor = index + 1;
			while (cursor < pattern.length() && Character.isWhitespace(pattern.charAt(cursor)))
				cursor++;
			int start = cursor;
			while (cursor < pattern.length() && Character.isDigit(pattern.charAt(cursor))) cursor++;
			if (cursor > start) result.add(Integer.parseInt(pattern.substring(start, cursor)));
		}
		return Set.copyOf(result);
	}

	private static Map<String, Entry> entries(String source) {
		Map<String, Entry> result = new LinkedHashMap<>();
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			if (end > start && source.charAt(end - 1) == '\r') end--;
			LineFragment line = line(source, start, end);
			if (line != null && !line.comment() && line.separator() >= 0) {
				int keyEnd = line.separator();
				while (keyEnd > line.contentStart()
						&& Character.isWhitespace(source.charAt(keyEnd - 1))) keyEnd--;
				int valueStart = line.separator() + 1;
				while (valueStart < end && Character.isWhitespace(source.charAt(valueStart)))
					valueStart++;
				int valueEnd = end;
				while (valueEnd > valueStart && Character.isWhitespace(source.charAt(valueEnd - 1)))
					valueEnd--;
				String key = source.substring(line.contentStart(), keyEnd);
				if (!key.isBlank()) result.putIfAbsent(key, new Entry(key,
						line.contentStart(), keyEnd, valueStart, Math.max(valueStart + 1, valueEnd),
						source.substring(valueStart, valueEnd)));
			}
			if (newline < 0) break;
			start = newline + 1;
		}
		return result;
	}

	private static Entry entryAt(String source, int offset) {
		for (Entry entry : entries(source).values())
			if (offset >= entry.keyStart() && offset <= entry.valueEnd()) return entry;
		return null;
	}

	private static LineFragment lineAt(String source, int offset) {
		int start = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
		int end = source.indexOf('\n', offset);
		if (end < 0) end = source.length();
		return line(source, start, end);
	}

	private static LineFragment line(String source, int start, int end) {
		int content = start;
		while (content < end && Character.isWhitespace(source.charAt(content))) content++;
		boolean comment = content < end
				&& (source.charAt(content) == '#' || source.charAt(content) == '!');
		int separator = comment ? -1 : separator(source, content, end);
		return new LineFragment(start, end, content, separator, comment);
	}

	private static int separator(String source, int start, int end) {
		boolean escaped = false;
		for (int index = start; index < end; index++) {
			char value = source.charAt(index);
			if (!escaped && (value == '=' || value == ':')) return index;
			escaped = !escaped && value == '\\';
			if (value != '\\') escaped = false;
		}
		return -1;
	}

	private static String singleLine(String text) {
		String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
		return value.length() <= 80 ? value : value.substring(0, 77) + "…";
	}

	private static GLCodeDiagnostic diagnostic(int start, int end,
			GLCodeDiagnostic.Severity severity, String message) {
		return new GLCodeDiagnostic(Math.max(0, start), Math.max(start + 1, end),
				severity, message);
	}

	private static <T> CompletionStage<T> completed(T value) {
		return CompletableFuture.completedFuture(value);
	}

	private record Entry(String key, int keyStart, int keyEnd, int valueStart,
			int valueEnd, String value) { }
	private record LineFragment(int start, int end, int contentStart,
			int separator, boolean comment) { }
}
