package com.vingame.glswing.node.document;

import java.nio.file.Path;

/** Portable, project-relative reference to a separately saved graph asset. */
public record GLNodeGraphAssetReference(String alias, String assetId,
		int minimumVersion, String location) {
	public GLNodeGraphAssetReference {
		if (!validId(alias) || !validId(assetId) || minimumVersion < 1
				|| !validLocation(location))
			throw new IllegalArgumentException("Invalid graph-asset reference");
	}

	public Path resolveAgainst(Path projectDirectory) {
		if (projectDirectory == null)
			throw new IllegalArgumentException("Project directory required");
		Path root = projectDirectory.toAbsolutePath().normalize();
		Path resolved = root.resolve(location.replace('/', java.io.File.separatorChar))
				.normalize();
		if (!resolved.startsWith(root))
			throw new IllegalArgumentException("Asset reference escapes the project");
		return resolved;
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}

	private static boolean validLocation(String value) {
		if (value == null || value.isBlank() || value.startsWith("/")
				|| value.startsWith("\\") || value.contains("\\")
				|| value.contains(":")) return false;
		Path path;
		try { path = Path.of(value).normalize(); }
		catch (RuntimeException failure) { return false; }
		return !path.isAbsolute() && path.getNameCount() > 0
				&& !path.startsWith("..");
	}
}
