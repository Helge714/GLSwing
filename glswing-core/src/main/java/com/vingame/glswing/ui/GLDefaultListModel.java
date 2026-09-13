package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mutable array-backed {@link GLListModel}. */
public final class GLDefaultListModel<T> implements GLListModel<T> {
	private final List<T> elements = new ArrayList<>();
	private final CopyOnWriteArrayList<Listener> listeners =
			new CopyOnWriteArrayList<>();

	public GLDefaultListModel() { }
	public GLDefaultListModel(Collection<? extends T> elements) {
		if (elements == null)
			throw new IllegalArgumentException("Elements must not be null");
		this.elements.addAll(elements);
	}

	@Override public int getSize() { return elements.size(); }
	@Override public T getElementAt(int index) { return elements.get(index); }
	public List<T> elements() { return List.copyOf(elements); }

	public void addElement(T element) {
		int index = elements.size();
		elements.add(element);
		fire(ChangeType.INTERVAL_ADDED, index, index);
	}
	public void insertElementAt(T element, int index) {
		elements.add(index, element);
		fire(ChangeType.INTERVAL_ADDED, index, index);
	}
	public T setElementAt(T element, int index) {
		T previous = elements.set(index, element);
		fire(ChangeType.CONTENTS_CHANGED, index, index);
		return previous;
	}
	public T removeElementAt(int index) {
		T removed = elements.remove(index);
		fire(ChangeType.INTERVAL_REMOVED, index, index);
		return removed;
	}
	/** Moves one element while preserving the order of all remaining elements. */
	public void moveElement(int fromIndex, int toIndex) {
		if (fromIndex < 0 || fromIndex >= elements.size()
				|| toIndex < 0 || toIndex >= elements.size())
			throw new IndexOutOfBoundsException("List move: " + fromIndex + " -> " + toIndex);
		if (fromIndex == toIndex) return;
		T element = elements.remove(fromIndex);
		elements.add(toIndex, element);
		fire(ChangeType.CONTENTS_CHANGED, Math.min(fromIndex, toIndex),
				Math.max(fromIndex, toIndex));
	}
	public boolean removeElement(T element) {
		int index = elements.indexOf(element);
		if (index < 0) return false;
		removeElementAt(index);
		return true;
	}
	public void clear() {
		if (elements.isEmpty()) return;
		int last = elements.size() - 1;
		elements.clear();
		fire(ChangeType.INTERVAL_REMOVED, 0, last);
	}
	public void setElements(Collection<? extends T> values) {
		if (values == null)
			throw new IllegalArgumentException("Elements must not be null");
		int oldSize = elements.size();
		elements.clear();
		elements.addAll(values);
		int affected = Math.max(oldSize, elements.size());
		if (affected > 0) fire(ChangeType.CONTENTS_CHANGED, 0, affected - 1);
	}

	@Override public void addListDataListener(Listener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Listener must not be null");
		listeners.addIfAbsent(listener);
	}
	@Override public void removeListDataListener(Listener listener) {
		listeners.remove(listener);
	}
	private void fire(ChangeType type, int first, int last) {
		Change change = new Change(type, first, last);
		for (Listener listener : listeners) listener.modelChanged(change);
	}
}
