package com.vingame.glswing.ui;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Applies a provider-produced edit spanning one or more workspace documents. */
@FunctionalInterface
public interface GLCodeWorkspaceEditHandler {
	CompletionStage<Boolean> apply(List<GLCodeWorkspaceEdit> edits);
}
