package com.vingame.glswing.test;

import java.awt.EventQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.file.GLFileBrowser;
import com.vingame.glswing.file.GLFileFilter;
import com.vingame.glswing.file.GLFileSystemTreeModel;

/** CPU checks for lazy filesystem trees and the embeddable file browser. */
public final class GLFileBrowserSelfTest {
	private GLFileBrowserSelfTest() { }
	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("glswing-file-browser-");
		Path sources = Files.createDirectory(root.resolve("sources"));
		Path javaFile = Files.writeString(root.resolve("Example.java"), "class Example { }");
		Path textFile = Files.writeString(root.resolve("notes.txt"), "notes");
		try {
			GLFileSystemTreeModel model = new GLFileSystemTreeModel(List.of(
					new GLFileSystemTreeModel.Location("Test", root)));
			GLFileSystemTreeModel.Node testRoot = model.getRootAt(0);
			check(!model.isLeaf(testRoot), "Filesystem location was not expandable");
			model.loadChildren(testRoot).toCompletableFuture().get(5, TimeUnit.SECONDS);
			check(childPath(model, testRoot, sources), "Lazy tree did not expose directory");

			Path generated = Files.createDirectory(root.resolve("generated"));
			model.refresh(root);
			model.loadChildren(testRoot).toCompletableFuture().get(5, TimeUnit.SECONDS);
			check(childPath(model, testRoot, generated), "Refreshed tree retained stale children");

			AtomicReference<GLFileBrowser> browserReference = new AtomicReference<>();
			AtomicReference<CompletionStage<List<GLFileBrowser.Entry>>> refresh =
					new AtomicReference<>();
			EventQueue.invokeAndWait(() -> {
				GLFileBrowser browser = new GLFileBrowser(root);
				browser.setFileFilter(new GLFileFilter("Java", "java"));
				browser.setMultipleSelection(true);
				browserReference.set(browser);
				refresh.set(browser.refresh());
			});
			GLFileBrowser browser = browserReference.get();
			List<GLFileBrowser.Entry> entries = refresh.get().toCompletableFuture()
					.get(5, TimeUnit.SECONDS);
			check(browser.isMultipleSelection(), "Multiple selection setting was lost");
			check(entries.stream().anyMatch(entry -> entry.path().equals(sources)),
					"File filter hid a navigable directory");
			check(entries.stream().anyMatch(entry -> entry.path().equals(javaFile)),
					"File filter hid a matching file");
			check(entries.stream().noneMatch(entry -> entry.path().equals(textFile)),
					"File filter retained a non-matching file");
			System.out.println("GL file browser self-test passed.");
		} finally {
			Files.deleteIfExists(javaFile);
			Files.deleteIfExists(textFile);
			Files.deleteIfExists(sources);
			Files.deleteIfExists(root.resolve("generated"));
			Files.deleteIfExists(root);
		}
	}
	private static boolean childPath(GLFileSystemTreeModel model,
			GLFileSystemTreeModel.Node parent, Path path) {
		for (int index = 0; index < model.getChildCount(parent); index++)
			if (path.equals(model.getChild(parent, index).path())) return true;
		return false;
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
