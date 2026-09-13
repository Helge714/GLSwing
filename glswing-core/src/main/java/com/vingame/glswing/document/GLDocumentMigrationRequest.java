package com.vingame.glswing.document;

import java.nio.file.Path;

/** Describes a successful in-memory upgrade awaiting user approval. */
public record GLDocumentMigrationRequest(GLDocument<?> document, Path path,
		int storedSchemaVersion, int currentSchemaVersion) {
	public GLDocumentMigrationRequest {
		if (document == null || path == null || storedSchemaVersion < 1
				|| currentSchemaVersion <= storedSchemaVersion)
			throw new IllegalArgumentException("Invalid migration request");
		path = path.toAbsolutePath().normalize();
	}
}
