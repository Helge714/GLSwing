package com.vingame.glswing.input;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reloads one input-map file whenever a {@code .glinput} file in the same
 * directory changes. Callbacks run on the AWT event thread and the last valid
 * map remains active when a temporarily incomplete file cannot be parsed.
 */
public final class GLInputMapWatcher implements AutoCloseable {
	private static final long SAVE_DEBOUNCE_MILLIS = 75;

	private final Path inputMapFile;
	private final WatchService watchService;
	private final Consumer<GLInputMap> reloadHandler;
	private final Consumer<GLInputMapLoadException> errorHandler;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Thread watchThread;

	private GLInputMapWatcher(Path inputMapFile, WatchService watchService,
			Consumer<GLInputMap> reloadHandler,
			Consumer<GLInputMapLoadException> errorHandler) {
		this.inputMapFile = inputMapFile;
		this.watchService = watchService;
		this.reloadHandler = reloadHandler;
		this.errorHandler = errorHandler;
		watchThread = new Thread(this::watchLoop,
				"GLSwing-input-map-watch-" + inputMapFile.getFileName());
		watchThread.setDaemon(true);
	}

	/** Starts watching and immediately loads the selected file once. */
	public static GLInputMapWatcher watch(Path inputMapFile,
			Consumer<GLInputMap> reloadHandler,
			Consumer<GLInputMapLoadException> errorHandler) {
		if (inputMapFile == null)
			throw new IllegalArgumentException("Input-map path must not be null");
		if (reloadHandler == null)
			throw new IllegalArgumentException(
					"Input-map reload handler must not be null");
		Path normalized = inputMapFile.toAbsolutePath().normalize();
		Path directory = normalized.getParent();
		if (directory == null || !Files.isDirectory(directory))
			throw new GLInputMapLoadException(
					"Input-map directory not found: " + directory);
		try {
			WatchService service = directory.getFileSystem().newWatchService();
			directory.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
			GLInputMapWatcher watcher = new GLInputMapWatcher(normalized, service,
					reloadHandler, errorHandler == null ? error -> { } : errorHandler);
			watcher.watchThread.start();
			watcher.reloadNow();
			return watcher;
		} catch (IOException error) {
			throw new GLInputMapLoadException(
					"Cannot watch input-map directory " + directory, error);
		}
	}

	public Path getInputMapFile() { return inputMapFile; }

	/** Requests an immediate reload without waiting for a file-system event. */
	public void reloadNow() {
		if (closed.get()) return;
		try {
			GLInputMap inputMap = GLInputMapLoader.load(inputMapFile);
			dispatch(() -> {
				if (!closed.get()) reloadHandler.accept(inputMap);
			});
		} catch (GLInputMapLoadException error) {
			dispatch(() -> {
				if (!closed.get()) errorHandler.accept(error);
			});
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		try { watchService.close(); }
		catch (IOException ignored) {
			// Closing is best-effort; the daemon thread is interrupted as well.
		}
		watchThread.interrupt();
	}

	private void watchLoop() {
		try {
			while (!closed.get()) {
				WatchKey key = watchService.take();
				boolean reload = containsInputMapChange(key);
				key.reset();
				if (!reload) continue;
				Thread.sleep(SAVE_DEBOUNCE_MILLIS);
				drainPendingEvents();
				reloadNow();
			}
		} catch (ClosedWatchServiceException ignored) {
			// Expected when close() stops the watcher.
		} catch (InterruptedException interrupted) {
			if (!closed.get()) Thread.currentThread().interrupt();
		}
	}

	private void drainPendingEvents() {
		WatchKey pending;
		while ((pending = watchService.poll()) != null) {
			pending.pollEvents();
			pending.reset();
		}
	}

	private static boolean containsInputMapChange(WatchKey key) {
		for (WatchEvent<?> event : key.pollEvents()) {
			if (event.kind() == OVERFLOW) return true;
			Object context = event.context();
			if (context instanceof Path path && path.getFileName().toString()
					.toLowerCase().endsWith(".glinput")) return true;
		}
		return false;
	}

	private static void dispatch(Runnable action) {
		if (EventQueue.isDispatchThread()) action.run();
		else EventQueue.invokeLater(action);
	}
}
