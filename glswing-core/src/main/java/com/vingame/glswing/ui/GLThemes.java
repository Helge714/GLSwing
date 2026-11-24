package com.vingame.glswing.ui;

/** Bundled external theme presets. */
public final class GLThemes {
	public static final GLTheme DEFAULT = GLTheme.DEFAULT;
	public static final GLTheme BLUE = GLThemeLoader.loadResource(
			"/com/vingame/glswing/themes/blue.gltheme");
	public static final GLTheme DARK_BLUE = GLThemeLoader.loadResource(
			"/com/vingame/glswing/themes/darkblue.gltheme");
	public static final GLTheme LIGHT = GLThemeLoader.loadResource(
			"/com/vingame/glswing/themes/light.gltheme");

	private GLThemes() {
	}
}
