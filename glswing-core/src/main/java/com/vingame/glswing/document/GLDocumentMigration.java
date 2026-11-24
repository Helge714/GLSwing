package com.vingame.glswing.document;

import java.io.IOException;

/** Converts serialized document bytes from one schema version to the next. */
@FunctionalInterface
public interface GLDocumentMigration {
	byte[] migrate(byte[] serialized) throws IOException;
}
