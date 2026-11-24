package com.vingame.glswing.ui;

/** Controls how the theme loader handles properties absent from its schema. */
public enum GLThemeUnknownPropertyPolicy {
	/** Preserve the value and report a warning. */
	WARNING,
	/** Reject the theme. */
	ERROR,
	/** Preserve the value without reporting it. */
	ALLOW
}
