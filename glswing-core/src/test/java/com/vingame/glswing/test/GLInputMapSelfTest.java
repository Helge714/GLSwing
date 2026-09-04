package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMapLoadException;
import com.vingame.glswing.input.GLInputMapLoader;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.rendering.GLContext2D;

/** Platform-independent checks for semantic input maps and scope inheritance. */
public final class GLInputMapSelfTest {

	private GLInputMapSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		testDefaults();
		testFileInheritanceAndOverrides();
		testContainerScopes();
		testInvalidInput();
		testMissingFilePolicy();
	}

	private static void testDefaults() {
		KeyEvent undo = key(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		check(GLInputMaps.DEFAULT.matches(GLInputActions.HISTORY_UNDO, undo),
				"Default undo binding must use the platform shortcut modifier");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_SAVE,
				key(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)),
				"Default save binding must use Ctrl+S on Windows");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_NEW,
				key(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK))
				&& GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_OPEN,
						key(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK))
				&& GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_CLOSE,
						key(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)),
				"Default document lifecycle shortcuts are incomplete");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_SAVE_AS,
				key(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)),
				"Default save-as binding must use Ctrl+Shift+S on Windows");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.DOCUMENT_SAVE_ALL,
				key(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK)),
				"Default save-all binding must use Ctrl+Alt+S on Windows");
		check(GLInputMaps.DEFAULT.hasModifiers(GLInputActions.SLIDER_PRECISE,
				InputEvent.SHIFT_DOWN_MASK), "Default precise modifier must be Shift");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.CODE_COMPLETE,
				key(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK)),
				"Default code completion binding must use Ctrl+Space on Windows");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.CODE_ORGANIZE_IMPORTS,
				key(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)),
				"Default organize-imports binding must use Ctrl+Shift+O on Windows");
		check(GLInputMaps.DEFAULT.matches(GLInputActions.CODE_GO_TO_DEFINITION,
				key(KeyEvent.VK_F3, 0)), "Default go-to-definition binding must use F3");
	}

	private static void testFileInheritanceAndOverrides() throws Exception {
		Path directory = Files.createTempDirectory("glswing-input-test");
		try {
			Path base = directory.resolve("base.glinput");
			Path child = directory.resolve("child.glinput");
			Files.writeString(base, "key.application.save=ctrl+S\n"
					+ "value.slider.precise-factor=0.25\n");
			Files.writeString(child, "input.extends=base.glinput\n"
					+ "key.application.save=alt+S\n"
					+ "modifier.slider.coarse=ALT\n");
			GLInputMap map = GLInputMapLoader.load(child);
			check(map.matches("application.save", key(KeyEvent.VK_S,
					InputEvent.ALT_DOWN_MASK)), "Child key binding must override its base");
			check(!map.matches("application.save", key(KeyEvent.VK_S,
					InputEvent.CTRL_DOWN_MASK)), "Overridden binding must not remain active");
			check(map.getValue(GLInputActions.SLIDER_PRECISE_FACTOR, 1) == 0.25,
					"Inherited numeric value must remain available");
		} finally {
			deleteTree(directory);
		}
	}

	private static void testContainerScopes() {
		GLContainer parent = container();
		GLContainer child = container();
		parent.addGL(child);
		GLInputMap map = new GLInputMap(java.util.Map.of(), java.util.Map.of(),
				java.util.Map.of("test", 2.0));
		parent.setGLInputMap(map);
		check(child.getGLInputMap() == map, "Child must inherit nearest input map");
		child.setGLInputMap(GLInputMaps.DEFAULT);
		check(child.hasExplicitGLInputMap(), "Child must expose its local input scope");
		child.clearGLInputMap();
		check(child.getGLInputMap() == map, "Clearing must restore inheritance");
	}

	private static void testInvalidInput() throws Exception {
		Path file = Files.createTempFile("glswing-invalid", ".glinput");
		try {
			Files.writeString(file, "key.history.undo=ctrl+NOT_A_KEY\n");
			try {
				GLInputMapLoader.load(file);
				throw new AssertionError("Invalid key must fail loading");
			} catch (GLInputMapLoadException expected) {
				// Expected.
			}
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void testMissingFilePolicy() throws Exception {
		Path missing = Files.createTempFile("glswing-missing-input-", ".glinput");
		Files.delete(missing);
		AtomicInteger reported = new AtomicInteger();
		GLInputMap fallback = new GLInputMap(java.util.Map.of(),
				java.util.Map.of(), java.util.Map.of());
		GLInputMap loaded = GLInputMapLoader.loadOrDefault(missing, fallback,
				error -> reported.incrementAndGet());
		check(loaded == fallback && reported.get() == 1,
				"Explicit input-map fallback or error reporting failed");
	}

	private static KeyEvent key(int code, int modifiers) {
		return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED);
	}

	private static GLContainer container() {
		return new GLContainer() {
			@Override protected void record(GLContext2D context) {
			}
		};
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
