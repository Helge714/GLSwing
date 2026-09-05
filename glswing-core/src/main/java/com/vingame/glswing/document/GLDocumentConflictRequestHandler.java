package com.vingame.glswing.document;

import java.util.concurrent.CompletionStage;

/** Presents an external file conflict without prescribing a UI toolkit. */
@FunctionalInterface
public interface GLDocumentConflictRequestHandler {
	CompletionStage<GLDocumentConflictChoice> requestResolution(
			GLDocumentConflictRequest request);
}
