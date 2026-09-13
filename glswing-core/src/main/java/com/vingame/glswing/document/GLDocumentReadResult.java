package com.vingame.glswing.document;

/** Content plus compatibility metadata produced by a codec read. */
public record GLDocumentReadResult<T>(T content, int storedSchemaVersion,
		boolean migrated) {
	public GLDocumentReadResult {
		if (content == null || storedSchemaVersion < 1)
			throw new IllegalArgumentException("Valid decoded content and schema required");
	}
}
