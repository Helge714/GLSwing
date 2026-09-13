package com.vingame.glswing.document;

import java.io.IOException;

/** Base failure for deterministic in-memory document migration. */
public class GLDocumentMigrationException extends IOException {
	private static final long serialVersionUID = 1L;
	public enum Reason { MISSING_STEP, NEWER_VERSION, CONVERSION_FAILED }
	private final Reason reason;
	public GLDocumentMigrationException(String message, Reason reason) {
		super(message); this.reason = require(reason);
	}
	public GLDocumentMigrationException(String message, Reason reason, Throwable cause) {
		super(message, cause); this.reason = require(reason);
	}
	public Reason reason() { return reason; }
	private static Reason require(Reason reason) {
		if (reason == null) throw new IllegalArgumentException("Migration reason required");
		return reason;
	}
}
