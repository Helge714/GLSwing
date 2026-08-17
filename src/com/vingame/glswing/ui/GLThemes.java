package com.vingame.glswing.ui;

/** Bundled external theme presets. */
public final class GLThemes {
	public static final GLTheme DEFAULT = GLTheme.DEFAULT;
	public static final GLTheme DARK = GLThemeLoader.loadResource(
			"/com/vingame/glswing/themes/dark.gltheme");
	public static final GLTheme LIGHT = GLThemeLoader.loadResource(
			"/com/vingame/glswing/themes/light.gltheme");

	private GLThemes() {
	}
}
