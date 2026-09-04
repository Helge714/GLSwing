package com.vingame.glswing.ui;

import java.util.concurrent.CompletionStage;

/** Executes a synchronous or asynchronous GLSwing action. */
@FunctionalInterface
public interface GLActionHandler {
	CompletionStage<?> execute(GLActionEvent event);
}
