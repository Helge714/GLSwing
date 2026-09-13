package com.vingame.glswing.document;

/** Decision made after a document was safely upgraded in memory. */
public enum GLDocumentMigrationChoice {
	UPGRADE,
	OPEN_READ_ONLY,
	CANCEL
}
