package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.Font;

import com.vingame.glswing.layout.GLInsets;

/** Builds the Java style records from the authoritative default theme resource. */
final class GLThemeDefaults {
	static final String RESOURCE =
			"/com/vingame/glswing/themes/default.gltheme";
	private static final GLThemeValues VALUES =
			GLThemeLoader.loadRootValues(RESOURCE);

	private GLThemeDefaults() {
	}

	static GLThemeValues values() { return VALUES; }

	static GLLabelStyle labelStyle() {
		return new GLLabelStyle(font("label"), color("label.text", "palette.text"),
				insets("label.padding", "metrics.padding"));
	}

	static GLButtonStyle buttonStyle() {
		return new GLButtonStyle(font("button"),
				color("button.background", "state.normal"), color("button.hover", "state.hover"),
				color("button.pressedLight"), color("button.pressedDark"),
				color("button.disabled", "state.disabled"),
				color("button.text", "palette.text"), color("button.textDisabled", "palette.textDisabled"),
				color("button.border", "palette.border"), color("button.focus", "palette.accent"),
				number("button.cornerRadius", "metrics.cornerRadius"),
				number("button.borderWidth", "metrics.borderWidth"),
				number("button.height", "metrics.controlHeight"),
				insets("button.padding", "metrics.padding"));
	}

	static GLCheckBoxStyle checkBoxStyle() {
		return new GLCheckBoxStyle(font("checkBox"),
				color("checkBox.background", "state.normal"), color("checkBox.hover", "state.hover"),
				color("checkBox.pressed", "state.pressed"), color("checkBox.disabled", "state.disabled"),
				color("checkBox.check", "palette.text"), color("checkBox.checkDisabled", "palette.textDisabled"),
				color("checkBox.text", "palette.text"), color("checkBox.textDisabled", "palette.textDisabled"),
				color("checkBox.border", "palette.border"), color("checkBox.focus", "palette.accent"),
				number("checkBox.boxSize"), number("checkBox.gap"),
				number("checkBox.cornerRadius", "metrics.cornerRadius"),
				number("checkBox.borderWidth", "metrics.borderWidth"),
				number("checkBox.checkStrokeWidth"), insets("checkBox.padding", "metrics.padding"));
	}

	static GLSliderStyle sliderStyle() {
		return new GLSliderStyle(font("slider"),
				color("slider.background", "state.normal"), color("slider.hover", "state.hover"),
				color("slider.pressed", "state.pressed"), color("slider.disabled", "state.disabled"),
				color("slider.text", "palette.text"), color("slider.textDisabled", "palette.textDisabled"),
				color("slider.border", "palette.border"),
				color("slider.progressLight"), color("slider.progressDark"),
				color("slider.arrowBackground", "state.normal"),
				color("slider.arrowHover", "state.hover"), color("slider.arrowPressed", "state.pressed"),
				color("slider.arrowDisabled", "state.disabled"), color("slider.arrow", "palette.text"),
				color("slider.arrowTextDisabled", "palette.textDisabled"),
				number("slider.cornerRadius", "metrics.cornerRadius"),
				number("slider.borderWidth", "metrics.borderWidth"),
				number("slider.height", "metrics.controlHeight"), number("slider.preferredWidth"),
				number("slider.arrowButtonWidth"), number("slider.arrowInset"),
				number("slider.arrowStrokeWidth"), insets("slider.padding", "metrics.padding"));
	}

	static GLTextEditorStyle textEditorStyle() {
		return new GLTextEditorStyle(font("textEditor"),
				color("textEditor.background", "state.normal"), color("textEditor.text", "palette.text"),
				color("textEditor.textDisabled", "palette.textDisabled"),
				color("textEditor.selection", "palette.selection", "palette.accent"),
				color("textEditor.caret", "palette.text"), color("textEditor.border", "palette.border"),
				color("textEditor.focus", "palette.accent"), color("textEditor.invalid", "palette.invalid"),
				number("textEditor.cornerRadius", "metrics.cornerRadius"),
				number("textEditor.borderWidth", "metrics.borderWidth"),
				number("textEditor.height", "metrics.controlHeight"), number("textEditor.preferredWidth"),
				number("textEditor.overflowFadeWidth"), insets("textEditor.padding", "metrics.padding"));
	}

