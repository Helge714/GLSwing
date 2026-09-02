package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.ui.GLCodeDiagnostic.Severity;

/**
 * Immutable schema for a UTF-8 {@code key=value} configuration language.
 * Unknown keys can remain valid so applications may extend a library format.
 */
public final class GLCodePropertySchema {
	public enum ValueType {
		TEXT, NUMBER, BOOLEAN, COLOR, INSETS, ENUM, PATH
	}

	/** Description and value contract for one known property. */
	public record Property(String key, ValueType type, String description,
			List<String> choices, String defaultValue) {
		public Property {
			if (key == null || key.isBlank() || type == null || description == null
					|| description.isBlank() || choices == null)
				throw new IllegalArgumentException("Invalid configuration property schema");
			choices = List.copyOf(choices);
			if (type != ValueType.ENUM && !choices.isEmpty())
				throw new IllegalArgumentException("Only enum properties may declare choices");
		}
	}

	/** A schema-level issue associated with one property declaration. */
	public record ValidationIssue(String key, Severity severity, String message) {
		public ValidationIssue {
			if (key == null || key.isBlank() || severity == null || message == null
					|| message.isBlank())
				throw new IllegalArgumentException("Invalid schema validation issue");
		}
	}

	@FunctionalInterface
	public interface Validator {
		List<ValidationIssue> validate(Map<String, String> values);
	}

	private final String id;
	private final Map<String, Property> properties;
	private final boolean unknownKeysAllowed;
	private final List<Validator> validators;

	private GLCodePropertySchema(String id, Map<String, Property> properties,
			boolean unknownKeysAllowed, List<Validator> validators) {
		this.id = id;
		this.properties = Map.copyOf(properties);
		this.unknownKeysAllowed = unknownKeysAllowed;
		this.validators = List.copyOf(validators);
	}

	public String id() { return id; }
	public Map<String, Property> properties() { return properties; }
	/**
	 * Resolves an exact property or the longest registered prefix property.
	 * Prefix properties use a trailing {@code *}, for example {@code key.*}.
	 */
	public Property property(String key) {
		Property exact = properties.get(key);
		if (exact != null || key == null) return exact;
		Property result = null;
		int matched = -1;
		for (Property property : properties.values()) {
			String pattern = property.key();
			if (!pattern.endsWith("*")) continue;
			String prefix = pattern.substring(0, pattern.length() - 1);
			if (key.length() > prefix.length() && key.startsWith(prefix)
					&& prefix.length() > matched) {
				result = property;
				matched = prefix.length();
			}
		}
		return result;
	}
	public boolean unknownKeysAllowed() { return unknownKeysAllowed; }
	List<Validator> validators() { return validators; }

	public static Builder builder(String id) { return new Builder(id); }

	/** Mutable builder used while defining a configuration format. */
	public static final class Builder {
		private final String id;
		private final LinkedHashMap<String, Property> properties = new LinkedHashMap<>();
		private boolean unknownKeysAllowed;
		private final List<Validator> validators = new ArrayList<>();

		private Builder(String id) {
			if (id == null || id.isBlank())
				throw new IllegalArgumentException("Schema ID must not be blank");
			this.id = id;
		}

		public Builder allowUnknownKeys(boolean allowed) {
			unknownKeysAllowed = allowed;
			return this;
		}

		public Builder validator(Validator validator) {
			if (validator == null) throw new IllegalArgumentException("Validator is required");
			validators.add(validator);
			return this;
		}

		public Builder property(String key, ValueType type, String description) {
			return property(key, type, description, List.of(), null);
		}

		/** Defines a contract for every non-empty key beginning with a prefix. */
		public Builder prefixProperty(String prefix, ValueType type,
				String description) {
			if (prefix == null || prefix.isBlank() || prefix.endsWith("*"))
				throw new IllegalArgumentException("Property prefix must not be blank");
			return property(prefix + "*", type, description);
		}

		public Builder property(String key, ValueType type, String description,
				String defaultValue) {
			return property(key, type, description, List.of(), defaultValue);
		}

		public Builder enumProperty(String key, String description,
				List<String> choices, String defaultValue) {
			if (choices == null || choices.isEmpty())
				throw new IllegalArgumentException("Enum choices must not be empty");
			return property(key, ValueType.ENUM, description,
					new ArrayList<>(choices), defaultValue);
		}

		private Builder property(String key, ValueType type, String description,
				List<String> choices, String defaultValue) {
			Property property = new Property(key, type, description, choices, defaultValue);
			if (properties.putIfAbsent(key, property) != null)
				throw new IllegalArgumentException("Duplicate schema property " + key);
			return this;
		}

		public GLCodePropertySchema build() {
			return new GLCodePropertySchema(id, properties, unknownKeysAllowed, validators);
		}
	}
}
