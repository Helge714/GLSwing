package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;
import com.vingame.glswing.ui.GLCodePropertySchema.ValidationIssue;

/** Built-in schema and in-process editor intelligence for {@code .gltheme} files. */
public final class GLThemeCodeSupport {
	private static final GLCodePropertySchema SCHEMA = createSchema();
	private static final GLPropertiesCodeLanguage LANGUAGE =
			new GLPropertiesCodeLanguage(SCHEMA);

	private GLThemeCodeSupport() { }

	/** Returns the schema used to validate and describe GLSwing theme properties. */
	public static GLCodePropertySchema schema() { return SCHEMA; }
	/** Returns the reusable built-in {@code .gltheme} language implementation. */
	public static GLCodeLanguage language() { return LANGUAGE; }

	private static GLCodePropertySchema createSchema() {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(true)
				.property("theme.name", ValueType.TEXT,
						"Human-readable theme name.", "Application theme")
				.property("theme.extends", ValueType.PATH,
						"Base preset or theme file inherited by this theme.", "default")
				.enumProperty("theme.type", "Role of this theme file.",
						List.of("colors", "metrics", "bundle"), null)
				.property("theme.colors", ValueType.PATH,
						"Color fragment imported by a bundle theme.")
				.property("theme.metrics", ValueType.PATH,
						"Metrics fragment imported by a bundle theme.")
				.validator(GLThemeCodeSupport::validateTheme);

		for (Map.Entry<String, String> entry : GLTheme.DEFAULT.values().asMap().entrySet()) {
			String key = entry.getKey();
			if (key.startsWith("theme.")) continue;
			String value = entry.getValue();
			ValueType type = inferType(key, value);
			String description = description(key, type);
			if (isFontStyle(key))
				builder.enumProperty(key, description,
						List.of("plain", "bold", "italic", "bolditalic",
								"bold_italic", "bold-italic"), value);
			else if (key.toLowerCase(Locale.ROOT).endsWith(".offsetspace"))
				builder.enumProperty(key, description, List.of("screen", "local"), value);
			else builder.property(key, type, description, value);
		}
		addOptionalProperties(builder, GLTheme.DEFAULT.values().asMap().keySet());
		return builder.build();
	}

	private static void addOptionalProperties(GLCodePropertySchema.Builder builder,
			Set<String> existing) {
		for (String key : List.of("button.background", "button.border", "button.disabled",
				"button.focus", "button.hover", "button.text", "button.textDisabled",
				"checkBox.background", "checkBox.border", "checkBox.check",
				"checkBox.checkDisabled", "checkBox.disabled", "checkBox.focus",
				"checkBox.hover", "checkBox.pressed", "checkBox.text",
				"checkBox.textDisabled", "comboBox.arrow", "comboBox.background",
				"comboBox.border", "comboBox.disabled", "comboBox.hover",
				"comboBox.pressed", "comboBox.text", "comboBox.textDisabled",
				"menu.accelerator", "menu.arrow", "menu.background", "menu.border",
				"menu.hover", "menu.pressed", "menu.separator", "menu.text",
				"menu.textDisabled", "menuBar.active", "menuBar.background",
				"menuBar.border", "menuBar.hover", "menuBar.pressed", "menuBar.text",
				"menuBar.textDisabled", "slider.background", "slider.border",
				"slider.disabled", "slider.text", "slider.textDisabled",
				"textEditor.background", "textEditor.border", "textEditor.caret",
				"textEditor.focus", "textEditor.invalid", "textEditor.selection",
				"textEditor.text", "textEditor.textDisabled"))
			addIfMissing(builder, existing, key, ValueType.COLOR);

		for (String prefix : List.of("font", "label.font", "button.font", "checkBox.font",
				"slider.font", "textEditor.font", "menu.font", "menuBar.font",
				"comboBox.font")) {
			addIfMissing(builder, existing, prefix + ".family", ValueType.TEXT);
			if (!existing.contains(prefix + ".style"))
				builder.enumProperty(prefix + ".style", description(prefix + ".style",
						ValueType.ENUM), List.of("plain", "bold", "italic", "bolditalic",
								"bold_italic", "bold-italic"), null);
			addIfMissing(builder, existing, prefix + ".size", ValueType.NUMBER);
			for (String source : List.of("source", "regular", "bold", "italic", "boldItalic"))
				addIfMissing(builder, existing, prefix + "." + source, ValueType.PATH);
		}
	}

