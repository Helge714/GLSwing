package com.vingame.glswing.file;

import java.nio.file.Path;
import java.util.List;

/** Immutable configuration shared by native and themed file dialogs. */
public record GLFileDialogRequest(String title, GLFileDialogMode mode,
		GLFileSelectionMode selectionMode, boolean multiple, Path initialDirectory,
		String suggestedFileName, List<GLFileFilter> filters, boolean confirmOverwrite) {
	public GLFileDialogRequest {
		title = title == null ? "" : title;
		if (mode == null || selectionMode == null || filters == null)
			throw new IllegalArgumentException("File-dialog values must not be null");
		if (mode == GLFileDialogMode.SAVE && multiple)
			throw new IllegalArgumentException("Save dialogs cannot select multiple files");
		filters = List.copyOf(filters);
	}
	public static Builder open() { return new Builder(GLFileDialogMode.OPEN); }
	public static Builder save() { return new Builder(GLFileDialogMode.SAVE); }
	public static final class Builder {
		private String title = "";
		private final GLFileDialogMode mode;
		private GLFileSelectionMode selectionMode = GLFileSelectionMode.FILES;
		private boolean multiple;
		private Path initialDirectory;
		private String suggestedFileName = "";
		private final java.util.ArrayList<GLFileFilter> filters = new java.util.ArrayList<>();
		private boolean confirmOverwrite = true;
		private Builder(GLFileDialogMode mode) { this.mode = mode; }
		public Builder title(String value) { title = value; return this; }
		public Builder selectionMode(GLFileSelectionMode value) { selectionMode = value; return this; }
		public Builder multiple(boolean value) { multiple = value; return this; }
		public Builder initialDirectory(Path value) { initialDirectory = value; return this; }
		public Builder suggestedFileName(String value) { suggestedFileName = value; return this; }
		public Builder filter(String description, String... extensions) {
			filters.add(new GLFileFilter(description, extensions)); return this;
		}
		public Builder confirmOverwrite(boolean value) { confirmOverwrite = value; return this; }
		public GLFileDialogRequest build() { return new GLFileDialogRequest(title, mode,
				selectionMode, multiple, initialDirectory, suggestedFileName,
				filters, confirmOverwrite); }
	}
}
