package com.vingame.glswing.node.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Mutable graph owner that publishes immutable revisions. Each edit callback is
 * atomic: invalid or failed edits leave the current snapshot untouched.
 */
public final class GLNodeGraph {
	@FunctionalInterface public interface Listener {
		void graphChanged(GLNodeGraph graph, Change change);
	}

	public record Change(String name, GLNodeGraphSnapshot before,
			GLNodeGraphSnapshot after) {
		public Change {
			if (name == null || name.isBlank() || before == null || after == null)
				throw new IllegalArgumentException("Complete graph change required");
		}
	}

	public interface Editor {
		void addNode(GLGraphNode node);
		void removeNode(GLNodeId id);
		void setProperty(GLNodeId nodeId, String key, GLNodeValue value);
        default void setExplicitProperty(GLNodeId nodeId, String key, GLNodeValue value) {
            setProperty(nodeId,key,value);setProperty(nodeId,GLGraphNode.AUTOMATIC_PREFIX+key,GLNodeValue.of(false));
        }
        default void setAutomaticProperty(GLNodeId nodeId, String key, GLNodeValue value) {
            setProperty(nodeId,key,value);setProperty(nodeId,GLGraphNode.AUTOMATIC_PREFIX+key,GLNodeValue.of(true));setProperty(nodeId,GLGraphNode.AUTOMATIC_DEFAULT_PREFIX+key,value);
        }
		void removeProperty(GLNodeId nodeId, String key);
		void addConnection(GLGraphConnection connection);
		void removeConnection(GLNodeConnectionId id);
	}

	private final GLNodeGraphId id;
	private final CopyOnWriteArrayList<Listener> listeners =
			new CopyOnWriteArrayList<>();
	private volatile GLNodeGraphSnapshot snapshot;

	public GLNodeGraph() { this(GLNodeGraphId.random()); }

	public GLNodeGraph(GLNodeGraphId id) {
		if (id == null) throw new IllegalArgumentException("Graph ID required");
		this.id = id;
		snapshot = new GLNodeGraphSnapshot(id, 0, List.of(), List.of());
	}

	public GLNodeGraph(GLNodeGraphSnapshot snapshot) {
		if (snapshot == null) throw new IllegalArgumentException("Snapshot required");
		id = snapshot.graphId();
		this.snapshot = snapshot;
	}

	public GLNodeGraphId id() { return id; }
	public GLNodeGraphSnapshot snapshot() { return snapshot; }

	/**
	 * Applies one atomic graph transaction and reports whether its content
	 * changed. The editor is valid only for the duration of the callback.
	 */
	public boolean edit(String name, Consumer<? super Editor> operation) {
		if (name == null || name.isBlank() || operation == null)
			throw new IllegalArgumentException("Edit name and operation required");
		Change change;
		synchronized (this) {
			GLNodeGraphSnapshot before = snapshot;
			Draft draft = new Draft(before);
			try {
				operation.accept(draft);
			} finally {
				draft.close();
			}
			GLNodeGraphSnapshot after = draft.snapshot(before.revision() + 1);
			if (before.nodes().equals(after.nodes())
					&& before.connections().equals(after.connections())) return false;
			snapshot = after;
			change = new Change(name, before, after);
		}
		for (Listener listener : listeners) listener.graphChanged(this, change);
		return true;
	}

	/** Replaces graph content with a validated snapshot of the same graph. */
	public boolean replace(String name, GLNodeGraphSnapshot replacement) {
		if (replacement == null || !id.equals(replacement.graphId()))
			throw new IllegalArgumentException("Replacement belongs to another graph");
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Replacement name required");
		Change change;
		synchronized (this) {
			GLNodeGraphSnapshot before = snapshot;
			if (before.nodes().equals(replacement.nodes())
					&& before.connections().equals(replacement.connections())) return false;
			GLNodeGraphSnapshot after = new GLNodeGraphSnapshot(id,
					before.revision() + 1, replacement.nodes(), replacement.connections());
			snapshot = after;
			change = new Change(name, before, after);
		}
		for (Listener listener : listeners) listener.graphChanged(this, change);
		return true;
	}

	public void addListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	public void removeListener(Listener listener) { listeners.remove(listener); }

	private final class Draft implements Editor {
		private final LinkedHashMap<GLNodeId, GLGraphNode> nodes =
				new LinkedHashMap<>();
		private final LinkedHashMap<GLNodeConnectionId, GLGraphConnection> connections =
				new LinkedHashMap<>();
		private boolean open = true;

		private Draft(GLNodeGraphSnapshot source) {
			for (GLGraphNode node : source.nodes()) nodes.put(node.id(), node);
			for (GLGraphConnection connection : source.connections())
				connections.put(connection.id(), connection);
		}

		@Override public void addNode(GLGraphNode node) {
			checkOpen();
			if (node == null || nodes.putIfAbsent(node.id(), node) != null)
				throw new IllegalArgumentException("Node ID already exists");
		}

		@Override public void removeNode(GLNodeId id) {
			checkOpen(); requireNode(id);
			nodes.remove(id);
			connections.values().removeIf(connection ->
					connection.input().nodeId().equals(id)
							|| connection.output().nodeId().equals(id));
		}

		@Override public void setProperty(GLNodeId nodeId, String key,
				GLNodeValue value) {
			checkOpen();
			GLGraphNode node = requireNode(nodeId);
			nodes.put(nodeId, node.withProperty(key, value));
		}

		@Override public void removeProperty(GLNodeId nodeId, String key) {
			checkOpen();
			GLGraphNode node = requireNode(nodeId);
			nodes.put(nodeId, node.withoutProperty(key));
		}

		@Override public void addConnection(GLGraphConnection connection) {
			checkOpen();
			if (connection == null
					|| connections.putIfAbsent(connection.id(), connection) != null)
				throw new IllegalArgumentException("Connection ID already exists");
			requireNode(connection.output().nodeId());
			requireNode(connection.input().nodeId());
			for (GLGraphConnection existing : connections.values())
				if (existing != connection && existing.output().equals(connection.output())
						&& existing.input().equals(connection.input()))
					throw new IllegalArgumentException("Connection already exists");
		}

		@Override public void removeConnection(GLNodeConnectionId id) {
			checkOpen();
			if (id == null || connections.remove(id) == null)
				throw new IllegalArgumentException("Unknown connection ID: " + id);
		}

		private GLGraphNode requireNode(GLNodeId id) {
			GLGraphNode node = nodes.get(id);
			if (node == null) throw new IllegalArgumentException("Unknown node ID: " + id);
			return node;
		}

		private GLNodeGraphSnapshot snapshot(long revision) {
			return new GLNodeGraphSnapshot(id, revision,
					new ArrayList<>(nodes.values()),
					new ArrayList<>(connections.values()));
		}
		private void close() { open = false; }
		private void checkOpen() {
			if (!open) throw new IllegalStateException("Graph editor is no longer active");
		}
	}
}
