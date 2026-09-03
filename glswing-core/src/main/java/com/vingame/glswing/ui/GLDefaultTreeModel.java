package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mutable ordered tree model whose node values must be unique. */
public final class GLDefaultTreeModel<T> implements GLTreeModel<T> {
	private final List<T> roots = new ArrayList<>();
	private final Map<T, List<T>> children = new LinkedHashMap<>();
	private final Map<T, T> parents = new HashMap<>();
	private final Set<T> known = new HashSet<>();
	private final Set<T> allowsChildren = new HashSet<>();
	private final CopyOnWriteArrayList<Listener<T>> listeners =
			new CopyOnWriteArrayList<>();

	public GLDefaultTreeModel() { }
	public GLDefaultTreeModel(Collection<? extends T> roots) {
		if (roots == null) throw new IllegalArgumentException("Roots must not be null");
		for (T root : roots) addRootInternal(root);
	}

	@Override public int getRootCount() { return roots.size(); }
	@Override public T getRootAt(int index) { return roots.get(index); }
	@Override public int getChildCount(T parent) {
		return children.getOrDefault(requireKnown(parent), List.of()).size();
	}
	@Override public T getChild(T parent, int index) {
		return children.getOrDefault(requireKnown(parent), List.of()).get(index);
	}
	@Override public boolean isLeaf(T node) {
		requireKnown(node);
		return !allowsChildren.contains(node) && getChildCount(node) == 0;
	}
	public List<T> roots() { return List.copyOf(roots); }
	public T getParent(T node) { return parents.get(requireKnown(node)); }
	public boolean contains(T node) { return known.contains(node); }

	public void addRoot(T root) { addRootInternal(root); fire(); }
	public void addChild(T parent, T child) {
		requireKnown(parent); requireNew(child);
		known.add(child);
		parents.put(child, parent);
		children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(child);
		allowsChildren.add(parent);
		fire();
	}
	/** Replaces all direct children and their subtrees with one model notification. */
	public void setChildren(T parent, Collection<? extends T> values) {
		requireKnown(parent);
		if (values == null) throw new IllegalArgumentException("Children must not be null");
		List<T> existing = new ArrayList<>(children.getOrDefault(parent, List.of()));
		List<T> replacements = new ArrayList<>();
		Set<T> unique = new HashSet<>();
		for (T child : values) {
			if (child == null) throw new IllegalArgumentException("Tree nodes must not be null");
			if (known.contains(child) && !existing.contains(child))
				throw new IllegalArgumentException("Tree nodes must be unique: " + child);
			if (!unique.add(child))
				throw new IllegalArgumentException("Tree nodes must be unique: " + child);
			replacements.add(child);
		}
		for (T child : existing) {
			if (unique.contains(child)) continue;
			parents.remove(child);
			removeDescendants(child);
		}
		if (replacements.isEmpty()) {
			children.remove(parent);
			allowsChildren.remove(parent);
		} else {
			children.put(parent, replacements);
			allowsChildren.add(parent);
			for (T child : replacements) {
				known.add(child);
				parents.put(child, parent);
			}
		}
		fire();
	}
	/** Marks an empty node as expandable, for example while loading children. */
	public void setAllowsChildren(T node, boolean value) {
		requireKnown(node);
		if (value ? allowsChildren.add(node) : allowsChildren.remove(node)) fire();
	}
	/** Updates the lazy-expandable marker for multiple nodes in one notification. */
	public void setAllowsChildren(Collection<? extends T> nodes, boolean value) {
		if (nodes == null) throw new IllegalArgumentException("Nodes must not be null");
		List<T> checked = new ArrayList<>();
		for (T node : nodes) checked.add(requireKnown(node));
		boolean changed = false;
		for (T node : checked)
			changed |= value ? allowsChildren.add(node) : allowsChildren.remove(node);
		if (changed) fire();
	}
	/** Replaces one node value without changing its position or descendants. */
	public void replaceNode(T node, T replacement) {
		requireKnown(node); requireNew(replacement);
		T parent = parents.remove(node);
		List<T> siblings = parent == null ? roots : children.get(parent);
		siblings.set(siblings.indexOf(node), replacement);
		if (parent != null) parents.put(replacement, parent);
		List<T> descendants = children.remove(node);
		if (descendants != null) {
			children.put(replacement, descendants);
			for (T child : descendants) parents.put(child, replacement);
		}
		if (allowsChildren.remove(node)) allowsChildren.add(replacement);
		known.remove(node); known.add(replacement);
		fire(GLTreeModel.Change.nodeReplaced(node, replacement));
	}
	public boolean removeSubtree(T node) {
		if (!known.contains(node)) return false;
		T parent = parents.remove(node);
		if (parent == null) roots.remove(node);
		else children.getOrDefault(parent, List.of()).remove(node);
		removeDescendants(node);
		fire();
		return true;
	}
	public void clear() {
		if (known.isEmpty()) return;
		roots.clear(); children.clear(); parents.clear(); known.clear();
		allowsChildren.clear(); fire();
	}

	@Override public void addTreeModelListener(Listener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	@Override public void removeTreeModelListener(Listener<T> listener) {
		listeners.remove(listener);
	}
	private void addRootInternal(T root) { requireNew(root); known.add(root); roots.add(root); }
	private void removeDescendants(T node) {
		for (T child : new ArrayList<>(children.getOrDefault(node, List.of()))) {
			parents.remove(child); removeDescendants(child);
		}
		children.remove(node); allowsChildren.remove(node); known.remove(node);
	}
	private T requireKnown(T node) {
		if (node == null || !known.contains(node))
			throw new IllegalArgumentException("Unknown tree node: " + node);
		return node;
	}
	private void requireNew(T node) {
		if (node == null) throw new IllegalArgumentException("Tree nodes must not be null");
		if (known.contains(node))
			throw new IllegalArgumentException("Tree nodes must be unique: " + node);
	}
	private void fire() { fire(GLTreeModel.Change.structureChanged()); }
	private void fire(GLTreeModel.Change<T> change) {
		for (Listener<T> listener : listeners) listener.treeStructureChanged(change);
	}
}
