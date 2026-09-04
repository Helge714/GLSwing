package com.vingame.glswing.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Codec base class that applies an explicit consecutive migration chain.
 * @param <T> model accepted by this codec
 */
public abstract class GLVersionedDocumentCodec<T> implements GLDocumentCodec<T> {
	private final Map<Integer, GLDocumentMigration> migrations = new TreeMap<>();

	/** Registers a migration from {@code schemaVersion} to the following version. */
	protected final void addMigration(int schemaVersion, GLDocumentMigration migration) {
		if (schemaVersion < 1 || schemaVersion >= currentSchemaVersion())
			throw new IllegalArgumentException("Migration version is outside the codec range");
		if (migration == null || migrations.putIfAbsent(schemaVersion, migration) != null)
			throw new IllegalArgumentException("Migration already registered: " + schemaVersion);
	}

	@Override public final boolean supportsSchemaVersion(int version) {
		if (version < 1 || version > currentSchemaVersion()) return false;
		for (int current = version; current < currentSchemaVersion(); current++)
			if (!migrations.containsKey(current)) return false;
		return true;
	}

	@Override public final T read(InputStream input, int schemaVersion) throws IOException {
		if (!supportsSchemaVersion(schemaVersion))
			throw new GLDocumentFormatException("Unsupported schema version " + schemaVersion
					+ " for " + typeId());
		byte[] serialized = input.readAllBytes();
		for (int version = schemaVersion; version < currentSchemaVersion(); version++)
			serialized = migrations.get(version).migrate(serialized);
		return readCurrent(new ByteArrayInputStream(serialized));
	}

	/** Decodes bytes that already use {@link #currentSchemaVersion()}. */
	protected abstract T readCurrent(InputStream input) throws IOException;
}
