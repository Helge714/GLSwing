package com.vingame.glswing.document;

import java.nio.file.Path;

/**
 * Context supplied to an application's save-conflict presenter.
 *
 * @param document locally edited document
 * @param path authoritative file location
 * @param fileState detected external state
 */
public record GLDocumentConflictRequest(GLDocument<?> document, Path path,
		GLDocumentFileState fileState) {
	public GLDocumentConflictRequest {
		if (document == null || path == null || fileState == null
				|| fileState == GLDocumentFileState.CURRENT)
			throw new IllegalArgumentException("A conflicting document file is required");
	}
}
