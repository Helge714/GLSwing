package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.ui.GLCodePropertySchema.ValidationIssue;

/** Built-in schema and in-process editor intelligence for {@code .gltheme} files. */
public final class GLThemeCodeSupport {
	private static final GLCodePropertySchema SCHEMA = decorate(
			GLThemeSchemaLoader.builtIn(), true);
	private static final GLPropertiesCodeLanguage LANGUAGE =
			new GLPropertiesCodeLanguage(SCHEMA, document -> decorate(
					GLThemeSchemaLoader.forDocument(document.uri(), document.text()), false));

	private GLThemeCodeSupport() { }

	/** Returns the schema used to validate and describe GLSwing theme properties. */
	public static GLCodePropertySchema schema() { return SCHEMA; }
	/** Returns the reusable built-in {@code .gltheme} language implementation. */
	public static GLCodeLanguage language() { return LANGUAGE; }

	private static GLCodePropertySchema decorate(GLCodePropertySchema source,
			boolean allowUnknown) {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("gltheme")
				.allowUnknownKeys(allowUnknown);
		Map<String, String> defaults = GLTheme.DEFAULT.values().asMap();
		for (GLCodePropertySchema.Property property : source.properties().values()) {
			String defaultValue = property.defaultValue() != null ? property.defaultValue()
					: defaults.get(property.key());
			builder.property(new GLCodePropertySchema.Property(property.key(),
					property.type(), property.description(), property.choices(), defaultValue,
					property.minimum(), property.maximum(), property.integer(),
					property.category()));
		}
		builder.validator(values -> validateTheme(source, values));
		return builder.build();
	}

	private static List<ValidationIssue> validateTheme(GLCodePropertySchema schema,
			Map<String, String> values) {
		List<ValidationIssue> result = new ArrayList<>();
		String type = values.getOrDefault("theme.type", "legacy").trim()
				.toLowerCase(java.util.Locale.ROOT);
		if (!"bundle".equals(type)) {
			if (values.containsKey("theme.colors")) result.add(issue("theme.colors",
					"theme.colors requires theme.type=bundle"));
			if (values.containsKey("theme.metrics")) result.add(issue("theme.metrics",
					"theme.metrics requires theme.type=bundle"));
			if (values.containsKey("theme.schema")) result.add(issue("theme.schema",
					"theme.schema requires theme.type=bundle"));
		}
		if ("colors".equals(type) || "metrics".equals(type)) {
			for (String directive : List.of("theme.extends", "theme.colors",
					"theme.metrics", "theme.schema"))
				if (values.containsKey(directive)) result.add(issue(directive,
						"Theme fragments cannot inherit or import other themes"));
			for (String key : values.keySet()) {
				GLCodePropertySchema.Property property = schema.property(key);
				if (property == null || "meta".equals(property.category())) continue;
				if ("colors".equals(type) && !"colors".equals(property.category()))
					result.add(issue(key, key + " is not permitted in a color theme"));
				else if ("metrics".equals(type) && !"metrics".equals(property.category()))
					result.add(issue(key, key + " is not permitted in a metrics theme"));
			}
		}
		return List.copyOf(result);
	}

	private static ValidationIssue issue(String key, String message) {
		return new ValidationIssue(key, GLCodeDiagnostic.Severity.ERROR, message);
	}

}
