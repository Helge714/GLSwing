package com.vingame.glswing.input;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable semantic keyboard and pointer-modifier configuration. */
public final class GLInputMap {

	private final Map<String, List<GLKeyBinding>> keys;
	private final Map<String, Integer> modifiers;
	private final Map<String, Double> values;

	public GLInputMap(Map<String, List<GLKeyBinding>> keys,
			Map<String, Integer> modifiers, Map<String, Double> values) {
		if (keys == null || modifiers == null || values == null)
			throw new IllegalArgumentException("Input-map entries must not be null");
		Map<String, List<GLKeyBinding>> immutableKeys = new LinkedHashMap<>();
		keys.forEach((action, bindings) -> immutableKeys.put(action,
				List.copyOf(bindings)));
		this.keys = Map.copyOf(immutableKeys);
		this.modifiers = Map.copyOf(modifiers);
		this.values = Map.copyOf(values);
	}

	public boolean matches(String action, KeyEvent event) {
		return keys.getOrDefault(action, List.of()).stream()
				.anyMatch(binding -> binding.matches(event));
	}

	public boolean matches(String action, GLKeyEvent event) {
		return event != null && matches(action, event.getOriginalEvent());
	}

	/** Returns whether all modifiers assigned to an action are currently held. */
	public boolean hasModifiers(String action, int modifiersEx) {
		Integer required = modifiers.get(action);
		return required != null && (modifiersEx & required) == required;
	}

	public double getValue(String name, double fallback) {
		return values.getOrDefault(name, fallback);
	}

	public List<GLKeyBinding> getKeyBindings(String action) {
		return keys.getOrDefault(action, List.of());
	}

	/** Returns every effective action and its immutable bindings. */
	public Map<String, List<GLKeyBinding>> getKeyBindings() { return keys; }

	public Integer getModifiers(String action) {
		return modifiers.get(action);
	}

	public Map<String, Double> getValues() {
		return values;
	}

	Map<String, List<GLKeyBinding>> keyBindings() { return keys; }
	Map<String, Integer> modifierBindings() { return modifiers; }
}
