package com.vingame.glswing.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.vingame.glswing.ui.GLCodePropertySchema.Property;
import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;

/** Loads declarative {@code .glschema} contracts used by themes and tooling. */
public final class GLThemeSchemaLoader {
	private static final String BUILT_IN_RESOURCE =
			"/com/vingame/glswing/themes/default.glschema";
	private static final String BUILT_IN_THEME_RESOURCE =
			"/com/vingame/glswing/themes/default.gltheme";
	private static final GLCodePropertySchema BUILT_IN = loadBuiltIn();
	private static final Map<Path, GLCodePropertySchema> DOCUMENT_SCHEMAS =
			new ConcurrentHashMap<>();

	private GLThemeSchemaLoader() { }

	/** Returns the immutable schema for all standard GLSwing properties. */
	public static GLCodePropertySchema builtIn() { return BUILT_IN; }

	/** Loads one schema file or every {@code .glschema} file in a directory. */
	public static GLCodePropertySchema load(Path path) {
		if (path == null) throw new IllegalArgumentException("Schema path is required");
		return merge(BUILT_IN, loadAdditional(path.toAbsolutePath().normalize()));
	}

	static GLCodePropertySchema forTheme(Path theme) {
		Path normalized = theme.toAbsolutePath().normalize();
		GLCodePropertySchema schema = schemaChain(normalized, new ArrayDeque<>());
		registerDocuments(normalized, schema);
		return schema;
	}

	static GLCodePropertySchema withInlineDeclarations(
			GLCodePropertySchema base, String source) {
		if (base == null) throw new IllegalArgumentException("Base schema is required");
		return mergeCompatible(base, inlineDeclarations(source));
	}

	static GLCodePropertySchema forDocument(URI uri, String source) {
		try {
			if (uri == null || !"file".equalsIgnoreCase(uri.getScheme()))
				return mergeCompatible(BUILT_IN, inlineDeclarations(source), false);
			Path path = Path.of(uri).toAbsolutePath().normalize();
			GLCodePropertySchema base = DOCUMENT_SCHEMAS.get(path);
			String declaration = directive(source, "theme.schema");
			if (base == null && declaration != null)
				base = merge(BUILT_IN, loadAdditional(resolve(path, declaration)));
			if (base == null) base = findOwningBundle(path);
			if (base == null) base = BUILT_IN;
			return mergeCompatible(base, inlineDeclarations(source), false);
		} catch (RuntimeException ignored) {
			return BUILT_IN;
		}
	}

