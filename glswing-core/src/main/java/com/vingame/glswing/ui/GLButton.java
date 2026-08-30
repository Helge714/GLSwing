package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;

/**
 * Lightweight push button. Its geometry is recorded once; interaction states
 * update replay parameters and therefore do not retriangulate the button.
 */
public class GLButton extends GLControl {
	public static final float DEFAULT_HEIGHT = 22;

	private final CopyOnWriteArrayList<ActionListener> actionListeners =
			new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters = new GLRenderParameters();
	private final GLRenderParameters pressedGradientParameters = new GLRenderParameters();
	private final GLRenderParameters focusParameters = new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private String text;
	private String actionCommand;
	private GLButtonStyle localStyle;
	private GLButtonStyle appliedStyle;

	public GLButton(String text) {
		super(true);
		this.text = requireText(text);
		actionCommand = text;
		updatePresentation(true);
	}

	public final String getText() {
		return text;
	}

	@Override
	protected String defaultName() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		String oldText = this.text;
		this.text = text;
		if (actionCommand.equals(oldText))
			actionCommand = text;
		updatePresentation(true);
	}

	public final String getActionCommand() {
		return actionCommand;
	}

	public final void setActionCommand(String actionCommand) {
		if (actionCommand == null)
			throw new IllegalArgumentException("Action command must not be null");
		this.actionCommand = actionCommand;
	}

	public final void addActionListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Action listener must not be null");
		actionListeners.addIfAbsent(listener);
	}

	public final void removeActionListener(ActionListener listener) {
		actionListeners.remove(listener);
	}

	public final ActionListener[] getActionListeners() {
		return actionListeners.toArray(ActionListener[]::new);
	}

	public final void doClick() {
		if (isEnabled())
			fireActionPerformed();
	}

	public final void setGLStyle(GLButtonStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Button style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation(true);
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation(true);
	}

	public final GLButtonStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().buttonStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLButtonStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		float border = style.borderWidth();

		context.withParameters(backgroundParameters, () -> {
			if (border > 0)
				context.fillAndDrawUIRoundRect(0, 0, width, height,
						style.cornerRadius(), border, Color.WHITE,
						style.borderColor(), GLStroke.WidthSpace.SCREEN);
			else
				context.fillUIRoundRect(0, 0, width, height,
						style.cornerRadius(), Color.WHITE);
		});
		float gradientInset = border > 0 ? Math.min(border + 1,
				Math.min(width, height) * 0.5f) : 0;
		context.withParameters(pressedGradientParameters, () ->
				context.fillUIVerticalGradientRoundRect(gradientInset, gradientInset,
						Math.max(0.0001f, width - gradientInset * 2),
						Math.max(0.0001f, height - gradientInset * 2),
						Math.max(0, style.cornerRadius() - gradientInset),
						style.pressedLightColor(), style.pressedDarkColor()));

		float focusInset = border;
		if (isFocusable() && width > focusInset * 2 && height > focusInset * 2) {
			context.withParameters(focusParameters, () ->
					context.drawUIRoundRect(focusInset, focusInset,
							width - focusInset * 2, height - focusInset * 2,
							Math.max(0, style.cornerRadius() - focusInset),
							1.5f, Color.WHITE, GLStroke.WidthSpace.SCREEN));
		}

		if (!text.isEmpty()) {
			GLFont font = getGLFont(style.font());
			GLInsets padding = style.padding();
			String renderedText = GLTextFitter.ellipsize(font, text,
					Math.max(0, width - padding.horizontal()));
			float textX = (width - font.measureText(renderedText)) * 0.5f;
			float baselineY = (height - font.getLineHeight()) * 0.5f + font.getAscent();
			context.withParameters(textParameters, () ->
					context.drawUIText(font, renderedText, textX, baselineY, Color.WHITE));
		}
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		updateColors();
	}

	@Override
	protected void controlFocusChanged(boolean focused) {
		focusParameters.setOpacity(focused ? 1 : 0);
	}

	@Override
	protected void focusabilityChanged(boolean focusable) {
		invalidateRecording();
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (isEnabled() && event.getButton() == MouseEvent.BUTTON1)
			fireActionPerformed();
	}

	@Override
	protected void controlKeyPressed(GLKeyEvent event) {
		if (getGLInputMap().matches(GLInputActions.ACTIVATE, event)
				&& !isKeyboardPressed()) {
			setKeyboardPressed(true);
			event.consume();
		}
	}

	@Override
	protected void controlKeyReleased(GLKeyEvent event) {
		if (getGLInputMap().matches(GLInputActions.ACTIVATE, event)
				&& isKeyboardPressed()) {
			setKeyboardPressed(false);
			fireActionPerformed();
			event.consume();
		}
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation(true);
	}

	private void updatePresentation(boolean geometryChanged) {
		GLButtonStyle style = getGLStyle();
		appliedStyle = style;
		GLFont font = measurementFont(style.font());
		GLInsets padding = style.padding();
		setIntrinsicPreferredGLSize(new GLSize(
				font.measureText(text) + padding.horizontal(),
				style.height()));
		focusParameters.setColor(style.focusColor()).setOpacity(isFocused() ? 1 : 0);
		updateColors();
		if (geometryChanged)
			invalidateRecording();
	}

	private void updateColors() {
		GLButtonStyle style = getGLStyle();
		GLControlState state = getControlState();
		backgroundParameters.setColor(style.background(state));
		pressedGradientParameters.setOpacity(
				state == GLControlState.PRESSED ? 1 : 0);
		textParameters.setColor(style.text(state));
		repaintGL();
	}

	private GLFont measurementFont(GLFontSpec specification) {
		return getGLParent() == null ? specification.measurementFont()
				: getGLFont(specification);
	}

	private void fireActionPerformed() {
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
				actionCommand, System.currentTimeMillis(), 0);
		for (ActionListener listener : actionListeners)
			listener.actionPerformed(event);
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Button text must not be null");
		return text;
	}
}
