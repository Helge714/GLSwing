package com.vingame.glswing.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vingame.glswing.file.GLFileDialogs;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTextArea;
import com.vingame.glswing.ui.GLTextDocument;

final class GLDocumentFileWatcherTest {
	@TempDir Path directory;

	@Test
	void detectsExternalChangesAndAcknowledgesApplicationSaves() throws Exception {
		Path path = directory.resolve("sample.txt");
		Files.writeString(path, "original", StandardCharsets.UTF_8);
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		GLPlainTextDocumentCodec codec = new GLPlainTextDocumentCodec("test.text", "txt");
		registry.register(codec);
		try (GLDocumentManager manager = new GLDocumentManager(registry);
				GLDocumentFileWatcher watcher = new GLDocumentFileWatcher()) {
			GLDocument<GLTextDocument> document = manager.open(path, codec, 1)
					.toCompletableFuture().get(5, TimeUnit.SECONDS);
			watcher.track(document);
			Files.writeString(path, "externally changed", StandardCharsets.UTF_8);
			assertEquals(GLDocumentFileState.MODIFIED, watcher.refresh(document));

			document.content().insert(document.content().length(), " locally");
			manager.save(document).toCompletableFuture().get(5, TimeUnit.SECONDS);
			watcher.acknowledge(document);
			assertEquals(GLDocumentFileState.CURRENT, watcher.refresh(document));

			Files.delete(path);
			assertEquals(GLDocumentFileState.DELETED, watcher.refresh(document));
		}
	}

	@Test
	void lifecycleCanReloadOrOverwriteAConflict() throws Exception {
		Path path = directory.resolve("lifecycle.txt");
		Files.writeString(path, "disk", StandardCharsets.UTF_8);
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		GLPlainTextDocumentCodec codec = new GLPlainTextDocumentCodec("test.lifecycle", "txt");
		registry.register(codec);
		GLTabbedPane tabs = new GLTabbedPane();
		GLDocumentWorkspace workspace = new GLDocumentWorkspace();
		GLFileDialogs dialogs = GLFileDialogs.themed();
		try (GLDocumentManager manager = new GLDocumentManager(registry);
				GLDocumentFileWatcher watcher = new GLDocumentFileWatcher();
				GLDocumentTabController controller = new GLDocumentTabController(tabs, workspace);
				GLDocumentLifecycle<GLTextDocument> lifecycle = new GLDocumentLifecycle<>(
						manager, codec, controller, dialogs, tabs, GLTextDocument::new,
						document -> new GLTextArea(document.content()))) {
			lifecycle.setFileWatcher(watcher);
			GLDocument<GLTextDocument> document = manager.open(path, codec, 1)
					.toCompletableFuture().get(5, TimeUnit.SECONDS);
			GLTextArea startupView = new GLTextArea(document.content());
			lifecycle.addDocument(document, startupView);
			Object firstView = controller.contentFor(document);
			assertEquals(startupView, firstView);
			document.content().insert(document.content().length(), " local");
			Files.writeString(path, "external version", StandardCharsets.UTF_8);
			lifecycle.setConflictRequestHandler(ignored -> CompletableFuture.completedFuture(
					GLDocumentConflictChoice.RELOAD));
			assertEquals(null, lifecycle.save().toCompletableFuture().get(5, TimeUnit.SECONDS));
			assertEquals("external version", document.content().getText());
			assertFalse(document.isModified());
			assertNotSame(firstView, controller.contentFor(document));

			document.content().insert(document.content().length(), " retained");
			Files.writeString(path, "another external version", StandardCharsets.UTF_8);
			lifecycle.setConflictRequestHandler(ignored -> CompletableFuture.completedFuture(
					GLDocumentConflictChoice.OVERWRITE));
			assertEquals(path.toAbsolutePath().normalize(), lifecycle.save()
					.toCompletableFuture().get(5, TimeUnit.SECONDS));
			assertEquals("external version retained", Files.readString(path));
			assertEquals(GLDocumentFileState.CURRENT, watcher.refresh(document));
		}
		finally { dialogs.close(); workspace.close(); }
	}
}
