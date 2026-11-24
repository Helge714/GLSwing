package com.vingame.glswing.document;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.history.GLHistoryManager;

/**
 * A typed editable model with storage metadata and a persisted-state checkpoint.
 * @param <T> application model type
 */
public final class GLDocument<T> {
	@FunctionalInterface public interface Listener<T> {
		void documentChanged(GLDocument<T> document);
	}
	private final UUID id;
	private final GLHistoryManager historyManager;
	private final CopyOnWriteArrayList<Listener<T>> listeners = new CopyOnWriteArrayList<>();
	private final java.util.Set<Object> transientModificationSources =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private T content;
	private URI location;
	private String title;
	private boolean readOnly;
	private long contentRevision;
	private long persistedContentRevision;

	public GLDocument(T content, String title) {
		this(content, title, null, new GLHistoryManager());
	}
	public GLDocument(T content, String title, URI location, GLHistoryManager historyManager) {
		this(UUID.randomUUID(), content, title, location, historyManager);
	}
	GLDocument(UUID id, T content, String title, URI location,
			GLHistoryManager historyManager) {
		if (id == null || content == null || title == null || title.isBlank()
				|| historyManager == null)
			throw new IllegalArgumentException("Content, title, and history are required");
		this.id = id;
		this.content = content; this.title = title; this.location = location;
		this.historyManager = historyManager;
		historyManager.addListener(ignored -> fireChanged());
		historyManager.markPersisted();
	}
	public UUID id() { return id; }
	public T content() { return content; }
	/** Replaces the model and marks the document as modified. */
	public void setContent(T value) {
		if (value == null) throw new IllegalArgumentException("Content required");
		content = value; markModified();
	}
	/** Replaces the model with persisted content and discards the old edit history. */
	void replacePersistedContent(T value) {
		if (value == null) throw new IllegalArgumentException("Content required");
		content = value;
		contentRevision++;
		persistedContentRevision = contentRevision;
		transientModificationSources.clear();
		historyManager.reset();
		fireChanged();
	}
	public URI location() { return location; }
	public void setLocation(URI value) {
		if (java.util.Objects.equals(location, value)) return;
		location = value; fireChanged();
	}
	public String title() { return title; }
	public void setTitle(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Title required");
		if (!title.equals(value)) { title = value; fireChanged(); }
	}
	public boolean isReadOnly() { return readOnly; }
	public void setReadOnly(boolean value) {
		if (readOnly != value) { readOnly = value; fireChanged(); }
	}
	public GLHistoryManager historyManager() { return historyManager; }
	public boolean isModified() {
		return contentRevision != persistedContentRevision || historyManager.isModified()
				|| !transientModificationSources.isEmpty();
	}
	/** Reports a live edit that has not yet become a document-history entry. */
	public void setTransientModified(Object source, boolean modified) {
		if (source == null) throw new IllegalArgumentException("Modification source required");
		boolean changed = modified ? transientModificationSources.add(source)
				: transientModificationSources.remove(source);
		if (changed) fireChanged();
	}
	/** Marks a model mutation that was not recorded through the history manager. */
	public void markModified() { contentRevision++; fireChanged(); }
	/** Establishes the current model and history position as the persisted state. */
	public void markPersisted() {
		persistedContentRevision = contentRevision;
		transientModificationSources.clear();
		historyManager.markPersisted();
	}
	public void addListener(Listener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	public void removeListener(Listener<T> listener) { listeners.remove(listener); }
	private void fireChanged() {
		for (Listener<T> listener : listeners) listener.documentChanged(this);
	}
}
