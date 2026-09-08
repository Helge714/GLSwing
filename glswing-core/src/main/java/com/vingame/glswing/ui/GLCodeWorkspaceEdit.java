package com.vingame.glswing.ui;

import java.net.URI;

/** One line/character based edit in a potentially different workspace document. */
public record GLCodeWorkspaceEdit(URI uri, int startLine, int startCharacter,
		int endLine, int endCharacter, String text) {
	public GLCodeWorkspaceEdit {
		if (uri == null || startLine < 0 || startCharacter < 0 || endLine < startLine
				|| endCharacter < 0 || endLine == startLine && endCharacter < startCharacter
				|| text == null) throw new IllegalArgumentException("Invalid workspace edit");
	}
}
