package com.vingame.glswing.ui;

/**
 * Visual-only information inserted at a UTF-16 document offset.
 *
 * @param offset insertion anchor in the source document
 * @param label text displayed by the editor
 * @param kind semantic presentation category
 */
public record GLCodeInlayHint(int offset, String label, Kind kind) {
	/** Standard categories understood by code-intelligence providers. */
	public enum Kind { PARAMETER, TYPE, OTHER }

	public GLCodeInlayHint {
		if (offset < 0 || label == null || label.isBlank() || kind == null)
			throw new IllegalArgumentException("Invalid code inlay hint");
		label = label.strip();
	}

	/** Creates a parameter hint at the supplied UTF-16 offset. */
	public GLCodeInlayHint(int offset, String label) {
		this(offset, label, Kind.PARAMETER);
	}
}
