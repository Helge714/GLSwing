package com.vingame.glswing.document;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one independently recoverable autosave snapshot.
 *
 * @param snapshotPath recovery container on disk
 * @param originalDocumentId stable identity of the original document
 * @param title last known display title
 * @param originalLocation last known authoritative URI, or {@code null}
 * @param typeId registered document type ID
 * @param codecId registered codec ID
 * @param schemaVersion encoded content schema
 * @param savedAt snapshot creation time
 */
public record GLDocumentRecovery(Path snapshotPath, UUID originalDocumentId,
		String title, URI originalLocation, String typeId, String codecId,
		int schemaVersion, Instant savedAt) {
	public GLDocumentRecovery {
		if (snapshotPath == null || originalDocumentId == null || title == null
				|| title.isBlank() || typeId == null || typeId.isBlank()
				|| codecId == null || codecId.isBlank() || schemaVersion < 1
				|| savedAt == null)
			throw new IllegalArgumentException("Recovery metadata is incomplete");
		snapshotPath = snapshotPath.toAbsolutePath().normalize();
	}
}
