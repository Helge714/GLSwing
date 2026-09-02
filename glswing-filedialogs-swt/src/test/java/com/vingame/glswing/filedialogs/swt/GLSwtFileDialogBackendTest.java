package com.vingame.glswing.filedialogs.swt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileSelectionMode;

final class GLSwtFileDialogBackendTest {
	@Test void identifiesNativeBackendAndRejectsCombinedSelection() {
		try (var backend = new GLSwtFileDialogBackend()) {
			assertEquals("swt-native", backend.id());
			var request = GLFileDialogRequest.open()
					.selectionMode(GLFileSelectionMode.FILES_AND_DIRECTORIES).build();
			assertThrows(java.util.concurrent.CompletionException.class,
					() -> backend.show(request, null).toCompletableFuture().join());
		}
	}
}
