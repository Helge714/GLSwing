package com.vingame.glswing.document;

import java.nio.file.AccessDeniedException;
import java.nio.file.Path;

/** Structured, presentation-neutral description of a failed document operation. */
public record GLDocumentFailure(GLDocumentOperation operation, Path path,
		GLDocumentFailureKind kind, Throwable cause) {
	public GLDocumentFailure {
		if (operation == null || kind == null || cause == null)
			throw new IllegalArgumentException("Complete document failure required");
		path = path == null ? null : path.toAbsolutePath().normalize();
	}
	public static GLDocumentFailure of(GLDocumentOperation operation, Path path,
			Throwable failure) {
		Throwable cause = unwrap(failure);
		GLDocumentFailureKind kind = cause instanceof GLDocumentCodecNotFoundException
				? GLDocumentFailureKind.MISSING_CODEC
				: cause instanceof GLDocumentSchemaException
						? GLDocumentFailureKind.UNSUPPORTED_VERSION
				: cause instanceof GLDocumentMigrationException migration
								? migration.reason() == GLDocumentMigrationException.Reason.NEWER_VERSION
										? GLDocumentFailureKind.UNSUPPORTED_VERSION
										: GLDocumentFailureKind.MIGRATION_FAILED
								: cause instanceof GLDocumentFormatException
										? GLDocumentFailureKind.INVALID_FORMAT
										: cause instanceof AccessDeniedException
												|| cause instanceof SecurityException
														? GLDocumentFailureKind.ACCESS_DENIED
														: cause instanceof java.io.IOException
																? GLDocumentFailureKind.IO
																: cause instanceof IllegalArgumentException
																		|| cause instanceof IllegalStateException
																				? GLDocumentFailureKind.INVALID_OPERATION
																				: GLDocumentFailureKind.UNKNOWN;
		return new GLDocumentFailure(operation, path, kind, cause);
	}
	public String technicalDetails() {
		java.io.StringWriter text = new java.io.StringWriter();
		cause.printStackTrace(new java.io.PrintWriter(text));
		return text.toString();
	}
	private static Throwable unwrap(Throwable failure) {
		Throwable result = failure == null ? new IllegalStateException("Unknown failure") : failure;
		while ((result instanceof java.util.concurrent.CompletionException
				|| result instanceof java.util.concurrent.ExecutionException)
				&& result.getCause() != null) result = result.getCause();
		return result;
	}
}
