package com.vingame.glswing.ui;

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
 * Reloads one file theme whenever a .gltheme file in the same directory changes.
 * Callbacks run on the AWT event thread and the last valid theme remains active
 * when a temporarily incomplete file cannot be parsed.
 */
public final class GLThemeWatcher implements AutoCloseable {
	private static final long SAVE_DEBOUNCE_MILLIS = 75;

	private final Path themeFile;
	private final WatchService watchService;
	private final Consumer<GLTheme> reloadHandler;
	private final Consumer<GLThemeLoadException> errorHandler;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final Thread watchThread;

	private GLThemeWatcher(Path themeFile, WatchService watchService,
			Consumer<GLTheme> reloadHandler,
			Consumer<GLThemeLoadException> errorHandler) {
		this.themeFile = themeFile;
		this.watchService = watchService;
		this.reloadHandler = reloadHandler;
		this.errorHandler = errorHandler;
		watchThread = new Thread(this::watchLoop,
				"GLSwing-theme-watch-" + themeFile.getFileName());
		watchThread.setDaemon(true);
	}

	/** Starts watching and immediately loads the selected file once. */
	public static GLThemeWatcher watch(Path themeFile,
			Consumer<GLTheme> reloadHandler,
			Consumer<GLThemeLoadException> errorHandler) {
		if (themeFile == null)
			throw new IllegalArgumentException("Theme path must not be null");
		if (reloadHandler == null)
			throw new IllegalArgumentException("Theme reload handler must not be null");
		Path normalized = themeFile.toAbsolutePath().normalize();
		Path directory = normalized.getParent();
		if (directory == null || !Files.isDirectory(directory))
			throw new GLThemeLoadException(
					"Theme directory not found: " + directory);
		try {
			WatchService service = directory.getFileSystem().newWatchService();
			directory.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
			GLThemeWatcher watcher = new GLThemeWatcher(normalized, service,
					reloadHandler, errorHandler == null ? error -> { } : errorHandler);
			watcher.watchThread.start();
			watcher.reloadNow();
			return watcher;
		} catch (IOException error) {
			throw new GLThemeLoadException(
					"Cannot watch theme directory " + directory, error);
		}
	}

	public Path getThemeFile() {
		return themeFile;
	}

	/** Requests an immediate reload without waiting for a file-system event. */
	public void reloadNow() {
		if (closed.get())
			return;
		try {
			GLTheme theme = GLThemeLoader.load(themeFile);
			dispatch(() -> {
				if (!closed.get())
					reloadHandler.accept(theme);
			});
		} catch (GLThemeLoadException error) {
			dispatch(() -> {
				if (!closed.get())
					errorHandler.accept(error);
			});
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		try {
			watchService.close();
		} catch (IOException ignored) {
			// Closing is best-effort; the daemon thread is interrupted as well.
		}
		watchThread.interrupt();
	}

	private void watchLoop() {
		try {
			while (!closed.get()) {
				WatchKey key = watchService.take();
				boolean reload = containsThemeChange(key);
				key.reset();
				if (!reload)
					continue;
				Thread.sleep(SAVE_DEBOUNCE_MILLIS);
				drainPendingEvents();
				reloadNow();
			}
		} catch (ClosedWatchServiceException ignored) {
			// Expected when close() stops the watcher.
		} catch (InterruptedException interrupted) {
			if (!closed.get())
				Thread.currentThread().interrupt();
		}
	}

	private void drainPendingEvents() {
		WatchKey pending;
		while ((pending = watchService.poll()) != null) {
			pending.pollEvents();
			pending.reset();
		}
	}

	private static boolean containsThemeChange(WatchKey key) {
		for (WatchEvent<?> event : key.pollEvents()) {
			if (event.kind() == OVERFLOW)
				return true;
			Object context = event.context();
			if (context instanceof Path path
					&& path.getFileName().toString().toLowerCase().endsWith(".gltheme"))
				return true;
		}
		return false;
	}

	private static void dispatch(Runnable action) {
		if (EventQueue.isDispatchThread())
			action.run();
		else
			EventQueue.invokeLater(action);
	}
}
