package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vingame.glswing.input.GLInputMapEditor;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.input.GLKeyBinding;
import com.vingame.glswing.ui.GLThemeEditor;
import com.vingame.glswing.ui.GLThemeLoader;
import com.vingame.glswing.ui.GLThemes;

/** CPU-only roundtrip checks for user-editable configuration overrides. */
public final class GLConfigurationEditorSelfTest {
	private GLConfigurationEditorSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		testThemeEditorRoundtrip();
		testInputMapEditorRoundtrip();
		System.out.println("GL configuration-editor self-test passed.");
	}

	private static void testThemeEditorRoundtrip() throws Exception {
		Path directory = Files.createTempDirectory("glswing-theme-editor-");
		Path file = directory.resolve("user.gltheme");
		try {
			GLThemeEditor editor = GLThemeEditor.from(GLThemes.DEFAULT)
					.setColor("palette.accent", new Color(12, 34, 56, 200))
					.set("button.cornerRadius", 9);
			check(editor.isOverridden("palette.accent"),
					"Theme editor did not retain its explicit override");
			check(editor.build().buttonStyle().cornerRadius() == 9,
					"Theme preview did not rebuild typed styles");
			editor.save(file);
			String saved = Files.readString(file);
			check(saved.contains("theme.extends=default")
					&& !saved.contains("button.height="),
					"Theme editor did not save a minimal override file");
			check(GLThemeLoader.load(file).buttonStyle().cornerRadius() == 9,
					"Saved theme did not roundtrip through the public loader");
			GLThemeEditor loaded = GLThemeEditor.loadOrCreate(file, GLThemes.DEFAULT);
			loaded.unset("button.cornerRadius");
			check(loaded.build().buttonStyle().cornerRadius()
					== GLThemes.DEFAULT.buttonStyle().cornerRadius(),
					"Unsetting a theme override did not restore inheritance");
		} finally {
			deleteTree(directory);
		}
	}

	private static void testInputMapEditorRoundtrip() throws Exception {
		Path directory = Files.createTempDirectory("glswing-input-editor-");
		Path file = directory.resolve("user.glinput");
		try {
			KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
					System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK,
					KeyEvent.VK_S, 'S');
			GLKeyBinding save = GLKeyBinding.from(event);
			GLInputMapEditor editor = GLInputMapEditor.from(GLInputMaps.DEFAULT)
					.bind("application.save", save)
					.setValue("slider.precise-factor", 0.025);
			check(editor.findActions(save).contains("application.save"),
					"Input editor did not find a shortcut conflict");
			editor.save(file);
			String saved = Files.readString(file);
			check(saved.contains("input.extends=default")
					&& saved.contains("key.application.save=shortcut+S"),
					"Captured shortcut was not saved portably");
			GLInputMapEditor loaded = GLInputMapEditor.loadOrCreate(file,
					GLInputMaps.DEFAULT);
			check(loaded.build().matches("application.save", event),
					"Saved input binding did not roundtrip");
			loaded.unsetBinding("application.save");
			check(!loaded.build().matches("application.save", event),
					"Removing an application override left its binding active");
		} finally {
			deleteTree(directory);
		}
	}

	private static void deleteTree(Path directory) throws Exception {
		try (var paths = Files.walk(directory)) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try { Files.deleteIfExists(path); }
				catch (Exception error) { throw new RuntimeException(error); }
			});
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
