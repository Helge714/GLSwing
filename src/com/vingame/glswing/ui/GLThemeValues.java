package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

	/**
	 * Parses hex, RGB(A), and derived colors. A {@code derive} expression can
	 * set ({@code =}), scale ({@code *=}), add ({@code +=}), or subtract
	 * ({@code -=}) the alpha, hue, saturation, and value/brightness channels.
	 * Hue accepts degrees via {@code deg}; unit channels accept percentages,
	 * normalized {@code f} values, or 0..255 integers.
	 */
	public static Color parseColor(String text) {
		if (text == null)
			throw new IllegalArgumentException("Color text must not be null");
		String value = text.trim();
		if (value.startsWith("#"))
			return parseHex(value);
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.startsWith("derive(") && value.endsWith(")"))
			return parseDerived(value.substring(7, value.length() - 1));
		if (lower.startsWith("rgba(") && value.endsWith(")"))
			return parseFunctional(value.substring(5, value.length() - 1), true);
		if (lower.startsWith("rgb(") && value.endsWith(")"))
			return parseFunctional(value.substring(4, value.length() - 1), false);
		throw new IllegalArgumentException("Unsupported theme color: " + text);
	}

	private static Color parseDerived(String body) {
		List<String> parts = splitTopLevel(body);
		if (parts.size() < 2)
			throw new IllegalArgumentException(
					"derive requires a base color and at least one override");
		Color base = parseColor(parts.get(0));
		float[] hsv = Color.RGBtoHSB(base.getRed(), base.getGreen(),
				base.getBlue(), null);
		float alpha = base.getAlpha() / 255f;
		boolean[] changed = new boolean[DeriveChannel.values().length];
		for (int index = 1; index < parts.size(); index++) {
			DeriveOverride override = parseDeriveOverride(parts.get(index));
			DeriveChannel channel = override.channel();
			if (changed[channel.ordinal()])
				throw new IllegalArgumentException("derive " + channel.key
						+ " may only be specified once");
			changed[channel.ordinal()] = true;
			float current = channel == DeriveChannel.ALPHA ? alpha
					: hsv[channel.hsvIndex];
			float changedValue = applyDerive(current, override);
			if (channel == DeriveChannel.HUE)
				hsv[0] = wrapUnit(changedValue);
			else if (channel == DeriveChannel.ALPHA)
				alpha = clampUnit(changedValue);
			else
				hsv[channel.hsvIndex] = clampUnit(changedValue);
		}
		int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
		return new Color((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff,
				Math.round(alpha * 255));
	}

	private static DeriveOverride parseDeriveOverride(String source) {
		String part = source.trim();
		int separator = part.indexOf('=');
		if (separator <= 0 || separator == part.length() - 1)
			throw new IllegalArgumentException(
					"Invalid derive override: " + part);
		String left = part.substring(0, separator).trim();
		DeriveOperation operation = DeriveOperation.SET;
		if (!left.isEmpty()) {
			operation = switch (left.charAt(left.length() - 1)) {
			case '*' -> DeriveOperation.MULTIPLY;
			case '+' -> DeriveOperation.ADD;
			case '-' -> DeriveOperation.SUBTRACT;
			default -> DeriveOperation.SET;
			};
			if (operation != DeriveOperation.SET)
				left = left.substring(0, left.length() - 1).trim();
		}
		DeriveChannel channel = DeriveChannel.forName(left);
		String amount = part.substring(separator + 1).trim();
		return new DeriveOverride(channel, operation, amount);
	}

	private static float applyDerive(float current, DeriveOverride override) {
		float amount = switch (override.operation()) {
		case MULTIPLY -> multiplier(override.amount());
		case SET, ADD, SUBTRACT -> override.channel() == DeriveChannel.HUE
				? hueAmount(override.amount())
				: unitAmount(override.amount(), override.operation() == DeriveOperation.SET);
		};
		return switch (override.operation()) {
		case SET -> amount;
		case MULTIPLY -> current * amount;
		case ADD -> current + amount;
		case SUBTRACT -> current - amount;
		};
	}

	private static float hueAmount(String text) {
		String value = text.trim();
		String lower = value.toLowerCase(Locale.ROOT);
		try {
			if (lower.endsWith("deg"))
				return finite(Float.parseFloat(
						value.substring(0, value.length() - 3)) / 360f, text);
			if (value.endsWith("%"))
				return finite(Float.parseFloat(
						value.substring(0, value.length() - 1)) / 100f, text);
			if (hasFloatSuffix(value))
				return finite(Float.parseFloat(
						value.substring(0, value.length() - 1)), text);
			float result = Float.parseFloat(value);
			return finite(Math.abs(result) > 1 ? result / 360f : result, text);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid hue amount: " + text, error);
		}
	}

	private static float unitAmount(String text, boolean requireUnitRange) {
		String value = text.trim();
		try {
			float result;
			if (value.endsWith("%"))
				result = Float.parseFloat(
						value.substring(0, value.length() - 1)) / 100f;
			else if (hasFloatSuffix(value))
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else {
				result = Float.parseFloat(value);
				if (Math.abs(result) > 1)
					result /= 255f;
			}
			result = finite(result, text);
			return requireUnitRange ? unit(result, text) : result;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(
					"Invalid derive channel amount: " + text, error);
		}
	}

	private static float multiplier(String text) {
		String value = text.trim();
		try {
			float result;
			if (value.endsWith("%"))
				result = Float.parseFloat(
						value.substring(0, value.length() - 1)) / 100f;
			else if (hasFloatSuffix(value))
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else
				result = Float.parseFloat(value);
			result = finite(result, text);
			if (result < 0)
				throw new IllegalArgumentException(
						"derive multiplier must not be negative: " + text);
			return result;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(
					"Invalid derive multiplier: " + text, error);
		}
	}

	private static float finite(float value, String source) {
		if (!Float.isFinite(value))
			throw new IllegalArgumentException(
					"Color amount must be finite: " + source);
		return value;
	}

	private static float clampUnit(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private static float wrapUnit(float value) {
		float wrapped = value % 1;
		return wrapped < 0 ? wrapped + 1 : wrapped;
	}

	private enum DeriveChannel {
		HUE("hue", 0), SATURATION("saturation", 1), VALUE("value", 2),
		ALPHA("alpha", -1);

		private final String key;
		private final int hsvIndex;

		DeriveChannel(String key, int hsvIndex) {
			this.key = key;
			this.hsvIndex = hsvIndex;
		}

		private static DeriveChannel forName(String name) {
			return switch (name.toLowerCase(Locale.ROOT)) {
			case "h", "hue" -> HUE;
			case "s", "saturation" -> SATURATION;
			case "v", "value", "brightness" -> VALUE;
			case "a", "alpha" -> ALPHA;
			default -> throw new IllegalArgumentException(
					"Unsupported derive channel: " + name);
			};
		}
	}

	private enum DeriveOperation {
		SET, MULTIPLY, ADD, SUBTRACT
	}

	private record DeriveOverride(DeriveChannel channel,
			DeriveOperation operation, String amount) {
	}

	private static List<String> splitTopLevel(String value) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '(')
				depth++;
			else if (character == ')') {
				if (--depth < 0)
					throw new IllegalArgumentException(
							"Unbalanced parentheses in derive color: " + value);
			} else if (character == ',' && depth == 0) {
				parts.add(value.substring(start, index).trim());
				start = index + 1;
			}
		}
		if (depth != 0)
			throw new IllegalArgumentException(
					"Unbalanced parentheses in derive color: " + value);
		parts.add(value.substring(start).trim());
		return parts;
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
