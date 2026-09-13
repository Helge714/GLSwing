package com.vingame.glswing.document;

/** Reports a stored schema that the selected codec cannot upgrade safely. */
public final class GLDocumentSchemaException extends GLDocumentFormatException {
	private static final long serialVersionUID = 1L;
	private final String typeId;
	private final int storedVersion;
	private final int currentVersion;
	public GLDocumentSchemaException(String typeId, int storedVersion, int currentVersion) {
		super("Unsupported schema version " + storedVersion + " for " + typeId
				+ " (supported version " + currentVersion + ")");
		this.typeId = typeId; this.storedVersion = storedVersion;
		this.currentVersion = currentVersion;
	}
	public String typeId() { return typeId; }
	public int storedVersion() { return storedVersion; }
	public int currentVersion() { return currentVersion; }
	public boolean isNewer() { return storedVersion > currentVersion; }
}
