package com.vingame.glswing.ui;

import java.util.Set;

/** Language-server classification for one UTF-16 source range. */
public record GLCodeSemanticToken(int start, int end, String type,
		Set<String> modifiers) {
	public GLCodeSemanticToken {
		if (start < 0 || end <= start || type == null || type.isBlank()
				|| modifiers == null)
			throw new IllegalArgumentException("Invalid semantic code token");
		modifiers = Set.copyOf(modifiers);
	}
}
