package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.vingame.glswing.history.GLSaveManager;

/** CPU-only checks for history, transactions, save points and shortcuts. */
public final class GLSaveManagerSelfTest {

	private GLSaveManagerSelfTest() {
	}

	public static void main(String[] args) {
		testUndoRedoAndSavePoint();
		testNestedTransactions();
		testBranchAndLimit();
		testKeyboardShortcuts();
		System.out.println("GL save-manager self-test passed.");
	}

	private static void testUndoRedoAndSavePoint() {
		GLSaveManager manager = new GLSaveManager();
		int[] value = { 0 };
		value[0] = 1;
		manager.record("Set one", () -> value[0] = 0, () -> value[0] = 1);
		check(manager.isDirty() && manager.canUndo() && !manager.canRedo(),
				"Recording did not create a dirty undo state");
		manager.markSaved();
		check(!manager.isDirty(), "Saved checkpoint remained dirty");
		check(manager.undo() && value[0] == 0 && manager.isDirty(),
				"Undo did not restore the previous value");
		check(manager.redo() && value[0] == 1 && !manager.isDirty(),
				"Redo did not return to the saved checkpoint");
	}

	private static void testNestedTransactions() {
		GLSaveManager manager = new GLSaveManager();
		StringBuilder value = new StringBuilder();
		try (var outer = manager.beginTransaction("Build value")) {
			value.append('A');
			manager.record("A", () -> value.deleteCharAt(value.length() - 1),
					() -> value.append('A'));
			try (var inner = manager.beginTransaction("Inner")) {
				value.append('B');
				manager.record("B", () -> value.deleteCharAt(value.length() - 1),
						() -> value.append('B'));
			}
		}
		check("Build value".equals(manager.getUndoName()),
				"A compound edit lost its transaction name");
		manager.undo();
		check(value.isEmpty(), "Compound undo did not run in reverse order");
		manager.redo();
		check("AB".contentEquals(value),
				"Compound redo did not run in forward order");
	}

	private static void testBranchAndLimit() {
		GLSaveManager manager = new GLSaveManager(2);
		int[] value = { 0 };
		for (int next = 1; next <= 3; next++) {
			int before = value[0];
			int after = next;
			value[0] = after;
			manager.record("Set " + next, () -> value[0] = before,
					() -> value[0] = after);
		}
		manager.undo();
		manager.undo();
		check(!manager.canUndo() && value[0] == 1,
				"History limit retained too many edits");
		value[0] = 9;
		manager.record("Branch", () -> value[0] = 1, () -> value[0] = 9);
		check(!manager.canRedo(), "A new branch retained stale redo edits");
	}

	private static void testKeyboardShortcuts() {
		GLSaveManager manager = new GLSaveManager();
		int[] value = { 1 };
		manager.record("Set", () -> value[0] = 0, () -> value[0] = 1);
		Canvas source = new Canvas();
		KeyEvent undo = new KeyEvent(source, KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK,
				KeyEvent.VK_Z, 'Z');
		check(manager.handleKeyPressed(undo) && undo.isConsumed() && value[0] == 0,
				"Ctrl+Z did not invoke undo");
		KeyEvent redo = new KeyEvent(source, KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK
						| InputEvent.SHIFT_DOWN_MASK,
				KeyEvent.VK_Z, 'Z');
		check(manager.handleKeyPressed(redo) && redo.isConsumed() && value[0] == 1,
				"Ctrl+Shift+Z did not invoke redo");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
