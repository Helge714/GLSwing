package com.vingame.glswing.ui;

import java.net.URI;

/** Immutable source snapshot submitted to a code-intelligence provider. */
public record GLCodeDocument(URI uri, String languageId, int version, String text) {
	public GLCodeDocument {
		if (uri == null || languageId == null || languageId.isBlank() || version < 0
				|| text == null)
			throw new IllegalArgumentException("Invalid code document");
	}
}
