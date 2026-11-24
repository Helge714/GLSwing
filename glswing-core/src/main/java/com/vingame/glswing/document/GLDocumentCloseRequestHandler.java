package com.vingame.glswing.document;

import java.util.concurrent.CompletionStage;

/** Presents an application's save/discard/cancel choice for a modified document. */
@FunctionalInterface
public interface GLDocumentCloseRequestHandler {
	CompletionStage<GLDocumentCloseChoice> requestClose(GLDocument<?> document);
}
