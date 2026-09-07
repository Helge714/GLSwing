package com.vingame.glswing.ui;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.internal.GLConfigurationFiles;

/** Mutable override draft that builds immutable {@link GLTheme} instances. */
public final class GLThemeEditor {
	private static final String EXTENDS = "theme.extends";

	private final GLTheme base;
	private final String baseReference;
	private final LinkedHashMap<String, String> overrides;

	private GLThemeEditor(GLTheme base, String baseReference,
			Map<String, String> overrides) {
		if (base == null) throw new IllegalArgumentException("Base theme must not be null");
		this.base = base;
		this.baseReference = baseReference;
		this.overrides = new LinkedHashMap<>(overrides);
		build();
	}

	public static GLThemeEditor from(GLTheme base) {
		return new GLThemeEditor(base, presetReference(base), Map.of());
	}

	/** Creates a draft with an explicit reference used by subsequently saved files. */
	public static GLThemeEditor from(GLTheme base, String baseReference) {
		return new GLThemeEditor(base, normalizeReference(baseReference), Map.of());
	}

	/** Loads existing overrides or creates an empty draft over the supplied base. */
	public static GLThemeEditor loadOrCreate(Path path, GLTheme defaultBase) {
		if (path == null) throw new IllegalArgumentException("Theme path must not be null");
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.exists(normalized)) return from(defaultBase);
		try {
			Map<String, String> raw = new LinkedHashMap<>(
					GLConfigurationFiles.read(normalized, "theme " + normalized));
			String reference = normalizeReference(raw.remove(EXTENDS));
			GLTheme resolvedBase = resolveBase(normalized, reference, defaultBase);
			String savedReference = reference == null
					? presetReference(resolvedBase) : reference;
			return new GLThemeEditor(resolvedBase, savedReference, raw);
		} catch (GLThemeLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw new GLThemeLoadException("Cannot edit theme " + normalized, error);
		}
	}

	public GLThemeEditor set(String key, String value) {
		validateEntry(key, value);
		String previous = overrides.put(key, value);
		try { build(); }
		catch (RuntimeException error) {
			if (previous == null) overrides.remove(key); else overrides.put(key, previous);
			throw error;
		}
		return this;
	}

	public GLThemeEditor set(String key, double value) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException("Theme number must be finite");
		return set(key, Double.toString(value));
	}

	public GLThemeEditor setColor(String key, Color value) {
		if (value == null) throw new IllegalArgumentException("Theme color must not be null");
		return set(key, "rgba(" + value.getRed() + ", " + value.getGreen() + ", "
				+ value.getBlue() + ", " + value.getAlpha() + ")");
	}

	public GLThemeEditor unset(String key) {
		overrides.remove(key);
		return this;
	}

	public GLThemeEditor reset() {
		overrides.clear();
		return this;
	}

	public Optional<String> getOverride(String key) {
		return Optional.ofNullable(overrides.get(key));
	}

	public boolean isOverridden(String key) { return overrides.containsKey(key); }

	public String getEffective(String key, String fallback) {
		return build().values().get(key, fallback);
	}

	public Map<String, String> getOverrides() { return Map.copyOf(overrides); }
	public GLTheme getBase() { return base; }
	public String getBaseReference() { return baseReference; }

	public GLTheme build() {
		return GLThemeLoader.build(base, overrides, GLThemeCodeSupport.schema());
	}

	/** Atomically saves only the explicit overrides. */
	public void save(Path path) {
		if (baseReference == null && base != GLTheme.DEFAULT)
			throw new IllegalStateException("A custom base theme requires an explicit "
					+ "base reference before overrides can be saved");
		GLConfigurationFiles.writeAtomically(path, "GLSwing theme overrides",
				EXTENDS, baseReference, overrides);
	}

	private static GLTheme resolveBase(Path source, String reference,
			GLTheme fallback) {
		if (reference == null) return fallback;
		return switch (reference.toLowerCase()) {
		case "default" -> GLThemes.DEFAULT;
		case "dark" -> GLThemes.DARK;
		case "light" -> GLThemes.LIGHT;
		default -> GLThemeLoader.load(source.resolveSibling(reference));
		};
	}

	private static String presetReference(GLTheme theme) {
		if (theme == GLThemes.DARK) return "dark";
		if (theme == GLThemes.LIGHT) return "light";
		if (theme == GLTheme.DEFAULT || theme == GLThemes.DEFAULT) return "default";
		return null;
	}

	private static String normalizeReference(String reference) {
		return reference == null || reference.isBlank() ? null : reference.trim();
	}

	private static void validateEntry(String key, String value) {
		if (key == null || key.isBlank() || key.equals(EXTENDS) || value == null)
			throw new IllegalArgumentException("Theme override must have a key and value");
	}
}
