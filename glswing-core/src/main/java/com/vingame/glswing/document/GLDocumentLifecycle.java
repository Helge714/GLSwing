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
	private final GLTabCloseListener closeListener = (source, index, title, content) ->
			releaseDiscardedView(content);
	private GLDocumentCloseRequestHandler closeHandler = document ->
			CompletableFuture.completedFuture(GLDocumentCloseChoice.CANCEL);
	private String untitledName = "Untitled";
	private String openDialogTitle = "Open document";
	private String saveDialogTitle = "Save document";
	private String formatDescription;
	private String allFilesDescription = "All files";
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
					? notifyAfter(manager.save(document)) : saveAsCommitted(document);
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
				? notifyAfter(manager.saveAs(document, result.selectedPath()))
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
	private void addView(GLDocument<T> document) {
		GLContainer view = viewFactory.apply(document);
		if (view == null) throw new IllegalStateException("Document view factory returned null");
		int index = tabs.addDocument(document, view);
		tabs.tabs().setSelectedIndex(index); fireChanged();
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
	private CompletionStage<Path> notifyAfter(CompletionStage<Path> operation) {
		return operation.thenCompose(path -> onUi(() -> { fireChanged(); return path; }));
	}
	private static <R> CompletionStage<R> failed(String message) {
		return CompletableFuture.failedFuture(new IllegalStateException(message));
	}
}
