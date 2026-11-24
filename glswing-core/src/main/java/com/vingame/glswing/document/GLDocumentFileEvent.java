package com.vingame.glswing.document;

import java.nio.file.Path;

/**
 * A transition in the externally observed state of a document file.
 *
 * @param document observed document
 * @param path normalized file path
 * @param previousState state before the transition
 * @param state newly observed state
 */
public record GLDocumentFileEvent(GLDocument<?> document, Path path,
		GLDocumentFileState previousState, GLDocumentFileState state) {
	public GLDocumentFileEvent {
		if (document == null || path == null || previousState == null || state == null)
			throw new IllegalArgumentException("Document, path, and states are required");
	}
}
