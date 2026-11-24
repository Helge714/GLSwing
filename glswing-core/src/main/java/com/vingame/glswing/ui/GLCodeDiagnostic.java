package com.vingame.glswing.ui;

/** One language diagnostic covering a half-open source range. */
public record GLCodeDiagnostic(int start, int end, Severity severity,
		String message) {
	public enum Severity { ERROR, WARNING }
	public GLCodeDiagnostic {
		if (start < 0 || end <= start || severity == null || message == null
				|| message.isBlank())
			throw new IllegalArgumentException("Invalid code diagnostic");
	}
}
