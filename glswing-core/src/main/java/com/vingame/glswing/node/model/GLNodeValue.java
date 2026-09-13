package com.vingame.glswing.node.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable immutable property value used by graph documents and backends.
 * Application objects intentionally require an explicit external codec.
 */
public sealed interface GLNodeValue permits GLNodeValue.NullValue,
		GLNodeValue.BooleanValue, GLNodeValue.IntegerValue,
		GLNodeValue.DecimalValue, GLNodeValue.TextValue,
		GLNodeValue.ListValue, GLNodeValue.MapValue {

	enum NullValue implements GLNodeValue { INSTANCE }
	record BooleanValue(boolean value) implements GLNodeValue { }
	record IntegerValue(long value) implements GLNodeValue { }
	record DecimalValue(double value) implements GLNodeValue {
		public DecimalValue {
			if (!Double.isFinite(value))
				throw new IllegalArgumentException("Decimal value must be finite");
		}
	}
	record TextValue(String value) implements GLNodeValue {
		public TextValue {
			if (value == null) throw new IllegalArgumentException("Text required");
		}
	}
	record ListValue(List<GLNodeValue> values) implements GLNodeValue {
		public ListValue {
			if (values == null || values.stream().anyMatch(java.util.Objects::isNull))
				throw new IllegalArgumentException("List values must not be null");
			values = List.copyOf(values);
		}
	}
	record MapValue(Map<String, GLNodeValue> values) implements GLNodeValue {
		public MapValue {
			if (values == null) throw new IllegalArgumentException("Map required");
			LinkedHashMap<String, GLNodeValue> copy = new LinkedHashMap<>();
			values.forEach((key, value) -> {
				if (key == null || key.isBlank() || value == null)
					throw new IllegalArgumentException(
							"Map keys and values must not be blank or null");
				copy.put(key, value);
			});
			values = Collections.unmodifiableMap(copy);
		}
	}

	/** Converts JSON-like Java values into the portable graph representation. */
	static GLNodeValue of(Object value) {
		if (value == null) return NullValue.INSTANCE;
		if (value instanceof GLNodeValue nodeValue) return nodeValue;
		if (value instanceof Boolean booleanValue)
			return new BooleanValue(booleanValue);
		if (value instanceof Byte || value instanceof Short
				|| value instanceof Integer || value instanceof Long)
			return new IntegerValue(((Number) value).longValue());
		if (value instanceof Float || value instanceof Double) {
			Number number = (Number) value;
			return new DecimalValue(number.doubleValue());
		}
		if (value instanceof Character character)
			return new TextValue(character.toString());
		if (value instanceof CharSequence text)
			return new TextValue(text.toString());
		if (value instanceof List<?> list) {
			List<GLNodeValue> copy = new ArrayList<>(list.size());
			for (Object element : list) copy.add(of(element));
			return new ListValue(copy);
		}
		if (value instanceof Map<?, ?> map) {
			LinkedHashMap<String, GLNodeValue> copy = new LinkedHashMap<>();
			map.forEach((key, element) -> {
				if (!(key instanceof String stringKey))
					throw new IllegalArgumentException("Graph map keys must be strings");
				copy.put(stringKey, of(element));
			});
			return new MapValue(copy);
		}
		throw new IllegalArgumentException(
				"Unsupported graph property value: " + value.getClass().getName());
	}

	/** Returns a detached graph of Java primitives, lists, and maps. */
	default Object toJavaValue() {
		if (this == NullValue.INSTANCE) return null;
		if (this instanceof BooleanValue booleanValue) return booleanValue.value();
		if (this instanceof IntegerValue integerValue) return integerValue.value();
		if (this instanceof DecimalValue decimalValue) return decimalValue.value();
		if (this instanceof TextValue textValue) return textValue.value();
		if (this instanceof ListValue listValue)
			return listValue.values().stream().map(GLNodeValue::toJavaValue).toList();
		MapValue mapValue = (MapValue) this;
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		mapValue.values().forEach((key, value) ->
				result.put(key, value.toJavaValue()));
		return Collections.unmodifiableMap(result);
	}
}
