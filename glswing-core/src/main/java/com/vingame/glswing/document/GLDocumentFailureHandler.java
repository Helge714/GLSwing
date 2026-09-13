package com.vingame.glswing.document;

/** Presents or records one structured document failure. */
@FunctionalInterface
public interface GLDocumentFailureHandler {
	void handle(GLDocumentFailure failure);
}
