package com.vingame.glswing.node.document;

import com.vingame.glswing.document.GLDocumentMigrationException;

/** Identifies a missing or failed graph, asset, or node migration step. */
public final class GLNodeGraphMigrationException extends GLDocumentMigrationException {
	private static final long serialVersionUID = 1L;
	private final String subjectId;
	private final int sourceVersion;

	public GLNodeGraphMigrationException(String message, String subjectId,
			int sourceVersion, Reason reason) {
		super(message, reason); this.subjectId = subjectId; this.sourceVersion = sourceVersion;
	}
	public GLNodeGraphMigrationException(String message, String subjectId,
			int sourceVersion, Reason reason, Throwable cause) {
		super(message, reason, cause); this.subjectId = subjectId; this.sourceVersion = sourceVersion;
	}
	public String subjectId() { return subjectId; }
	public int sourceVersion() { return sourceVersion; }
}
