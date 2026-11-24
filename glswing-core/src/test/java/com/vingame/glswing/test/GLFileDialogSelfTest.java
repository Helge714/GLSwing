package com.vingame.glswing.test;

import java.nio.file.Path;
import java.util.List;
import com.vingame.glswing.file.GLFileDialogMode;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileDialogResult;
import com.vingame.glswing.file.GLFileFilter;

public final class GLFileDialogSelfTest {
	private GLFileDialogSelfTest() { }
	public static void main(String[] args) {
		GLFileFilter images = new GLFileFilter("Images", "*.PNG", ".jpg");
		check(images.accepts(Path.of("photo.png")) && images.swtPattern().equals("*.png;*.jpg"),
				"File-filter normalization failed");
		GLFileFilter allFiles = new GLFileFilter("All files");
		check(allFiles.accepts(Path.of("archive.unknown"))
				&& allFiles.swtPattern().equals("*.*"),
				"Unrestricted file filter failed");
		GLFileDialogRequest open = GLFileDialogRequest.open().multiple(true)
				.filter("All files").build();
		check(open.multiple() && open.filters().get(0).extensions().isEmpty(),
				"Multiple open request or unrestricted filter was lost");
		GLFileDialogRequest save = GLFileDialogRequest.save().title("Save")
				.suggestedFileName("image").filter("Images", "png").build();
		check(save.mode() == GLFileDialogMode.SAVE && save.confirmOverwrite(),
				"Save request defaults are invalid");
		GLFileDialogResult result = GLFileDialogResult.approved(List.of(Path.of("file.txt")));
		check(result.approved() && result.selectedPath().isAbsolute(),
				"Approved file result was not normalized");
		check(!GLFileDialogResult.cancelled().approved(), "Cancellation result is invalid");
		System.out.println("GL file-dialog self-test passed.");
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
