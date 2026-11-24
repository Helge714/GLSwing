package com.vingame.glswing.ui;

import java.net.URI;

/** One provider-resolved symbol reference with a complete source range. */
public record GLCodeReference(URI uri, int startLine, int startCharacter,
		int endLine, int endCharacter) {
	public GLCodeReference {
		if (uri == null || startLine < 0 || startCharacter < 0 || endLine < startLine
				|| endCharacter < 0 || endLine == startLine && endCharacter <= startCharacter)
			throw new IllegalArgumentException("Invalid code reference");
	}
	public GLCodeLocation location() {
		return new GLCodeLocation(uri, startLine, startCharacter);
	}
}
