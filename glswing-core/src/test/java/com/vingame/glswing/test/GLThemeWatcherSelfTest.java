package com.vingame.glswing.test;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeWatcher;

/** CPU regression test for initial and inherited file-theme hot reloads. */
public final class GLThemeWatcherSelfTest {
	private GLThemeWatcherSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("glswing-theme-watch-");
		Path defaults = directory.resolve("default.gltheme");
		Path selected = directory.resolve("selected.gltheme");
		try {
			writeAccent(defaults, 10, 20, 30);
			Files.writeString(selected, "theme.extends=default\n",
					StandardCharsets.UTF_8);

			AtomicReference<Color> accent = new AtomicReference<>();
			AtomicReference<GLThemeLoadException> failure = new AtomicReference<>();
			AtomicReference<CountDownLatch> reload =
					new AtomicReference<>(new CountDownLatch(1));
			try (GLThemeWatcher watcher = GLThemeWatcher.watch(selected, theme -> {
				accent.set(theme.values().getColor("palette.accent", Color.BLACK));
				reload.get().countDown();
			}, failure::set)) {
				await(reload.get(), "Initial watched theme was not loaded");
				check(new Color(10, 20, 30).equals(accent.get()),
						"Sibling default theme was not inherited");

				CountDownLatch inheritedReload = new CountDownLatch(1);
				reload.set(inheritedReload);
				writeAccent(defaults, 40, 50, 60);
				await(inheritedReload,
						"Changing an inherited theme did not reload the selected theme");
				check(new Color(40, 50, 60).equals(accent.get()),
						"Inherited value was stale after hot reload");
				check(failure.get() == null,
						"Theme watcher reported an unexpected load error");
			}
		} finally {
			Files.deleteIfExists(selected);
			Files.deleteIfExists(defaults);
			Files.deleteIfExists(directory);
		}
		System.out.println("GL theme watcher self-test passed.");
	}

	private static void writeAccent(Path path, int red, int green, int blue)
			throws Exception {
		Files.writeString(path, "palette.accent=rgba(" + red + ", " + green
				+ ", " + blue + ", 255)\n", StandardCharsets.UTF_8);
	}

	private static void await(CountDownLatch latch, String message)
			throws InterruptedException {
		if (!latch.await(5, TimeUnit.SECONDS))
			throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
