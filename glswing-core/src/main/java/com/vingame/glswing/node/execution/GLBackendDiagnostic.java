package com.vingame.glswing.node.execution;

import java.util.Optional;

import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.model.GLNodeId;

/** Structured backend problem with an optional operation and composite origin. */
public record GLBackendDiagnostic(Severity severity, String backendId,
		String code, String message, Optional<Integer> operationIndex,
		Optional<GLNodeId> nodeId, Optional<GLCompositeOrigin> origin,
		Optional<String> technicalDetails) {
	public enum Severity { WARNING, ERROR }
	public GLBackendDiagnostic {
		if (severity == null || !valid(backendId) || !valid(code)
				|| message == null || message.isBlank() || operationIndex == null
				|| nodeId == null || origin == null || technicalDetails == null
				|| operationIndex.filter(index -> index < 0).isPresent())
			throw new IllegalArgumentException("Invalid backend diagnostic");
	}
	public static GLBackendDiagnostic error(String backendId, String code,
			String message) {
		return new GLBackendDiagnostic(Severity.ERROR, backendId, code, message,
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}
	public static GLBackendDiagnostic error(String backendId, String code,
			String message, GLExecutionOperation operation, Throwable failure) {
		return new GLBackendDiagnostic(Severity.ERROR, backendId, code, message,
				Optional.of(operation.index()), Optional.of(operation.nodeId()),
				operation.origin(), Optional.ofNullable(failure == null ? null
						: failure.toString()));
	}
	private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
