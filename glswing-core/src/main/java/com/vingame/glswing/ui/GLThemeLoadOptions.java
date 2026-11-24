package com.vingame.glswing.ui;

import java.util.function.Consumer;

/** Immutable validation options for loading external themes. */
public record GLThemeLoadOptions(GLThemeUnknownPropertyPolicy unknownProperties,
		Consumer<GLThemeLoadWarning> warningHandler) {
	/** Default behavior preserves unknown values and emits warnings. */
	public static final GLThemeLoadOptions DEFAULT = new GLThemeLoadOptions(
			GLThemeUnknownPropertyPolicy.WARNING, warning -> System.getLogger(
					GLThemeLoader.class.getName()).log(System.Logger.Level.WARNING,
					warning.message()));

	public GLThemeLoadOptions {
		if (unknownProperties == null || warningHandler == null)
			throw new IllegalArgumentException("Theme-load options must not be null");
	}

	/** Creates options with the selected policy and a silent warning sink. */
	public static GLThemeLoadOptions of(GLThemeUnknownPropertyPolicy policy) {
		return new GLThemeLoadOptions(policy, warning -> { });
	}
}
