package com.vingame.glswing.ui;

import java.util.concurrent.CompletionStage;

import com.vingame.glswing.component.GLContainer;

/** Asynchronously approves or vetoes a close action initiated by a tab user. */
@FunctionalInterface
public interface GLTabCloseRequestHandler {
	CompletionStage<Boolean> requestClose(GLTabbedPane source, int index,
			String title, GLContainer content);
}
