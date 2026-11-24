package com.vingame.glswing.document;

/**
 * Manifest metadata for one independently readable bundle entry.
 * @param id stable hierarchical entry ID
 * @param typeId persistent model type ID
 * @param codecId persistent encoding ID
 * @param schemaVersion stored entry schema
 * @param location relative ZIP payload location
 */
public record GLDocumentEntryDescriptor(String id, String typeId, String codecId,
		int schemaVersion, String location) {
	public GLDocumentEntryDescriptor {
		if (!validId(id) || !validToken(typeId) || !validToken(codecId)
				|| schemaVersion < 1 || !validLocation(location))
			throw new IllegalArgumentException("Invalid document entry metadata");
	}
	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
				&& !value.contains("//") && !value.contains("..");
	}
	private static boolean validToken(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
	private static boolean validLocation(String value) {
		return value != null && !value.isBlank() && !value.startsWith("/")
				&& !value.startsWith("\\") && !value.contains("..")
				&& !value.contains("\\") && !value.endsWith("/");
	}
}
