package com.vingame.glswing.node.composite;

import com.vingame.glswing.node.model.GLNodeValue;

/** Typed public property, default value, and optional inclusive numeric range. */
public record GLCompositePropertyDescriptor(String id, String valueTypeId,
		GLCompositeValueKind valueKind, GLNodeValue defaultValue,
		Double minimum, Double maximum) {
	public GLCompositePropertyDescriptor {
		if (!validId(id) || !validId(valueTypeId) || valueKind == null
				|| !valueKind.accepts(defaultValue)
				|| minimum != null && !Double.isFinite(minimum)
				|| maximum != null && !Double.isFinite(maximum)
				|| minimum != null && maximum != null && minimum > maximum)
			throw new IllegalArgumentException("Invalid composite property descriptor");
		if ((minimum != null || maximum != null)
				&& valueKind != GLCompositeValueKind.INTEGER
				&& valueKind != GLCompositeValueKind.DECIMAL)
			throw new IllegalArgumentException("Only numeric properties accept ranges");
		if (!accepts(valueKind, defaultValue, minimum, maximum))
			throw new IllegalArgumentException("Default value is outside the property range");
	}
	public GLCompositePropertyDescriptor(String id, String valueTypeId,
			GLCompositeValueKind kind, GLNodeValue defaultValue) {
		this(id, valueTypeId, kind, defaultValue, null, null);
	}
	public boolean accepts(GLNodeValue value) {
		return accepts(valueKind, value, minimum, maximum);
	}
	private static boolean accepts(GLCompositeValueKind kind, GLNodeValue value,
			Double minimum, Double maximum) {
		if (!kind.accepts(value)) return false;
		if (minimum == null && maximum == null) return true;
		double number = value instanceof GLNodeValue.IntegerValue integer
				? integer.value() : ((GLNodeValue.DecimalValue) value).value();
		return (minimum == null || number >= minimum)
				&& (maximum == null || number <= maximum);
	}
	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
