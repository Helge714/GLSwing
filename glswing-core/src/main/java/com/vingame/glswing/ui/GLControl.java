package com.vingame.glswing.ui;

import java.awt.event.MouseEvent;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLMouseEvent;

/**
 * Common interaction state for lightweight GLSwing controls. Geometry remains
 * the responsibility of subclasses; this class only translates input into the
 * familiar normal, hovered, pressed and disabled states.
 */
public abstract class GLControl extends GLContainer implements GLKeyTarget {

	private boolean enabled = true;
	private boolean focusable = true;
	private boolean hovered;
	private boolean mousePressed;
	private boolean keyboardPressed;
	private boolean focused;
	private GLControlState state = GLControlState.NORMAL;

	protected GLControl() {
	}

	protected GLControl(boolean focusable) {
		this.focusable = focusable;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled)
			return;
		this.enabled = enabled;
		if (!enabled) {
			mousePressed = false;
			keyboardPressed = false;
			clearGLFocus(this);
		}
		updateState();
	}

	public final boolean isFocusable() {
		return focusable;
	}

	public final void setFocusable(boolean focusable) {
		if (this.focusable == focusable)
			return;
		this.focusable = focusable;
		if (!focusable)
			clearGLFocus(this);
		focusabilityChanged(focusable);
		repaintGL();
	}

	public final boolean isFocused() {
		return focused;
	}

	public final GLControlState getControlState() {
		return state;
	}

	@Override
	public final boolean isFocusTraversable() {
		return enabled && focusable && isVisible();
	}

	@Override
	public final void mouseEntered(GLMouseEvent event) {
		hovered = true;
		updateState();
		controlMouseEntered(event);
	}

	@Override
	public final void mouseExited(GLMouseEvent event) {
		hovered = false;
		updateState();
		controlMouseExited(event);
	}

	@Override
	public final void mouseMoved(GLMouseEvent event) {
		controlMouseMoved(event);
	}

	@Override
	public final void mousePressed(GLMouseEvent event) {
		if (enabled && event.getButton() == MouseEvent.BUTTON1) {
			mousePressed = true;
			requestGLFocus(this);
			updateState();
		}
		controlMousePressed(event);
	}

	@Override
	public final void mouseDragged(GLMouseEvent event) {
		if (mousePressed && event.hasLocalPosition()) {
			hovered = event.getLocalX() >= 0 && event.getLocalY() >= 0
					&& event.getLocalX() <= getWidth()
					&& event.getLocalY() <= getHeight();
			updateState();
		}
		controlMouseDragged(event);
	}

	@Override
	public final void mouseReleased(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1) {
			mousePressed = false;
			updateState();
		}
		controlMouseReleased(event);
	}

	@Override
	public final void mouseCaptureLost(GLMouseEvent event) {
		mousePressed = false;
		hovered = false;
		updateState();
		controlMouseCaptureLost(event);
	}

	@Override
	public final void mouseClicked(GLMouseEvent event) {
		controlMouseClicked(event);
	}

	@Override
	public final void focusGained() {
		if (focused)
			return;
		focused = true;
		controlFocusChanged(true);
		repaintGL();
	}

	@Override
	public final void focusLost() {
		if (!focused)
			return;
		focused = false;
		keyboardPressed = false;
		updateState();
		controlFocusChanged(false);
		repaintGL();
	}

	@Override
	public final void keyPressed(GLKeyEvent event) {
		if (enabled)
			controlKeyPressed(event);
	}

	@Override
	public final void keyReleased(GLKeyEvent event) {
		if (enabled)
			controlKeyReleased(event);
	}

	@Override
	public final void keyTyped(GLKeyEvent event) {
		if (enabled)
			controlKeyTyped(event);
	}

	protected final boolean isKeyboardPressed() {
		return keyboardPressed;
	}

	protected final void setKeyboardPressed(boolean pressed) {
		if (keyboardPressed == pressed)
			return;
		keyboardPressed = pressed;
		updateState();
	}

	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
	}

	protected void controlFocusChanged(boolean focused) {
	}

	/** Called when focus-ring geometry may have become necessary or unnecessary. */
	protected void focusabilityChanged(boolean focusable) {
	}

	protected void controlMouseEntered(GLMouseEvent event) {
	}

	protected void controlMouseExited(GLMouseEvent event) {
	}

	protected void controlMouseMoved(GLMouseEvent event) {
	}

	protected void controlMousePressed(GLMouseEvent event) {
	}

	protected void controlMouseDragged(GLMouseEvent event) {
	}

	protected void controlMouseReleased(GLMouseEvent event) {
	}

	protected void controlMouseCaptureLost(GLMouseEvent event) {
	}

	protected void controlMouseClicked(GLMouseEvent event) {
	}

	protected void controlKeyPressed(GLKeyEvent event) {
	}

	protected void controlKeyReleased(GLKeyEvent event) {
	}

	protected void controlKeyTyped(GLKeyEvent event) {
	}

	private void updateState() {
		GLControlState next = !enabled ? GLControlState.DISABLED
				: keyboardPressed || mousePressed && hovered
						? GLControlState.PRESSED
						: hovered ? GLControlState.HOVERED : GLControlState.NORMAL;
		if (state == next)
			return;
		GLControlState previous = state;
		state = next;
		controlStateChanged(previous, next);
		repaintGL();
	}
}
