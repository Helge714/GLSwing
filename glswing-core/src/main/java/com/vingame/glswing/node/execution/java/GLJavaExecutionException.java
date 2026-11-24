package com.vingame.glswing.node.execution.java;

import com.vingame.glswing.node.execution.GLExecutionOperation;
import com.vingame.glswing.node.execution.GLBackendDiagnostic;

/** Structured runtime failure retaining the originating planned operation. */
public final class GLJavaExecutionException extends Exception {
	private static final long serialVersionUID = 1L;
	private final GLExecutionOperation operation;

	public GLJavaExecutionException(GLExecutionOperation operation,
			String message, Throwable cause) {
		super(message, cause);
		if (operation == null) throw new IllegalArgumentException("Operation required");
		this.operation = operation;
	}
	public GLExecutionOperation operation() { return operation; }
	public GLBackendDiagnostic diagnostic() {
		return GLBackendDiagnostic.error("java-reference", "backend.execution",
				getMessage(), operation, getCause());
	}
}
