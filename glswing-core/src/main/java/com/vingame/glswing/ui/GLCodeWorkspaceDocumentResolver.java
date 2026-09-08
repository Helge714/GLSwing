package com.vingame.glswing.ui;

import java.util.concurrent.CompletionStage;

import com.vingame.glswing.document.GLDocument;

/** Asynchronously loads a text document requested by source navigation. */
@FunctionalInterface
public interface GLCodeWorkspaceDocumentResolver {
	CompletionStage<GLDocument<GLTextDocument>> resolve(GLCodeLocation location);
}
