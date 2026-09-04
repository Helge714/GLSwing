package com.vingame.glswing.document;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLTabSelectionListener;
import com.vingame.glswing.ui.GLTabbedPane;

/** Binds tab content subtrees and the selected tab to document history scopes. */
public final class GLDocumentTabController implements AutoCloseable {
	private record Binding(GLDocument<?> document, boolean explicitHistory,
			GLHistoryManager previousHistory,
			com.vingame.glswing.ui.GLTextDocument.Listener textListener,
			java.util.concurrent.atomic.AtomicReference<
					com.vingame.glswing.ui.GLTextDocument.Snapshot> persistedText) { }
	private final GLTabbedPane tabs;
	private final GLDocumentWorkspace workspace;
	private final Map<GLContainer, Binding> documents = new IdentityHashMap<>();
	private final Map<GLDocument<?>, GLDocument.Listener<?>> documentListeners =
			new IdentityHashMap<>();
	private final GLTabSelectionListener selectionListener =
			(source, oldIndex, newIndex) -> selectionChanged();
	private boolean active = true;
	private String modifiedPrefix = "* ";
	private boolean closed;

	public GLDocumentTabController(GLTabbedPane tabs, GLDocumentWorkspace workspace) {
		if (tabs == null || workspace == null)
			throw new IllegalArgumentException("Tabs and workspace required");
		this.tabs = tabs; this.workspace = workspace;
		tabs.addSelectionListener(selectionListener);
	}
	public GLTabbedPane tabs() { return tabs; }
	public GLDocumentWorkspace workspace() { return workspace; }
	public boolean isActive() { return active; }
	/** Controls whether this tab group supplies the workspace's active document. */
	public void setActive(boolean value) {
		if (active == value) return;
		active = value; selectionChanged();
	}
	public String modifiedPrefix() { return modifiedPrefix; }
	public void setModifiedPrefix(String value) {
		if (value == null) throw new IllegalArgumentException("Modified prefix required");
		modifiedPrefix = value;
		for (GLDocument<?> document : documentListeners.keySet()) updateTitles(document);
	}
	/** Adds a tab and makes its entire content subtree inherit the document history. */
	public int addDocument(GLDocument<?> document, GLContainer content) {
		ensureOpen();
		if (document == null || content == null)
			throw new IllegalArgumentException("Document and content required");
		if (documents.containsKey(content))
			throw new IllegalArgumentException("Tab content is already bound to a document");
		boolean explicit = content.hasExplicitGLHistoryManager();
		GLHistoryManager previous = explicit ? content.getGLHistoryManager() : null;
		com.vingame.glswing.ui.GLTextDocument.Listener textListener = null;
		java.util.concurrent.atomic.AtomicReference<
				com.vingame.glswing.ui.GLTextDocument.Snapshot> persistedText = null;
		if (content instanceof com.vingame.glswing.ui.GLTextArea textArea
				&& document.content() == textArea.getDocument()) {
			persistedText = new java.util.concurrent.atomic.AtomicReference<>(
					textArea.getDocument().snapshot());
			var baseline = persistedText;
			textListener = (model, change) -> document.setTransientModified(content,
					!model.snapshot().equals(baseline.get()));
			textArea.getDocument().addDocumentListener(textListener);
		}
		documents.put(content, new Binding(document, explicit, previous,
				textListener, persistedText));
		content.setGLHistoryManager(document.historyManager());
		attach(document);
		try {
			int index = tabs.addTab(title(document), content);
			selectionChanged(); return index;
		} catch (RuntimeException failure) {
			Binding binding = documents.remove(content);
			restoreHistory(content, binding); detachIfUnused(document); throw failure;
		}
	}
	public GLDocument<?> selectedDocument() {
		Binding binding = documents.get(tabs.getSelectedComponent());
		return binding == null ? null : binding.document();
	}
	public GLDocument<?> documentFor(GLContainer content) {
		Binding binding = documents.get(content); return binding == null ? null : binding.document();
	}
	/** Returns each distinct bound document in tab order. */
	public List<GLDocument<?>> documents() {
		java.util.ArrayList<GLDocument<?>> result = new java.util.ArrayList<>();
		for (int index = 0; index < tabs.getTabCount(); index++) {
			GLDocument<?> document = documentFor(tabs.getComponentAt(index));
			if (document != null && result.stream().noneMatch(value -> value == document))
				result.add(document);
		}
		return List.copyOf(result);
	}
	/** Returns the first currently open view for a document. */
	public GLContainer contentFor(GLDocument<?> document) {
		for (int index = 0; index < tabs.getTabCount(); index++) {
			GLContainer content = tabs.getComponentAt(index);
			if (documentFor(content) == document) return content;
		}
		return null;
	}
	/** Commits pending editor-local transactions in every open view of a document. */
	public boolean commitPendingEdits(GLDocument<?> document) {
		if (document == null) throw new IllegalArgumentException("Document required");
		for (Map.Entry<GLContainer, Binding> entry : documents.entrySet())
			if (entry.getValue().document() == document
					&& !commitPendingEdits(entry.getKey())) return false;
		return true;
	}
	private static boolean commitPendingEdits(GLContainer container) {
		if (container instanceof com.vingame.glswing.input.GLEditingHistoryTarget editor
				&& !editor.commitEditingHistory()) return false;
		for (GLContainer child : container.getGLChildren())
			if (!commitPendingEdits(child)) return false;
		return true;
	}
	/** Removes a currently open view and its document binding. */
	public boolean removeDocument(GLContainer content) {
		int index = tabs.indexOfComponent(content);
		if (index < 0) return false;
		Binding binding = documents.get(content);
		if (binding == null) return false;
		GLDocument<?> document = binding.document();
		tabs.removeTabAt(index);
		documents.remove(content);
		restoreHistory(content, binding); detachIfUnused(document);
		selectionChanged(); return true;
	}
	/** Releases a document binding after its tab has already been removed. */
	public boolean releaseClosedDocument(GLContainer content) {
		if (tabs.indexOfComponent(content) >= 0)
			throw new IllegalStateException("Document tab is still open");
		Binding binding = documents.remove(content);
		if (binding == null) return false;
		restoreHistory(content, binding); detachIfUnused(binding.document());
		selectionChanged(); return true;
	}
	@Override public void close() {
		if (closed) return; closed = true;
		tabs.removeSelectionListener(selectionListener);
		if (workspace.activeDocument() != null && containsDocument(workspace.activeDocument()))
			workspace.setActiveDocument(null);
		for (Map.Entry<GLDocument<?>, GLDocument.Listener<?>> entry :
				new IdentityHashMap<>(documentListeners).entrySet())
			detach(entry.getKey(), entry.getValue());
		for (Map.Entry<GLContainer, Binding> entry : documents.entrySet())
			restoreHistory(entry.getKey(), entry.getValue());
		documents.clear(); documentListeners.clear();
	}

