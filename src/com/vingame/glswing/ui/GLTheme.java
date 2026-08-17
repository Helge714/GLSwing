package com.vingame.glswing.ui;

/** Immutable collection of styles inherited through a GLSwing hierarchy. */
public record GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
		GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
		GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle,
		GLMenuBarStyle menuBarStyle, GLComboBoxStyle comboBoxStyle,
		GLUiStrings uiStrings, GLThemeValues values) {

	public static final GLTheme DEFAULT =
			new GLTheme(GLLabelStyle.DEFAULT, GLButtonStyle.DEFAULT,
					GLCheckBoxStyle.DEFAULT, GLSliderStyle.DEFAULT,
					GLTextEditorStyle.DEFAULT, GLMenuStyle.DEFAULT,
					GLMenuBarStyle.DEFAULT, GLComboBoxStyle.DEFAULT,
					GLUiStrings.ENGLISH, GLThemeDefaults.values());

	/** Retains the former canonical constructor. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
			GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle,
			GLMenuBarStyle menuBarStyle, GLComboBoxStyle comboBoxStyle,
			GLUiStrings uiStrings) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle, textEditorStyle,
				menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				GLThemeValues.EMPTY);
	}

	/** Retains the original two-style constructor for existing themes. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle) {
		this(labelStyle, buttonStyle, GLCheckBoxStyle.DEFAULT,
				GLSliderStyle.DEFAULT, GLTextEditorStyle.DEFAULT,
				GLMenuStyle.DEFAULT, GLMenuBarStyle.DEFAULT,
				GLComboBoxStyle.DEFAULT, GLUiStrings.ENGLISH);
	}

	/** Retains the original three-style constructor for existing themes. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, GLSliderStyle.DEFAULT,
				GLTextEditorStyle.DEFAULT, GLMenuStyle.DEFAULT,
				GLMenuBarStyle.DEFAULT, GLComboBoxStyle.DEFAULT,
				GLUiStrings.ENGLISH);
	}

	/** Retains the original four-style constructor for existing themes. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				GLTextEditorStyle.DEFAULT, GLMenuStyle.DEFAULT,
				GLMenuBarStyle.DEFAULT, GLComboBoxStyle.DEFAULT,
				GLUiStrings.ENGLISH);
	}

	/** Retains the original five-style constructor for existing themes. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
			GLTextEditorStyle textEditorStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, GLMenuStyle.DEFAULT, GLMenuBarStyle.DEFAULT,
				GLComboBoxStyle.DEFAULT, GLUiStrings.ENGLISH);
	}

	/** Retains the original six-style constructor for existing themes. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
			GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, GLMenuBarStyle.DEFAULT,
				GLComboBoxStyle.DEFAULT, GLUiStrings.ENGLISH);
	}

	/** Retains the former seven-style constructor. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
			GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle,
			GLMenuBarStyle menuBarStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, GLComboBoxStyle.DEFAULT,
				GLUiStrings.ENGLISH);
	}

	/** Retains the former eight-style constructor. */
	public GLTheme(GLLabelStyle labelStyle, GLButtonStyle buttonStyle,
			GLCheckBoxStyle checkBoxStyle, GLSliderStyle sliderStyle,
			GLTextEditorStyle textEditorStyle, GLMenuStyle menuStyle,
			GLMenuBarStyle menuBarStyle, GLComboBoxStyle comboBoxStyle) {
		this(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle,
				GLUiStrings.ENGLISH);
	}

	public GLTheme {
		if (labelStyle == null || buttonStyle == null || checkBoxStyle == null
				|| sliderStyle == null || textEditorStyle == null
				|| menuStyle == null || menuBarStyle == null
				|| comboBoxStyle == null || uiStrings == null || values == null)
			throw new IllegalArgumentException("Theme styles must not be null");
	}

	public GLTheme withLabelStyle(GLLabelStyle style) {
		return new GLTheme(style, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				values);
	}

	public GLTheme withButtonStyle(GLButtonStyle style) {
		return new GLTheme(labelStyle, style, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				values);
	}

	public GLTheme withCheckBoxStyle(GLCheckBoxStyle style) {
		return new GLTheme(labelStyle, buttonStyle, style, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				values);
	}

	public GLTheme withSliderStyle(GLSliderStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, style,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				values);
	}

	public GLTheme withTextEditorStyle(GLTextEditorStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				style, menuStyle, menuBarStyle, comboBoxStyle, uiStrings, values);
	}

	public GLTheme withMenuStyle(GLMenuStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, style, menuBarStyle, comboBoxStyle, uiStrings,
				values);
	}

	public GLTheme withMenuBarStyle(GLMenuBarStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, style, comboBoxStyle, uiStrings, values);
	}

	public GLTheme withComboBoxStyle(GLComboBoxStyle style) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, style, uiStrings, values);
	}

	public GLTheme withUiStrings(GLUiStrings strings) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, strings,
				values);
	}

	public GLTheme withValues(GLThemeValues themeValues) {
		return new GLTheme(labelStyle, buttonStyle, checkBoxStyle, sliderStyle,
				textEditorStyle, menuStyle, menuBarStyle, comboBoxStyle, uiStrings,
				themeValues);
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
				.with("tabbedPane.cornerRadius", Float.toString(radius));
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
				uiStrings, roundedValues);
	}
}