	private static void addIfMissing(GLCodePropertySchema.Builder builder,
			Set<String> existing, String key, ValueType type) {
		if (!existing.contains(key)) builder.property(key, type, description(key, type));
	}

	private static List<ValidationIssue> validateTheme(Map<String, String> values) {
		List<ValidationIssue> result = new ArrayList<>();
		String type = values.getOrDefault("theme.type", "legacy").trim()
				.toLowerCase(Locale.ROOT);
		if (!"bundle".equals(type)) {
			if (values.containsKey("theme.colors")) result.add(issue("theme.colors",
					"theme.colors requires theme.type=bundle"));
			if (values.containsKey("theme.metrics")) result.add(issue("theme.metrics",
					"theme.metrics requires theme.type=bundle"));
		}
		if ("colors".equals(type) || "metrics".equals(type)) {
			for (String directive : List.of("theme.extends", "theme.colors", "theme.metrics"))
				if (values.containsKey(directive)) result.add(issue(directive,
						"Theme fragments cannot inherit or import other themes"));
			for (String key : values.keySet()) {
				if ("theme.type".equals(key)) continue;
				boolean color = isColorOrFontKey(key);
				if ("colors".equals(type) && !color)
					result.add(issue(key, key + " is not permitted in a color theme"));
				else if ("metrics".equals(type) && color)
					result.add(issue(key, key + " is not permitted in a metrics theme"));
			}
		}
		return List.copyOf(result);
	}

	private static ValidationIssue issue(String key, String message) {
		return new ValidationIssue(key, GLCodeDiagnostic.Severity.ERROR, message);
	}

	private static boolean isColorOrFontKey(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.startsWith("palette.") || lower.startsWith("state.")
				|| lower.startsWith("font.") || lower.contains(".font.")) return true;
		String leaf = lower.substring(lower.lastIndexOf('.') + 1);
		if (leaf.matches(".*(width|height|radius|padding|gap|size|length|distance|"
				+ "tolerance|factor|inset|offset|softness|thickness)$")) return false;
		return leaf.matches(".*(background|foreground|text|border|focus|selection|"
				+ "caret|invalid|separator|arrow|progress|track|thumb|shadow|accent|"
				+ "color|disabled|hover|pressed|normal|surface|active|port|fill|tab).*");
	}

	private static ValueType inferType(String key, String value) {
		String lower = key.toLowerCase(Locale.ROOT);
		if (isFontPath(lower)) return ValueType.PATH;
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
			return ValueType.BOOLEAN;
		if (isFontStyle(lower) || lower.endsWith(".offsetspace")) return ValueType.ENUM;
		if ((lower.endsWith(".padding") || lower.endsWith(".insets"))
				&& value.split(",", -1).length == 4) return ValueType.INSETS;
		try { GLThemeValues.parseColor(value); return ValueType.COLOR; }
		catch (RuntimeException ignored) { }
		try { Double.parseDouble(value); return ValueType.NUMBER; }
		catch (RuntimeException ignored) { }
		return ValueType.TEXT;
	}

	private static boolean isFontPath(String key) {
		int separator = key.lastIndexOf('.');
		if (separator < 0 || !key.substring(0, separator).endsWith("font")) return false;
		return switch (key.substring(separator + 1)) {
		case "source", "regular", "bold", "italic", "bolditalic" -> true;
		default -> false;
		};
	}

	private static boolean isFontStyle(String key) {
		return key.toLowerCase(Locale.ROOT).endsWith("font.style");
	}

	private static String description(String key, ValueType type) {
		String owner = key.substring(0, Math.max(0, key.lastIndexOf('.')))
				.replace('.', ' ');
		String subject = owner.isBlank() ? "theme" : owner;
		return switch (type) {
		case COLOR -> "Color used by " + subject + ".";
		case NUMBER -> "Numeric metric used by " + subject + ".";
		case BOOLEAN -> "Feature switch for " + subject + ".";
		case INSETS -> "Top, left, bottom, and right insets for " + subject + ".";
		case ENUM -> "Named option used by " + subject + ".";
		case PATH -> "External resource used by " + subject + ".";
		case TEXT -> "Text value used by " + subject + ".";
		};
	}
}
