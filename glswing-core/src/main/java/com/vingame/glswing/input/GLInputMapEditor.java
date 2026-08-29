package com.vingame.glswing.input;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.internal.GLConfigurationFiles;

/** Mutable override draft that builds immutable {@link GLInputMap} instances. */
public final class GLInputMapEditor {
	private static final String EXTENDS = "input.extends";

	private final GLInputMap base;
	private final String baseReference;
	private final LinkedHashMap<String, String> overrides;

	private GLInputMapEditor(GLInputMap base, String baseReference,
			Map<String, String> overrides) {
		if (base == null) throw new IllegalArgumentException("Base input map must not be null");
		this.base = base;
		this.baseReference = baseReference;
		this.overrides = new LinkedHashMap<>(overrides);
		build();
	}

	public static GLInputMapEditor from(GLInputMap base) {
		return new GLInputMapEditor(base,
				base == GLInputMaps.DEFAULT ? "default" : null, Map.of());
	}

	public static GLInputMapEditor from(GLInputMap base, String baseReference) {
		return new GLInputMapEditor(base, normalizeReference(baseReference), Map.of());
	}

	public static GLInputMapEditor loadOrCreate(Path path,
			GLInputMap defaultBase) {
		if (path == null) throw new IllegalArgumentException("Input-map path must not be null");
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.exists(normalized)) return from(defaultBase);
		try {
			Map<String, String> raw = new LinkedHashMap<>(
					GLConfigurationFiles.read(normalized, "input map " + normalized));
			String reference = normalizeReference(raw.remove(EXTENDS));
			GLInputMap resolvedBase = resolveBase(normalized, reference, defaultBase);
			String savedReference = reference == null
					&& resolvedBase == GLInputMaps.DEFAULT ? "default" : reference;
			return new GLInputMapEditor(resolvedBase, savedReference, raw);
		} catch (GLInputMapLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw new GLInputMapLoadException("Cannot edit input map " + normalized,
					error);
		}
	}

	public GLInputMapEditor bind(String action, String bindings) {
		return put("key." + requireName(action), bindings);
	}

	public GLInputMapEditor bind(String action, GLKeyBinding... bindings) {
		if (bindings == null)
			throw new IllegalArgumentException("Key bindings must not be null");
		String encoded = Arrays.stream(bindings).map(GLKeyBinding::toConfigString)
				.reduce((left, right) -> left + ", " + right).orElse("");
		return bind(action, encoded);
	}

	public GLInputMapEditor unbind(String action) {
		overrides.put("key." + requireName(action), "");
		return this;
	}

	public GLInputMapEditor setModifier(String action, String modifiers) {
		return put("modifier." + requireName(action), modifiers);
	}

	public GLInputMapEditor setModifier(String action, int modifiers) {
		return setModifier(action, GLKeyBinding.modifiersToConfigString(modifiers));
	}

	public GLInputMapEditor setValue(String name, double value) {
		if (!Double.isFinite(value) || value <= 0)
			throw new IllegalArgumentException("Input-map value must be finite and positive");
		return put("value." + requireName(name), Double.toString(value));
	}

	/** Removes an override so the property is inherited from the base again. */
	public GLInputMapEditor unset(String property) {
		if (property == null || property.isBlank())
			throw new IllegalArgumentException("Input-map property must not be blank");
		overrides.remove(property);
		return this;
	}

	public GLInputMapEditor unsetBinding(String action) {
		return unset("key." + requireName(action));
	}

	public GLInputMapEditor unsetModifier(String action) {
		return unset("modifier." + requireName(action));
	}

	public GLInputMapEditor unsetValue(String name) {
		return unset("value." + requireName(name));
	}

	public GLInputMapEditor reset() { overrides.clear(); return this; }

	public Optional<String> getOverride(String property) {
		return Optional.ofNullable(overrides.get(property));
	}

	public boolean isOverridden(String property) {
		return overrides.containsKey(property);
	}

	public Map<String, String> getOverrides() { return Map.copyOf(overrides); }
	public GLInputMap getBase() { return base; }
	public String getBaseReference() { return baseReference; }
	public GLInputMap build() {
		return GLInputMapLoader.build(base, overrides, "input-map editor");
	}

	/** Returns effective actions already assigned to the supplied binding. */
	public List<String> findActions(GLKeyBinding binding) {
		if (binding == null) throw new IllegalArgumentException("Binding must not be null");
		List<String> result = new ArrayList<>();
		build().getKeyBindings().forEach((action, bindings) -> {
			if (bindings.contains(binding)) result.add(action);
		});
		return List.copyOf(result);
	}

	/** Atomically saves only the explicit overrides. */
	public void save(Path path) {
		if (baseReference == null && base != GLInputMaps.DEFAULT)
			throw new IllegalStateException("A custom base input map requires an explicit "
					+ "base reference before overrides can be saved");
		GLConfigurationFiles.writeAtomically(path, "GLSwing input-map overrides",
				EXTENDS, baseReference, overrides);
	}

	private GLInputMapEditor put(String property, String value) {
		if (value == null)
			throw new IllegalArgumentException("Input-map value must not be null");
		String previous = overrides.put(property, value.trim());
		try { build(); }
		catch (RuntimeException error) {
			if (previous == null) overrides.remove(property); else overrides.put(property, previous);
			throw error;
		}
		return this;
	}

	private static GLInputMap resolveBase(Path source, String reference,
			GLInputMap fallback) {
		if (reference == null) return fallback;
		if (reference.equalsIgnoreCase("default")) return GLInputMaps.DEFAULT;
		return GLInputMapLoader.load(source.resolveSibling(reference));
	}

	private static String normalizeReference(String reference) {
		return reference == null || reference.isBlank() ? null : reference.trim();
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Input-map action must not be blank");
		return name.trim();
	}
}
