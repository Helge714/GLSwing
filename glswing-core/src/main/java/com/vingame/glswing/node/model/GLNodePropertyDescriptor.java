package com.vingame.glswing.node.model;

/** Explicit, application-independent metadata for an editable node property. */
public sealed interface GLNodePropertyDescriptor permits
		GLNumberNodeProperty, GLEnumNodeProperty, GLBooleanNodeProperty, GLCustomNodeProperty {
	String id();
	String displayName();
}
