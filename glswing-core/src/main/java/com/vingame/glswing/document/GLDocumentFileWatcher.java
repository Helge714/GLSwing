package com.vingame.glswing.document;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Observes file-backed documents for changes made outside the application.
 * Listener callbacks run on the watcher's daemon thread; UI clients must marshal
 * presentation work to their UI thread.
 */
public final class GLDocumentFileWatcher implements AutoCloseable {
	@FunctionalInterface public interface Listener {
		void fileStateChanged(GLDocumentFileEvent event);
	}
	private record FileStamp(boolean exists, FileTime modified, long size,
			Object fileKey) { }
	private static final class Tracked {
		private final GLDocument<?> document;
		private volatile Path path;
		private volatile FileStamp acknowledged;
		private volatile GLDocumentFileState state = GLDocumentFileState.CURRENT;
		private Tracked(GLDocument<?> document, Path path, FileStamp acknowledged) {
			this.document = document; this.path = path; this.acknowledged = acknowledged;
		}
	}

	private final WatchService service;
	private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();
	private final Map<Path, WatchKey> directoryKeys = new ConcurrentHashMap<>();
	private final Map<WatchKey, Path> keyDirectories = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Thread thread;

	/** Creates and starts a daemon watcher for subsequently tracked documents. */
	public GLDocumentFileWatcher() throws IOException {
		service = FileSystems.getDefault().newWatchService();
		thread = new Thread(this::watch, "GLSwing document file watcher");
		thread.setDaemon(true);
		thread.start();
	}

	/** Starts observing a file-backed document and acknowledges its current file. */
	public boolean track(GLDocument<?> document) {
		ensureOpen();
		Path path = pathOf(document);
		if (path == null) return false;
		try {
			register(path.getParent());
			tracked.put(document.id(), new Tracked(document, path, stamp(path)));
			return true;
		} catch (IOException failure) {
			throw new IllegalStateException("Could not watch document file " + path, failure);
		}
	}

	/** Stops observing a document without changing its file or content. */
	public boolean untrack(GLDocument<?> document) {
		return document != null && tracked.remove(document.id()) != null;
	}

	/** Returns the most recently observed state, or {@code CURRENT} when untracked. */
	public GLDocumentFileState state(GLDocument<?> document) {
		Tracked value = document == null ? null : tracked.get(document.id());
		return value == null ? GLDocumentFileState.CURRENT : value.state;
	}

	/** Immediately compares a document with its last acknowledged file state. */
	public GLDocumentFileState refresh(GLDocument<?> document) {
		ensureOpen();
		Tracked value = document == null ? null : tracked.get(document.id());
		if (value == null) return GLDocumentFileState.CURRENT;
		return inspect(value);
	}

	/** Acknowledges the document's current file after a successful open, save, or reload. */
	public boolean acknowledge(GLDocument<?> document) {
		ensureOpen();
		Path path = pathOf(document);
		if (path == null) return untrack(document);
		try {
			register(path.getParent());
			Tracked value = tracked.computeIfAbsent(document.id(), ignored ->
					new Tracked(document, path, new FileStamp(false, FileTime.fromMillis(0), 0, null)));
			value.path = path;
			value.acknowledged = stamp(path);
			transition(value, GLDocumentFileState.CURRENT);
			return true;
		} catch (IOException failure) {
			throw new IllegalStateException("Could not acknowledge document file " + path, failure);
		}
	}

	/** Adds a listener invoked on the watcher's daemon thread. */
	public void addListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	/** Removes a previously registered listener. */
	public void removeListener(Listener listener) { listeners.remove(listener); }

	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		try { service.close(); } catch (IOException ignored) { }
		tracked.clear(); directoryKeys.clear(); keyDirectories.clear(); listeners.clear();
	}

	private void watch() {
		while (!closed.get()) {
			try {
				WatchKey key = service.take();
				Path directory = keyDirectories.get(key);
				boolean overflow = false;
				java.util.Set<Path> changed = new java.util.HashSet<>();
				for (var event : key.pollEvents()) {
					if (event.kind() == StandardWatchEventKinds.OVERFLOW) overflow = true;
					else if (directory != null && event.context() instanceof Path name)
						changed.add(directory.resolve(name).toAbsolutePath().normalize());
				}
				if (!key.reset()) {
					Path removed = keyDirectories.remove(key);
					if (removed != null) directoryKeys.remove(removed, key);
				}
				for (Tracked value : tracked.values())
					if (overflow || changed.contains(value.path)) inspect(value);
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt(); return;
			} catch (java.nio.file.ClosedWatchServiceException failure) {
				return;
			}
		}
	}

	private GLDocumentFileState inspect(Tracked value) {
		GLDocumentFileState result;
		try {
			FileStamp current = stamp(value.path);
			result = !current.exists() ? GLDocumentFileState.DELETED
					: current.equals(value.acknowledged)
							? GLDocumentFileState.CURRENT : GLDocumentFileState.MODIFIED;
		} catch (IOException failure) {
			result = GLDocumentFileState.UNAVAILABLE;
		}
		transition(value, result);
		return result;
	}
	private void transition(Tracked value, GLDocumentFileState next) {
		GLDocumentFileState previous = value.state;
		if (previous == next) return;
		value.state = next;
		GLDocumentFileEvent event = new GLDocumentFileEvent(
				value.document, value.path, previous, next);
		for (Listener listener : listeners) listener.fileStateChanged(event);
	}
	private void register(Path directory) throws IOException {
		if (directory == null || directoryKeys.containsKey(directory)) return;
		WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
		WatchKey previous = directoryKeys.putIfAbsent(directory, key);
		if (previous == null) keyDirectories.put(key, directory); else key.cancel();
	}
	private static FileStamp stamp(Path path) throws IOException {
		if (!Files.exists(path))
			return new FileStamp(false, FileTime.fromMillis(0), 0, null);
		BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
		return new FileStamp(true, attributes.lastModifiedTime(), attributes.size(),
				attributes.fileKey());
	}
	private static Path pathOf(GLDocument<?> document) {
		if (document == null) return null;
		URI location = document.location();
		return location != null && "file".equalsIgnoreCase(location.getScheme())
				? Path.of(location).toAbsolutePath().normalize() : null;
	}
	private void ensureOpen() {
		if (closed.get()) throw new IllegalStateException("Document file watcher is closed");
	}
}
