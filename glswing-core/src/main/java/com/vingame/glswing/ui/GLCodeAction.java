package com.vingame.glswing.ui;

import java.util.List;

/** A provider-supplied source action and the edits needed to apply it. */
public record GLCodeAction(String title, String kind, boolean preferred,
		List<GLCodeWorkspaceEdit> edits) {
	public GLCodeAction {
		if (title == null || title.isBlank() || kind == null || edits == null)
			throw new IllegalArgumentException("Invalid code action");
		edits = List.copyOf(edits);
	}
}
