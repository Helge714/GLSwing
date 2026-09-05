package com.vingame.glswing.document;

/** Relationship between a document's last acknowledged file and the current file. */
public enum GLDocumentFileState {
	/** The file still matches the last opened or saved version. */
	CURRENT,
	/** The file exists but was changed outside this document session. */
	MODIFIED,
	/** The file no longer exists at its known location. */
	DELETED,
	/** The file's state could not be determined, for example because access failed. */
	UNAVAILABLE
}
