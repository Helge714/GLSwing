package com.vingame.glswing.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.document.GLDocument;
import com.vingame.glswing.document.GLDocumentAutosaveConfiguration;
import com.vingame.glswing.document.GLDocumentAutosaveCoordinator;
import com.vingame.glswing.document.GLDocumentBundle;
import com.vingame.glswing.document.GLDocumentCodec;
import com.vingame.glswing.document.GLDocumentCodecRegistry;
import com.vingame.glswing.document.GLDocumentManager;
import com.vingame.glswing.document.GLDocumentLifecycle;
import com.vingame.glswing.document.GLDocumentCloseChoice;
import com.vingame.glswing.document.GLPlainTextDocumentCodec;
import com.vingame.glswing.document.GLDocumentTabController;
import com.vingame.glswing.document.GLDocumentWorkspace;
import com.vingame.glswing.document.GLVersionedDocumentCodec;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTextArea;
import com.vingame.glswing.ui.GLTextDocument;
import com.vingame.glswing.file.GLFileDialogs;

/** CPU checks for document state, codec migration, lazy bundles and atomic I/O. */
public final class GLDocumentSelfTest {
	private GLDocumentSelfTest() { }
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("glswing-documents-");
		Path plain = directory.resolve("plain.sample");
		Path oldBundle = directory.resolve("old.glbundle");
		Path upgradedBundle = directory.resolve("upgraded.glbundle");
		Path preservedBundle = directory.resolve("preserved.glbundle");
		Path recoveryDirectory = directory.resolve("recovery");
		try {
			documentStateAndPlainFiles(plain);
			plainTextCodecMetadata();
			documentHistoryRouting();
			liveTextDirtyState();
			documentCloseLifecycle(recoveryDirectory.resolve("lifecycle"));
			documentAutosaveRecovery(recoveryDirectory);
			lazyVersionedBundles(oldBundle, upgradedBundle, preservedBundle);
			System.out.println("GL document self-test passed.");
		} finally {
			Files.deleteIfExists(plain); Files.deleteIfExists(oldBundle);
			Files.deleteIfExists(upgradedBundle); Files.deleteIfExists(preservedBundle);
			Files.deleteIfExists(recoveryDirectory); Files.deleteIfExists(directory);
		}
	}
	private static void documentAutosaveRecovery(Path recoveryDirectory) throws Exception {
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		StringCodecV2 codec = new StringCodecV2(); registry.register(codec);
		GLDocumentAutosaveConfiguration configuration =
				new GLDocumentAutosaveConfiguration(true, Duration.ofMillis(25),
						Duration.ofDays(1));
		Path preferences = recoveryDirectory.resolve("autosave.properties");
		configuration.save(preferences);
		check(configuration.equals(GLDocumentAutosaveConfiguration.load(preferences)),
				"External autosave preferences did not round-trip");
		try (GLDocumentManager manager = new GLDocumentManager(registry);
				GLDocumentAutosaveCoordinator autosave =
						new GLDocumentAutosaveCoordinator(manager, recoveryDirectory,
								configuration)) {
			GLDocument<String> document = manager.create("recover me", "Draft", codec);
			autosave.track(document);
			Path snapshot = recoveryDirectory.resolve(document.id() + ".glrecovery");
			awaitPresent(snapshot);
			var recovery = autosave.scan().toCompletableFuture().get(5, TimeUnit.SECONDS)
					.get(0);
			check(recovery != null && Files.isRegularFile(recovery.snapshotPath()),
					"Autosave did not create an independent recovery snapshot");
			check(autosave.scan().toCompletableFuture().get(5, TimeUnit.SECONDS)
					.equals(java.util.List.of(recovery)),
					"Autosave scan did not return the saved recovery metadata");
			try (GLDocumentAutosaveCoordinator restarted =
					new GLDocumentAutosaveCoordinator(manager, recoveryDirectory,
							configuration)) {
				GLDocument<String> recovered = restarted.recover(recovery, String.class)
						.toCompletableFuture().get(5, TimeUnit.SECONDS);
				check("recover me".equals(recovered.content()) && recovered.isModified()
						&& recovered.id().equals(document.id()) && restarted.isTracked(recovered),
						"Recovered document did not preserve content, identity and dirty state");
				recovered.markPersisted();
				awaitMissing(recovery.snapshotPath());
			}
		}
		Files.deleteIfExists(preferences);
	}
	private static void awaitMissing(Path path) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10);
		check(!Files.exists(path), "Persisted document retained its recovery snapshot");
	}
	private static void awaitPresent(Path path) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10);
		check(Files.isRegularFile(path), "Scheduled autosave did not create a snapshot");
	}
	private static void liveTextDirtyState() throws Exception {
		GLTextDocument text = new GLTextDocument("saved");
		GLDocument<GLTextDocument> document = new GLDocument<>(text, "Notes");
		GLTabbedPane tabs = new GLTabbedPane();
		GLDocumentWorkspace workspace = new GLDocumentWorkspace();
		try (GLDocumentTabController controller =
				new GLDocumentTabController(tabs, workspace)) {
			GLTextArea editor = new GLTextArea(text);
			controller.addDocument(document, editor);
			text.insert(text.length(), "!");
			java.awt.EventQueue.invokeAndWait(() -> { });
			check(document.isModified() && tabs.getTitleAt(0).startsWith("* "),
					"Live text editing did not update the document dirty marker");
			check(controller.commitPendingEdits(document),
					"Pending editor transaction could not be committed before save");
			document.markPersisted();
			java.awt.EventQueue.invokeAndWait(() -> { });
			check(!document.isModified() && !tabs.getTitleAt(0).startsWith("* "),
					"Persisting live text did not clear the dirty marker");
			check(controller.commitPendingEdits(document) && !document.isModified(),
					"A saved editor transaction became dirty again when committed later");
			text.insert(text.length(), "?");
			text.delete(text.length() - 1, text.length());
			java.awt.EventQueue.invokeAndWait(() -> { });
			check(!document.isModified(),
					"Returning live text to its persisted value remained dirty");
		} finally { workspace.close(); }
	}
	private static void plainTextCodecMetadata() {
		GLPlainTextDocumentCodec codec = new GLPlainTextDocumentCodec(
				"example.source-text", "JAVA", ".txt", "*.md");
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		registry.register(codec);
		check(codec.fileExtensions().equals(Set.of("java", "txt", "md")),
				"Plain-text codec did not normalize its extensions");
	}
	private static void documentCloseLifecycle(Path autosaveDirectory) throws Exception {
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		StringCodecV2 codec = new StringCodecV2(); registry.register(codec);
		GLTabbedPane tabs = new GLTabbedPane(); tabs.setTabsClosable(true);
		tabs.setClosedTabHistoryEnabled(true);
		GLDocumentWorkspace workspace = new GLDocumentWorkspace();
		GLFileDialogs dialogs = GLFileDialogs.themed();
		try (GLDocumentManager manager = new GLDocumentManager(registry);
				GLDocumentAutosaveCoordinator autosave =
						new GLDocumentAutosaveCoordinator(manager, autosaveDirectory,
								new GLDocumentAutosaveConfiguration(true,
										Duration.ofDays(1), Duration.ofDays(1)));
				GLDocumentTabController tabController =
						new GLDocumentTabController(tabs, workspace);
				GLDocumentLifecycle<String> lifecycle = new GLDocumentLifecycle<>(manager,
						codec, tabController, dialogs, tabs, () -> "", ignored -> view())) {
			lifecycle.setAutosaveCoordinator(autosave);
			GLDocument<String> document = lifecycle.newDocument();
			check(autosave.isTracked(document),
					"Lifecycle did not register a new document for autosave");
			check(document.isModified() && tabs.getTabCount() == 1 && lifecycle.canClose(),
					"New command did not create a managed modified document");
			check(lifecycle.canSave() && lifecycle.canSaveAs() && lifecycle.canSaveAll(),
					"Save commands were not enabled for a modified writable document");
			document.markPersisted();
			check(!lifecycle.canSave() && lifecycle.canSaveAs() && !lifecycle.canSaveAll(),
					"Save command state did not distinguish a clean document from Save As");
			document.markModified();
			lifecycle.setCloseRequestHandler(ignored ->
					java.util.concurrent.CompletableFuture.completedFuture(
							GLDocumentCloseChoice.CANCEL));
			check(!lifecycle.closeActiveDocument().toCompletableFuture()
					.get(5, TimeUnit.SECONDS) && tabs.getTabCount() == 1,
					"Cancelled close removed the document tab");
			lifecycle.setCloseRequestHandler(ignored ->
					java.util.concurrent.CompletableFuture.completedFuture(
							GLDocumentCloseChoice.DISCARD));
			check(lifecycle.closeActiveDocument().toCompletableFuture()
					.get(5, TimeUnit.SECONDS) && tabs.getTabCount() == 0,
					"Discarded close did not remove the document tab");
			check(!tabs.reopenLastClosedTab(),
					"Discarded document remained in closed-tab history");
			check(!autosave.isTracked(document),
					"Discarded document remained registered for autosave");
			lifecycle.newDocument();
			lifecycle.setCloseRequestHandler(ignored ->
					java.util.concurrent.CompletableFuture.completedFuture(
							GLDocumentCloseChoice.CANCEL));
			check(!lifecycle.prepareCloseAll().toCompletableFuture()
					.get(5, TimeUnit.SECONDS) && tabs.getTabCount() == 1,
					"Cancelled application close removed an open document");
			lifecycle.setCloseRequestHandler(ignored ->
					java.util.concurrent.CompletableFuture.completedFuture(
							GLDocumentCloseChoice.DISCARD));
			check(lifecycle.prepareCloseAll().toCompletableFuture()
					.get(5, TimeUnit.SECONDS) && tabs.getTabCount() == 1,
					"Approved application close mutated the retained tab set");
		} finally { dialogs.close(); workspace.close(); }
	}
	private static void documentHistoryRouting() {
		AtomicInteger applicationValue = new AtomicInteger(1);
		com.vingame.glswing.history.GLHistoryManager applicationHistory =
				new com.vingame.glswing.history.GLHistoryManager();
		applicationHistory.record("Application edit", () -> applicationValue.set(0),
				() -> applicationValue.set(1));
		GLDocument<String> first = new GLDocument<>("first", "First");
		GLDocument<String> second = new GLDocument<>("second", "Second");
		AtomicInteger firstValue = new AtomicInteger(1), secondValue = new AtomicInteger(1);
		first.historyManager().record("First edit", () -> firstValue.set(0),
				() -> firstValue.set(1));
		second.historyManager().record("Second edit", () -> secondValue.set(0),
				() -> secondValue.set(1));

		GLTabbedPane tabs = new GLTabbedPane();
		tabs.setClosedTabHistoryEnabled(true);
		GLDocumentWorkspace workspace = new GLDocumentWorkspace(applicationHistory);
		GLContainer firstView = view(), secondView = view();
		com.vingame.glswing.history.GLHistoryManager previousScope =
				new com.vingame.glswing.history.GLHistoryManager();
		firstView.setGLHistoryManager(previousScope);
		try (GLDocumentTabController controller =
				new GLDocumentTabController(tabs, workspace)) {
			controller.addDocument(first, firstView);
			controller.addDocument(second, secondView);
			check(firstView.getGLHistoryManager() == first.historyManager()
					&& workspace.activeDocument() == first,
					"First document did not establish its history scope");
			check(tabs.getTitleAt(0).startsWith("* "), "Modified document tab lacks marker");
			tabs.setSelectedIndex(1); workspace.undo();
			check(secondValue.get() == 0 && firstValue.get() == 1,
					"Undo was not routed exclusively to the selected document");
			tabs.removeTabAt(1);
			check(workspace.activeDocument() == first,
					"Closing a selected tab did not activate its replacement document");
			check(tabs.reopenLastClosedTab() && workspace.activeDocument() == second,
					"Reopened tab did not restore its active document");
			controller.setActive(false); workspace.undo();
			check(applicationValue.get() == 0,
					"Inactive document tabs did not restore application history routing");
		}
		check(firstView.hasExplicitGLHistoryManager()
				&& firstView.getGLHistoryManager() == previousScope
				&& !secondView.hasExplicitGLHistoryManager(),
				"Closing the tab controller did not restore previous history scopes");
	}
	private static GLContainer view() {
		return new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
	}
	private static void documentStateAndPlainFiles(Path plain) throws Exception {
		GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
		StringCodecV2 codec = new StringCodecV2(); registry.register(codec);
		try (GLDocumentManager manager = new GLDocumentManager(registry)) {
			GLDocument<String> document = manager.create("hello", "Greeting", codec);
			check(document.isModified(), "New document was not dirty");
			manager.saveAs(document, plain).toCompletableFuture().get(5, TimeUnit.SECONDS);
			check(!document.isModified() && plain.toUri().equals(document.location()),
					"Successful save did not establish the persisted checkpoint");
			document.historyManager().record("Edit", () -> { }, () -> { });
			check(document.isModified(), "Document ignored its history checkpoint");
			GLDocument<String> reopened = manager.open(plain, String.class)
					.toCompletableFuture().get(5, TimeUnit.SECONDS);
			check("hello".equals(reopened.content()) && !reopened.isModified(),
					"Plain document did not round-trip through its codec");
		}
	}
	private static void lazyVersionedBundles(Path oldBundle, Path upgradedBundle,
			Path preservedBundle) throws Exception {
		StringCodecV1 oldCodec = new StringCodecV1();
		GLDocumentCodecRegistry oldRegistry = new GLDocumentCodecRegistry();
		oldRegistry.register(oldCodec);
		GLDocumentBundle created = GLDocumentBundle.create(
				"example.project", 4, "test-1", oldRegistry);
		created.put("graph/main", "node graph", oldCodec);
		created.put("theme/editor", "dark theme", oldCodec);
		created.save(oldBundle);

		StringCodecV2 currentCodec = new StringCodecV2();
		GLDocumentCodecRegistry currentRegistry = new GLDocumentCodecRegistry();
		currentRegistry.register(currentCodec);
		GLDocumentBundle opened = GLDocumentBundle.open(oldBundle, currentRegistry);
		check(opened.entries().size() == 2 && !opened.isLoaded("graph/main")
				&& !opened.isLoaded("theme/editor"), "Opening a bundle eagerly decoded entries");
		String graph = opened.open("graph/main", String.class).document().content();
		check("NODE GRAPH".equals(graph) && opened.isLoaded("graph/main")
				&& !opened.isLoaded("theme/editor"), "Entry migration or lazy loading failed");
		opened.save(upgradedBundle);
		GLDocumentBundle upgraded = GLDocumentBundle.open(upgradedBundle, currentRegistry);
		check(upgraded.entries().stream().filter(entry -> entry.id().equals("graph/main"))
				.allMatch(entry -> entry.schemaVersion() == 2)
				&& upgraded.entries().stream().filter(entry -> entry.id().equals("theme/editor"))
						.allMatch(entry -> entry.schemaVersion() == 1),
				"Saving did not upgrade only the decoded entry schema");
		check("dark theme".equals(upgraded.open("theme/editor", String.class)
				.document().content().toLowerCase()), "Unopened entry was not preserved");

		GLDocumentCodecRegistry emptyRegistry = new GLDocumentCodecRegistry();
		GLDocumentBundle opaque = GLDocumentBundle.open(upgradedBundle, emptyRegistry);
		opaque.setCreatedWith("test-2"); opaque.save(preservedBundle);
		check(payload(preservedBundle, "entries/graph/main.sample").length > 0,
				"Unknown unloaded payload was discarded");
	}
	private static byte[] payload(Path bundle, String location) throws IOException {
		try (ZipFile archive = new ZipFile(bundle.toFile())) {
			return archive.getInputStream(archive.getEntry(location)).readAllBytes();
		}
	}
	private abstract static class StringCodec implements GLDocumentCodec<String> {
		@Override public String typeId() { return "example.text"; }
		@Override public String codecId() { return "utf8"; }
		@Override public Class<String> contentType() { return String.class; }
		@Override public Set<String> fileExtensions() { return Set.of("sample"); }
		@Override public void write(String content, OutputStream output) throws IOException {
			output.write(content.getBytes(StandardCharsets.UTF_8));
		}
		protected static String string(InputStream input) throws IOException {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
	private static final class StringCodecV1 extends StringCodec {
		@Override public int currentSchemaVersion() { return 1; }
		@Override public String read(InputStream input, int version) throws IOException {
			if (version != 1) throw new IOException("Unsupported version");
			return string(input);
		}
	}
	private static final class StringCodecV2 extends GLVersionedDocumentCodec<String> {
		private StringCodecV2() { addMigration(1, bytes -> new String(bytes,
				StandardCharsets.UTF_8).toUpperCase().getBytes(StandardCharsets.UTF_8)); }
		@Override public String typeId() { return "example.text"; }
		@Override public String codecId() { return "utf8"; }
		@Override public Class<String> contentType() { return String.class; }
		@Override public int currentSchemaVersion() { return 2; }
		@Override public Set<String> fileExtensions() { return Set.of("sample"); }
		@Override protected String readCurrent(InputStream input) throws IOException {
			return StringCodec.string(input);
		}
		@Override public void write(String content, OutputStream output) throws IOException {
			output.write(content.getBytes(StandardCharsets.UTF_8));
		}
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
