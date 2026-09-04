package com.vingame.glswing.ui;

import com.vingame.glswing.input.GLKeyEvent;

/** Context supplied when a {@link GLAction} is invoked. */
public record GLActionEvent(GLAction action, Object source,
		GLKeyEvent keyEvent, long when) {
	public GLActionEvent {
		if (action == null) throw new IllegalArgumentException("Action required");
	}
}
