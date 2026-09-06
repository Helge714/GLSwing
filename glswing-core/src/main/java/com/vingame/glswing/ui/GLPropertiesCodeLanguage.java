package com.vingame.glswing.ui;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vingame.glswing.ui.GLCodePropertySchema.Property;
import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;

/**
 * Schema-backed language and in-process code-intelligence provider for
 * {@code key=value} configuration files.
 */
public final class GLPropertiesCodeLanguage implements GLCodeLanguage {
	private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
	private static final Pattern NUMBER = Pattern.compile(
			"(?<![\\w.])[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?(?:%|f|deg)?");
	private static final Pattern FUNCTION = Pattern.compile(
			"\\b(?:rgb|rgba|hsv|hsl|oklab|oklch|derive)\\b(?=\\s*\\()",
			Pattern.CASE_INSENSITIVE);
	private static final Map<String, List<String>> FUNCTION_INLAY_LABELS = Map.of(
			"rgb", List.of("Red", "Green", "Blue", "Alpha"),
			"rgba", List.of("Red", "Green", "Blue", "Alpha"),
			"hsv", List.of("Hue", "Saturation", "Value", "Alpha"),
			"hsl", List.of("Hue", "Saturation", "Lightness", "Alpha"),
			"oklab", List.of("Lightness", "A", "B", "Alpha"),
			"oklch", List.of("Lightness", "Chroma", "Hue", "Alpha"),
			"derive", List.of("Base", "Adjustment"));
	private static final Set<String> BUILT_IN_REFERENCES = Set.of(
			"default", "dark", "light");

	private final GLCodePropertySchema schema;
	private final GLCodeDiagnosticProvider provider = new PropertyProvider();

	/** Creates a configuration language backed by the supplied immutable schema. */
	public GLPropertiesCodeLanguage(GLCodePropertySchema schema) {
		if (schema == null) throw new IllegalArgumentException("Property schema is required");
		this.schema = schema;
	}

	/** Returns the property schema exposed by this language. */
	public GLCodePropertySchema schema() { return schema; }
	@Override public String id() { return schema.id(); }
	@Override public String lineCommentPrefix() { return "#"; }
	@Override public boolean supportsDefinitionNavigation() { return true; }
	@Override public boolean supportsInlayHints() { return true; }
	@Override public boolean supportsColorFormatting() {
		return "gltheme".equals(schema.id());
	}
	@Override public GLCodeDiagnosticProvider codeIntelligenceProvider() { return provider; }

	String formatColorValues(String source, GLColorModel preferredModel,
			GLColorExpressionFormat format) {
		if (source == null || format == null)
			throw new IllegalArgumentException("Source and color format are required");
		Document document = parse(source);
		StringBuilder result = new StringBuilder(source);
		List<Line> lines = document.lines();
		for (int index = lines.size() - 1; index >= 0; index--) {
			Line line = lines.get(index);
			if (!line.hasProperty() || line.value().isBlank()) continue;
			GLColorModel sourceModel = functionalColorModel(line.value());
			if (sourceModel == null) continue;
			try {
				java.awt.Color color = GLThemeValues.parseColor(line.value());
				GLColorModel output = preferredModel == null ? sourceModel : preferredModel;
				String replacement = output.format(output.channels(color),
						color.getAlpha() / 255d, format);
				result.replace(line.valueStart(), line.valueEnd(), replacement);
			} catch (RuntimeException ignored) { }
		}
		return result.toString();
	}

	private static GLColorModel functionalColorModel(String value) {
		String lower = value.stripLeading().toLowerCase(java.util.Locale.ROOT);
		for (GLColorModel model : GLColorModel.values())
			if (lower.startsWith(model.name().toLowerCase(java.util.Locale.ROOT) + "("))
				return model;
		return null;
	}

	@Override
	public List<GLCodeToken> tokenize(String source) {
		Document document = parse(source);
		List<GLCodeToken> tokens = new ArrayList<>();
		for (Line line : document.lines()) {
			if (line.comment()) {
				tokens.add(new GLCodeToken(line.contentStart(), line.end(),
						GLCodeToken.Type.COMMENT));
				continue;
			}
			if (!line.hasProperty()) continue;
			tokens.add(new GLCodeToken(line.keyStart(), line.keyEnd(),
					isDirective(line.key()) ? GLCodeToken.Type.KEYWORD : GLCodeToken.Type.TYPE));
			addLexicalValueTokens(source, line, tokens);
			if (line.inlineCommentStart() >= 0)
				tokens.add(new GLCodeToken(line.inlineCommentStart(), line.end(),
						GLCodeToken.Type.COMMENT));
		}
		return List.copyOf(tokens);
	}

	@Override
	public List<GLCodeDiagnostic> diagnose(String source) {
		Document document = parse(source);
		List<GLCodeDiagnostic> result = new ArrayList<>(document.syntaxDiagnostics());
		Map<String, Line> declarations = new LinkedHashMap<>();
		for (Line line : document.lines()) {
			if (!line.hasProperty()) continue;
			Line previous = declarations.putIfAbsent(line.key(), line);
			if (previous != null)
				result.add(diagnostic(line.keyStart(), line.keyEnd(),
						GLCodeDiagnostic.Severity.WARNING,
						"Duplicate property '" + line.key() + "'"));
			validateReferenceSyntax(source, line, result);
			Property property = schema.property(line.key());
			if (property == null) {
				if (!schema.unknownKeysAllowed())
					result.add(diagnostic(line.keyStart(), line.keyEnd(),
							GLCodeDiagnostic.Severity.WARNING,
							"Unknown property '" + line.key() + "'"));
				continue;
			}
			validateValue(line, property, result);
		}
		validateReferenceTypes(declarations, result);
		addReferenceCycles(declarations, result);
		Map<String, String> values = new LinkedHashMap<>();
		declarations.forEach((key, line) -> values.put(key, line.value()));
		for (GLCodePropertySchema.Validator validator : schema.validators())
			for (GLCodePropertySchema.ValidationIssue issue : validator.validate(Map.copyOf(values))) {
				Line line = declarations.get(issue.key());
				if (line != null) result.add(diagnostic(line.keyStart(), line.keyEnd(),
						issue.severity(), issue.message()));
			}
		result.sort(Comparator.comparingInt(GLCodeDiagnostic::start));
		return List.copyOf(result);
	}

