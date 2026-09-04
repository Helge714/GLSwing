package com.vingame.glswing.application;

import java.util.concurrent.CompletionStage;

/** Asynchronously approves or cancels a user or handle initiated application close. */
@FunctionalInterface
public interface GLApplicationCloseRequestHandler {
	CompletionStage<Boolean> requestClose(GLApplicationContext context);
}
