package com.vingame.glswing.input;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.ui.GLCodeDiagnostic;
import com.vingame.glswing.ui.GLCodeLanguage;
import com.vingame.glswing.ui.GLCodePropertySchema;
import com.vingame.glswing.ui.GLCodePropertySchema.ValidationIssue;
import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;
import com.vingame.glswing.ui.GLPropertiesCodeLanguage;

/** Built-in schema and in-process editor intelligence for {@code .glinput}. */
public final class GLInputCodeSupport {
	private static final GLCodePropertySchema SCHEMA = createSchema();
	private static final GLPropertiesCodeLanguage LANGUAGE =
			new GLPropertiesCodeLanguage(SCHEMA);

	private GLInputCodeSupport() { }

	/** Returns the reusable schema for GLSwing input-map files. */
	public static GLCodePropertySchema schema() { return SCHEMA; }
	/** Returns the reusable built-in {@code .glinput} language. */
	public static GLCodeLanguage language() { return LANGUAGE; }

	private static GLCodePropertySchema createSchema() {
		GLCodePropertySchema.Builder builder = GLCodePropertySchema.builder("glinput")
				.allowUnknownKeys(true)
				.property("input.extends", ValueType.PATH,
						"Parent input map; use default or a sibling .glinput file.",
						"default");
		GLInputMap defaults = GLInputMaps.DEFAULT;
		defaults.keyBindings().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> builder.property("key." + entry.getKey(), ValueType.TEXT,
						"Keyboard alternatives for semantic action '" + entry.getKey() + "'.",
						bindings(entry.getValue())));
		defaults.modifierBindings().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> builder.property("modifier." + entry.getKey(), ValueType.TEXT,
						"Required pointer or mnemonic modifiers for '" + entry.getKey() + "'.",
						GLKeyBinding.modifiersToConfigString(entry.getValue())));
		defaults.getValues().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> builder.property("value." + entry.getKey(), ValueType.NUMBER,
						"Positive behavior preference '" + entry.getKey() + "'.",
						Double.toString(entry.getValue())));
		builder.prefixProperty("key.", ValueType.TEXT,
				"Keyboard alternatives for an application-defined semantic action.")
				.prefixProperty("modifier.", ValueType.TEXT,
						"Required modifiers for an application-defined interaction.")
				.prefixProperty("value.", ValueType.NUMBER,
						"Positive numeric application preference.")
				.validator(GLInputCodeSupport::validateEntries);
		return builder.build();
	}

	private static String bindings(List<GLKeyBinding> bindings) {
		return bindings.stream().map(GLKeyBinding::toConfigString)
				.reduce((left, right) -> left + ", " + right).orElse("");
	}

	private static List<ValidationIssue> validateEntries(Map<String, String> values) {
		Map<String, String> entry = new LinkedHashMap<>();
		List<ValidationIssue> issues = new ArrayList<>();
		for (Map.Entry<String, String> candidate : values.entrySet()) {
			if ("input.extends".equals(candidate.getKey())) continue;
			entry.clear();
			entry.put(candidate.getKey(), candidate.getValue());
			try {
				GLInputMapLoader.build(empty(), entry, "editor");
			} catch (RuntimeException failure) {
				issues.add(new ValidationIssue(candidate.getKey(),
						GLCodeDiagnostic.Severity.ERROR, message(failure)));
			}
		}
		return List.copyOf(issues);
	}

	private static GLInputMap empty() {
		return new GLInputMap(Map.of(), Map.of(), Map.of());
	}

	private static String message(RuntimeException failure) {
		Throwable cause = failure.getCause();
		String message = cause == null ? failure.getMessage() : cause.getMessage();
		return message == null || message.isBlank() ? "Invalid input-map value" : message;
	}
}