	private void validateReferenceTypes(Map<String, Line> declarations,
			List<GLCodeDiagnostic> result) {
		for (Line line : declarations.values()) {
			Property owner = schema.property(line.key());
			if (owner == null) continue;
			List<Reference> found = references(line.value(), line.valueStart());
			for (Reference reference : found) {
				Property target = schema.property(reference.key());
				if (target == null) continue;
				boolean exact = line.value().trim().equals("${" + reference.key() + "}");
				boolean compatible = owner.type() == ValueType.COLOR
						? target.type() == ValueType.COLOR
						: !exact || owner.type() == target.type();
				if (!compatible)
					result.add(diagnostic(reference.start(), reference.end(),
							GLCodeDiagnostic.Severity.ERROR,
							"Property '" + line.key() + "' expects "
									+ owner.type().name().toLowerCase(java.util.Locale.ROOT)
									+ " but '" + reference.key() + "' is "
									+ target.type().name().toLowerCase(java.util.Locale.ROOT)));
			}
		}
	}

	private static void addLexicalValueTokens(String source, Line line,
			List<GLCodeToken> tokens) {
		String value = source.substring(line.valueStart(), line.valueEnd());
		int initialSize = tokens.size();
		Matcher references = REFERENCE.matcher(value);
		while (references.find())
			tokens.add(new GLCodeToken(line.valueStart() + references.start(),
					line.valueStart() + references.end(), GLCodeToken.Type.TYPE));
		Matcher numbers = NUMBER.matcher(value);
		while (numbers.find())
			if (!insideReference(references(value), numbers.start()))
				tokens.add(new GLCodeToken(line.valueStart() + numbers.start(),
						line.valueStart() + numbers.end(), GLCodeToken.Type.NUMBER));
		if ("true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim()))
			tokens.add(new GLCodeToken(line.valueStart(), line.valueEnd(),
					GLCodeToken.Type.KEYWORD));
		else if (tokens.size() == initialSize && line.valueEnd() > line.valueStart())
			tokens.add(new GLCodeToken(line.valueStart(), line.valueEnd(),
					GLCodeToken.Type.STRING));
	}

	private static void validateValue(Line line, Property property,
			List<GLCodeDiagnostic> result) {
		String value = line.value();
		if (value.isBlank()) {
			result.add(diagnostic(line.separator(), line.separator() + 1,
					GLCodeDiagnostic.Severity.ERROR,
					"Property '" + line.key() + "' requires a value"));
			return;
		}
		if (value.contains("${")) return;
		try {
			switch (property.type()) {
			case NUMBER -> Double.parseDouble(value);
			case BOOLEAN -> {
				if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
					throw new IllegalArgumentException("Expected true or false");
			}
			case COLOR -> GLThemeValues.parseColor(value);
			case INSETS -> parseInsets(value);
			case ENUM -> {
				if (property.choices().stream().noneMatch(value::equalsIgnoreCase))
					throw new IllegalArgumentException("Expected one of "
							+ String.join(", ", property.choices()));
			}
			case TEXT, PATH -> { }
			}
		} catch (RuntimeException failure) {
			result.add(diagnostic(line.valueStart(), line.valueEnd(),
					GLCodeDiagnostic.Severity.ERROR, concise(failure.getMessage())));
		}
	}

	private static void parseInsets(String value) {
		String[] parts = value.split(",", -1);
		if (parts.length != 4)
			throw new IllegalArgumentException("Expected top, left, bottom, right");
		for (String part : parts) Double.parseDouble(part.trim());
	}

	private static void validateReferenceSyntax(String source, Line line,
			List<GLCodeDiagnostic> result) {
		int cursor = line.valueStart();
		while (cursor < line.valueEnd()) {
			int start = source.indexOf("${", cursor);
			if (start < 0 || start >= line.valueEnd()) return;
			int end = source.indexOf('}', start + 2);
			if (end < 0 || end >= line.valueEnd()) {
				result.add(diagnostic(start, line.valueEnd(),
						GLCodeDiagnostic.Severity.ERROR, "Unclosed property reference"));
				return;
			}
			if (source.substring(start + 2, end).isBlank())
				result.add(diagnostic(start, end + 1,
						GLCodeDiagnostic.Severity.ERROR, "Property reference must not be empty"));
			cursor = end + 1;
		}
	}

	private static void addReferenceCycles(Map<String, Line> declarations,
			List<GLCodeDiagnostic> result) {
		Map<String, Set<String>> graph = new HashMap<>();
		for (Line line : declarations.values()) {
			Set<String> targets = new LinkedHashSet<>();
			for (Reference reference : references(line.value(), line.valueStart()))
				if (declarations.containsKey(reference.key())) targets.add(reference.key());
			graph.put(line.key(), targets);
		}
		Set<String> visited = new HashSet<>(), active = new HashSet<>(), reported = new HashSet<>();
		for (String key : graph.keySet())
			findCycles(key, graph, declarations, visited, active, reported, result);
	}

	private static void findCycles(String key, Map<String, Set<String>> graph,
			Map<String, Line> declarations, Set<String> visited, Set<String> active,
			Set<String> reported, List<GLCodeDiagnostic> result) {
		if (active.contains(key)) {
			if (reported.add(key)) {
				Line line = declarations.get(key);
				result.add(diagnostic(line.keyStart(), line.keyEnd(),
						GLCodeDiagnostic.Severity.ERROR,
						"Cyclic property reference involving '" + key + "'"));
			}
			return;
		}
		if (!visited.add(key)) return;
		active.add(key);
		for (String target : graph.getOrDefault(key, Set.of()))
			findCycles(target, graph, declarations, visited, active, reported, result);
		active.remove(key);
	}

	private final class PropertyProvider implements GLCodeDiagnosticProvider {
		@Override
		public Session open(GLCodeDocument document, Consumer<Result> listener) {
			if (document == null || listener == null)
				throw new IllegalArgumentException("Document and listener are required");
			return new PropertySession(document, listener);
		}
	}

	private final class PropertySession implements GLCodeDiagnosticProvider.Session {
		private GLCodeDocument current;
		private final Consumer<GLCodeDiagnosticProvider.Result> listener;
		private boolean closed;

		PropertySession(GLCodeDocument document,
				Consumer<GLCodeDiagnosticProvider.Result> listener) {
			this.current = document;
			this.listener = listener;
			publish();
		}

		@Override public void update(GLCodeDocument document) {
			if (closed || document == null || !current.uri().equals(document.uri())) return;
			current = document;
			publish();
		}

		private void publish() {
			listener.accept(new GLCodeDiagnosticProvider.Result(
					current.version(), externalDiagnostics(current)));
		}

		@Override public CompletionStage<List<GLCodeCompletion>> complete(
				GLCodeDocument document, int offset, Character trigger) {
			return completed(completions(document, offset));
		}

		@Override public CompletionStage<Optional<GLCodeHover>> hover(
				GLCodeDocument document, int offset) {
			return completed(hoverAt(document, offset));
		}

		@Override public CompletionStage<List<GLCodeLocation>> definitions(
				GLCodeDocument document, int offset) {
			return completed(definitionsAt(document, offset));
		}

		@Override public CompletionStage<List<GLCodeSemanticToken>> semanticTokens(
				GLCodeDocument document) {
			return completed(semanticTokensFor(document.text()));
		}

		@Override public CompletionStage<List<GLCodeColor>> documentColors(
				GLCodeDocument document) {
			return completed(documentColorsFor(document));
		}

		@Override public CompletionStage<List<GLCodeInlayHint>> inlayHints(
				GLCodeDocument document) {
			return completed(functionInlayHints(document.text()));
		}

		@Override public void close() { closed = true; }
	}

	private static List<GLCodeInlayHint> functionInlayHints(String source) {
		ArrayList<GLCodeInlayHint> result = new ArrayList<>();
		Matcher functions = FUNCTION.matcher(source);
		while (functions.find()) {
			String name = functions.group().toLowerCase(java.util.Locale.ROOT);
			List<Integer> arguments = argumentStarts(source, functions.end());
			List<String> names = FUNCTION_INLAY_LABELS.get(name);
			for (int index = 0; index < arguments.size(); index++) {
				String label = index < names.size() ? names.get(index)
						: "Adjustment";
				result.add(new GLCodeInlayHint(arguments.get(index), label));
			}
		}
		return List.copyOf(result);
	}

	private static List<Integer> argumentStarts(String source, int afterName) {
		int open = afterName;
		while (open < source.length() && Character.isWhitespace(source.charAt(open)))
			open++;
		if (open >= source.length() || source.charAt(open) != '(') return List.of();
		ArrayList<Integer> result = new ArrayList<>();
		int candidate = open + 1;
		int depth = 0;
		char quote = 0;
		for (int index = open + 1; index < source.length(); index++) {
			char value = source.charAt(index);
			if (quote != 0) {
				if (value == '\\') index++;
				else if (value == quote) quote = 0;
				continue;
			}
			if (value == '\'' || value == '"') { quote = value; continue; }
			if (value == '(') { depth++; continue; }
			if (value == ')' && depth > 0) { depth--; continue; }
			if ((value == ',' && depth == 0) || (value == ')' && depth == 0)) {
				int start = candidate;
				while (start < index && Character.isWhitespace(source.charAt(start))) start++;
				// Preserve the parameter slot even while its value is temporarily
				// empty. The surrounding commas still determine its meaning.
				result.add(start);
				if (value == ')') break;
				candidate = index + 1;
			}
		}
		return List.copyOf(result);
	}

	private List<GLCodeCompletion> completions(GLCodeDocument document, int offset) {
		if (offset < 0 || offset > document.text().length()) return List.of();
		Document parsed = parse(document.text());
		Line line = parsed.lineAt(offset);
		if (line == null || line.comment() || line.isInlineCommentAt(offset))
			return List.of();
		Reference partial = partialReference(document.text(), line, offset);
		if (partial != null)
			return referenceCompletions(partial.key(), partial.keyStart(),
					partial.keyEnd(), document.uri(), parsed);
		if (!line.hasProperty() || offset <= line.separator()) {
			int start = line == null ? offset : line.contentStart();
			String prefix = document.text().substring(start, offset).trim();
			start = offset - prefix.length();
			return propertyCompletions(prefix, start, line != null && line.hasProperty()
					? line.keyEnd() : offset, !line.hasProperty(), parsed);
		}
		Property property = schema.property(line.key());
		if (property == null) return List.of();
		String prefix = document.text().substring(line.valueStart(), offset).trim();
		int start = offset - prefix.length();
		return valueCompletions(document.uri(), property, prefix, start, offset);
	}

	private List<GLCodeCompletion> propertyCompletions(String prefix, int start,
			int end, boolean appendSeparator, Document document) {
		String lower = prefix.toLowerCase(java.util.Locale.ROOT);
		Set<String> declared = document.lines().stream().filter(Line::hasProperty)
				.map(Line::key).collect(java.util.stream.Collectors.toSet());
		List<GLCodeCompletion> result = new ArrayList<>();
		for (Property property : schema.properties().values().stream()
				.sorted(Comparator.comparing(Property::key)).toList()) {
			if (property.key().endsWith("*")) continue;
			if (declared.contains(property.key())) continue;
			if (!propertyAllowedInDocument(property, document)) continue;
			if (!property.key().toLowerCase(java.util.Locale.ROOT).startsWith(lower)) continue;
			String inserted = property.key() + (appendSeparator ? "=" : "");
			result.add(new GLCodeCompletion(property.key(), detail(property), inserted,
					start, end));
		}
		return List.copyOf(result);
	}

	private boolean propertyAllowedInDocument(Property property, Document document) {
		if (!"gltheme".equals(schema.id())) return true;
		Line declaration = document.first("theme.type");
		if (declaration == null) return true;
		String type = declaration.value().trim().toLowerCase(java.util.Locale.ROOT);
		if (!"colors".equals(type) && !"metrics".equals(type)) return true;
		String key = property.key();
		if ("theme.type".equals(key) || "theme.name".equals(key)) return true;
		boolean color = property.type() == ValueType.COLOR
				|| GLThemeKeyClassifier.isColorOrFontKey(key);
		return "colors".equals(type) ? color : !color && !key.startsWith("theme.");
	}

	private List<GLCodeCompletion> valueCompletions(URI uri, Property property,
			String prefix, int start, int end) {
		List<String> candidates = new ArrayList<>();
		switch (property.type()) {
		case BOOLEAN -> candidates.addAll(List.of("true", "false"));
		case ENUM -> candidates.addAll(property.choices());
		case COLOR -> candidates.addAll(List.of("rgb(0, 0, 0)",
				"rgb(0, 0, 0, 255)", "hsv(0deg, 0%, 0%)",
				"hsl(0deg, 0%, 0%)", "oklab(0.0, 0, 0)",
				"oklch(0.0, 0, 0deg)", "#000000", "${palette.text}"));
		case INSETS -> candidates.add("0, 0, 0, 0");
		case NUMBER -> candidates.addAll(List.of("0", "1", "${metrics.controlHeight}"));
		case PATH -> candidates.addAll(siblingCandidates(uri, property.key()));
		case TEXT -> { }
		}
		if ("theme.extends".equals(property.key())) candidates.addAll(BUILT_IN_REFERENCES);
		else if ("input.extends".equals(property.key())) candidates.add("default");
		if (property.defaultValue() != null) candidates.add(property.defaultValue());
		List<GLCodeCompletion> result = new ArrayList<>();
		for (String candidate : new LinkedHashSet<>(candidates))
			if (candidate.toLowerCase(java.util.Locale.ROOT)
					.startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
				result.add(new GLCodeCompletion(candidate, detail(property), candidate,
						start, end));
		return List.copyOf(result);
	}

	private List<GLCodeCompletion> referenceCompletions(String prefix, int start,
			int end, URI uri, Document document) {
		String lower = prefix.toLowerCase(java.util.Locale.ROOT);
		Map<String, String> details = new LinkedHashMap<>();
		for (Property property : schema.properties().values())
			details.put(property.key(), detail(property));
		for (Line line : document.lines()) if (line.hasProperty())
			details.putIfAbsent(line.key(), "custom property");
		Path owner = filePath(uri);
		if (owner != null)
			for (Line line : document.pathProperties(schema)) {
				if (isBuiltInReference(line)) continue;
				Path imported = resolvePath(uri, line.value());
				collectPropertyDetails(imported, details, new HashSet<>());
			}
		return details.entrySet().stream().filter(entry -> entry.getKey()
				.toLowerCase(java.util.Locale.ROOT).startsWith(lower))
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new GLCodeCompletion(entry.getKey(), entry.getValue(),
						entry.getKey(), start, end)).toList();
	}

	private void collectPropertyDetails(Path file, Map<String, String> details,
			Set<Path> visited) {
		if (file == null) return;
		Path normalized = file.toAbsolutePath().normalize();
		if (!visited.add(normalized) || !Files.isRegularFile(normalized)
				|| !isConfigurationFile(normalized)) return;
		try {
			Document parsed = parse(Files.readString(normalized));
			for (Line line : parsed.lines()) if (line.hasProperty()) {
				Property property = schema.property(line.key());
				details.putIfAbsent(line.key(), property == null
						? "imported property" : detail(property));
			}
			for (Line path : parsed.pathProperties(schema))
				if (!isBuiltInReference(path))
					collectPropertyDetails(resolvePath(normalized.toUri(), path.value()),
							details, visited);
		} catch (java.io.IOException ignored) { }
	}

	private List<String> siblingCandidates(URI uri, String key) {
		Path owner = filePath(uri);
		if (owner == null || owner.getParent() == null) return List.of();
		String lowerKey = key.toLowerCase(java.util.Locale.ROOT);
		try (var files = Files.list(owner.getParent())) {
			return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
					.filter(name -> lowerKey.contains("font")
							? name.matches("(?i).+\\.(?:ttf|otf|pfa|pfb)")
							: name.toLowerCase(java.util.Locale.ROOT)
									.endsWith("." + schema.id().toLowerCase(
											java.util.Locale.ROOT)))
					.sorted().toList();
		} catch (java.io.IOException failure) { return List.of(); }
	}

	private Optional<GLCodeHover> hoverAt(GLCodeDocument document, int offset) {
		Document parsed = parse(document.text());
		Line line = parsed.lineAt(offset);
		if (line == null || !line.hasProperty() || line.isInlineCommentAt(offset))
			return Optional.empty();
		for (Reference reference : references(line.value(), line.valueStart()))
			if (offset >= reference.start() && offset < reference.end()) {
				Property property = schema.property(reference.key());
				String contents = property == null
						? "Custom configuration reference `${" + reference.key() + "}`"
						: hoverText(property);
				return Optional.of(new GLCodeHover(reference.start(), reference.end(), contents));
			}
		if (offset >= line.keyStart() && offset < line.keyEnd()) {
			Property property = schema.property(line.key());
			String contents = property == null
					? "Custom configuration property `" + line.key() + "`"
					: hoverText(property, line.key());
			return Optional.of(new GLCodeHover(line.keyStart(), line.keyEnd(), contents));
		}
		if (offset >= line.valueStart() && offset <= line.valueEnd()) {
			Property property = schema.property(line.key());
			if (property != null && property.type() == ValueType.PATH
					&& builtInThemeLocation(line) != null)
				return Optional.of(new GLCodeHover(line.valueStart(), line.valueEnd(),
						"Built-in theme preset: `" + line.value() + "`"));
			if (property != null && property.type() == ValueType.PATH)
				return Optional.of(new GLCodeHover(line.valueStart(), line.valueEnd(),
						"Referenced file: `" + line.value() + "`"));
		}
		return Optional.empty();
	}

	private List<GLCodeLocation> definitionsAt(GLCodeDocument document, int offset) {
		Document parsed = parse(document.text());
		Line line = parsed.lineAt(offset);
		if (line == null || !line.hasProperty() || line.isInlineCommentAt(offset))
			return List.of();
		for (Reference reference : references(line.value(), line.valueStart()))
			if (offset >= reference.start() && offset < reference.end()) {
				Line local = parsed.first(reference.key());
				if (local != null) return List.of(location(document.uri(), document.text(), local.keyStart()));
				GLCodeLocation imported = findImportedDefinition(document, parsed, reference.key());
				return imported == null ? List.of() : List.of(imported);
			}
		Property property = schema.property(line.key());
		if (property != null && property.type() == ValueType.PATH
				&& offset >= line.valueStart() && offset <= line.valueEnd()) {
			GLCodeLocation builtIn = builtInThemeLocation(line);
			if (builtIn != null) return List.of(builtIn);
			Path target = resolvePath(document.uri(), line.value());
			if (target != null && Files.isRegularFile(target) && isConfigurationFile(target))
				return List.of(new GLCodeLocation(target.toUri(), 0, 0));
		}
		return List.of();
	}

	private GLCodeLocation findImportedDefinition(GLCodeDocument document,
			Document parsed, String key) {
		Path owner = filePath(document.uri());
		if (owner == null) return null;
		Set<Path> visited = new HashSet<>();
		List<Line> imports = orderedConfigurationImports(parsed);
		for (int index = imports.size() - 1; index >= 0; index--) {
			Line line = imports.get(index);
			Path imported = resolvePath(owner.toUri(), line.value());
			GLCodeLocation found = findDefinition(imported, key, visited);
			if (found != null) return found;
		}
		return null;
	}

	private GLCodeLocation findDefinition(Path file, String key, Set<Path> visited) {
		if (file == null || !Files.isRegularFile(file) || !isConfigurationFile(file))
			return null;
		Path normalized = file.toAbsolutePath().normalize();
		if (!visited.add(normalized)) return null;
		try {
			String source = Files.readString(normalized);
			Document parsed = parse(source);
			Line line = parsed.first(key);
			if (line != null) return location(normalized.toUri(), source, line.keyStart());
			for (Line path : parsed.pathProperties(schema)) {
				GLCodeLocation nested = findDefinition(resolvePath(normalized.toUri(), path.value()),
						key, visited);
				if (nested != null) return nested;
			}
		} catch (java.io.IOException ignored) { }
		return null;
	}

	private List<GLCodeDiagnostic> externalDiagnostics(GLCodeDocument document) {
		Document parsed = parse(document.text());
		Set<String> available = new HashSet<>(schema.properties().keySet());
		for (Line line : parsed.lines()) if (line.hasProperty()) available.add(line.key());
		Path owner = filePath(document.uri());
		Set<Path> visited = new HashSet<>();
		if (owner != null)
			for (Line line : orderedConfigurationImports(parsed)) {
				if (isBuiltInReference(line)) continue;
				Path target = resolvePath(document.uri(), line.value());
				if (target != null && Files.isRegularFile(target)
						&& isConfigurationFile(target)) collectKeys(target, available, visited);
			}
		List<GLCodeDiagnostic> result = new ArrayList<>();
		if (owner != null)
			for (Line line : parsed.pathProperties(schema)) {
				if (isBuiltInReference(line) || line.value().contains("${")) continue;
				Path target = resolvePath(document.uri(), line.value());
				if (target == null || !Files.isRegularFile(target))
					result.add(diagnostic(line.valueStart(), line.valueEnd(),
							GLCodeDiagnostic.Severity.ERROR,
							"Referenced file does not exist: " + line.value()));
				else if (isConfigurationFile(target)
						&& createsImportCycle(target, owner, new HashSet<>()))
					result.add(diagnostic(line.valueStart(), line.valueEnd(),
							GLCodeDiagnostic.Severity.ERROR,
							"Configuration import creates a cycle"));
			}
		for (Line line : parsed.lines()) if (line.hasProperty())
			for (Reference reference : references(line.value(), line.valueStart()))
				if (!available.contains(reference.key()))
					result.add(diagnostic(reference.start(), reference.end(),
							GLCodeDiagnostic.Severity.ERROR,
							"Unknown property reference '" + reference.key() + "'"));
		result.sort(Comparator.comparingInt(GLCodeDiagnostic::start));
		return List.copyOf(result);
	}

	private void collectKeys(Path file, Set<String> keys, Set<Path> visited) {
		Path normalized = file.toAbsolutePath().normalize();
		if (!visited.add(normalized) || !Files.isRegularFile(normalized)
				|| !isConfigurationFile(normalized)) return;
		try {
			Document parsed = parse(Files.readString(normalized));
			for (Line line : parsed.lines()) if (line.hasProperty()) keys.add(line.key());
			for (Line path : orderedConfigurationImports(parsed))
				if (!isBuiltInReference(path)) {
					Path nested = resolvePath(normalized.toUri(), path.value());
					if (nested != null) collectKeys(nested, keys, visited);
				}
		} catch (java.io.IOException ignored) { }
	}

	private boolean createsImportCycle(Path file, Path owner, Set<Path> visited) {
		Path normalized = file.toAbsolutePath().normalize();
		Path root = owner.toAbsolutePath().normalize();
		if (normalized.equals(root)) return true;
		if (!visited.add(normalized) || !Files.isRegularFile(normalized)
				|| !isConfigurationFile(normalized)) return false;
		try {
			Document parsed = parse(Files.readString(normalized));
			for (Line line : parsed.pathProperties(schema)) {
				if (isBuiltInReference(line)) continue;
				Path nested = resolvePath(normalized.toUri(), line.value());
				if (nested != null && createsImportCycle(nested, root, visited)) return true;
			}
		} catch (java.io.IOException ignored) { }
		return false;
	}

	private boolean isConfigurationFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		return name.endsWith("." + schema.id().toLowerCase(java.util.Locale.ROOT));
	}

