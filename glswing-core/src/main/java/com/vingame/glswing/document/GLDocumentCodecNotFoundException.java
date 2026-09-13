package com.vingame.glswing.document;

/** Indicates that a document requires a codec or plug-in not installed here. */
public final class GLDocumentCodecNotFoundException extends GLDocumentFormatException {
	private static final long serialVersionUID = 1L;
	private final String typeId;
	private final String codecId;
	public GLDocumentCodecNotFoundException(String typeId, String codecId) {
		super("No codec registered for " + typeId + " using " + codecId);
		this.typeId = typeId; this.codecId = codecId;
	}
	public String typeId() { return typeId; }
	public String codecId() { return codecId; }
}
