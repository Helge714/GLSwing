package com.vingame.glswing.document;

import java.awt.event.KeyEvent;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.history.GLHistoryManagerListener;
import com.vingame.glswing.input.GLInputMap;

/** Routes application-level history actions to the currently active document. */
public final class GLDocumentWorkspace implements AutoCloseable {
	@FunctionalInterface public interface Listener {
		void workspaceChanged(GLDocumentWorkspace workspace);
	}
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private final GLHistoryManagerListener fallbackListener = ignored -> fire();
	private GLHistoryManager fallbackHistory;
	private GLDocument<?> activeDocument;
	private GLDocument.Listener<Object> activeDocumentListener;
	private boolean closed;

	public GLDocumentWorkspace() { this(null); }
	public GLDocumentWorkspace(GLHistoryManager fallbackHistory) {
		setFallbackHistoryManager(fallbackHistory);
	}
	public GLDocument<?> activeDocument() { return activeDocument; }
	public void setActiveDocument(GLDocument<?> document) {
		ensureOpen();
		if (activeDocument == document) return;
		detachActiveDocument(); activeDocument = document; attachActiveDocument(); fire();
	}
	public GLHistoryManager fallbackHistoryManager() { return fallbackHistory; }
	public void setFallbackHistoryManager(GLHistoryManager manager) {
		ensureOpen();
		if (fallbackHistory == manager) return;
		if (fallbackHistory != null) fallbackHistory.removeListener(fallbackListener);
		fallbackHistory = manager;
		if (fallbackHistory != null) fallbackHistory.addListener(fallbackListener);
		fire();
	}
	/** Returns the active document history, or the configured application fallback. */
	public GLHistoryManager historyManager() {
		return activeDocument == null ? fallbackHistory : activeDocument.historyManager();
	}
	public boolean canUndo() {
		GLHistoryManager history = historyManager(); return history != null && history.canUndo();
	}
	public boolean canRedo() {
		GLHistoryManager history = historyManager(); return history != null && history.canRedo();
	}
	public String undoName() {
		GLHistoryManager history = historyManager(); return history == null ? null : history.getUndoName();
	}
	public String redoName() {
		GLHistoryManager history = historyManager(); return history == null ? null : history.getRedoName();
	}
	public boolean undo() {
		GLHistoryManager history = historyManager(); return history != null && history.undo();
	}
	public boolean redo() {
		GLHistoryManager history = historyManager(); return history != null && history.redo();
	}
	public boolean handleKeyPressed(KeyEvent event, GLInputMap inputMap) {
		GLHistoryManager history = historyManager();
		return history != null && history.handleKeyPressed(event, inputMap);
	}
	public void addListener(Listener listener) {
		ensureOpen();
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	public void removeListener(Listener listener) { listeners.remove(listener); }
	@Override public void close() {
		if (closed) return;
		detachActiveDocument();
		if (fallbackHistory != null) fallbackHistory.removeListener(fallbackListener);
		activeDocument = null; fallbackHistory = null; listeners.clear(); closed = true;
	}

	@SuppressWarnings("unchecked")
	private void attachActiveDocument() {
		if (activeDocument == null) return;
		activeDocumentListener = ignored -> fire();
		((GLDocument<Object>) activeDocument).addListener(activeDocumentListener);
	}
	@SuppressWarnings("unchecked")
	private void detachActiveDocument() {
		if (activeDocument != null && activeDocumentListener != null)
			((GLDocument<Object>) activeDocument).removeListener(activeDocumentListener);
		activeDocumentListener = null;
	}
	private void fire() { for (Listener listener : listeners) listener.workspaceChanged(this); }
	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Document workspace is closed");
	}
}
