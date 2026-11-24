package com.vingame.glswing.ui;

import java.net.URI;

/** Source location returned by a code-intelligence provider. */
public record GLCodeLocation(URI uri, int line, int character) {
	public GLCodeLocation {
		if (uri == null || line < 0 || character < 0)
			throw new IllegalArgumentException("Invalid code location");
	}
}
