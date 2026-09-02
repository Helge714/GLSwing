package com.vingame.glswing.file;

import java.nio.file.Path;
import java.util.List;

/** Completed file-dialog selection; cancellation is a normal result. */
public record GLFileDialogResult(boolean approved, List<Path> selectedPaths) {
	public GLFileDialogResult {
		selectedPaths = selectedPaths == null ? List.of() : selectedPaths.stream()
				.map(path -> path.toAbsolutePath().normalize()).toList();
		if (!approved && !selectedPaths.isEmpty())
			throw new IllegalArgumentException("A cancelled result cannot contain paths");
	}
	public static GLFileDialogResult cancelled() { return new GLFileDialogResult(false, List.of()); }
	public static GLFileDialogResult approved(List<Path> paths) {
		if (paths == null || paths.isEmpty())
			throw new IllegalArgumentException("An approved result requires a path");
		return new GLFileDialogResult(true, paths);
	}
	public Path selectedPath() { return selectedPaths.isEmpty() ? null : selectedPaths.get(0); }
}