	private void selectionChanged() {
		if (closed) return;
		GLDocument<?> selected = active ? selectedDocument() : null;
		if (selected != null) updateTitles(selected);
		if (workspace.activeDocument() != selected
				&& (selected != null || containsDocument(workspace.activeDocument())))
			workspace.setActiveDocument(selected);
	}
	private <T> void attach(GLDocument<T> document) {
		if (documentListeners.containsKey(document)) return;
		GLDocument.Listener<T> listener = ignored -> updateTitles(document);
		document.addListener(listener); documentListeners.put(document, listener);
	}
	private void detachIfUnused(GLDocument<?> document) {
		if (containsDocument(document)) return;
		GLDocument.Listener<?> listener = documentListeners.remove(document);
		if (listener != null) detach(document, listener);
	}
	@SuppressWarnings("unchecked")
	private static void detach(GLDocument<?> document, GLDocument.Listener<?> listener) {
		((GLDocument<Object>) document).removeListener((GLDocument.Listener<Object>) listener);
	}
	private void updateTitles(GLDocument<?> document) {
		if (!java.awt.EventQueue.isDispatchThread()) {
			try {
				java.awt.EventQueue.invokeAndWait(() -> updateTitles(document));
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while updating document tabs", failure);
			} catch (java.lang.reflect.InvocationTargetException failure) {
				Throwable cause = failure.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				if (cause instanceof Error error) throw error;
				throw new IllegalStateException("Could not update document tabs", cause);
			}
			return;
		}
		for (Map.Entry<GLContainer, Binding> entry : documents.entrySet()) {
			if (entry.getValue().document() != document) continue;
			if (!document.isModified() && entry.getValue().persistedText() != null
					&& entry.getKey() instanceof com.vingame.glswing.ui.GLTextArea textArea)
				entry.getValue().persistedText().set(textArea.getDocument().snapshot());
			int index = tabs.indexOfComponent(entry.getKey());
			if (index >= 0) tabs.setTitleAt(index, title(document));
		}
	}
	private boolean containsDocument(GLDocument<?> document) {
		return document != null && documents.values().stream()
				.anyMatch(binding -> binding.document() == document);
	}
	private static void restoreHistory(GLContainer content, Binding binding) {
		if (binding.textListener() != null
				&& content instanceof com.vingame.glswing.ui.GLTextArea textArea) {
			textArea.getDocument().removeDocumentListener(binding.textListener());
			binding.document().setTransientModified(content, false);
		}
		if (binding.explicitHistory()) content.setGLHistoryManager(binding.previousHistory());
		else content.clearGLHistoryManager();
	}
	private String title(GLDocument<?> document) {
		return (document.isModified() ? modifiedPrefix : "") + document.title();
	}
	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Document tab controller is closed");
	}
}
