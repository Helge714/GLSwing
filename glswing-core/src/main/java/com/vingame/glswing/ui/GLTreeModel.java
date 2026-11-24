package com.vingame.glswing.ui;

/** Hierarchical data source used by {@link GLTree}. Nodes must be stable values. */
public interface GLTreeModel<T> {
	record Change<T>(T replacedNode, T replacement) {
		public static <T> Change<T> structureChanged() { return new Change<>(null, null); }
		public static <T> Change<T> nodeReplaced(T node, T replacement) {
			return new Change<>(node, replacement);
		}
		public boolean isReplacement() {
			return replacedNode != null && replacement != null;
		}
	}

	@FunctionalInterface
	interface Listener<T> { void treeStructureChanged(Change<T> change); }

	int getRootCount();
	T getRootAt(int index);
	int getChildCount(T parent);
	T getChild(T parent, int index);

	/** May return {@code false} before lazily loaded children are available. */
	default boolean isLeaf(T node) { return getChildCount(node) == 0; }

	void addTreeModelListener(Listener<T> listener);
	void removeTreeModelListener(Listener<T> listener);
}
