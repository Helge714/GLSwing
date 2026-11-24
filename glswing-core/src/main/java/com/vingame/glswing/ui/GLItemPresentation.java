package com.vingame.glswing.ui;

import com.vingame.glswing.rendering.GLIcon;

/** Text and optional state-specific icons used to present one selectable value. */
public record GLItemPresentation(String text, GLIcon icon, GLIcon disabledIcon) {
	public GLItemPresentation {
		if (text == null)
			throw new IllegalArgumentException("Item presentation text must not be null");
	}

	public GLItemPresentation(String text, GLIcon icon) {
		this(text, icon, null);
	}

	public static GLItemPresentation text(String text) {
		return new GLItemPresentation(text, null, null);
	}
}
