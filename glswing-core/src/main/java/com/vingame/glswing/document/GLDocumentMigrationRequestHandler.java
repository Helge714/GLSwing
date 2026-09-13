package com.vingame.glswing.document;

import java.util.concurrent.CompletionStage;

/** Requests approval before an in-memory document upgrade becomes editable. */
@FunctionalInterface
public interface GLDocumentMigrationRequestHandler {
	CompletionStage<GLDocumentMigrationChoice> requestMigration(
			GLDocumentMigrationRequest request);
}
