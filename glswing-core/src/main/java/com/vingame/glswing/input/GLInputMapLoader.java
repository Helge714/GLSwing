package com.vingame.glswing.input;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Loads UTF-8 {@code .glinput} resources or files with inheritance. */
public final class GLInputMapLoader {

	private static final String EXTENDS = "input.extends";

	private GLInputMapLoader() {
	}

	public static GLInputMap loadResource(String resource) {
		String normalized = normalizeResource(resource);
		try {
			return loadResource(normalized, new ArrayDeque<>());
		} catch (GLInputMapLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw failure("Cannot load input-map resource " + normalized, error);
		}
	}

	public static GLInputMap load(Path path) {
		if (path == null)
			throw new IllegalArgumentException("Input-map path must not be null");
		Path normalized = path.toAbsolutePath().normalize();
		try {
			return loadPath(normalized, new ArrayDeque<>());
		} catch (GLInputMapLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw failure("Cannot load input-map file " + normalized, error);
		}
	}

	/** Loads a file or returns the supplied fallback after reporting the error. */
	public static GLInputMap loadOrDefault(Path path, GLInputMap fallback,
			Consumer<GLInputMapLoadException> errorHandler) {
		if (fallback == null)
			throw new IllegalArgumentException("Fallback input map must not be null");
		try {
			return load(path);
		} catch (GLInputMapLoadException error) {
			if (errorHandler != null)
				errorHandler.accept(error);
			return fallback;
		}
	}

	/** Loads a file or falls back to {@link GLInputMaps#DEFAULT}. */
	public static GLInputMap loadOrDefault(Path path,
			Consumer<GLInputMapLoadException> errorHandler) {
		return loadOrDefault(path, GLInputMaps.DEFAULT, errorHandler);
	}

	/** Loads a file or silently falls back to {@link GLInputMaps#DEFAULT}. */
	public static GLInputMap loadOrDefault(Path path) {
		return loadOrDefault(path, GLInputMaps.DEFAULT, null);
	}

	private static GLInputMap loadResource(String resource, Deque<String> stack) {
		enter(resource, stack);
		try (InputStream stream = GLInputMapLoader.class.getResourceAsStream(resource)) {
			if (stream == null)
				throw new GLInputMapLoadException("Input-map resource not found: " + resource);
			Map<String, String> raw = read(stream, resource);
			String inherited = raw.remove(EXTENDS);
			GLInputMap base = resolveResourceBase(inherited, resource, stack);
			return build(base, raw, resource);
		} catch (IOException error) {
			throw new GLInputMapLoadException("Cannot read input map " + resource, error);
		} finally {
			stack.removeLast();
		}
	}

	private static GLInputMap loadPath(Path path, Deque<String> stack) {
		String identity = path.toString();
		enter(identity, stack);
		try (InputStream stream = Files.newInputStream(path)) {
			Map<String, String> raw = read(stream, identity);
			String inherited = raw.remove(EXTENDS);
			GLInputMap base = resolvePathBase(inherited, path, stack);
			return build(base, raw, identity);
		} catch (IOException error) {
			throw new GLInputMapLoadException("Cannot read input map " + path, error);
		} finally {
			stack.removeLast();
		}
	}

	private static GLInputMap resolveResourceBase(String inherited,
			String resource, Deque<String> stack) {
		if (inherited == null || inherited.isBlank())
			return empty();
		if (inherited.trim().equalsIgnoreCase("default"))
			return resource.endsWith("/default.glinput") ? empty() : GLInputMaps.DEFAULT;
		String parent = inherited.startsWith("/") ? inherited
				: resource.substring(0, resource.lastIndexOf('/') + 1) + inherited;
		return loadResource(normalizeResource(parent), stack);
	}

	private static GLInputMap resolvePathBase(String inherited, Path path,
			Deque<String> stack) {
		if (inherited == null || inherited.isBlank())
			return empty();
		if (inherited.trim().equalsIgnoreCase("default"))
			return GLInputMaps.DEFAULT;
		return loadPath(path.resolveSibling(inherited.trim()).normalize(), stack);
	}

