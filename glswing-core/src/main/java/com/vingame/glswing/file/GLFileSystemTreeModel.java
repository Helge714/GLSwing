package com.vingame.glswing.file;

import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.filechooser.FileSystemView;

import com.vingame.glswing.ui.GLDefaultTreeModel;
import com.vingame.glswing.ui.GLTreeModel;

/**
 * Asynchronously populated directory tree model. Nodes use identity semantics,
 * allowing the same path to appear below a shortcut and a filesystem root.
 */
public final class GLFileSystemTreeModel implements GLTreeModel<GLFileSystemTreeModel.Node> {
	/** Describes the presentation and behavior of a filesystem tree node. */
	public enum Kind { PLACE, COMPUTER, DRIVE, DIRECTORY, LOADING, ERROR }
	/** A named filesystem shortcut shown as a root alongside the computer root. */
	public record Location(String label, Path path) {
		public Location {
			if (label == null || label.isBlank()) throw new IllegalArgumentException("Label required");
			if (path == null) throw new IllegalArgumentException("Path required");
			path = path.toAbsolutePath().normalize();
		}
	}
	public static final class Node {
		private final String label;
		private final Path path;
		private final Kind kind;
		private State state;
		private long generation;
		private CompletableFuture<Void> loading;
		private Node(String label, Path path, Kind kind, State state) {
			this.label = label; this.path = path; this.kind = kind; this.state = state;
		}
		public String label() { return label; }
		public Path path() { return path; }
		public Kind kind() { return kind; }
		public boolean isStatus() { return kind == Kind.LOADING || kind == Kind.ERROR; }
	}
	private enum State { STATIC, UNLOADED, LOADING, LOADED, FAILED }
	private record Directory(Path path, String label) { }

	private final GLDefaultTreeModel<Node> delegate = new GLDefaultTreeModel<>();
	private final List<Node> pathNodes = new CopyOnWriteArrayList<>();
	private List<Location> locations = List.of();
	private String computerLabel = "This PC";
	private String loadingLabel = "Loading…";
	private String errorLabel = "Cannot read this location";
	private boolean includeHidden;
	private long resetGeneration;

	/** Creates a model containing only the current filesystem's roots. */
	public GLFileSystemTreeModel() {
		reset(List.of(), computerLabel, loadingLabel, errorLabel);
	}
	/** Creates a model with named shortcuts followed by the filesystem roots. */
	public GLFileSystemTreeModel(Collection<Location> locations) {
		reset(locations, computerLabel, loadingLabel, errorLabel);
	}
	public List<Location> getLocations() { return locations; }

	/** Rebuilds the common-place and filesystem roots with caller-provided labels. */
	public void reset(Collection<Location> locations, String computerLabel,
			String loadingLabel, String errorLabel) {
		if (locations == null) throw new IllegalArgumentException("Locations required");
		this.locations = List.copyOf(locations);
		this.computerLabel = requireText(computerLabel, "Computer label");
		this.loadingLabel = requireText(loadingLabel, "Loading label");
		this.errorLabel = requireText(errorLabel, "Error label");
		resetGeneration++;
		delegate.clear();
		pathNodes.clear();
		for (Location location : this.locations) {
			if (!Files.isDirectory(location.path())) continue;
			Node node = pathNode(location.label(), location.path(), Kind.PLACE);
			delegate.addRoot(node);
			delegate.setAllowsChildren(node, true);
		}
		Node computer = new Node(this.computerLabel, null, Kind.COMPUTER, State.STATIC);
		delegate.addRoot(computer);
		for (Path root : FileSystems.getDefault().getRootDirectories()) {
			Node drive = pathNode(systemDisplayName(root), root, Kind.DRIVE);
			delegate.addChild(computer, drive);
			delegate.setAllowsChildren(drive, true);
		}
	}
	public boolean isIncludeHidden() { return includeHidden; }
	public void setIncludeHidden(boolean value) {
		if (includeHidden == value) return;
		includeHidden = value;
		reset(locations, computerLabel, loadingLabel, errorLabel);
	}
	/** Loads one directory node asynchronously; repeated calls share an active load. */
	public CompletionStage<Void> loadChildren(Node node) {
		requireNode(node);
		if (node.path == null || node.state == State.STATIC || node.state == State.LOADED)
			return CompletableFuture.completedFuture(null);
		if (node.state == State.LOADING) return node.loading;
		node.state = State.LOADING;
		long nodeGeneration = ++node.generation;
		long modelGeneration = resetGeneration;
		replaceChildren(node, List.of(new Node(loadingLabel, null, Kind.LOADING, State.STATIC)));
		CompletableFuture<Void> completion = new CompletableFuture<>();
		node.loading = completion;
		CompletableFuture.supplyAsync(() -> directories(node.path, includeHidden))
				.whenComplete((directories, failure) -> EventQueue.invokeLater(() -> {
					if (!delegate.contains(node) || modelGeneration != resetGeneration
							|| nodeGeneration != node.generation) {
						completion.complete(null); return;
					}
					node.loading = null;
					if (failure != null) {
						node.state = State.FAILED;
						replaceChildren(node, List.of(new Node(errorLabel, null,
								Kind.ERROR, State.STATIC)));
						completion.complete(null); return;
					}
					node.state = State.LOADED;
					List<Node> children = directories.stream()
							.map(directory -> pathNode(directory.label(), directory.path(),
									Kind.DIRECTORY)).toList();
					replaceChildren(node, children);
					completion.complete(null);
				}));
		return completion;
	}
	/** Invalidates every occurrence of a path; the next expansion reloads it. */
	public void refresh(Path path) {
		if (path == null) return;
		Path normalized = path.toAbsolutePath().normalize();
		for (Node node : pathNodes) {
			if (!normalized.equals(node.path) || !delegate.contains(node)) continue;
			CompletableFuture<Void> stale = node.loading;
			node.loading = null;
			node.generation++;
			node.state = State.UNLOADED;
			if (stale != null) stale.complete(null);
		}
	}
	/** Returns the deepest materialized occurrence containing a path, or {@code null}. */
	public Node nearestLoadedAncestor(Path path) {
		if (path == null) return null;
		Path target = path.toAbsolutePath().normalize();
		Node nearest = null;
		int depth = -1;
		for (Node node : pathNodes)
			if (delegate.contains(node) && target.startsWith(node.path)
					&& node.path.getNameCount() > depth) {
				nearest = node; depth = node.path.getNameCount();
			}
		return nearest;
	}
	public boolean contains(Node node) { return delegate.contains(node); }

