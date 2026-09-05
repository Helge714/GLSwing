package com.vingame.glswing.document;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates asynchronous document I/O while codecs own data conversion. */
public final class GLDocumentManager implements AutoCloseable {
	private final GLDocumentCodecRegistry codecs;
	private final Executor executor;
	private final ExecutorService ownedExecutor;
	private final Map<UUID, GLDocumentCodec<?>> bindings = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> schemaBindings = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	public GLDocumentManager(GLDocumentCodecRegistry codecs) {
		this(codecs, Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "GLSwing document I/O");
			thread.setDaemon(true); return thread;
		}), true);
	}
	public GLDocumentManager(GLDocumentCodecRegistry codecs, Executor executor) {
		this(codecs, executor, false);
	}
	private GLDocumentManager(GLDocumentCodecRegistry codecs, Executor executor, boolean owned) {
		if (codecs == null || executor == null)
			throw new IllegalArgumentException("Codec registry and executor required");
		this.codecs = codecs; this.executor = executor;
		this.ownedExecutor = owned ? (ExecutorService) executor : null;
	}
	public GLDocumentCodecRegistry codecs() { return codecs; }
	/** Associates an existing document with a codec for subsequent save operations. */
	public <T> void manage(GLDocument<T> document, GLDocumentCodec<T> codec) {
		ensureOpen();
		if (document == null) throw new IllegalArgumentException("Document required");
		validateRegistered(codec);
		bindings.put(document.id(), codec);
		schemaBindings.put(document.id(), codec.currentSchemaVersion());
	}
	public boolean isManaged(GLDocument<?> document) {
		return document != null && bindings.containsKey(document.id());
	}
	GLDocumentCodec<?> codecFor(GLDocument<?> document) {
		return document == null ? null : bindings.get(document.id());
	}

	/** Creates an unsaved document bound to a registered codec. */
	public <T> GLDocument<T> create(T content, String title, GLDocumentCodec<T> codec) {
		ensureOpen();
		validateRegistered(codec);
		GLDocument<T> document = new GLDocument<>(content, title);
		document.markModified(); bindings.put(document.id(), codec);
		schemaBindings.put(document.id(), codec.currentSchemaVersion()); return document;
	}
	/** Resolves a codec by extension and opens a current-schema plain document. */
	public <T> CompletionStage<GLDocument<T>> open(Path path, Class<T> contentType) {
		ensureOpen();
		GLDocumentCodec<T> codec = codecs.find(path, contentType).orElseThrow(() ->
				new IllegalArgumentException("No codec registered for " + path));
		return open(path, codec, codec.currentSchemaVersion());
	}
	/** Opens a plain document with an explicit stored schema version. */
	public <T> CompletionStage<GLDocument<T>> open(Path path, GLDocumentCodec<T> codec,
			int schemaVersion) {
		ensureOpen();
		validateRegistered(codec); Path source = normalize(path);
		return CompletableFuture.supplyAsync(() -> {
			try (var input = new BufferedInputStream(Files.newInputStream(source))) {
				T content = codec.read(input, schemaVersion);
				GLDocument<T> document = new GLDocument<>(content,
						source.getFileName().toString(), source.toUri(),
						new com.vingame.glswing.history.GLHistoryManager());
				bindings.put(document.id(), codec);
				schemaBindings.put(document.id(), schemaVersion); return document;
			} catch (IOException failure) {
				throw new CompletionException(failure);
			}
		}, executor);
	}
	/** Saves a document to its file location. */
	public CompletionStage<Path> save(GLDocument<?> document) {
		if (document == null || document.location() == null
				|| !"file".equalsIgnoreCase(document.location().getScheme()))
			return CompletableFuture.failedFuture(
					new IllegalStateException("Document has no writable file location"));
		return saveAs(document, Path.of(document.location()));
	}
	/** Atomically saves a document to a new file location. */
	public CompletionStage<Path> saveAs(GLDocument<?> document, Path path) {
		ensureOpen();
		if (document == null) throw new IllegalArgumentException("Document required");
		if (document.isReadOnly()) return CompletableFuture.failedFuture(
				new IllegalStateException("Document is read-only"));
		GLDocumentCodec<?> codec = bindings.get(document.id());
		if (codec == null) return CompletableFuture.failedFuture(
				new IllegalStateException("Document is not managed by this manager"));
		Path destination = normalize(path);
		return CompletableFuture.supplyAsync(() -> {
			try {
				writeAtomically(destination, codec, document.content());
				document.setLocation(destination.toUri());
				if (destination.getFileName() != null)
					document.setTitle(destination.getFileName().toString());
				document.markPersisted();
				schemaBindings.put(document.id(), codec.currentSchemaVersion());
				return destination;
			} catch (IOException failure) {
				throw new CompletionException(failure);
			}
		}, executor);
	}
	/** Reloads a managed file-backed document and resets its persisted checkpoint. */
	public <T> CompletionStage<GLDocument<T>> reload(GLDocument<T> document) {
		ensureOpen();
		if (document == null || document.location() == null
				|| !"file".equalsIgnoreCase(document.location().getScheme()))
			return CompletableFuture.failedFuture(
					new IllegalStateException("Document has no readable file location"));
		GLDocumentCodec<?> bound = bindings.get(document.id());
		if (bound == null || !bound.contentType().isInstance(document.content()))
			return CompletableFuture.failedFuture(
					new IllegalStateException("Document is not managed by this manager"));
		@SuppressWarnings("unchecked") GLDocumentCodec<T> codec = (GLDocumentCodec<T>) bound;
		int schemaVersion = schemaBindings.getOrDefault(document.id(),
				codec.currentSchemaVersion());
		Path source = normalize(Path.of(document.location()));
		return CompletableFuture.supplyAsync(() -> {
			try (var input = new BufferedInputStream(Files.newInputStream(source))) {
				document.replacePersistedContent(codec.read(input, schemaVersion));
				return document;
			} catch (IOException failure) {
				throw new CompletionException(failure);
			}
		}, executor);
	}
	public boolean release(GLDocument<?> document) {
		if (document == null) return false;
		schemaBindings.remove(document.id());
		return bindings.remove(document.id()) != null;
	}
	public CompletionStage<GLDocumentBundle> openBundle(Path path) {
		ensureOpen();
		return CompletableFuture.supplyAsync(() -> {
			try { return GLDocumentBundle.open(path, codecs); }
			catch (IOException failure) { throw new CompletionException(failure); }
		}, executor);
	}
	/** Lazily decodes one bundle entry on the manager's I/O executor. */
	public <T> CompletionStage<GLDocumentEntry<T>> openEntry(GLDocumentBundle bundle,
			String id, Class<T> contentType) {
		ensureOpen();
		if (bundle == null) throw new IllegalArgumentException("Bundle required");
		return CompletableFuture.supplyAsync(() -> {
			try { return bundle.open(id, contentType); }
			catch (IOException failure) { throw new CompletionException(failure); }
		}, executor);
	}
	public CompletionStage<Path> saveBundle(GLDocumentBundle bundle, Path path) {
		ensureOpen();
		if (bundle == null) throw new IllegalArgumentException("Bundle required");
		return CompletableFuture.supplyAsync(() -> {
			try { bundle.save(path); return path.toAbsolutePath().normalize(); }
			catch (IOException failure) { throw new CompletionException(failure); }
		}, executor);
	}
	public CompletionStage<Path> saveBundle(GLDocumentBundle bundle) {
		if (bundle == null || bundle.path() == null)
			return CompletableFuture.failedFuture(
					new IllegalStateException("Bundle has no file location"));
		return saveBundle(bundle, bundle.path());
	}
	@Override public void close() {
		if (!closed.compareAndSet(false, true)) return;
		if (ownedExecutor != null) ownedExecutor.shutdown();
		bindings.clear(); schemaBindings.clear();
	}

	private <T> void validateRegistered(GLDocumentCodec<T> codec) {
		if (codec == null) throw new IllegalArgumentException("Codec required");
		try {
			if (codecs.require(codec.typeId(), codec.codecId(), codec.contentType()) != codec)
				throw new IllegalArgumentException("Use the registered codec instance");
		} catch (GLDocumentFormatException failure) {
			throw new IllegalArgumentException(failure.getMessage(), failure);
		}
	}
	private void ensureOpen() {
		if (closed.get()) throw new IllegalStateException("Document manager is closed");
	}
	private static Path normalize(Path path) {
		if (path == null) throw new IllegalArgumentException("Path required");
		return path.toAbsolutePath().normalize();
	}
	private static void writeAtomically(Path target, GLDocumentCodec<?> codec,
			Object content) throws IOException {
		Path parent = target.getParent();
		if (parent == null) throw new IOException("Document path has no parent: " + target);
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
		try {
			try (var output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
				writeCaptured(codec, content, output);
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException failure) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(temporary); }
	}
	private static <T> void writeCaptured(GLDocumentCodec<T> codec, Object content,
			java.io.OutputStream output) throws IOException {
		codec.write(codec.contentType().cast(content), output);
	}
}
