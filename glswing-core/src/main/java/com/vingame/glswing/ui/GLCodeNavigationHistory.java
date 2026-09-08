package com.vingame.glswing.ui;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded browser-style history of source locations shared by code editors in
 * one workspace. This history is independent of document undo and redo.
 */
public final class GLCodeNavigationHistory {
	private static final int DEFAULT_LIMIT = 100;
	private final ArrayDeque<GLCodeLocation> back = new ArrayDeque<>();
	private final ArrayDeque<GLCodeLocation> forward = new ArrayDeque<>();
	private final CopyOnWriteArrayList<Runnable> listeners =
			new CopyOnWriteArrayList<>();
	private int limit;

	public GLCodeNavigationHistory() { this(DEFAULT_LIMIT); }

	public GLCodeNavigationHistory(int limit) {
		setLimit(limit);
	}

	public int getLimit() { return limit; }

	/** Changes the retained entry limit and trims the oldest locations. */
	public void setLimit(int value) {
		if (value < 1) throw new IllegalArgumentException("History limit must be positive");
		if (limit == value) return;
		limit = value;
		trim(back);
		trim(forward);
		changed();
	}

	public boolean canNavigateBack() { return !back.isEmpty(); }
	public boolean canNavigateForward() { return !forward.isEmpty(); }
	public int getBackSize() { return back.size(); }
	public int getForwardSize() { return forward.size(); }

	/**
	 * Records the location being left by a new navigation and clears the forward
	 * branch. Consecutive duplicate locations are coalesced.
	 */
	public void recordDeparture(GLCodeLocation location) {
		if (location == null) throw new IllegalArgumentException("Source location required");
		boolean changed = !forward.isEmpty();
		forward.clear();
		if (!location.equals(back.peekLast())) {
			back.addLast(location);
			trim(back);
			changed = true;
		}
		if (changed) changed();
	}

	/** Returns and consumes the preceding location for the supplied current one. */
	public Optional<GLCodeLocation> navigateBack(GLCodeLocation current) {
		return navigate(back, forward, current);
	}

	/** Returns and consumes the following location for the supplied current one. */
	public Optional<GLCodeLocation> navigateForward(GLCodeLocation current) {
		return navigate(forward, back, current);
	}

	public void clear() {
		if (back.isEmpty() && forward.isEmpty()) return;
		back.clear();
		forward.clear();
		changed();
	}

	public void addChangeListener(Runnable listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}

	public void removeChangeListener(Runnable listener) {
		listeners.remove(listener);
	}

	private Optional<GLCodeLocation> navigate(ArrayDeque<GLCodeLocation> source,
			ArrayDeque<GLCodeLocation> destination, GLCodeLocation current) {
		if (current == null) return Optional.empty();
		while (!source.isEmpty()) {
			GLCodeLocation target = source.removeLast();
			if (target.equals(current)) continue;
			append(destination, current);
			changed();
			return Optional.of(target);
		}
		changed();
		return Optional.empty();
	}

	private void append(ArrayDeque<GLCodeLocation> entries, GLCodeLocation value) {
		if (!value.equals(entries.peekLast())) entries.addLast(value);
		trim(entries);
	}

	private void trim(ArrayDeque<GLCodeLocation> entries) {
		while (entries.size() > limit) entries.removeFirst();
	}

	private void changed() {
		for (Runnable listener : listeners) listener.run();
	}
}
