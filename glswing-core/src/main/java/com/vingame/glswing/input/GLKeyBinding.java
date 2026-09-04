package com.vingame.glswing.input;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/** One immutable key and modifier combination. */
public record GLKeyBinding(int keyCode, int modifiers) {

	private static final int KEY_MODIFIERS = InputEvent.SHIFT_DOWN_MASK
			| InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
			| InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

	public GLKeyBinding {
		if (keyCode == KeyEvent.VK_UNDEFINED)
			throw new IllegalArgumentException("Key code must be defined");
		modifiers &= KEY_MODIFIERS;
	}

	public boolean matches(KeyEvent event) {
		return event != null && event.getKeyCode() == keyCode
				&& (event.getModifiersEx() & KEY_MODIFIERS) == modifiers;

	}

	/** Captures the key code and keyboard modifiers from an AWT key event. */
	public static GLKeyBinding from(KeyEvent event) {
		if (event == null)
			throw new IllegalArgumentException("Key event must not be null");
		return new GLKeyBinding(event.getKeyCode(), event.getModifiersEx());
	}

	/** Returns a portable {@code .glinput} representation of this binding. */
	public String toConfigString() {
		String prefix = modifiersToConfigString(modifiers);
		return prefix.isEmpty() ? keyName(keyCode) : prefix + "+" + keyName(keyCode);
	}

	/** Returns a platform-localized label suitable for menus and tooltips. */
	public String toDisplayString() {
		String prefix = InputEvent.getModifiersExText(modifiers);
		String key = KeyEvent.getKeyText(keyCode);
		return prefix.isEmpty() ? key : prefix + "+" + key;
	}

	static String modifiersToConfigString(int modifiers) {
		StringBuilder result = new StringBuilder();
		int remaining = modifiers & KEY_MODIFIERS;
		int shortcut = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
				.contains("mac") ? InputEvent.META_DOWN_MASK
						: InputEvent.CTRL_DOWN_MASK;
		if ((remaining & shortcut) != 0) {
			result.append("shortcut+");
			remaining &= ~shortcut;
		}
		remaining = appendModifier(result, remaining, InputEvent.CTRL_DOWN_MASK, "CTRL");
		remaining = appendModifier(result, remaining, InputEvent.META_DOWN_MASK, "META");
		remaining = appendModifier(result, remaining, InputEvent.ALT_DOWN_MASK, "ALT");
		remaining = appendModifier(result, remaining, InputEvent.SHIFT_DOWN_MASK, "SHIFT");
		appendModifier(result, remaining, InputEvent.ALT_GRAPH_DOWN_MASK, "ALT_GRAPH");
		if (!result.isEmpty()) result.setLength(result.length() - 1);
		return result.toString();
	}

	private static int appendModifier(StringBuilder target, int modifiers,
			int mask, String name) {
		if ((modifiers & mask) != 0) target.append(name).append('+');
		return modifiers & ~mask;
	}

	private static String keyName(int keyCode) {
		for (Field field : KeyEvent.class.getFields()) {
			try {
				if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("VK_")
						&& field.getType() == int.class && field.getInt(null) == keyCode)
					return field.getName().substring(3);
			} catch (IllegalAccessException ignored) {
				// Public KeyEvent fields are accessible.
			}
		}
		throw new IllegalArgumentException("Unsupported key code " + keyCode);
	}
}