	static GLInputMap build(GLInputMap base, Map<String, String> raw,
			String source) {
		Map<String, List<GLKeyBinding>> keys = new LinkedHashMap<>();
		Map<String, Integer> modifiers = new LinkedHashMap<>();
		Map<String, Double> values = new LinkedHashMap<>(base.getValues());
		keys.putAll(base.keyBindings());
		modifiers.putAll(base.modifierBindings());
		for (Map.Entry<String, String> entry : raw.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			try {
				if (key.startsWith("key."))
					keys.put(name(key, "key."), parseBindings(value));
				else if (key.startsWith("modifier."))
					modifiers.put(name(key, "modifier."), parseModifiers(value));
				else if (key.startsWith("value."))
					values.put(name(key, "value."), finitePositive(value));
				else
					throw new IllegalArgumentException("Unsupported property " + key);
			} catch (RuntimeException error) {
				throw new GLInputMapLoadException("Invalid input-map entry " + key
						+ " in " + source + ": " + error.getMessage(), error);
			}
		}
		return new GLInputMap(keys, modifiers, values);
	}

	private static List<GLKeyBinding> parseBindings(String text) {
		if (text.isBlank()) return List.of();
		List<GLKeyBinding> result = new ArrayList<>();
		for (String alternative : text.split(",")) {
			String[] tokens = alternative.trim().split("\\+");
			int modifiers = 0;
			Integer keyCode = null;
			for (String token : tokens) {
				String normalized = token.trim().toUpperCase(Locale.ROOT);
				Integer modifier = modifier(normalized);
				if (modifier != null) modifiers |= modifier;
				else if (normalized.equals("SHORTCUT")) modifiers |= shortcutMask();
				else if (keyCode == null) keyCode = keyCode(normalized);
				else throw new IllegalArgumentException("More than one key in " + alternative);
			}
			if (keyCode == null) throw new IllegalArgumentException("Missing key in " + alternative);
			result.add(new GLKeyBinding(keyCode, modifiers));
		}
		return List.copyOf(result);
	}

	private static int parseModifiers(String text) {
		int result = 0;
		for (String token : text.trim().split("\\+")) {
			String normalized = token.trim().toUpperCase(Locale.ROOT);
			if (normalized.equals("SHORTCUT")) result |= shortcutMask();
			else {
				Integer modifier = modifier(normalized);
				if (modifier == null) throw new IllegalArgumentException("Unknown modifier " + token);
				result |= modifier;
			}
		}
		if (result == 0) throw new IllegalArgumentException("Modifier must not be empty");
		return result;
	}

	private static Integer modifier(String token) {
		return switch (token) {
		case "SHIFT" -> InputEvent.SHIFT_DOWN_MASK;
		case "CTRL", "CONTROL" -> InputEvent.CTRL_DOWN_MASK;
		case "ALT" -> InputEvent.ALT_DOWN_MASK;
		case "META", "COMMAND", "CMD" -> InputEvent.META_DOWN_MASK;
		case "ALT_GRAPH" -> InputEvent.ALT_GRAPH_DOWN_MASK;
		default -> null;
		};
	}

	private static int shortcutMask() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
				? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
	}

	private static int keyCode(String name) {
		String fieldName = name.startsWith("VK_") ? name : "VK_" + name;
		try {
			return KeyEvent.class.getField(fieldName).getInt(null);
		} catch (ReflectiveOperationException error) {
			throw new IllegalArgumentException("Unknown key " + name);
		}
	}

	private static double finitePositive(String text) {
		double value = Double.parseDouble(text.trim());
		if (!Double.isFinite(value) || value <= 0)
			throw new IllegalArgumentException("Value must be finite and positive");
		return value;
	}

	private static Map<String, String> read(InputStream stream, String source)
			throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8))) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
				int separator = trimmed.indexOf('=');
				if (separator <= 0)
					throw new GLInputMapLoadException("Invalid line " + number + " in " + source);
				String key = trimmed.substring(0, separator).trim();
				String value = trimmed.substring(separator + 1).trim();
				if (result.put(key, value) != null)
					throw new GLInputMapLoadException("Duplicate property " + key + " in " + source);
			}
		}
		return result;
	}

	private static String name(String key, String prefix) {
		String name = key.substring(prefix.length()).trim();
		if (name.isEmpty()) throw new IllegalArgumentException("Action name must not be blank");
		return name;
	}

	private static void enter(String identity, Deque<String> stack) {
		if (stack.contains(identity))
			throw new GLInputMapLoadException("Cyclic input-map inheritance: " + identity);
		stack.addLast(identity);
	}

	private static String normalizeResource(String resource) {
		if (resource == null || resource.isBlank())
			throw new IllegalArgumentException("Input-map resource must not be blank");
		return resource.startsWith("/") ? resource : "/" + resource;
	}

	private static GLInputMap empty() {
		return new GLInputMap(Map.of(), Map.of(), Map.of());
	}

	private static GLInputMapLoadException failure(String context,
			RuntimeException error) {
		return new GLInputMapLoadException(context + ": " + error.getMessage(), error);
	}
}
