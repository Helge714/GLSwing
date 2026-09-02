package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.LinkedHashSet;
import java.util.List;

import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTextShadow;

final class GLMnemonicSupport {
	private GLMnemonicSupport() {
	}

	static int requireMnemonic(int keyCode) {
		if (keyCode == KeyEvent.VK_UNDEFINED)
			return keyCode;
		if (keyCode <= 0)
			throw new IllegalArgumentException("Mnemonic must be a valid key code");
		return keyCode;
	}

	static int findIndex(String text, int keyCode) {
		if (keyCode == KeyEvent.VK_UNDEFINED)
			return -1;
		for (int index = 0; index < text.length(); index++)
			if (keyCode(text.charAt(index)) == keyCode)
				return index;
		return -1;
	}

	static List<Integer> candidates(String text) {
		LinkedHashSet<Integer> result = new LinkedHashSet<>();
		boolean wordStart = true;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (Character.isLetterOrDigit(character)) {
				if (wordStart)
					addCandidate(result, character);
				wordStart = false;
			} else {
				wordStart = true;
			}
		}
		for (int index = 0; index < text.length(); index++)
			addCandidate(result, text.charAt(index));
		return List.copyOf(result);
	}

	private static void addCandidate(LinkedHashSet<Integer> result,
			char character) {
		if (!Character.isLetterOrDigit(character))
			return;
		int keyCode = keyCode(character);
		if (keyCode != KeyEvent.VK_UNDEFINED)
			result.add(keyCode);
	}

	private static int keyCode(char character) {
		return KeyEvent.getExtendedKeyCodeForChar(
				Character.toUpperCase(character));
	}

	static boolean matches(int mnemonic, int keyCode) {
		return mnemonic != KeyEvent.VK_UNDEFINED && mnemonic == keyCode;
	}

	static void drawUnderline(GLContext2D context, GLFont font, String text,
			int index, float x, float baseline, Color color) {
		if (index < 0 || index >= text.length())
			return;
		float start = x + font.measureText(text.substring(0, index));
		float width = font.measureText(text.substring(index, index + 1));
		if (width <= 0)
			return;
		context.setStroke(new GLStroke(1)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		context.drawUILine(start, baseline + 2,
				start + width, baseline + 2, color);
	}

	static void drawUnderlineShadow(GLContext2D context, GLFont font,
			String text, int index, float x, float baseline,
			GLTextShadow shadow) {
		if (shadow == null || !shadow.isVisible()
				|| index < 0 || index >= text.length())
			return;
		float start = x + font.measureText(text.substring(0, index));
		float width = font.measureText(text.substring(index, index + 1));
		if (width > 0)
			context.drawUIRoundRectShadow(start, baseline + 1.5f,
					width, 1, 0.5f, shadow.softness(),
					shadow.offsetX(), shadow.offsetY(), shadow.color());
	}
}
