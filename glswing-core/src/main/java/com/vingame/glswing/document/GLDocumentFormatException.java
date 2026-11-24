package com.vingame.glswing.document;

import java.io.IOException;

/** Reports an invalid, unsupported, or inconsistent document container. */
public class GLDocumentFormatException extends IOException {
	private static final long serialVersionUID = 1L;
	public GLDocumentFormatException(String message) { super(message); }
	public GLDocumentFormatException(String message, Throwable cause) { super(message, cause); }
}
