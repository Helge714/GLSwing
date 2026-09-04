package com.vingame.glswing.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Serializes one versioned application model without owning its storage.
 * @param <T> model accepted by this codec
 */
public interface GLDocumentCodec<T> {
	String typeId();
	String codecId();
	Class<T> contentType();
	int currentSchemaVersion();
	default Set<String> fileExtensions() { return Set.of(); }
	default boolean supportsSchemaVersion(int version) {
		return version == currentSchemaVersion();
	}
	T read(InputStream input, int schemaVersion) throws IOException;
	void write(T content, OutputStream output) throws IOException;
}
