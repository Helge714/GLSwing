package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;

/** A lightweight, non-pickable text label with theme inheritance. */
public class GLLabel extends com.vingame.glswing.component.GLContainer {

	private String text;
	private GLLabelStyle localStyle;
	private GLLabelStyle appliedStyle;
	private final GLRenderParameters textParameters = new GLRenderParameters();

	public GLLabel(String text) {
		this.text = requireText(text);
		updatePresentation();
	}

	public final String getText() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		this.text = text;
		updatePresentation();
	}

	public final void setGLStyle(GLLabelStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Label style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation();
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation();
	}

	public final GLLabelStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().labelStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLLabelStyle style = getGLStyle();
		GLFont font = getGLFont(style.font());
		GLInsets padding = style.padding();
		context.withParameters(textParameters, () -> context.drawUIText(font, text,
				padding.left(), padding.top() + font.getAscent(), Color.WHITE));
	}

	@Override
	protected boolean isMouseTargetEnabled() {
		return false;
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation();
	}

	private void updatePresentation() {
		GLLabelStyle style = getGLStyle();
		appliedStyle = style;
		textParameters.setColor(style.textColor());
		GLFont font = measurementFont(style.font());
		GLInsets padding = style.padding();
		setIntrinsicPreferredGLSize(new GLSize(
				font.measureText(text) + padding.horizontal(),
				font.getLineHeight() + padding.vertical()));
		invalidateRecording();
	}

	private GLFont measurementFont(GLFontSpec specification) {
		return getGLParent() == null ? specification.measurementFont()
				: getGLFont(specification);
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Label text must not be null");
		return text;
	}
}
