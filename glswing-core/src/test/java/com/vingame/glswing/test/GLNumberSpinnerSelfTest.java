package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLNumberSpinner;

/** CPU-only checks for numeric spinner constraints, editing and history. */
public final class GLNumberSpinnerSelfTest {
	private GLNumberSpinnerSelfTest() { }

	public static void main(String[] args) {
		testRangeAndIntegerValues();
		testEditorAndButtons();
		testHistory();
		System.out.println("GL number spinner self-test passed.");
	}

	private static void testRangeAndIntegerValues() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 12, 4.6);
		AtomicInteger changes = new AtomicInteger();
		spinner.addChangeListener(event -> changes.incrementAndGet());
		spinner.setIntegerValuesOnly(true);
		check(spinner.getValue() == 5 && changes.get() == 1,
				"Integer mode did not normalize and notify");
		spinner.setValue(20);
		check(spinner.getValue() == 12
				&& !spinner.getIncrementButton().isEnabled()
				&& spinner.getDecrementButton().isEnabled(),
				"Bounded spinner did not clamp its value");
		spinner.setRange(-3, 3);
		check(spinner.getValue() == 3,
				"Range change did not clamp the current value");
	}

	private static void testEditorAndButtons() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 12, 3);
		spinner.setIntegerValuesOnly(true);
		check(!spinner.getEditor().getInputFilter().test("2.5")
				&& spinner.getEditor().getInputFilter().test("12"),
				"Integer spinner accepted decimal editor input");
		spinner.getEditor().setText("8");
		check(spinner.getEditor().commit() && spinner.getValue() == 8,
				"Committed editor text did not update the spinner");
		spinner.getIncrementButton().doClick();
		spinner.getDecrementButton().doClick();
		check(spinner.getValue() == 8,
				"Spinner buttons did not change by one step");
		spinner.setStep(2);
		spinner.getIncrementButton().doClick();
		check(spinner.getValue() == 10,
				"Spinner button ignored the configured step");
	}

	private static void testHistory() {
		GLNumberSpinner spinner = new GLNumberSpinner(0, 10, 4);
		GLHistoryManager history = new GLHistoryManager();
		spinner.setGLHistoryManager(history);
		spinner.getIncrementButton().doClick();
		check(spinner.getValue() == 5 && history.canUndo(),
				"Button change was not recorded");
		history.undo();
		check(spinner.getValue() == 4,
				"Undo did not restore the previous spinner value");
		history.redo();
		check(spinner.getValue() == 5,
				"Redo did not restore the changed spinner value");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