	private static GLCodePropertySchema findOwningBundle(Path document) {
		Path directory = document.getParent();
		for (int depth = 0; directory != null && depth < 3; depth++,
				directory = directory.getParent()) {
			try (var entries = Files.list(directory)) {
				for (Path candidate : entries.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString()
								.toLowerCase(Locale.ROOT).endsWith(".gltheme"))
						.sorted().toList()) {
					if (candidate.toAbsolutePath().normalize().equals(document)) continue;
					String theme = Files.readString(candidate, StandardCharsets.UTF_8);
					for (String key : List.of("theme.colors", "theme.metrics")) {
						String reference = directive(theme, key);
						if (reference != null && resolve(candidate, reference).equals(document))
							return forTheme(candidate);
					}
				}
			} catch (IOException ignored) { }
		}
		return null;
	}

	private static GLCodePropertySchema schemaChain(Path theme, Deque<Path> stack) {
		if (stack.contains(theme))
			throw new GLThemeLoadException("Cyclic theme inheritance while loading schemas: "
					+ theme);
		stack.addLast(theme);
		try {
			if (!Files.isRegularFile(theme)) return BUILT_IN;
			String source = Files.readString(theme, StandardCharsets.UTF_8);
			String inherited = directive(source, "theme.extends");
			GLCodePropertySchema base = BUILT_IN;
			if (inherited != null && !inherited.isBlank()
					&& !isBuiltInName(inherited))
				base = schemaChain(resolve(theme, inherited), stack);
			else if (inherited == null) {
				Path sibling = theme.resolveSibling("default.gltheme")
						.toAbsolutePath().normalize();
				if (!sibling.equals(theme) && Files.isRegularFile(sibling))
					base = schemaChain(sibling, stack);
			}
			String declaration = directive(source, "theme.schema");
			if (declaration != null)
				base = merge(base, loadAdditional(resolve(theme, declaration)));
			base = mergeCompatible(base, inlineDeclarations(source));
			for (String key : List.of("theme.colors", "theme.metrics")) {
				String reference = directive(source, key);
				if (reference == null) continue;
				Path fragment = resolve(theme, reference);
				if (Files.isRegularFile(fragment))
					base = mergeCompatible(base, inlineDeclarations(
							Files.readString(fragment, StandardCharsets.UTF_8)));
			}
			return base;
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot read theme schema declaration " + theme,
					error);
		} finally {
			stack.removeLast();
		}
	}

	private static boolean isBuiltInName(String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "", "default", "dark", "light" -> true;
		default -> false;
		};
	}

	private static void registerDocuments(Path theme, GLCodePropertySchema schema) {
		DOCUMENT_SCHEMAS.put(theme, schema);
		try {
			String source = Files.readString(theme, StandardCharsets.UTF_8);
			for (String key : List.of("theme.colors", "theme.metrics")) {
				String reference = directive(source, key);
				if (reference != null) DOCUMENT_SCHEMAS.put(resolve(theme, reference), schema);
			}
		} catch (IOException ignored) { }
	}

	private static GLCodePropertySchema loadBuiltIn() {
		try (InputStream stream = GLThemeSchemaLoader.class
				.getResourceAsStream(BUILT_IN_RESOURCE)) {
			if (stream == null)
				throw new ExceptionInInitializerError("Missing " + BUILT_IN_RESOURCE);
			GLCodePropertySchema schema = parse(new BufferedReader(new InputStreamReader(
					stream, StandardCharsets.UTF_8)), BUILT_IN_RESOURCE);
			try (InputStream themeStream = GLThemeSchemaLoader.class
					.getResourceAsStream(BUILT_IN_THEME_RESOURCE)) {
				if (themeStream == null)
					throw new ExceptionInInitializerError(
							"Missing " + BUILT_IN_THEME_RESOURCE);
				String theme = new String(themeStream.readAllBytes(),
						StandardCharsets.UTF_8);
				return mergeCompatible(schema, inlineDeclarations(theme));
			}
		} catch (IOException error) {
			throw new ExceptionInInitializerError(error);
		}
	}

	private static GLCodePropertySchema loadAdditional(Path path) {
		List<Path> files;
		try {
			if (Files.isDirectory(path)) {
				try (var entries = Files.list(path)) {
					files = entries.filter(Files::isRegularFile)
							.filter(file -> file.getFileName().toString()
									.toLowerCase(Locale.ROOT).endsWith(".glschema"))
							.sorted(Comparator.comparing(file -> file.getFileName()
									.toString().toLowerCase(Locale.ROOT))).toList();
				}
				if (files.isEmpty()) throw new GLThemeLoadException(
						"Schema directory contains no .glschema files: " + path);
			} else files = List.of(path);
			GLCodePropertySchema result = emptySchema();
			for (Path file : files) {
				if (!Files.isRegularFile(file))
					throw new GLThemeLoadException("Theme schema not found: " + file);
				try (BufferedReader reader = Files.newBufferedReader(file,
						StandardCharsets.UTF_8)) {
					result = merge(result, parse(reader, file.toString()));
				}
			}
			return result;
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot load theme schema " + path, error);
		}
	}

	private static GLCodePropertySchema parse(BufferedReader reader, String source)
			throws IOException {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(false);
		String line;
		int number = 0;
		while ((line = reader.readLine()) != null) {
			number++;
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!"))
				continue;
			int separator = trimmed.indexOf('=');
			if (separator <= 0) throw schemaError(source, number, "Expected key=TYPE");
			String key = trimmed.substring(0, separator).trim();
			if (key.startsWith("schema.")) continue;
			List<String> fields = fields(trimmed.substring(separator + 1));
			if (fields.isEmpty()) throw schemaError(source, number, "Missing property type");
			ValueType type;
			try { type = ValueType.valueOf(fields.get(0).trim().toUpperCase(Locale.ROOT)); }
			catch (IllegalArgumentException error) {
				throw schemaError(source, number, "Unknown property type " + fields.get(0));
			}
			String description = "Theme property '" + key + "'.";
			List<String> choices = List.of();
			Double minimum = null, maximum = null;
			boolean integer = false;
			String category = type == ValueType.COLOR ? "colors" : "metrics";
			for (int index = 1; index < fields.size(); index++) {
				String field = fields.get(index).trim();
				int equals = field.indexOf('=');
				if (equals < 0) description = unquote(field);
				else {
					String option = field.substring(0, equals).trim().toLowerCase(Locale.ROOT);
					String value = unquote(field.substring(equals + 1).trim());
					switch (option) {
					case "description" -> description = value;
					case "values" -> choices = List.of(value.split("\\|", -1));
					case "min" -> minimum = Double.valueOf(value);
					case "max" -> maximum = Double.valueOf(value);
					case "integer" -> integer = Boolean.parseBoolean(value);
					case "fragment" -> category = value.toLowerCase(Locale.ROOT);
					default -> throw schemaError(source, number,
							"Unknown schema option " + option);
					}
				}
			}
			if (type == ValueType.ENUM && choices.isEmpty())
				throw schemaError(source, number, "ENUM requires values=a|b");
			builder.property(key, type, description, choices, null, minimum, maximum,
					integer, category);
		}
		return builder.build();
	}

	private static List<String> fields(String value) {
		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"') quoted = !quoted;
			else if (character == ',' && !quoted) {
				result.add(field.toString().trim());
				field.setLength(0);
				continue;
			}
			field.append(character);
		}
		if (quoted) throw new GLThemeLoadException("Unclosed schema description quote");
		result.add(field.toString().trim());
		return result;
	}

	private static String unquote(String value) {
		return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
				? value.substring(1, value.length() - 1) : value;
	}

	private static GLCodePropertySchema merge(GLCodePropertySchema base,
			GLCodePropertySchema additions) {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(false);
		for (Property property : base.properties().values()) builder.property(property);
		for (Property property : additions.properties().values()) builder.property(property);
		return builder.build();
	}

	private static GLCodePropertySchema mergeCompatible(GLCodePropertySchema base,
			GLCodePropertySchema additions) {
		return mergeCompatible(base, additions, true);
	}

	private static GLCodePropertySchema mergeCompatible(GLCodePropertySchema base,
			GLCodePropertySchema additions, boolean rejectConflicts) {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(false);
		for (Property property : base.properties().values()) {
			Property declared = additions.properties().get(property.key());
			if (declared != null && compatibleDeclaration(property, declared)
					&& !property.category().equals(declared.category())) continue;
			builder.property(property);
		}
		for (Property property : additions.properties().values()) {
			Property existing = base.properties().get(property.key());
			if (existing == null || (compatibleDeclaration(existing, property)
					&& !existing.category().equals(property.category())))
				builder.property(property);
			else if (rejectConflicts && !compatibleDeclaration(existing, property))
				throw new GLThemeLoadException("Inline type of " + property.key()
						+ " conflicts with its schema declaration");
		}
		return builder.build();
	}

	private static boolean compatibleDeclaration(Property existing,
			Property declared) {
		return existing.type() == declared.type()
				&& existing.choices().equals(declared.choices());
	}

	private static GLCodePropertySchema inlineDeclarations(String source) {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(false);
		String documentType = directive(source, "theme.type");
		int start = 0;
		while (start <= source.length()) {
			int newline = source.indexOf('\n', start);
			int end = newline < 0 ? source.length() : newline;
			int physicalEnd = end > start && source.charAt(end - 1) == '\r' ? end - 1 : end;
			int contentStart = start;
			while (contentStart < physicalEnd
					&& Character.isWhitespace(source.charAt(contentStart))) contentStart++;
			GLPropertiesSyntax.TypeAnnotation annotation;
			try {
				annotation = GLPropertiesSyntax.typeAnnotation(source, contentStart, physicalEnd);
			} catch (IllegalArgumentException error) {
				throw new GLThemeLoadException(error.getMessage(), error);
			}
			if (annotation != null) {
				int separator = propertySeparator(source, annotation.end(), physicalEnd);
				if (separator < 0)
					throw new GLThemeLoadException("Inline theme token requires key=value");
				int keyEnd = separator;
				while (keyEnd > annotation.end()
						&& Character.isWhitespace(source.charAt(keyEnd - 1))) keyEnd--;
				String key = source.substring(annotation.end(), keyEnd).trim();
				if (key.isEmpty()) throw new GLThemeLoadException(
						"Inline theme token property is missing");
				String category = "colors".equalsIgnoreCase(documentType) ? "colors"
						: "metrics".equalsIgnoreCase(documentType) ? "metrics"
						: annotation.type() == ValueType.COLOR ? "colors" : "metrics";
				builder.property(key, annotation.type(),
						"Document-local " + annotation.type().name().toLowerCase(Locale.ROOT)
								+ " theme token.", annotation.choices(), null, null, null, false,
						category);
			}
			if (newline < 0) break;
			start = newline + 1;
		}
		return builder.build();
	}

	private static int propertySeparator(String source, int start, int end) {
		boolean escaped = false;
		for (int index = start; index < end; index++) {
			char value = source.charAt(index);
			if (!escaped && (value == '=' || value == ':')) return index;
			escaped = !escaped && value == '\\';
			if (value != '\\') escaped = false;
		}
		return -1;
	}

	private static GLCodePropertySchema emptySchema() {
		return GLCodePropertySchema.builder("gltheme").allowUnknownKeys(false).build();
	}

	private static Path resolve(Path owner, String reference) {
		return owner.resolveSibling(reference.trim()).toAbsolutePath().normalize();
	}

	private static String directive(String source, String key) {
		if (source == null) return null;
		for (String line : source.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
			int separator = trimmed.indexOf('=');
			if (separator > 0 && key.equals(trimmed.substring(0, separator).trim()))
				return trimmed.substring(separator + 1).trim();
		}
		return null;
	}

	private static GLThemeLoadException schemaError(String source, int line,
			String message) {
		return new GLThemeLoadException(source + ":" + line + ": " + message);
	}
}
