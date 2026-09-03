package com.vingame.glswing.ui;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaceable index-based selection state for a list view. */
public final class GLListSelectionModel {
	public enum SelectionMode { SINGLE, SINGLE_INTERVAL, MULTIPLE_INTERVAL }
	@FunctionalInterface
	public interface Listener {
		void selectionChanged(GLListSelectionModel source,
				NavigableSet<Integer> oldSelection,
				NavigableSet<Integer> newSelection);
	}

	private final NavigableSet<Integer> selected = new TreeSet<>();
	private final CopyOnWriteArrayList<Listener> listeners =
			new CopyOnWriteArrayList<>();
	private SelectionMode selectionMode = SelectionMode.SINGLE;
	private int anchorIndex = -1;
	private int leadIndex = -1;

	public SelectionMode getSelectionMode() { return selectionMode; }
	public void setSelectionMode(SelectionMode mode) {
		if (mode == null)
			throw new IllegalArgumentException("Selection mode must not be null");
		if (selectionMode == mode) return;
		selectionMode = mode;
		if (mode == SelectionMode.SINGLE && selected.size() > 1)
			setSelectionInterval(leadIndex, leadIndex);
		else if (mode == SelectionMode.SINGLE_INTERVAL && selected.size() > 1)
			setSelectionInterval(selected.first(), selected.last());
	}
	public boolean isSelectionEmpty() { return selected.isEmpty(); }
	public boolean isSelectedIndex(int index) { return selected.contains(index); }
	public int getMinSelectionIndex() { return selected.isEmpty() ? -1 : selected.first(); }
	public int getMaxSelectionIndex() { return selected.isEmpty() ? -1 : selected.last(); }
	public int getAnchorSelectionIndex() { return anchorIndex; }
	public int getLeadSelectionIndex() { return leadIndex; }
	public int[] getSelectedIndices() {
		return selected.stream().mapToInt(Integer::intValue).toArray();
	}

	public void clearSelection() {
		mutate(() -> selected.clear());
		anchorIndex = leadIndex = -1;
	}
	public void setSelectionInterval(int first, int last) {
		requireIndex(first); requireIndex(last);
		int low = Math.min(first, last), high = Math.max(first, last);
		mutate(() -> {
			selected.clear();
			if (selectionMode == SelectionMode.SINGLE) selected.add(last);
			else for (int index = low; index <= high; index++) selected.add(index);
		});
		anchorIndex = first;
		leadIndex = last;
	}
	public void addSelectionInterval(int first, int last) {
		requireIndex(first); requireIndex(last);
		if (selectionMode != SelectionMode.MULTIPLE_INTERVAL) {
			setSelectionInterval(first, last);
			return;
		}
		int low = Math.min(first, last), high = Math.max(first, last);
		mutate(() -> {
			for (int index = low; index <= high; index++) selected.add(index);
		});
		anchorIndex = first;
		leadIndex = last;
	}
	public void removeSelectionInterval(int first, int last) {
		requireIndex(first); requireIndex(last);
		int low = Math.min(first, last), high = Math.max(first, last);
		mutate(() -> selected.subSet(low, true, high, true).clear());
		leadIndex = last;
	}
	public void toggleSelection(int index) {
		requireIndex(index);
		if (selectionMode != SelectionMode.MULTIPLE_INTERVAL) {
			if (selected.contains(index)) clearSelection();
			else setSelectionInterval(index, index);
			return;
		}
		mutate(() -> {
			if (!selected.remove(index)) selected.add(index);
		});
		anchorIndex = leadIndex = index;
	}

	public void addSelectionListener(Listener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Listener must not be null");
		listeners.addIfAbsent(listener);
	}
	public void removeSelectionListener(Listener listener) {
		listeners.remove(listener);
	}

	void insertIndexInterval(int index, int length) {
		if (length <= 0) return;
		mutate(() -> {
			NavigableSet<Integer> shifted = new TreeSet<>();
			for (int selectedIndex : selected)
				shifted.add(selectedIndex >= index ? selectedIndex + length : selectedIndex);
			selected.clear(); selected.addAll(shifted);
		});
		if (anchorIndex >= index) anchorIndex += length;
		if (leadIndex >= index) leadIndex += length;
	}
	void removeIndexInterval(int first, int last) {
		int length = last - first + 1;
		mutate(() -> {
			NavigableSet<Integer> shifted = new TreeSet<>();
			for (int selectedIndex : selected)
				if (selectedIndex < first) shifted.add(selectedIndex);
				else if (selectedIndex > last) shifted.add(selectedIndex - length);
			selected.clear(); selected.addAll(shifted);
		});
		anchorIndex = adjustRemovedIndex(anchorIndex, first, last, length);
		leadIndex = adjustRemovedIndex(leadIndex, first, last, length);
	}
	void retainIndicesBelow(int size) {
		mutate(() -> selected.removeIf(index -> index >= size));
		if (anchorIndex >= size) anchorIndex = -1;
		if (leadIndex >= size) leadIndex = -1;
	}

	private void mutate(Runnable mutation) {
		NavigableSet<Integer> old = snapshot();
		mutation.run();
		if (!old.equals(selected)) {
			NavigableSet<Integer> next = snapshot();
			for (Listener listener : listeners)
				listener.selectionChanged(this, old, next);
		}
	}
	private NavigableSet<Integer> snapshot() {
		return Collections.unmodifiableNavigableSet(new TreeSet<>(selected));
	}
	private static int adjustRemovedIndex(int index, int first, int last, int length) {
		if (index < first) return index;
		if (index > last) return index - length;
		return -1;
	}
	private static void requireIndex(int index) {
		if (index < 0) throw new IllegalArgumentException("Index must not be negative");
	}
}