	static GLMenuStyle menuStyle() {
		return new GLMenuStyle(font("menu"), color("menu.background", "palette.surface"),
				color("menu.hover", "state.hover"), color("menu.pressed", "state.pressed"),
				color("menu.text", "palette.text"), color("menu.textDisabled", "palette.textDisabled"),
				color("menu.accelerator", "palette.textSecondary", "palette.textDisabled"),
				color("menu.border", "palette.border"), color("menu.separator", "palette.border"),
				color("menu.arrow", "palette.text"), number("menu.cornerRadius", "metrics.cornerRadius"),
				number("menu.itemCornerRadius"),
				number("menu.borderWidth", "metrics.borderWidth"),
				number("menu.itemHeight", "metrics.controlHeight"), number("menu.separatorHeight"),
				number("menu.minimumWidth"), number("menu.itemHorizontalPadding"), number("menu.columnGap"),
				insets("menu.padding", "metrics.padding"));
	}

	static GLMenuBarStyle menuBarStyle() {
		return new GLMenuBarStyle(font("menuBar"), color("menuBar.background", "palette.surface"),
				color("menuBar.text", "palette.text"), color("menuBar.textDisabled", "palette.textDisabled"),
				color("menuBar.hover", "state.hover"), color("menuBar.pressed", "state.pressed"),
				color("menuBar.active", "palette.accent"), color("menuBar.border", "palette.border"),
				number("menuBar.height"), number("menuBar.itemHorizontalPadding"),
				number("menuBar.itemGap"), number("menuBar.borderWidth", "metrics.borderWidth"),
				number("menuBar.cornerRadius", "metrics.cornerRadius"));
	}

	static GLComboBoxStyle comboBoxStyle() {
		return new GLComboBoxStyle(font("comboBox"),
				color("comboBox.background", "state.normal"), color("comboBox.hover", "state.hover"),
				color("comboBox.pressed", "state.pressed"),
				color("comboBox.pressedLight", "button.pressedLight"),
				color("comboBox.pressedDark", "button.pressedDark"),
				color("comboBox.disabled", "state.disabled"),
				color("comboBox.text", "palette.text"), color("comboBox.textDisabled", "palette.textDisabled"),
				color("comboBox.border", "palette.border"), color("comboBox.arrow", "palette.text"),
				number("comboBox.arrowStrokeWidth"),
				number("comboBox.cornerRadius", "metrics.cornerRadius"),
				number("comboBox.borderWidth", "metrics.borderWidth"),
				number("comboBox.height", "metrics.controlHeight"), number("comboBox.minimumWidth"),
				number("comboBox.arrowAreaWidth"), insets("comboBox.padding", "metrics.padding"));
	}

	private static GLFontSpec font(String prefix) {
		String styleText = text(prefix + ".font.style", "font.style");
		int style = switch (styleText.toLowerCase()) {
		case "plain" -> Font.PLAIN;
		case "bold" -> Font.BOLD;
		case "italic" -> Font.ITALIC;
		case "bolditalic", "bold_italic", "bold-italic" -> Font.BOLD | Font.ITALIC;
		default -> throw invalid("Unknown font style: " + styleText);
		};
		return new GLFontSpec(text(prefix + ".font.family", "font.family"), style,
				number(prefix + ".font.size", "font.size"));
	}

	private static Color color(String... keys) {
		try { return GLThemeValues.parseColor(text(keys)); }
		catch (IllegalArgumentException error) { throw invalid(error.getMessage(), error); }
	}

	private static float number(String... keys) {
		String key = firstPresent(keys);
		try { return GLThemeValues.parseFloat(VALUES.get(key, null), key); }
		catch (IllegalArgumentException error) { throw invalid(error.getMessage(), error); }
	}

	private static GLInsets insets(String... keys) {
		String key = firstPresent(keys);
		try { return VALUES.getInsets(key, null); }
		catch (IllegalArgumentException error) { throw invalid(error.getMessage(), error); }
	}

	private static String text(String... keys) { return VALUES.get(firstPresent(keys), null); }

	private static String firstPresent(String... keys) {
		for (String key : keys)
			if (VALUES.contains(key)) return key;
		throw invalid("Required value is missing; expected one of: " + String.join(", ", keys));
	}

	private static GLThemeLoadException invalid(String message) {
		return new GLThemeLoadException("Invalid built-in default theme " + RESOURCE + ": " + message);
	}

	private static GLThemeLoadException invalid(String message, Throwable cause) {
		return new GLThemeLoadException("Invalid built-in default theme " + RESOURCE + ": " + message, cause);
	}
}
