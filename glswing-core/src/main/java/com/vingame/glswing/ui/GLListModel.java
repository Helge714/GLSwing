package com.vingame.glswing.ui;

/** Read-only data source used by {@link GLList}. */
public interface GLListModel<T> {
	enum ChangeType { CONTENTS_CHANGED, INTERVAL_ADDED, INTERVAL_REMOVED }
	record Change(ChangeType type, int firstIndex, int lastIndex) {
		public Change {
			if (type == null || firstIndex < 0 || lastIndex < firstIndex)
				throw new IllegalArgumentException("Invalid list-model change");
		}
	}
	@FunctionalInterface
	interface Listener { void modelChanged(Change change); }

	int getSize();
	T getElementAt(int index);
	void addListDataListener(Listener listener);
	void removeListDataListener(Listener listener);
}
