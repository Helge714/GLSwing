package com.vingame.glswing.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Maintains crash-recovery snapshots independently of authoritative document
 * files. Tracked modified documents are saved after an idle delay and at a
 * periodic safety interval. A normal {@link #close()} removes owned snapshots;
 * an abrupt process termination leaves them available for the next launch.
 */
public final class GLDocumentAutosaveCoordinator implements AutoCloseable {
	@FunctionalInterface
	public interface Listener {
		void autosaveChanged(GLDocumentAutosaveEvent event);
	}

	private static final String METADATA_ENTRY = "metadata.properties";
	private static final String CONTENT_ENTRY = "content";
	private static final String SNAPSHOT_SUFFIX = ".glrecovery";
	private final GLDocumentManager manager;
	private final Path directory;
	private final GLDocumentAutosaveConfiguration configuration;
	private final ScheduledExecutorService executor;
	private final Map<UUID, TrackedDocument> tracked = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	public GLDocumentAutosaveCoordinator(GLDocumentManager manager, Path directory) {
		this(manager, directory, GLDocumentAutosaveConfiguration.DEFAULT);
	}

	public GLDocumentAutosaveCoordinator(GLDocumentManager manager, Path directory,
			GLDocumentAutosaveConfiguration configuration) {
		if (manager == null || directory == null || configuration == null)
			throw new IllegalArgumentException(
					"Document manager, recovery directory and configuration are required");
		this.manager = manager;
		this.directory = directory.toAbsolutePath().normalize();
		this.configuration = configuration;
		executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "GLSwing document autosave");
			thread.setDaemon(true);
			return thread;
		});
	}

	/** Returns the normalized directory containing recovery snapshots. */
	public Path directory() { return directory; }
	/** Returns the immutable scheduling configuration. */
	public GLDocumentAutosaveConfiguration configuration() { return configuration; }

	/** Starts observing a managed document. Repeated calls are harmless. */
	public void track(GLDocument<?> document) {
		ensureOpen();
		if (document == null) throw new IllegalArgumentException("Document required");
		GLDocumentCodec<?> codec = manager.codecFor(document);
		if (codec == null)
			throw new IllegalArgumentException("Document is not managed by this manager");
		TrackedDocument state = new TrackedDocument(document, codec);
		TrackedDocument existing = tracked.putIfAbsent(document.id(), state);
		if (existing != null) {
			if (existing.document != document)
				throw new IllegalArgumentException(
						"A different document with the same ID is already tracked");
			return;
		}
		document.addListener(state.listener);
		if (configuration.enabled()) {
			schedulePeriodic(state);
			if (document.isModified()) documentChanged(state);
		}
	}

	/** Stops observing a document and deletes its recovery snapshot. */
	public CompletionStage<Boolean> untrack(GLDocument<?> document) {
		ensureOpen();
		if (document == null) return CompletableFuture.completedFuture(false);
		TrackedDocument state = tracked.remove(document.id());
		if (state == null) return CompletableFuture.completedFuture(false);
		state.document.removeListener(state.listener);
		cancelSchedules(state);
		return submit(() -> deleteSnapshot(state, true));
	}

	/** Returns whether the exact document instance is currently tracked. */
	public boolean isTracked(GLDocument<?> document) {
		return document != null && tracked.containsKey(document.id());
	}

	/** Immediately writes the latest modified state of a tracked document. */
	public CompletionStage<GLDocumentRecovery> saveNow(GLDocument<?> document) {
		ensureOpen();
		TrackedDocument state = document == null ? null : tracked.get(document.id());
		if (state == null)
			return CompletableFuture.failedFuture(
					new IllegalArgumentException("Document is not tracked"));
		return submit(() -> saveSnapshot(state, true));
	}

	/** Discovers valid recovery snapshots left by an earlier process. */
	public CompletionStage<List<GLDocumentRecovery>> scan() {
		ensureOpen();
		return submit(this::scanSnapshots);
	}

	/**
	 * Decodes a recovery snapshot, binds it to its original codec and starts
	 * tracking it. The returned document remains modified until explicitly saved.
	 */
	public <T> CompletionStage<GLDocument<T>> recover(GLDocumentRecovery recovery,
			Class<T> contentType) {
		ensureOpen();
		if (recovery == null || contentType == null)
			throw new IllegalArgumentException("Recovery and content type required");
		return submit(() -> {
			Snapshot snapshot = readSnapshot(checkedSnapshot(recovery.snapshotPath()));
			GLDocumentRecovery actual = snapshot.recovery;
			if (!sameRecovery(recovery, actual))
				throw new GLDocumentFormatException("Recovery metadata no longer matches");
			GLDocumentCodec<T> codec = manager.codecs().require(actual.typeId(),
					actual.codecId(), contentType);
			if (!codec.supportsSchemaVersion(actual.schemaVersion()))
				throw new GLDocumentFormatException("Unsupported recovery schema version "
						+ actual.schemaVersion());
			T content;
			try (InputStream input = new ByteArrayInputStream(snapshot.content)) {
				content = codec.read(input, actual.schemaVersion());
			}
			GLDocument<T> document = new GLDocument<>(actual.originalDocumentId(), content,
					actual.title(), actual.originalLocation(),
					new com.vingame.glswing.history.GLHistoryManager());
			document.markModified();
			manager.manage(document, codec);
			track(document);
			return document;
		});
	}

	/** Permanently discards one recovery candidate without opening it. */
	public CompletionStage<Boolean> discard(GLDocumentRecovery recovery) {
		ensureOpen();
		if (recovery == null) return CompletableFuture.completedFuture(false);
		return submit(() -> {
			boolean deleted = Files.deleteIfExists(checkedSnapshot(recovery.snapshotPath()));
			if (deleted)
				fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.DELETED,
						null, recovery, null));
			return deleted;
		});
	}

	/** Adds a listener invoked on the coordinator's worker thread. */
	public void addListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	/** Removes a previously registered listener. */
	public void removeListener(Listener listener) { listeners.remove(listener); }

	/**
	 * Performs clean-shutdown cleanup. Close this only after the application's
	 * modified-document close workflow has completed.
	 */
	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		List<TrackedDocument> states = List.copyOf(tracked.values());
		tracked.clear();
		for (TrackedDocument state : states) {
			state.document.removeListener(state.listener);
			cancelSchedules(state);
		}
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
		} catch (InterruptedException interrupted) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		for (TrackedDocument state : states) {
			try { deleteSnapshot(state, false); }
			catch (IOException failure) {
				fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.FAILED,
						state.document, null, failure));
			}
		}
		fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.CLOSED,
				null, null, null));
		listeners.clear();
	}

	private void documentChanged(TrackedDocument state) {
		if (closed.get()) return;
		synchronized (state) {
			state.revision++;
			if (!state.document.isModified()) {
				if (state.idle != null) state.idle.cancel(false);
				state.idle = null;
				executor.execute(() -> deleteQuietly(state));
				return;
			}
			if (!configuration.enabled()) return;
			if (configuration.idleDelay().isZero()) {
				executor.execute(() -> saveQuietly(state));
			} else {
				if (state.idle != null) state.idle.cancel(false);
				state.idle = executor.schedule(() -> saveQuietly(state),
						configuration.idleDelay().toMillis(), TimeUnit.MILLISECONDS);
			}
			fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.SCHEDULED,
					state.document, null, null));
		}
	}

	private void schedulePeriodic(TrackedDocument state) {
		if (configuration.periodicInterval().isZero()) return;
		state.periodic = executor.scheduleWithFixedDelay(() -> saveQuietly(state),
				configuration.periodicInterval().toMillis(),
				configuration.periodicInterval().toMillis(), TimeUnit.MILLISECONDS);
	}

	private void saveQuietly(TrackedDocument state) {
		try { saveSnapshot(state, false); }
		catch (Exception failure) {
			fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.FAILED,
					state.document, null, failure));
		}
	}

	private GLDocumentRecovery saveSnapshot(TrackedDocument state, boolean force)
			throws IOException {
		if (!tracked.containsKey(state.document.id()) || !state.document.isModified())
			return null;
		long revision;
		synchronized (state) {
			revision = state.revision;
			if (!force && revision == state.savedRevision) return state.recovery;
		}
		fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.SAVING,
				state.document, state.recovery, null));
		Files.createDirectories(directory);
		Path target = snapshotPath(state.document.id());
		Path temporary = Files.createTempFile(directory,
				"." + state.document.id() + ".", ".tmp");
		GLDocumentRecovery recovery = new GLDocumentRecovery(target, state.document.id(),
				state.document.title(), state.document.location(), state.codec.typeId(),
				state.codec.codecId(), state.codec.currentSchemaVersion(), Instant.now());
		try {
			try (OutputStream file = Files.newOutputStream(temporary);
					ZipOutputStream zip = new ZipOutputStream(file)) {
				writeMetadata(zip, recovery);
				zip.putNextEntry(new ZipEntry(CONTENT_ENTRY));
				writeCaptured(state.codec, state.document.content(), zip);
				zip.closeEntry();
			}
			moveAtomically(temporary, target);
		} finally { Files.deleteIfExists(temporary); }
		synchronized (state) {
			state.savedRevision = revision;
			state.recovery = recovery;
		}
		fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.SAVED,
				state.document, recovery, null));
		return recovery;
	}

	private boolean deleteSnapshot(TrackedDocument state, boolean notify) throws IOException {
		boolean deleted = Files.deleteIfExists(snapshotPath(state.document.id()));
		synchronized (state) {
			state.recovery = null;
			state.savedRevision = -1;
		}
		if (notify && deleted)
			fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.DELETED,
					state.document, null, null));
		return deleted;
	}

	private void deleteQuietly(TrackedDocument state) {
		try { deleteSnapshot(state, true); }
		catch (IOException failure) {
			fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.FAILED,
					state.document, state.recovery, failure));
		}
	}

	private List<GLDocumentRecovery> scanSnapshots() throws IOException {
		if (!Files.isDirectory(directory)) return List.of();
		ArrayList<GLDocumentRecovery> result = new ArrayList<>();
		try (var paths = Files.list(directory)) {
			for (Path path : paths.filter(candidate -> candidate.getFileName().toString()
					.endsWith(SNAPSHOT_SUFFIX)).toList()) {
				try { result.add(readRecovery(path, false).recovery); }
				catch (IOException failure) {
					fire(new GLDocumentAutosaveEvent(GLDocumentAutosaveEvent.State.FAILED,
							null, null, failure));
				}
			}
		}
		result.sort(Comparator.comparing(GLDocumentRecovery::savedAt).reversed());
		return List.copyOf(result);
	}

	private Snapshot readSnapshot(Path path) throws IOException {
		return readRecovery(path, true);
	}

	private Snapshot readRecovery(Path path, boolean includeContent) throws IOException {
		Properties metadata = null;
		byte[] content = null;
		try (InputStream file = Files.newInputStream(path);
				ZipInputStream zip = new ZipInputStream(file)) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				if (entry.isDirectory()) continue;
				if (METADATA_ENTRY.equals(entry.getName())) {
					metadata = new Properties();
					metadata.load(zip);
				} else if (includeContent && CONTENT_ENTRY.equals(entry.getName())) {
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					zip.transferTo(bytes);
					content = bytes.toByteArray();
				}
				zip.closeEntry();
			}
		}
		if (metadata == null || includeContent && content == null)
			throw new GLDocumentFormatException("Recovery snapshot is incomplete: " + path);
		try {
			if (!"glswing-document-recovery".equals(required(metadata, "format"))
					|| !"1".equals(required(metadata, "formatVersion")))
				throw new GLDocumentFormatException(
						"Unsupported recovery snapshot format in " + path);
			String location = metadata.getProperty("location", "");
			GLDocumentRecovery recovery = new GLDocumentRecovery(path,
					UUID.fromString(required(metadata, "documentId")),
					required(metadata, "title"), location.isBlank() ? null : URI.create(location),
					required(metadata, "typeId"), required(metadata, "codecId"),
					Integer.parseInt(required(metadata, "schemaVersion")),
					Instant.parse(required(metadata, "savedAt")));
			return new Snapshot(recovery, content);
		} catch (IllegalArgumentException failure) {
			throw new GLDocumentFormatException("Invalid recovery metadata in " + path,
					failure);
		}
	}

	private static void writeMetadata(ZipOutputStream zip, GLDocumentRecovery recovery)
			throws IOException {
		Properties metadata = new Properties();
		metadata.setProperty("format", "glswing-document-recovery");
		metadata.setProperty("formatVersion", "1");
		metadata.setProperty("documentId", recovery.originalDocumentId().toString());
		metadata.setProperty("title", recovery.title());
		metadata.setProperty("location", recovery.originalLocation() == null ? ""
				: recovery.originalLocation().toString());
		metadata.setProperty("typeId", recovery.typeId());
		metadata.setProperty("codecId", recovery.codecId());
		metadata.setProperty("schemaVersion", Integer.toString(recovery.schemaVersion()));
		metadata.setProperty("savedAt", recovery.savedAt().toString());
		zip.putNextEntry(new ZipEntry(METADATA_ENTRY));
		metadata.store(zip, "GLSwing document recovery");
		zip.closeEntry();
	}

	private Path snapshotPath(UUID id) { return directory.resolve(id + SNAPSHOT_SUFFIX); }

	private Path checkedSnapshot(Path path) {
		Path checked = path.toAbsolutePath().normalize();
		if (!directory.equals(checked.getParent())
				|| !checked.getFileName().toString().endsWith(SNAPSHOT_SUFFIX))
			throw new IllegalArgumentException("Recovery snapshot is outside its directory");
		return checked;
	}

	private void cancelSchedules(TrackedDocument state) {
		synchronized (state) {
			if (state.idle != null) state.idle.cancel(false);
			if (state.periodic != null) state.periodic.cancel(false);
			state.idle = null;
			state.periodic = null;
		}
	}

	private <T> CompletionStage<T> submit(IoSupplier<T> operation) {
		CompletableFuture<T> result = new CompletableFuture<>();
		try {
			executor.execute(() -> {
				try { result.complete(operation.get()); }
				catch (Throwable failure) { result.completeExceptionally(failure); }
			});
		} catch (RuntimeException failure) { result.completeExceptionally(failure); }
		return result;
	}

	private void fire(GLDocumentAutosaveEvent event) {
		for (Listener listener : listeners) listener.autosaveChanged(event);
	}
	private void ensureOpen() {
		if (closed.get()) throw new IllegalStateException("Autosave coordinator is closed");
	}

	private static String required(Properties values, String key)
			throws GLDocumentFormatException {
		String value = values.getProperty(key);
		if (value == null || value.isBlank())
			throw new GLDocumentFormatException("Missing recovery property: " + key);
		return value;
	}
	private static boolean sameRecovery(GLDocumentRecovery expected,
			GLDocumentRecovery actual) {
		return expected.originalDocumentId().equals(actual.originalDocumentId())
				&& expected.typeId().equals(actual.typeId())
				&& expected.codecId().equals(actual.codecId())
				&& expected.schemaVersion() == actual.schemaVersion();
	}
	private static void moveAtomically(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
	private static <T> void writeCaptured(GLDocumentCodec<T> codec, Object content,
			OutputStream output) throws IOException {
		codec.write(codec.contentType().cast(content), output);
	}

	@FunctionalInterface private interface IoSupplier<T> { T get() throws Exception; }
	private record Snapshot(GLDocumentRecovery recovery, byte[] content) { }

	private final class TrackedDocument {
		private final GLDocument<?> document;
		private final GLDocumentCodec<?> codec;
		@SuppressWarnings("rawtypes")
		private final GLDocument.Listener listener;
		private long revision;
		private long savedRevision = -1;
		private GLDocumentRecovery recovery;
		private ScheduledFuture<?> idle;
		private ScheduledFuture<?> periodic;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private TrackedDocument(GLDocument<?> document, GLDocumentCodec<?> codec) {
			this.document = document;
			this.codec = codec;
			listener = changed -> documentChanged(this);
		}
	}
}
