package com.vingame.glswing.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.layout.GLConstraints;

/** Shared hierarchy implementation for panels and regular GL containers. */
final class GLChildList {

	private final GLParent owner;
	private final CopyOnWriteArrayList<Entry> children =
			new CopyOnWriteArrayList<>();
	private volatile List<Entry> paintOrder = List.of();

	GLChildList(GLParent owner) {
		this.owner = owner;
	}

	void add(GLContainer child) {
		add(child, GLConstraints.DEFAULT);
	}

	void add(GLContainer child, GLConstraints constraints) {
		if (child == null)
			throw new IllegalArgumentException("GL child must not be null");
		if (constraints == null)
			throw new IllegalArgumentException("GL constraints must not be null");
		if (child == owner)
			throw new IllegalArgumentException("A GL container cannot contain itself");
		for (GLParent ancestor = owner; ancestor instanceof GLContainer container;
				ancestor = container.getGLParent())
			if (ancestor == child)
				throw new IllegalArgumentException("Adding this GL child would create a cycle");
		if (child.getGLParent() == owner) {
			setConstraints(child, constraints);
			return;
		}

		child.removeFromGLParent();
		Entry entry = new Entry(child, constraints);
		children.add(entry);
		appendPaintEntry(entry);
		child.setGLParent(owner);
		child.attached(owner);
		child.inheritedThemeChanged();
		child.inheritedLocalizerChanged();
		owner.invalidateGLLayout();
	}

	boolean remove(GLContainer child) {
		if (child == null || child.getGLParent() != owner)
			return false;
		Entry entry = find(child);
		if (entry == null || !children.remove(entry))
			return false;
		removePaintEntry(entry);
		owner.clearGLFocus(child);
		child.setGLParent(null);
		child.detached(owner);
		child.inheritedThemeChanged();
		child.inheritedLocalizerChanged();
		owner.invalidateGLLayout();
		return true;
	}

	void removeAll() {
		for (Entry entry : children)
			remove(entry.child);
	}

	List<GLContainer> snapshot() {
		return children.stream().map(entry -> entry.child).toList();
	}

	List<GLContainer> paintOrderSnapshot() {
		return paintOrder.stream().map(entry -> entry.child).toList();
	}

	boolean isEmpty() {
		return children.isEmpty();
	}

	void setConstraints(GLContainer child, GLConstraints constraints) {
		if (constraints == null)
			throw new IllegalArgumentException("GL constraints must not be null");
		Entry entry = find(child);
		if (entry == null)
			throw new IllegalArgumentException("GL container is not a direct child");
		if (entry.constraints == constraints)
			return;
		entry.constraints = constraints;
		owner.invalidateGLLayout();
	}

	GLConstraints getConstraints(GLContainer child) {
		Entry entry = find(child);
		if (entry == null)
			throw new IllegalArgumentException("GL container is not a direct child");
		return entry.constraints;
	}

	int getZOrder(GLContainer child) {
		Entry entry = find(child);
		if (entry == null)
			throw new IllegalArgumentException("GL container is not a direct child");
		return paintOrder.indexOf(entry);
	}

	synchronized void setZOrder(GLContainer child, int zOrder) {
		Entry entry = find(child);
		if (entry == null)
			throw new IllegalArgumentException("GL container is not a direct child");
		if (zOrder < 0 || zOrder >= paintOrder.size())
			throw new IndexOutOfBoundsException(
					"Z-order must be between 0 and " + (paintOrder.size() - 1));
		int previous = paintOrder.indexOf(entry);
		if (previous == zOrder)
			return;
		List<Entry> changed = new ArrayList<>(paintOrder);
		changed.remove(previous);
		changed.add(zOrder, entry);
		paintOrder = List.copyOf(changed);
		owner.repaintAndRebuildPicking(child);
	}

	void render(GLContext2D context) {
		List<Entry> snapshot = paintOrder;
		for (Entry entry : snapshot)
			entry.child.renderComponent(context);
	}

	private synchronized void appendPaintEntry(Entry entry) {
		List<Entry> changed = new ArrayList<>(paintOrder);
		changed.add(entry);
		paintOrder = List.copyOf(changed);
	}

	private synchronized void removePaintEntry(Entry entry) {
		List<Entry> changed = new ArrayList<>(paintOrder);
		changed.remove(entry);
		paintOrder = List.copyOf(changed);
	}

	private Entry find(GLContainer child) {
		for (Entry entry : children)
			if (entry.child == child)
				return entry;
		return null;
	}

	private static final class Entry {
		private final GLContainer child;
		private volatile GLConstraints constraints;

		private Entry(GLContainer child, GLConstraints constraints) {
			this.child = child;
			this.constraints = constraints;
		}
	}
}
