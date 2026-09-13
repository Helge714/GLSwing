package com.vingame.glswing.document;

/** Stable category suitable for localized error presentation. */
public enum GLDocumentFailureKind {
	MISSING_CODEC,
	UNSUPPORTED_VERSION,
	MIGRATION_FAILED,
	INVALID_FORMAT,
	ACCESS_DENIED,
	IO,
	INVALID_OPERATION,
	UNKNOWN
}
