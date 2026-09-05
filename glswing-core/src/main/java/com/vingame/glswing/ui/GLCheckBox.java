package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
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
 * Lightweight selectable check box rendered entirely by the shared UI batch.
 */
public class GLCheckBox extends GLControl {
	public static final float DEFAULT_BOX_SIZE = 14;
	public static final float DEFAULT_GAP = 6;

	private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters = new GLRenderParameters();
	private final GLRenderParameters checkParameters = new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private String text;
	private String actionCommand;
	private boolean selected;
	private boolean syncingAction;
	private GLAction action;
	private final PropertyChangeListener actionListener = event -> applyAction();
	private GLCheckBoxStyle localStyle;
	private GLCheckBoxStyle appliedStyle;

	public GLCheckBox(String text) {
		this(text, false);
	}

	public GLCheckBox(String text, boolean selected) {
		super(true);
		this.text = requireText(text);
		actionCommand = text;
		this.selected = selected;
		updatePresentation(true);
	}

	public GLCheckBox(GLAction action) {
		this("");
		setAction(action);
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

	public final boolean isSelected() {
		return selected;
	}

	public final void setSelected(boolean selected) {
		if (!syncingAction && action != null && action.isSelectable()
				&& action.isSelected() != selected) {
			action.setSelected(selected);
			return;
		}
		if (this.selected == selected)
			return;
		this.selected = selected;
		checkParameters.setOpacity(selected ? 1 : 0);
		repaintGL();
	}

	public final String getActionCommand() {
		return actionCommand;
	}

	public final void setActionCommand(String actionCommand) {
		if (actionCommand == null)
			throw new IllegalArgumentException("Action command must not be null");
		this.actionCommand = actionCommand;
	}

	public final GLAction getAction() { return action; }
	/** Binds text, tooltip, availability, selection and invocation. */
	public final void setAction(GLAction action) {
		if (this.action == action) return;
		if (this.action != null)
			this.action.removePropertyChangeListener(actionListener);
		this.action = action;
		if (action != null) action.addPropertyChangeListener(actionListener);
		applyAction();
	}
	public final void clearAction() { setAction(null); }

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
		if (!isEnabled())
			return;
		toggleAndFire();
	}

	public final void setGLStyle(GLCheckBoxStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Check box style must not be null");
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

	public final GLCheckBoxStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().checkBoxStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLCheckBoxStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLInsets padding = style.padding();
		GLFont font = getGLFont(style.font());
		float contentHeight = Math.max(style.boxSize(), font.getLineHeight());
		float boxX = padding.left();
		float boxY = padding.top() + (contentHeight - style.boxSize()) * 0.5f;
		float border = layoutBorderWidth(style.borderWidth());

		if (border > 0) {
			Color borderColor = isFocused()
					? style.focusBorderColor() : style.borderColor();
			context.withParameters(backgroundParameters, () ->
					context.fillAndDrawUIRoundRect(boxX, boxY,
							style.boxSize(), style.boxSize(), layoutCornerRadius(style.cornerRadius()),
							border, Color.WHITE, borderColor,
							GLStroke.WidthSpace.SCREEN));
		} else {
			context.withParameters(backgroundParameters, () ->
					context.fillUIRoundRect(boxX, boxY,
							style.boxSize(), style.boxSize(),
							layoutCornerRadius(style.cornerRadius()), Color.WHITE));
		}
		
		float checkInset = Math.min(style.boxSize() * 0.22f, layoutBorderWidth(style.borderWidth()) + 1);
		float checkSize = style.boxSize() - checkInset * 2;
		if (checkSize > 0) {
			context.withParameters(checkParameters, () -> 
				context.drawUICheckMark(boxX + checkInset, boxY + checkInset,
					checkSize, checkSize, style.checkStrokeWidth(), Color.WHITE));
		}

		if (!text.isEmpty()) {
			float textX = boxX + style.boxSize() + style.gap();
			String renderedText = GLTextFitter.ellipsize(font, text,
					Math.max(0, width - padding.right() - textX));
			float baselineY = padding.top() + (contentHeight - font.getLineHeight()) * 0.5f + font.getAscent();
			context.drawUITextShadow(font, renderedText, textX, baselineY,
					getGLTheme().textShadow("checkBox"));
			context.withParameters(textParameters, () -> context.drawUIText(font, renderedText, textX, baselineY, Color.WHITE));
		}

		// The label and its gap are part of the check box's mouse target too.
		context.addUIHitArea(0, 0, width, height);
	}

	@Override
	protected void controlStateChanged(GLControlState oldState, GLControlState newState) {
		updateColors();
	}

	@Override protected String toolTipTextAt(GLMouseEvent event) {
		String explicit = super.toolTipTextAt(event);
		if (explicit != null) return explicit;
		GLCheckBoxStyle style = getGLStyle();
		float available = getWidth() - style.padding().right()
				- style.padding().left() - style.boxSize() - style.gap();
		return GLTextFitter.overflowToolTip(getGLFont(style.font()), text,
				Math.max(0, available));
	}

	@Override
	protected void controlFocusChanged(boolean focused) {
		invalidateRecording();
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (isEnabled() && event.getButton() == MouseEvent.BUTTON1)
			toggleAndFire();
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
			toggleAndFire();
			event.consume();
		}
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation(true);
	}

	@Override protected void localizerChanged() { applyAction(); }

	private void updatePresentation(boolean geometryChanged) {
		GLCheckBoxStyle style = getGLStyle();
		appliedStyle = style;
		GLFont font = measurementFont(style.font());
		GLInsets padding = style.padding();
		float textWidth = text.isEmpty() ? 0 : style.gap() + font.measureText(text);
		setIntrinsicPreferredGLSize(new GLSize(padding.horizontal() + style.boxSize() + textWidth,
				padding.vertical() + Math.max(style.boxSize(), font.getLineHeight())));
		checkParameters.setOpacity(selected ? 1 : 0);
		updateColors();
		if (geometryChanged)
			invalidateRecording();
	}

	private void updateColors() {
		GLCheckBoxStyle style = getGLStyle();
		GLControlState state = getControlState();
		backgroundParameters.setColor(style.background(state));
		checkParameters.setColor(style.check(state));
		textParameters.setColor(style.text(state));
		repaintGL();
	}

	private GLFont measurementFont(GLFontSpec specification) {
		return getGLParent() == null ? specification.measurementFont() : getGLFont(specification);
	}

	private void toggleAndFire() {
		boolean before = selected;
		if (action != null && action.isSelectable()) action.perform(this);
		else {
			setSelected(!before);
			if (action != null) action.perform(this);
		}
		boolean after = selected;
		fireActionPerformed();
		var manager = getGLHistoryManager();
		if (manager != null && before != after)
			manager.record("Toggle checkbox",
					() -> applySelectionAndFire(before),
					() -> applySelectionAndFire(after));
	}

	private void applySelectionAndFire(boolean selected) {
		setSelected(selected);
		fireActionPerformed();
	}

	private void fireActionPerformed() {
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand,
				System.currentTimeMillis(), 0);
		for (ActionListener listener : actionListeners)
			listener.actionPerformed(event);
	}

	private void applyAction() {
		if (action == null) return;
		setText(action.resolvedText(getGLLocalizer()));
		setActionCommand(action.id());
		setToolTipText(action.resolvedShortDescription(getGLLocalizer()));
		setEnabled(action.isEnabled());
		if (action.isSelectable()) {
			syncingAction = true;
			try { setSelected(action.isSelected()); }
			finally { syncingAction = false; }
		}
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Check box text must not be null");
		return text;
	}
}
