package com.vingame.glswing.ui;

/** One incoming or outgoing call reported by a language service. */
public record GLCodeCall(String label, GLCodeLocation location) {
	public GLCodeCall {
		if (label == null || label.isBlank() || location == null)
			throw new IllegalArgumentException("Invalid code call");
	}
}
