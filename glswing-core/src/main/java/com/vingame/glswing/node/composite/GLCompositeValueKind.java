package com.vingame.glswing.node.composite;

import com.vingame.glswing.node.model.GLNodeValue;

/** Portable storage shape of an exposed composite property. */
public enum GLCompositeValueKind {
	BOOLEAN, INTEGER, DECIMAL, TEXT, LIST, MAP;

	public boolean accepts(GLNodeValue value) {
		if (value == null || value == GLNodeValue.NullValue.INSTANCE) return false;
		return switch (this) {
			case BOOLEAN -> value instanceof GLNodeValue.BooleanValue;
			case INTEGER -> value instanceof GLNodeValue.IntegerValue;
			case DECIMAL -> value instanceof GLNodeValue.DecimalValue
					|| value instanceof GLNodeValue.IntegerValue;
			case TEXT -> value instanceof GLNodeValue.TextValue;
			case LIST -> value instanceof GLNodeValue.ListValue;
			case MAP -> value instanceof GLNodeValue.MapValue;
		};
	}
}
