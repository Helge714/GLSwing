package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.rendering.GLTextShadow;
import com.vingame.glswing.rendering.GLTextShadow.OffsetSpace;

/** Immutable collection of styles inherited through a GLSwing hierarchy. */
public record GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
		GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
		GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle,
		GLMenuBarStyle menuBarStyle, GLComboBoxStyle comboBoxStyle,
		GLThemeValues values) {

	public static final GLTheme DEFAULT =
			new GLTheme(GLLabelStyle.DEFAULT, GLButtonStyle.DEFAULT,
					GLCheckBoxStyle.DEFAULT, GLSliderStyle.DEFAULT,
					GLTextEditorStyle.DEFAULT, GLMenuStyle.DEFAULT,
					GLMenuBarStyle.DEFAULT, GLComboBoxStyle.DEFAULT,
					GLThemeDefaults.values());

	public GLTheme {
		if (labelStyle == null || buttonStyle == null || checkBoxStyle == null
				|| sliderStyle == null || textEditorStyle == null
				|| menuStyle == null || menuBarStyle == null
				|| comboBoxStyle == null || values == null)
			throw new IllegalArgumentException("Theme styles must not be null");
	}

	public GLTheme withLabelStyle(GLLabelStyle style) {
		return new GLTheme(style, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withButtonStyle(GLButtonStyle style) {
		return new GLTheme(labelStyle, style, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withCheckBoxStyle(GLCheckBoxStyle style) {
		return new GLTheme(labelStyle, buttonStyle, style, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withSliderStyle(GLSliderStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, style,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withTextEditorStyle(GLTextEditorStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				style, menuStyle, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withMenuStyle(GLMenuStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, style, menuBarStyle, comboBoxStyle, values);
	}

	public GLTheme withMenuBarStyle(GLMenuBarStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, style, comboBoxStyle, values);
	}

	public GLTheme withComboBoxStyle(GLComboBoxStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, style, values);
	}

	public GLTheme withValues(GLThemeValues themeValues) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, themeValues);
	}

	/** Resolves the global text shadow with optional control-specific overrides. */
	public GLTextShadow textShadow(String controlPrefix) {
		String prefix = controlPrefix == null || controlPrefix.isBlank()
				? "" : controlPrefix + ".";
		String enabledText = values.get(prefix + "textShadow.on",
				values.get("textShadow.on", "true"));
		if (!"true".equalsIgnoreCase(enabledText.trim())
				&& !"false".equalsIgnoreCase(enabledText.trim()))
			throw new IllegalArgumentException(
					"Text-shadow switch must be true or false: " + enabledText);
		if (!Boolean.parseBoolean(enabledText.trim())) return GLTextShadow.NONE;
		Color color = values.getColor(prefix + "textShadow.color",
				values.getColor("textShadow.color", GLTextShadow.NONE.color()));
		float offsetX = values.getFloat(prefix + "textShadow.offsetX",
				values.getFloat("textShadow.offsetX", 0));
		float offsetY = values.getFloat(prefix + "textShadow.offsetY",
				values.getFloat("textShadow.offsetY", 0));
		float softness = values.getFloat(prefix + "textShadow.softness",
				values.getFloat("textShadow.softness", 0));
		String space = values.get(prefix + "textShadow.offsetSpace",
				values.get("textShadow.offsetSpace", "screen"));
		OffsetSpace offsetSpace;
		try {
			offsetSpace = OffsetSpace.valueOf(space.trim().toUpperCase());
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException(
					"Unsupported text-shadow offset space: " + space, error);
		}
		return new GLTextShadow(color, offsetX, offsetY, softness, offsetSpace);
	}

	/**
	 * Applies one corner radius to every rounded control family in this theme.
	 * Labels remain unchanged because they do not draw themed geometry.
	 */
	public GLTheme withCornerRadius(float radius) {
		GLThemeValues roundedValues = values
				.with("metrics.cornerRadius", Float.toString(radius))
				.with("scrollBar.cornerRadius", Float.toString(radius))
				.with("scrollPane.cornerRadius", Float.toString(radius))
				.with("list.cornerRadius", Float.toString(radius))
				.with("tabbedPane.cornerRadius", Float.toString(radius))
				.with("node.cornerRadius", Float.toString(radius));
		return new GLTheme(labelStyle,
				buttonStyle.withCornerRadius(radius),
				checkBoxStyle.withCornerRadius(radius),
				sliderStyle.withGeometry(radius, sliderStyle.height(),
						sliderStyle.preferredWidth()),
				textEditorStyle.withGeometry(radius, textEditorStyle.borderWidth(),
						textEditorStyle.height(), textEditorStyle.preferredWidth(),
						textEditorStyle.padding()),
				menuStyle.withGeometry(radius, menuStyle.itemHeight(),
						menuStyle.minimumWidth()),
				menuBarStyle.withGeometry(menuBarStyle.height(),
						menuBarStyle.itemHorizontalPadding(), menuBarStyle.itemGap(),
						menuBarStyle.borderWidth(), radius),
				comboBoxStyle.withGeometry(radius, comboBoxStyle.height(),
						comboBoxStyle.minimumWidth(), comboBoxStyle.arrowAreaWidth()),
				roundedValues);
	}
}
