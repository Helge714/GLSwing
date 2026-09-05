package com.vingame.glswing.document;

import java.awt.EventQueue;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileDialogs;
import com.vingame.glswing.ui.GLTabCloseRequestHandler;
import com.vingame.glswing.ui.GLTabCloseListener;

/**
 * Coordinates the common new/open/save/close workflow for one document type.
 * Applications retain control over model creation, document views and the
 * presentation of modified-document close choices.
 * @param <T> document model type
 */
public final class GLDocumentLifecycle<T> implements AutoCloseable {
	@FunctionalInterface public interface Listener {
		void lifecycleChanged(GLDocumentLifecycle<?> lifecycle);
	}
	private final GLDocumentManager manager;
	private final GLDocumentCodec<T> codec;
	private final GLDocumentTabController tabs;
	private final GLFileDialogs dialogs;
	private final GLParent owner;
	private final Supplier<T> newContent;
	private final Function<GLDocument<T>, GLContainer> viewFactory;
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private final GLDocumentWorkspace.Listener workspaceListener = ignored -> workspaceChanged();
	private final GLTabCloseRequestHandler previousCloseHandler;
	private final GLTabCloseRequestHandler installedCloseHandler = this::requestTabClose;
	private final java.util.Set<GLContainer> discardedViews = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());
	private final java.util.Set<GLDocument<?>> autosavedDocuments = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());
	private final java.util.Set<GLDocument<?>> watchedDocuments =
			java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<GLDocument<?>> reloadsInFlight =
			java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<GLDocument<?>> writesInFlight =
			java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final GLDocumentFileWatcher.Listener fileListener = this::fileStateChanged;
	private final GLTabCloseListener closeListener = (source, index, title, content) ->
			releaseDiscardedView(content);
	private GLDocumentCloseRequestHandler closeHandler = document ->
			CompletableFuture.completedFuture(GLDocumentCloseChoice.CANCEL);
	private String untitledName = "Untitled";
	private String openDialogTitle = "Open document";
	private String saveDialogTitle = "Save document";
	private String formatDescription;
	private String allFilesDescription = "All files";
	private GLDocumentAutosaveCoordinator autosaveCoordinator;
	private GLDocumentFileWatcher fileWatcher;
	private GLDocumentConflictRequestHandler conflictHandler = request ->
			CompletableFuture.completedFuture(GLDocumentConflictChoice.CANCEL);
	private java.util.function.Consumer<Throwable> reloadFailureHandler = ignored -> { };
	private boolean autoReloadCleanDocuments;
	private boolean closed;

	public GLDocumentLifecycle(GLDocumentManager manager, GLDocumentCodec<T> codec,
			GLDocumentTabController tabs, GLFileDialogs dialogs, GLParent owner,
			Supplier<T> newContent, Function<GLDocument<T>, GLContainer> viewFactory) {
		if (manager == null || codec == null || tabs == null || dialogs == null
				|| owner == null || newContent == null || viewFactory == null)
			throw new IllegalArgumentException("Document lifecycle dependencies required");
		this.manager = manager; this.codec = codec; this.tabs = tabs;
		this.dialogs = dialogs; this.owner = owner; this.newContent = newContent;
		this.viewFactory = viewFactory;
		previousCloseHandler = tabs.tabs().getCloseRequestHandler();
		tabs.tabs().setCloseRequestHandler(installedCloseHandler);
		tabs.tabs().addCloseListener(closeListener);
		tabs.workspace().addListener(workspaceListener);
	}

	public GLDocument<?> activeDocument() {
		return tabs.isActive() ? tabs.selectedDocument() : null;
	}
	public boolean canSave() {
		GLDocument<?> document = activeDocument();
		return canWrite(document) && document.isModified();
	}
	public boolean canSaveAs() { return canWrite(activeDocument()); }
	public boolean canSaveAll() {
		return tabs.documents().stream().anyMatch(document -> document.isModified()
				&& !document.isReadOnly() && manager.isManaged(document));
	}
	public boolean canClose() { return activeDocument() != null; }
	public String untitledName() { return untitledName; }
	public void setUntitledName(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Name required");
		untitledName = value;
	}
	public void setDialogText(String openTitle, String saveTitle,
			String formatDescription, String allFilesDescription) {
		if (openTitle == null || openTitle.isBlank() || saveTitle == null
				|| saveTitle.isBlank() || formatDescription == null
				|| formatDescription.isBlank() || allFilesDescription == null
				|| allFilesDescription.isBlank())
			throw new IllegalArgumentException("Dialog text must not be blank");
		openDialogTitle = openTitle; saveDialogTitle = saveTitle;
		this.formatDescription = formatDescription;
		this.allFilesDescription = allFilesDescription;
	}
	public void setCloseRequestHandler(GLDocumentCloseRequestHandler handler) {
		if (handler == null) throw new IllegalArgumentException("Close handler required");
		closeHandler = handler;
	}
	/**
	 * Attaches an optional autosave service. Existing and subsequently opened
	 * documents are registered automatically; passing {@code null} detaches it.
	 */
	public void setAutosaveCoordinator(GLDocumentAutosaveCoordinator coordinator) {
		ensureOpen();
		if (autosaveCoordinator == coordinator) return;
		GLDocumentAutosaveCoordinator previous = autosaveCoordinator;
		if (previous != null)
			for (GLDocument<?> document : List.copyOf(autosavedDocuments))
				previous.untrack(document);
		autosavedDocuments.clear();
		autosaveCoordinator = coordinator;
		if (coordinator != null)
			for (GLDocument<?> document : tabs.documents()) trackForAutosave(document);
	}
	public GLDocumentAutosaveCoordinator autosaveCoordinator() {
		return autosaveCoordinator;
	}
	/** Attaches an optional external file-change watcher to this lifecycle. */
	public void setFileWatcher(GLDocumentFileWatcher watcher) {
		ensureOpen();
		if (fileWatcher == watcher) return;
		if (fileWatcher != null) {
			fileWatcher.removeListener(fileListener);
			for (GLDocument<?> document : List.copyOf(watchedDocuments))
				fileWatcher.untrack(document);
		}
		watchedDocuments.clear();
		fileWatcher = watcher;
		if (watcher != null) {
			watcher.addListener(fileListener);
			for (GLDocument<?> document : tabs.documents()) trackFile(document);
		}
	}
	public GLDocumentFileWatcher fileWatcher() { return fileWatcher; }
	public void setConflictRequestHandler(GLDocumentConflictRequestHandler handler) {
		if (handler == null) throw new IllegalArgumentException("Conflict handler required");
		conflictHandler = handler;
	}
	/** Automatically reloads externally modified documents that have no local edits. */
	public void setAutoReloadCleanDocuments(boolean value) {
		autoReloadCleanDocuments = value;
	}
	public boolean isAutoReloadCleanDocuments() { return autoReloadCleanDocuments; }
	/** Receives failures from automatic background reload attempts. */
	public void setReloadFailureHandler(java.util.function.Consumer<Throwable> handler) {
		if (handler == null) throw new IllegalArgumentException("Failure handler required");
		reloadFailureHandler = handler;
	}

	/** Creates, manages and opens a new modified document tab. */
	public GLDocument<T> newDocument() {
		ensureOpen();
		GLDocument<T> document = manager.create(newContent.get(), nextUntitledName(), codec);
		addView(document); return document;
	}
	/** Shows the configured file chooser and opens the approved file. */
	public CompletionStage<GLDocument<T>> open() {
		ensureOpen();
		return dialogs.show(openRequest(), owner).thenCompose(result -> result.approved()
				? open(result.selectedPath()) : CompletableFuture.completedFuture(null));
	}
	public CompletionStage<GLDocument<T>> open(Path path) {
		ensureOpen();
		return manager.open(path, codec, codec.currentSchemaVersion())
				.thenCompose(document -> onUi(() -> { addView(document); return document; }));
	}
	/** Adds an already managed document, including a recovered document, as a tab. */
	public int addDocument(GLDocument<T> document) {
		validateDocument(document);
		return addView(document);
	}
	/**
	 * Adds an already constructed document view and enrolls it in every lifecycle
	 * service, including autosave and external file monitoring.
	 */
	public int addDocument(GLDocument<T> document, GLContainer view) {
		validateDocument(document);
		if (view == null) throw new IllegalArgumentException("Document view required");
		return addView(document, view);
	}
	public CompletionStage<Path> save() {
		GLDocument<?> document = activeDocument();
		return document == null ? failed("No active document") : save(document);
	}
	public CompletionStage<Path> saveAs() {
		GLDocument<?> document = activeDocument();
		return document == null ? failed("No active document") : saveAs(document);
	}
	public CompletionStage<List<Path>> saveAll() {
		ensureOpen();
		return saveAll(tabs.documents(), 0, new ArrayList<>());
	}
	/** Reloads a managed document, resets its history, and rebuilds its open view. */
	public CompletionStage<GLDocument<T>> reload(GLDocument<T> document) {
		ensureOpen();
		if (document == null || !manager.isManaged(document))
			return CompletableFuture.failedFuture(
					new IllegalArgumentException("Managed document required"));
		return manager.reload(document).thenCompose(reloaded -> onUi(() -> {
			GLContainer replacement = viewFactory.apply(reloaded);
			if (replacement == null)
				throw new IllegalStateException("Document view factory returned null");
			if (!tabs.replaceDocumentView(reloaded, replacement))
				throw new IllegalStateException("Document has no open view");
			if (fileWatcher != null) fileWatcher.acknowledge(reloaded);
			fireChanged();
			return reloaded;
		}));
	}
	public CompletionStage<Boolean> closeActiveDocument() {
		if (activeDocument() == null) return CompletableFuture.completedFuture(false);
		int index = tabs.tabs().getSelectedIndex();
		return index < 0 ? CompletableFuture.completedFuture(false)
				: tabs.tabs().requestCloseTabAt(index);
	}
	/**
	 * Resolves unsaved changes for every open document before an application
	 * closes. Tabs are retained; an approved caller may tear down the application,
	 * while cancellation leaves the complete interface intact.
	 */
	public CompletionStage<Boolean> prepareCloseAll() {
		ensureOpen();
		return prepareCloseAll(tabs.documents(), 0);
	}

	public void addListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	public void removeListener(Listener listener) { listeners.remove(listener); }

	@Override public void close() {
		if (closed) return;
		closed = true;
		tabs.workspace().removeListener(workspaceListener);
		tabs.tabs().removeCloseListener(closeListener);
		if (tabs.tabs().getCloseRequestHandler() == installedCloseHandler)
			tabs.tabs().setCloseRequestHandler(previousCloseHandler);
		synchronized (discardedViews) { discardedViews.clear(); }
		if (autosaveCoordinator != null)
			for (GLDocument<?> document : List.copyOf(autosavedDocuments))
				autosaveCoordinator.untrack(document);
		autosavedDocuments.clear();
		autosaveCoordinator = null;
		if (fileWatcher != null) {
			fileWatcher.removeListener(fileListener);
			for (GLDocument<?> document : List.copyOf(watchedDocuments))
				fileWatcher.untrack(document);
		}
		watchedDocuments.clear(); reloadsInFlight.clear(); writesInFlight.clear(); fileWatcher = null;
		listeners.clear();
	}

	private CompletionStage<Boolean> requestTabClose(com.vingame.glswing.ui.GLTabbedPane source,
			int index, String title, GLContainer content) {
		GLDocument<?> document = tabs.documentFor(content);
		CompletionStage<Boolean> own = document == null || !document.isModified()
				? CompletableFuture.completedFuture(true)
				: closeHandler.requestClose(document).thenCompose(choice -> switch (
						choice == null ? GLDocumentCloseChoice.CANCEL : choice) {
				case SAVE -> save(document).thenApply(path -> path != null);
				case DISCARD -> {
					synchronized (discardedViews) { discardedViews.add(content); }
					yield CompletableFuture.completedFuture(true);
				}
				case CANCEL -> CompletableFuture.completedFuture(false);
			});
		CompletionStage<Boolean> combined = previousCloseHandler == null ? own
				: own.thenCompose(approved -> approved
				? previousCloseHandler.requestClose(source, index, title, content)
				: CompletableFuture.completedFuture(false));
		return combined.whenComplete((approved, failure) -> {
			if (failure != null || !Boolean.TRUE.equals(approved))
				synchronized (discardedViews) { discardedViews.remove(content); }
		});
	}
	private void releaseDiscardedView(GLContainer content) {
		synchronized (discardedViews) {
			if (!discardedViews.remove(content)) return;
		}
		GLDocument<?> document = tabs.documentFor(content);
		untrackForAutosave(document);
		untrackFile(document);
		tabs.tabs().forgetClosedTab(content);
		tabs.releaseClosedDocument(content);
		fireChanged();
	}

	private CompletionStage<Path> save(GLDocument<?> document) {
		if (document.isReadOnly()) return failed("Document is read-only");
		return commitPendingEdits(document).thenCompose(committed -> {
			if (!committed) return CompletableFuture.completedFuture(null);
			URI location = document.location();
			return location != null && "file".equalsIgnoreCase(location.getScheme())
					? saveFileBacked(document) : saveAsCommitted(document);
		});
	}
	private CompletionStage<Path> saveFileBacked(GLDocument<?> document) {
		GLDocumentFileState state = fileWatcher == null
				? GLDocumentFileState.CURRENT : fileWatcher.refresh(document);
		if (state == GLDocumentFileState.CURRENT)
			return performSave(document, () -> manager.save(document));
		Path path = Path.of(document.location()).toAbsolutePath().normalize();
		CompletionStage<GLDocumentConflictChoice> decision;
		try {
			decision = conflictHandler.requestResolution(
					new GLDocumentConflictRequest(document, path, state));
		} catch (RuntimeException failure) {
			return CompletableFuture.failedFuture(failure);
		}
		if (decision == null) return CompletableFuture.completedFuture(null);
		return decision.thenCompose(choice -> switch (
				choice == null ? GLDocumentConflictChoice.CANCEL : choice) {
			case OVERWRITE -> performSave(document, () -> manager.save(document));
			case SAVE_AS -> saveAsCommitted(document);
			case RELOAD -> reloadUnchecked(document).thenApply(ignored -> null);
			case CANCEL -> CompletableFuture.completedFuture(null);
		});
	}
	private CompletionStage<Path> saveAs(GLDocument<?> document) {
		if (document.isReadOnly()) return failed("Document is read-only");
		return commitPendingEdits(document).thenCompose(committed -> committed
				? saveAsCommitted(document) : CompletableFuture.completedFuture(null));
	}
	private CompletionStage<Path> saveAsCommitted(GLDocument<?> document) {
		Path initialDirectory = null;
		if (document.location() != null && "file".equalsIgnoreCase(document.location().getScheme())) {
			Path current = Path.of(document.location()); initialDirectory = current.getParent();
		}
		GLFileDialogRequest.Builder request = GLFileDialogRequest.save()
				.title(saveDialogTitle).suggestedFileName(document.title());
		if (initialDirectory != null) request.initialDirectory(initialDirectory);
		addCodecFilter(request);
		return dialogs.show(request.build(), owner).thenCompose(result -> result.approved()
				? performSave(document, () -> manager.saveAs(document, result.selectedPath()))
				: CompletableFuture.completedFuture(null));
	}
	private CompletionStage<Boolean> commitPendingEdits(GLDocument<?> document) {
		return onUi(() -> tabs.commitPendingEdits(document));
	}
	private GLFileDialogRequest openRequest() {
		GLFileDialogRequest.Builder request = GLFileDialogRequest.open().title(openDialogTitle);
		addCodecFilter(request); return request.build();
	}
	private void addCodecFilter(GLFileDialogRequest.Builder request) {
		if (!codec.fileExtensions().isEmpty()) request.filter(
				formatDescription == null ? codec.typeId() : formatDescription,
				codec.fileExtensions().toArray(String[]::new));
		request.filter(allFilesDescription);
	}
	private int addView(GLDocument<T> document) {
		GLContainer view = viewFactory.apply(document);
		if (view == null) throw new IllegalStateException("Document view factory returned null");
		return addView(document, view);
	}
	private int addView(GLDocument<T> document, GLContainer view) {
		int index = tabs.addDocument(document, view);
		try {
			trackForAutosave(document);
			trackFile(document);
		}
		catch (RuntimeException failure) {
			untrackForAutosave(document);
			untrackFile(document);
			tabs.removeDocument(view);
			throw failure;
		}
		tabs.tabs().setSelectedIndex(index); fireChanged(); return index;
	}
	private void validateDocument(GLDocument<T> document) {
		ensureOpen();
		if (document == null || !codec.contentType().isInstance(document.content())
				|| !manager.isManaged(document))
			throw new IllegalArgumentException(
					"Document must be managed and compatible with this lifecycle");
	}
	private void trackForAutosave(GLDocument<?> document) {
		if (autosaveCoordinator == null || autosavedDocuments.contains(document)) return;
		autosaveCoordinator.track(document);
		autosavedDocuments.add(document);
	}
	private void untrackForAutosave(GLDocument<?> document) {
		if (autosaveCoordinator != null && document != null
				&& autosavedDocuments.remove(document))
			autosaveCoordinator.untrack(document);
	}
	private void trackFile(GLDocument<?> document) {
		if (fileWatcher == null || watchedDocuments.contains(document)) return;
		if (fileWatcher.track(document)) watchedDocuments.add(document);
	}
	private void untrackFile(GLDocument<?> document) {
		if (fileWatcher != null && document != null && watchedDocuments.remove(document))
			fileWatcher.untrack(document);
	}
	private void fileStateChanged(GLDocumentFileEvent event) {
		if (closed || !watchedDocuments.contains(event.document())) return;
		if (event.state() == GLDocumentFileState.MODIFIED
				&& autoReloadCleanDocuments && !event.document().isModified()) {
			synchronized (reloadsInFlight) {
				synchronized (writesInFlight) {
					if (writesInFlight.contains(event.document())) return;
				}
				if (!reloadsInFlight.add(event.document())) return;
			}
			reloadUnchecked(event.document()).whenComplete((document, failure) -> {
				synchronized (reloadsInFlight) { reloadsInFlight.remove(event.document()); }
				if (failure != null) reloadFailureHandler.accept(unwrap(failure));
			});
		}
		if (EventQueue.isDispatchThread()) fireChanged();
		else EventQueue.invokeLater(this::fireChanged);
	}
	@SuppressWarnings("unchecked")
	private CompletionStage<GLDocument<T>> reloadUnchecked(GLDocument<?> document) {
		return reload((GLDocument<T>) document);
	}
	private CompletionStage<List<Path>> saveAll(List<GLDocument<?>> documents,
			int index, ArrayList<Path> paths) {
		if (index >= documents.size()) return CompletableFuture.completedFuture(List.copyOf(paths));
		GLDocument<?> document = documents.get(index);
		if (!document.isModified() || document.isReadOnly())
			return saveAll(documents, index + 1, paths);
		return save(document).thenCompose(path -> {
			if (path == null) return CompletableFuture.completedFuture(List.copyOf(paths));
			paths.add(path); return saveAll(documents, index + 1, paths);
		});
	}
	private CompletionStage<Boolean> prepareCloseAll(List<GLDocument<?>> documents,
			int index) {
		if (index >= documents.size()) return CompletableFuture.completedFuture(true);
		GLDocument<?> document = documents.get(index);
		if (!document.isModified()) return prepareCloseAll(documents, index + 1);
		CompletionStage<GLDocumentCloseChoice> decision;
		try { decision = closeHandler.requestClose(document); }
		catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
		if (decision == null) return CompletableFuture.completedFuture(false);
		return decision.thenCompose(choice -> switch (
				choice == null ? GLDocumentCloseChoice.CANCEL : choice) {
			case SAVE -> save(document).thenCompose(path -> path == null
					? CompletableFuture.completedFuture(false)
					: prepareCloseAll(documents, index + 1));
			case DISCARD -> prepareCloseAll(documents, index + 1);
			case CANCEL -> CompletableFuture.completedFuture(false);
		});
	}
	private String nextUntitledName() {
		List<String> titles = tabs.documents().stream().map(GLDocument::title).toList();
		if (!titles.contains(untitledName)) return untitledName;
		for (int number = 2;; number++) {
			String candidate = untitledName + " " + number;
			if (!titles.contains(candidate)) return candidate;
		}
	}
	private boolean canWrite(GLDocument<?> document) {
		return document != null && !document.isReadOnly() && manager.isManaged(document);
	}
	private void fireChanged() { for (Listener listener : listeners) listener.lifecycleChanged(this); }
	private void workspaceChanged() {
		if (EventQueue.isDispatchThread()) fireChanged();
		else EventQueue.invokeLater(this::fireChanged);
	}
	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Document lifecycle is closed");
	}
	private static <R> CompletionStage<R> onUi(Supplier<R> action) {
		if (EventQueue.isDispatchThread()) {
			try { return CompletableFuture.completedFuture(action.get()); }
			catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
		}
		CompletableFuture<R> result = new CompletableFuture<>();
		EventQueue.invokeLater(() -> {
			try { result.complete(action.get()); }
			catch (RuntimeException failure) { result.completeExceptionally(failure); }
		});
		return result;
	}
	private CompletionStage<Path> performSave(GLDocument<?> document,
			Supplier<CompletionStage<Path>> operation) {
		synchronized (writesInFlight) { writesInFlight.add(document); }
		CompletionStage<Path> started;
		try { started = operation.get(); }
		catch (RuntimeException failure) {
			synchronized (writesInFlight) { writesInFlight.remove(document); }
			return CompletableFuture.failedFuture(failure);
		}
		return started.whenComplete((path, failure) -> {
			if (failure == null && fileWatcher != null) {
				fileWatcher.acknowledge(document);
				watchedDocuments.add(document);
			}
			synchronized (writesInFlight) { writesInFlight.remove(document); }
		}).thenCompose(path -> onUi(() -> { fireChanged(); return path; }));
	}
	private static Throwable unwrap(Throwable failure) {
		Throwable result = failure;
		while ((result instanceof java.util.concurrent.CompletionException
				|| result instanceof java.util.concurrent.ExecutionException)
				&& result.getCause() != null) result = result.getCause();
		return result;
	}
	private static <R> CompletionStage<R> failed(String message) {
		return CompletableFuture.failedFuture(new IllegalStateException(message));
	}
}