	@Override public int getRootCount() { return delegate.getRootCount(); }
	@Override public Node getRootAt(int index) { return delegate.getRootAt(index); }
	@Override public int getChildCount(Node parent) { return delegate.getChildCount(parent); }
	@Override public Node getChild(Node parent, int index) { return delegate.getChild(parent, index); }
	@Override public boolean isLeaf(Node node) { return delegate.isLeaf(node); }
	@Override public void addTreeModelListener(Listener<Node> listener) {
		delegate.addTreeModelListener(listener);
	}
	@Override public void removeTreeModelListener(Listener<Node> listener) {
		delegate.removeTreeModelListener(listener);
	}

	private Node pathNode(String label, Path path, Kind kind) {
		Node node = new Node(label, path.toAbsolutePath().normalize(), kind, State.UNLOADED);
		pathNodes.add(node);
		return node;
	}
	private void replaceChildren(Node parent, List<Node> children) {
		for (int index = 0; index < delegate.getChildCount(parent); index++)
			removePathNodes(delegate.getChild(parent, index));
		delegate.setChildren(parent, children);
		List<Node> expandable = children.stream().filter(child -> child.path != null).toList();
		if (!expandable.isEmpty()) delegate.setAllowsChildren(expandable, true);
	}
	private void removePathNodes(Node node) {
		for (int index = 0; index < delegate.getChildCount(node); index++)
			removePathNodes(delegate.getChild(node, index));
		pathNodes.remove(node);
	}
	private void requireNode(Node node) {
		if (node == null || !delegate.contains(node))
			throw new IllegalArgumentException("Unknown filesystem node");
	}
	private static List<Directory> directories(Path parent, boolean includeHidden) {
		try (var stream = Files.list(parent)) {
			return stream.filter(Files::isDirectory)
					.filter(path -> includeHidden || !isHidden(path))
					.map(path -> new Directory(path.toAbsolutePath().normalize(),
							systemDisplayName(path)))
					.sorted(Comparator.comparing(Directory::label,
							String.CASE_INSENSITIVE_ORDER)).toList();
		} catch (IOException failure) {
			throw new java.util.concurrent.CompletionException(failure);
		}
	}
	private static boolean isHidden(Path path) {
		try { return Files.isHidden(path); }
		catch (IOException failure) { return false; }
	}
	private static String systemDisplayName(Path path) {
		String display = FileSystemView.getFileSystemView().getSystemDisplayName(path.toFile());
		if (display != null && !display.isBlank()) return display;
		Path name = path.getFileName();
		return name == null ? path.toString() : name.toString();
	}
	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
		return value;
	}
}
