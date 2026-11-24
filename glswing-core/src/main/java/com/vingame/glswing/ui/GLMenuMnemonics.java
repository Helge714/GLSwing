package com.vingame.glswing.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;

/** Dispatches keyboard mnemonics for menu bars and currently open popups. */
public final class GLMenuMnemonics {
	private static final int MODIFIER_MASK = InputEvent.SHIFT_DOWN_MASK
			| InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
			| InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

	private GLMenuMnemonics() {
	}

	/**
	 * Handles one host-level key press. Applications normally rely on the GLSwing
	 * hosts, which call this method before dispatching the key to the focused
	 * control.
	 */
	public static boolean handleKeyPressed(GLParent host, KeyEvent event,
			GLInputMap inputMap) {
		if (host == null || event == null || inputMap == null
				|| event.getID() != KeyEvent.KEY_PRESSED)
			return false;
		int actual = event.getModifiersEx() & MODIFIER_MASK;
		if (activateMenuBar(host, event.getKeyCode(), actual))
			return consume(event);
		if (GLPopupMenu.activateOpenMnemonic(host, event.getKeyCode(), actual))
			return consume(event);
		return false;
	}

	private static boolean activateMenuBar(GLParent parent, int keyCode,
			int actualModifiers) {
		for (GLContainer child : parent.getGLChildrenInPaintOrder()) {
			if (!child.isVisible())
				continue;
			if (child instanceof GLMenuBar menuBar
					&& actualModifiers == mnemonicModifiers(menuBar.getGLInputMap())
					&& menuBar.activateMnemonic(keyCode))
				return true;
			if (activateMenuBar(child, keyCode, actualModifiers))
				return true;
		}
		return false;
	}

	static int mnemonicModifiers(GLInputMap inputMap) {
		Integer configured = inputMap.getModifiers(GLInputActions.MENU_MNEMONIC);
		return configured == null ? InputEvent.ALT_DOWN_MASK : configured;
	}

	private static boolean consume(KeyEvent event) {
		event.consume();
		return true;
	}
}
