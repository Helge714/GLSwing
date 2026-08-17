package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.vingame.glswing.layout.GLInsets;

/** Immutable resolved values retained from a loaded theme for custom controls. */
public final class GLThemeValues {
	public static final GLThemeValues EMPTY = new GLThemeValues(Map.of());

	private final Map<String, String> values;
	private final Map<String, String> sourceValues;

	public GLThemeValues(Map<String, String> values) {
		this(values, values);
	}

	GLThemeValues(Map<String, String> values, Map<String, String> sourceValues) {
		if (values == null || sourceValues == null)
			throw new IllegalArgumentException("Theme values must not be null");
		this.values = validatedCopy(values);
		this.sourceValues = validatedCopy(sourceValues);
	}

	private static Map<String, String> validatedCopy(Map<String, String> values) {
		Map<String, String> copy = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()
					|| entry.getValue() == null)
				throw new IllegalArgumentException(
						"Theme keys must not be blank and values must not be null");
			copy.put(entry.getKey(), entry.getValue());
		}
		return Map.copyOf(copy);
	}

	public Map<String, String> asMap() {
		return values;
	}

	public boolean contains(String key) {
		return values.containsKey(key);
	}

	public String get(String key, String fallback) {
		return values.getOrDefault(key, fallback);
	}

	public GLThemeValues with(String key, String value) {
		if (key == null || key.isBlank() || value == null)
			throw new IllegalArgumentException(
					"Theme key must not be blank and value must not be null");
		Map<String, String> changed = new LinkedHashMap<>(values);
		changed.put(key, value);
		Map<String, String> changedSource = new LinkedHashMap<>(sourceValues);
		changedSource.put(key, value);
		return new GLThemeValues(changed, changedSource);
	}

	Map<String, String> sourceMap() {
		return sourceValues;
	}

	public Color getColor(String key, Color fallback) {
		String value = values.get(key);
		return value == null ? fallback : parseColor(value);
	}

	public float getFloat(String key, float fallback) {
		String value = values.get(key);
		return value == null ? fallback : parseFloat(value, key);
	}

	public GLInsets getInsets(String key, GLInsets fallback) {
		String value = values.get(key);
		if (value == null)
			return fallback;
		String[] parts = value.split(",");
		if (parts.length != 4)
			throw new IllegalArgumentException(
					"Theme inset " + key + " requires top,left,bottom,right");
		return new GLInsets(parseFloat(parts[0], key), parseFloat(parts[1], key),
				parseFloat(parts[2], key), parseFloat(parts[3], key));
	}

	public static Color parseColor(String text) {
		if (text == null)
			throw new IllegalArgumentException("Color text must not be null");
		String value = text.trim();
		if (value.startsWith("#"))
			return parseHex(value);
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.startsWith("rgba(") && value.endsWith(")"))
			return parseFunctional(value.substring(5, value.length() - 1), true);
		if (lower.startsWith("rgb(") && value.endsWith(")"))
			return parseFunctional(value.substring(4, value.length() - 1), false);
		throw new IllegalArgumentException("Unsupported theme color: " + text);
	}

	private static Color parseHex(String value) {
		String digits = value.substring(1);
		if (digits.length() != 6 && digits.length() != 8)
			throw new IllegalArgumentException(
					"Hex colors require #RRGGBB or #RRGGBBAA: " + value);
		try {
			int red = Integer.parseInt(digits.substring(0, 2), 16);
			int green = Integer.parseInt(digits.substring(2, 4), 16);
			int blue = Integer.parseInt(digits.substring(4, 6), 16);
			int alpha = digits.length() == 8
					? Integer.parseInt(digits.substring(6, 8), 16) : 255;
			return new Color(red, green, blue, alpha);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid hex color: " + value, error);
		}
	}

	private static Color parseFunctional(String body, boolean alphaRequired) {
		String[] parts = body.split(",");
		int expected = alphaRequired ? 4 : 3;
		if (parts.length != expected)
			throw new IllegalArgumentException(
					(alphaRequired ? "rgba" : "rgb") + " requires "
							+ expected + " components");
		boolean normalized = hasFloatSuffix(parts[0])
				|| hasFloatSuffix(parts[1]) || hasFloatSuffix(parts[2]);
		boolean percentages = parts[0].trim().endsWith("%")
				|| parts[1].trim().endsWith("%") || parts[2].trim().endsWith("%");
		for (int index = 0; index < 3; index++) {
			String part = parts[index].trim();
			if (normalized != hasFloatSuffix(part)
					|| percentages != part.endsWith("%"))
				throw new IllegalArgumentException(
						"RGB components must consistently use integers, f-suffixed floats or percentages");
		}
		float red = colorComponent(parts[0], normalized, percentages);
		float green = colorComponent(parts[1], normalized, percentages);
		float blue = colorComponent(parts[2], normalized, percentages);
		float alpha = alphaRequired ? alphaComponent(parts[3]) : 1;
		return new Color(red, green, blue, alpha);
	}

	private static float colorComponent(String text, boolean normalized,
			boolean percentage) {
		String value = text.trim();
		float result;
		try {
			if (percentage)
				result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100;
			else if (normalized)
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else
				result = Integer.parseInt(value) / 255f;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid color component: " + text, error);
		}
		return unit(result, text);
	}

	private static float alphaComponent(String text) {
		String value = text.trim();
		try {
			float result;
			if (value.endsWith("%"))
				result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100;
			else if (hasFloatSuffix(value))
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else {
				result = Float.parseFloat(value);
				if (result > 1)
					result /= 255;
			}
			return unit(result, text);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid alpha component: " + text, error);
		}
	}

	private static boolean hasFloatSuffix(String value) {
		String trimmed = value.trim();
		return trimmed.endsWith("f") || trimmed.endsWith("F");
	}

	private static float unit(float value, String source) {
		if (!Float.isFinite(value) || value < 0 || value > 1)
			throw new IllegalArgumentException(
					"Color component must resolve between 0 and 1: " + source);
		return value;
	}

	static float parseFloat(String text, String key) {
		String value = text.trim();
		if (hasFloatSuffix(value))
			value = value.substring(0, value.length() - 1);
		try {
			float result = Float.parseFloat(value);
			if (!Float.isFinite(result))
				throw new NumberFormatException("not finite");
			return result;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(
					"Invalid numeric theme value for " + key + ": " + text, error);
		}
	}
}