	private List<Line> orderedConfigurationImports(Document document) {
		return document.pathProperties(schema).stream()
				.sorted(Comparator.comparingInt(GLPropertiesCodeLanguage::importPriority))
				.toList();
	}

	private static int importPriority(Line line) {
		return switch (line.key()) {
		case "theme.extends" -> 0;
		case "theme.colors" -> 1;
		case "theme.metrics" -> 2;
		default -> 3;
		};
	}

	private List<GLCodeSemanticToken> semanticTokensFor(String source) {
		Document parsed = parse(source);
		List<GLCodeSemanticToken> result = new ArrayList<>();
		for (Line line : parsed.lines()) {
			if (line.comment()) {
				result.add(semantic(line.contentStart(), line.end(), "comment"));
				continue;
			}
			if (!line.hasProperty()) continue;
			result.add(semantic(line.keyStart(), line.keyEnd(),
					isDirective(line.key()) ? "keyword" : "property", "declaration"));
			result.add(semantic(line.separator(), line.separator() + 1, "operator"));
			String value = source.substring(line.valueStart(), line.valueEnd());
			int valueTokenStart = result.size();
			List<Range> occupied = new ArrayList<>();
			for (Reference reference : references(value, line.valueStart())) {
				result.add(semantic(reference.start(), reference.end(), "variable"));
				occupied.add(new Range(reference.start(), reference.end()));
			}
			Matcher functions = FUNCTION.matcher(value);
			while (functions.find()) {
				int start = line.valueStart() + functions.start(), end = line.valueStart() + functions.end();
				if (!overlaps(occupied, start, end)) result.add(semantic(start, end, "function"));
			}
			Matcher numbers = NUMBER.matcher(value);
			while (numbers.find()) {
				int start = line.valueStart() + numbers.start(), end = line.valueStart() + numbers.end();
				if (!overlaps(occupied, start, end)) result.add(semantic(start, end, "number"));
			}
			if ("true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim()))
				result.add(semantic(line.valueStart(), line.valueEnd(), "keyword"));
			else if (result.size() == valueTokenStart && line.valueEnd() > line.valueStart())
				result.add(semantic(line.valueStart(), line.valueEnd(), "string"));
			if (line.inlineCommentStart() >= 0)
				result.add(semantic(line.inlineCommentStart(), line.end(), "comment"));
		}
		result.sort(Comparator.comparingInt(GLCodeSemanticToken::start));
		return result.stream().filter(new java.util.function.Predicate<>() {
			private int end = -1;
			@Override public boolean test(GLCodeSemanticToken token) {
				if (token.start() < end) return false;
				end = token.end(); return true;
			}
		}).toList();
	}

	private List<GLCodeColor> documentColorsFor(GLCodeDocument document) {
		Document parsed = parse(document.text());
		Map<String, String> values = new LinkedHashMap<>();
		for (Property property : schema.properties().values())
			if (property.defaultValue() != null)
				values.put(property.key(), property.defaultValue());
		applyBuiltInThemeBase(parsed, values);
		Path owner = filePath(document.uri());
		if (owner != null)
			for (Line line : orderedConfigurationImports(parsed)) {
				if (isBuiltInReference(line)) continue;
				collectValues(resolvePath(document.uri(), line.value()), values,
						new HashSet<>());
			}
		for (Line line : parsed.lines()) if (line.hasProperty())
			values.put(line.key(), line.value());

		List<GLCodeColor> result = new ArrayList<>();
		for (Line line : parsed.lines()) {
			if (!line.hasProperty() || line.value().isBlank()) continue;
			String resolved = resolveValue(line.value(), values, new HashSet<>());
			if (resolved == null) continue;
			try {
				result.add(new GLCodeColor(line.valueStart(), line.valueEnd(),
						GLThemeValues.parseColor(resolved)));
			} catch (RuntimeException ignored) { }
		}
		return List.copyOf(result);
	}

	private void applyBuiltInThemeBase(Document document, Map<String, String> values) {
		if (!"gltheme".equalsIgnoreCase(schema.id())) return;
		Line inherited = document.first("theme.extends");
		if (inherited == null) return;
		Map<String, String> builtIn = switch (inherited.value().trim()
				.toLowerCase(java.util.Locale.ROOT)) {
		case "dark" -> GLThemes.DARK.values().asMap();
		case "light" -> GLThemes.LIGHT.values().asMap();
		case "default", "" -> GLThemes.DEFAULT.values().asMap();
		default -> null;
		};
		if (builtIn != null) values.putAll(builtIn);
	}

	private void collectValues(Path file, Map<String, String> values,
			Set<Path> visited) {
		if (file == null) return;
		Path normalized = file.toAbsolutePath().normalize();
		if (!visited.add(normalized) || !Files.isRegularFile(normalized)
				|| !isConfigurationFile(normalized)) return;
		try {
			Document parsed = parse(Files.readString(normalized));
			applyBuiltInThemeBase(parsed, values);
			for (Line path : orderedConfigurationImports(parsed))
				if (!isBuiltInReference(path))
					collectValues(resolvePath(normalized.toUri(), path.value()), values, visited);
			for (Line line : parsed.lines()) if (line.hasProperty())
				values.put(line.key(), line.value());
		} catch (java.io.IOException ignored) { }
	}

	private static String resolveValue(String source, Map<String, String> values,
			Set<String> active) {
		Matcher matcher = REFERENCE.matcher(source);
		StringBuffer resolved = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1).trim();
			String value = values.get(key);
			if (value == null || !active.add(key)) return null;
			String replacement = resolveValue(value, values, active);
			active.remove(key);
			if (replacement == null) return null;
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	private Document parse(String source) {
		List<Line> lines = new ArrayList<>();
		List<GLCodeDiagnostic> diagnostics = new ArrayList<>();
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			int physicalEnd = end > start && source.charAt(end - 1) == '\r' ? end - 1 : end;
			int contentStart = start;
			while (contentStart < physicalEnd && Character.isWhitespace(source.charAt(contentStart)))
				contentStart++;
			boolean comment = contentStart < physicalEnd
					&& (source.charAt(contentStart) == '#' || source.charAt(contentStart) == '!');
			if (contentStart >= physicalEnd || comment) {
				lines.add(new Line(start, physicalEnd, contentStart, comment,
						-1, -1, -1, -1, -1, -1, "", ""));
			} else {
				int separator = separator(source, contentStart, physicalEnd);
				if (separator < 0) {
					diagnostics.add(diagnostic(contentStart, physicalEnd,
							GLCodeDiagnostic.Severity.ERROR,
							"Configuration property requires '='"));
					lines.add(new Line(start, physicalEnd, contentStart, false,
							-1, -1, -1, -1, -1, -1, "", ""));
				} else {
					int keyEnd = separator;
					while (keyEnd > contentStart && Character.isWhitespace(source.charAt(keyEnd - 1))) keyEnd--;
					int valueStart = separator + 1;
					while (valueStart < physicalEnd && Character.isWhitespace(source.charAt(valueStart))) valueStart++;
					int inlineCommentStart = "gltheme".equals(schema.id())
							? GLPropertiesSyntax.inlineCommentStart(source, valueStart,
									physicalEnd) : -1;
					int valueEnd = inlineCommentStart < 0 ? physicalEnd : inlineCommentStart;
					while (valueEnd > valueStart && Character.isWhitespace(source.charAt(valueEnd - 1))) valueEnd--;
					String key = source.substring(contentStart, keyEnd);
					String value = source.substring(valueStart, valueEnd);
					if (key.isBlank()) diagnostics.add(diagnostic(contentStart,
							Math.max(contentStart + 1, separator), GLCodeDiagnostic.Severity.ERROR,
							"Configuration property key must not be empty"));
					lines.add(new Line(start, physicalEnd, contentStart, false,
							contentStart, keyEnd, separator, valueStart, valueEnd,
							inlineCommentStart, key, value));
				}
			}
			if (newline < 0) break;
			start = newline + 1;
		}
		return new Document(source, List.copyOf(lines), List.copyOf(diagnostics));
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

	private static List<Reference> references(String value, int absoluteStart) {
		List<Reference> result = new ArrayList<>();
		Matcher matcher = REFERENCE.matcher(value);
		while (matcher.find()) result.add(new Reference(absoluteStart + matcher.start(),
				absoluteStart + matcher.end(), absoluteStart + matcher.start(1),
				absoluteStart + matcher.end(1), matcher.group(1).trim()));
		return result;
	}

	private static Reference partialReference(String source, Line line, int offset) {
		if (line == null || !line.hasProperty() || offset < line.valueStart()) return null;
		int open = source.lastIndexOf("${", offset);
		if (open < line.valueStart()) return null;
		int close = source.indexOf('}', open + 2);
		if (close >= 0 && close < offset) return null;
		int end = close >= 0 && close <= line.valueEnd() ? close : offset;
		return new Reference(open, close >= 0 ? close + 1 : offset,
				open + 2, end, source.substring(open + 2, offset));
	}

	private static List<Range> references(String value) {
		return references(value, 0).stream().map(reference ->
				new Range(reference.start(), reference.end())).toList();
	}

	private static boolean insideReference(List<Range> references, int offset) {
		return references.stream().anyMatch(range -> offset >= range.start() && offset < range.end());
	}

	private static boolean overlaps(List<Range> ranges, int start, int end) {
		return ranges.stream().anyMatch(range -> start < range.end() && end > range.start());
	}

	private static boolean isDirective(String key) {
		return key.startsWith("theme.") || key.startsWith("input.");
	}
	private static boolean isBuiltInReference(Line line) {
		String value = line.value().toLowerCase(java.util.Locale.ROOT);
		return ("theme.extends".equals(line.key()) && BUILT_IN_REFERENCES.contains(value))
				|| ("input.extends".equals(line.key()) && "default".equals(value));
	}

	private static GLCodeLocation builtInThemeLocation(Line line) {
		if (!"theme.extends".equals(line.key())) return null;
		String name = line.value().trim().toLowerCase(java.util.Locale.ROOT);
		if (!BUILT_IN_REFERENCES.contains(name)) return null;
		var resource = GLPropertiesCodeLanguage.class.getResource(
				"/com/vingame/glswing/themes/" + name + ".gltheme");
		if (resource == null) return null;
		try { return new GLCodeLocation(resource.toURI(), 0, 0); }
		catch (java.net.URISyntaxException invalidResource) { return null; }
	}

	private static Path filePath(URI uri) {
		try { return uri != null && "file".equalsIgnoreCase(uri.getScheme())
				? Path.of(uri).toAbsolutePath().normalize() : null; }
		catch (RuntimeException failure) { return null; }
	}

	private static Path resolvePath(URI ownerUri, String reference) {
		Path owner = filePath(ownerUri);
		if (owner == null || reference == null || reference.isBlank()) return null;
		try {
			Path path = Path.of(reference.trim());
			return (path.isAbsolute() ? path : owner.resolveSibling(path))
					.toAbsolutePath().normalize();
		} catch (RuntimeException failure) { return null; }
	}

	private static GLCodeLocation location(URI uri, String source, int offset) {
		int line = 0, character = 0;
		for (int index = 0; index < offset && index < source.length(); index++) {
			if (source.charAt(index) == '\n') { line++; character = 0; }
			else character++;
		}
		return new GLCodeLocation(uri, line, character);
	}

	private static String hoverText(Property property) {
		return hoverText(property, property.key());
	}

	private static String hoverText(Property property, String displayedKey) {
		StringBuilder text = new StringBuilder("`").append(displayedKey).append("`\n")
				.append(property.description()).append("\nType: ")
				.append(property.type().name().toLowerCase(java.util.Locale.ROOT));
		if (!property.choices().isEmpty())
			text.append("\nValues: ").append(String.join(", ", property.choices()));
		if (property.defaultValue() != null)
			text.append("\nDefault: `").append(property.defaultValue()).append('`');
		return text.toString();
	}

	private static String detail(Property property) {
		return property.type().name().toLowerCase(java.util.Locale.ROOT)
				+ " · " + property.description();
	}

	private static String concise(String message) {
		if (message == null || message.isBlank()) return "Invalid property value";
		int colon = message.indexOf(':');
		return colon > 0 && colon < 48 ? message.substring(0, colon) + ":"
				+ message.substring(colon + 1) : message;
	}

	private static GLCodeDiagnostic diagnostic(int start, int end,
			GLCodeDiagnostic.Severity severity, String message) {
		return new GLCodeDiagnostic(Math.max(0, start), Math.max(start + 1, end),
				severity, message);
	}

	private static GLCodeSemanticToken semantic(int start, int end, String type,
			String... modifiers) {
		return new GLCodeSemanticToken(start, end, type, Set.of(modifiers));
	}

	private static <T> CompletionStage<T> completed(T value) {
		return CompletableFuture.completedFuture(value);
	}

	private record Range(int start, int end) { }
	private record Reference(int start, int end, int keyStart, int keyEnd, String key) { }
	private record Line(int start, int end, int contentStart, boolean comment,
			int keyStart, int keyEnd, int separator, int valueStart, int valueEnd,
			int inlineCommentStart, String key, String value) {
		boolean hasProperty() { return separator >= 0 && keyStart >= 0 && keyEnd > keyStart; }
		boolean isInlineCommentAt(int offset) {
			return inlineCommentStart >= 0 && offset >= inlineCommentStart;
		}
	}
	private record Document(String source, List<Line> lines,
			List<GLCodeDiagnostic> syntaxDiagnostics) {
		Line lineAt(int offset) {
			for (Line line : lines) if (offset >= line.start() && offset <= line.end()) return line;
			return null;
		}
		Line first(String key) {
			for (Line line : lines) if (line.hasProperty() && line.key().equals(key)) return line;
			return null;
		}
		List<Line> pathProperties(GLCodePropertySchema schema) {
			return lines.stream().filter(Line::hasProperty).filter(line -> {
				Property property = schema.property(line.key());
				return property != null && property.type() == ValueType.PATH;
			}).toList();
		}
	}
}
