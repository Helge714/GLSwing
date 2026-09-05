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
	 * Parses hex, RGB, HSV, HSL, OKLab, OKLCH, and derived colors. Functional
	 * colors accept an optional final alpha channel; {@code rgba} remains an
	 * alias for the four-channel RGB form. A {@code derive} expression can
	 * set ({@code =}), scale ({@code *=}), add ({@code +=}), or subtract
	 * ({@code -=}) the alpha, hue, saturation, and value/brightness channels.
	 * Bounded functional channels independently accept 0..255 integers,
	 * normalized decimal or {@code f}-suffixed values, and percentages. Hue
	 * accepts degrees via {@code deg}; signed OKLab axes and OKLCH chroma remain
	 * decimal quantities. Derived unit channels use the same percentage,
	 * normalized-float, and integer conventions.
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
			return parseRgb(value.substring(5, value.length() - 1), true);
		if (lower.startsWith("rgb(") && value.endsWith(")"))
			return parseRgb(value.substring(4, value.length() - 1), false);
		if (lower.startsWith("hsv(") && value.endsWith(")"))
			return parseHsv(value.substring(4, value.length() - 1));
		if (lower.startsWith("hsl(") && value.endsWith(")"))
			return parseHsl(value.substring(4, value.length() - 1));
		if (lower.startsWith("oklab(") && value.endsWith(")"))
			return parseOklab(value.substring(6, value.length() - 1));
		if (lower.startsWith("oklch(") && value.endsWith(")"))
			return parseOklch(value.substring(6, value.length() - 1));
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
			else if (value.indexOf('.') >= 0)
				result = Float.parseFloat(value);
			else
				result = Integer.parseInt(value) / 255f;
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

	private static Color parseRgb(String body, boolean rgbaAlias) {
		String[] parts = functionalParts(body, rgbaAlias ? "rgba" : "rgb",
				rgbaAlias ? 4 : 3, 4);
		float red = functionalColorComponent(parts[0]);
		float green = functionalColorComponent(parts[1]);
		float blue = functionalColorComponent(parts[2]);
		float alpha = parts.length == 4 ? functionalColorComponent(parts[3]) : 1;
		return new Color(red, green, blue, alpha);
	}

	private static Color parseHsv(String body) {
		String[] parts = functionalParts(body, "hsv", 3, 4);
		float hue = hueComponent(parts[0]);
		float saturation = polarUnitComponent(parts[1], "HSV saturation");
		float value = polarUnitComponent(parts[2], "HSV value");
		float alpha = parts.length == 4 ? functionalColorComponent(parts[3]) : 1;
		int rgb = Color.HSBtoRGB(hue, saturation, value);
		return new Color((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff,
				Math.round(alpha * 255));
	}

	private static Color parseHsl(String body) {
		String[] parts = functionalParts(body, "hsl", 3, 4);
		float hue = hueComponent(parts[0]);
		float saturation = polarUnitComponent(parts[1], "HSL saturation");
		float lightness = polarUnitComponent(parts[2], "HSL lightness");
		float alpha = parts.length == 4 ? functionalColorComponent(parts[3]) : 1;
		float amplitude = saturation * Math.min(lightness, 1 - lightness);
		float red = hslComponent(hue, lightness, amplitude, 0);
		float green = hslComponent(hue, lightness, amplitude, 8);
		float blue = hslComponent(hue, lightness, amplitude, 4);
		return new Color(clampUnit(red), clampUnit(green), clampUnit(blue), alpha);
	}

	private static float hslComponent(float hue, float lightness,
			float amplitude, float offset) {
		float position = (offset + hue * 12) % 12;
		return lightness - amplitude * Math.max(-1,
				Math.min(Math.min(position - 3, 9 - position), 1));
	}

	private static Color parseOklab(String body) {
		String[] parts = functionalParts(body, "oklab", 3, 4);
		float lightness = polarUnitComponent(parts[0], "OKLab lightness");
		float axisA = signedDecimalComponent(parts[1], "OKLab a");
		float axisB = signedDecimalComponent(parts[2], "OKLab b");
		float alpha = parts.length == 4 ? functionalColorComponent(parts[3]) : 1;
		return oklabToSrgb(lightness, axisA, axisB, alpha);
	}

	private static Color parseOklch(String body) {
		String[] parts = functionalParts(body, "oklch", 3, 4);
		float lightness = polarUnitComponent(parts[0], "OKLCH lightness");
		float chroma = signedDecimalComponent(parts[1], "OKLCH chroma");
		if (chroma < 0)
			throw new IllegalArgumentException("OKLCH chroma must not be negative: "
					+ parts[1]);
		float hue = hueComponent(parts[2]);
		float alpha = parts.length == 4 ? functionalColorComponent(parts[3]) : 1;
		double angle = hue * Math.PI * 2;
		return oklabToSrgb(lightness, chroma * (float) Math.cos(angle),
				chroma * (float) Math.sin(angle), alpha);
	}

	private static String[] functionalParts(String body, String function,
			int minimum, int maximum) {
		String[] parts = body.split(",", -1);
		if (parts.length < minimum || parts.length > maximum)
			throw new IllegalArgumentException(function + " requires " + minimum
					+ (minimum == maximum ? "" : " or " + maximum) + " components");
		for (String part : parts)
			if (part.isBlank())
				throw new IllegalArgumentException(
						function + " components must not be empty");
		return parts;
	}

	private static float hueComponent(String text) {
		String value = text.trim();
		String lower = value.toLowerCase(Locale.ROOT);
		try {
			float turns;
			if (lower.endsWith("deg"))
				turns = Float.parseFloat(value.substring(0, value.length() - 3)) / 360f;
			else if (value.endsWith("%"))
				turns = Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
			else if (hasFloatSuffix(value))
				turns = Float.parseFloat(value.substring(0, value.length() - 1));
			else if (value.indexOf('.') >= 0)
				turns = Float.parseFloat(value);
			else
				turns = Integer.parseInt(value) / 360f;
			return wrapUnit(finite(turns, text));
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid hue component: " + text, error);
		}
	}

	private static float polarUnitComponent(String text, String channel) {
		String value = text.trim();
		try {
			float result;
			if (value.endsWith("%"))
				result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
			else if (hasFloatSuffix(value))
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else if (value.indexOf('.') >= 0)
				result = Float.parseFloat(value);
			else
				result = Integer.parseInt(value) / 255f;
			return unit(result, text);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException(channel
					+ " must be a percentage or normalized decimal/f-suffixed value: "
					+ text, error);
		}
	}

	private static float signedDecimalComponent(String text, String channel) {
		String value = text.trim();
		if (hasFloatSuffix(value)) value = value.substring(0, value.length() - 1);
		try {
			return finite(Float.parseFloat(value), text);
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid " + channel + " component: "
					+ text, error);
		}
	}

	private static Color oklabToSrgb(float lightness, float axisA,
			float axisB, float alpha) {
		double lRoot = lightness + 0.3963377774 * axisA + 0.2158037573 * axisB;
		double mRoot = lightness - 0.1055613458 * axisA - 0.0638541728 * axisB;
		double sRoot = lightness - 0.0894841775 * axisA - 1.2914855480 * axisB;
		double l = lRoot * lRoot * lRoot;
		double m = mRoot * mRoot * mRoot;
		double s = sRoot * sRoot * sRoot;
		double red = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
		double green = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
		double blue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
		return new Color(linearToSrgb(red), linearToSrgb(green),
				linearToSrgb(blue), alpha);
	}

	private static float linearToSrgb(double component) {
		double encoded = component <= 0.0031308
				? 12.92 * component
				: 1.055 * Math.pow(component, 1 / 2.4) - 0.055;
		return clampUnit((float) encoded);
	}

	private static float functionalColorComponent(String text) {
		String value = text.trim();
		float result;
		try {
			if (value.endsWith("%"))
				result = Float.parseFloat(value.substring(0, value.length() - 1)) / 100;
			else if (hasFloatSuffix(value))
				result = Float.parseFloat(value.substring(0, value.length() - 1));
			else if (value.indexOf('.') >= 0)
				result = Float.parseFloat(value);
			else
				result = Integer.parseInt(value) / 255f;
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid color component '" + text
					+ "'; expected a 0..255 integer, 0..1 decimal/f-suffixed value, or percentage",
					error);
		}
		return unit(result, text);
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
