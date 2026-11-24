package com.vingame.glswing.document;

/** Available resolutions when a document file changed before a save. */
public enum GLDocumentConflictChoice {
	/** Discard local edits and load the file from disk. */
	RELOAD,
	/** Replace the externally changed or deleted file with the local document. */
	OVERWRITE,
	/** Preserve both versions by choosing another file location. */
	SAVE_AS,
	/** Leave the document and file unchanged. */
	CANCEL
}
