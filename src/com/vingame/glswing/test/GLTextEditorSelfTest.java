package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLTextInputFormats;
import com.vingame.glswing.rendering.GLContext2D;

/** CPU checks for single-line editing, selection, validation and cancellation. */
public final class GLTextEditorSelfTest {

	private GLTextEditorSelfTest() {
	}

	public static void main(String[] args) {
		testSelectionAndTyping();
		testValidationCommitAndCancel();
		testInputFormatAndRestoreMode();
		testTextCursor();
		System.out.println("GL text editor self-test passed.");
	}

	private static void testInputFormatAndRestoreMode() {
		GLTextEditor editor = new GLTextEditor("12");
		editor.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
		editor.setInputVerifier(text -> {
			try {
				return Double.isFinite(Double.parseDouble(text.replace(',', '.')));
			} catch (NumberFormatException ignored) {
				return false;
			}
		});
		editor.setCaretPosition(editor.getText().length());
		type(editor, 'x');
		check(editor.getText().equals("12"),
				"Decimal input format accepted a letter");

		editor.setValidationFailureMode(
				GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
		editor.setText("invalid");
		TestPanel panel = new TestPanel();
		panel.addGL(editor);
		panel.requestGLFocus(editor);
		check(!editor.commit() && editor.getText().equals("12")
				&& editor.isInvalid() && !editor.isFocused(),
				"Restore mode did not recover the text and release focus");
	}

	private static void testTextCursor() {
		GLTextEditor editor = new GLTextEditor("Text");
		editor.setBounds(0, 0, 100, 20);
		Canvas source = new Canvas();
		Cursor original = source.getCursor();
		GLPickResult pick = new GLPickResult(1, editor, 10, 10, 10, 10,
				1, 0, 0, 1, 0, 0);
		editor.mouseEntered(mouse(source, MouseEvent.MOUSE_ENTERED, pick));
		check(source.getCursor().getType() == Cursor.TEXT_CURSOR,
				"Editor did not install the text cursor");
		editor.mouseExited(mouse(source, MouseEvent.MOUSE_EXITED, pick));
		check(source.getCursor().equals(original),
				"Editor did not restore the previous cursor");
	}

	private static GLMouseEvent mouse(Canvas source, int id, GLPickResult pick) {
		MouseEvent event = new MouseEvent(source, id, System.currentTimeMillis(),
				0, 10, 10, 0, false, MouseEvent.NOBUTTON);
		return new GLMouseEvent(event, pick, 0, 0);
	}

	private static void testSelectionAndTyping() {
		GLTextEditor editor = new GLTextEditor("old");
		editor.selectAll();
		type(editor, '4');
		type(editor, '2');
		check(editor.getText().equals("42"),
				"Typing did not replace the selected text");
		check(editor.getCaretPosition() == 2
				&& editor.getSelectionStart() == editor.getSelectionEnd(),
				"Caret or selection was not updated after typing");
	}

	private static void testValidationCommitAndCancel() {
		GLTextEditor editor = new GLTextEditor("12");
		editor.setInputVerifier(text -> text.matches("\\d+"));
		AtomicInteger commits = new AtomicInteger();
		AtomicInteger cancellations = new AtomicInteger();
		editor.addActionListener(event -> commits.incrementAndGet());
		editor.addCancelListener(event -> cancellations.incrementAndGet());

		editor.setText("34");
		check(editor.commit() && commits.get() == 1,
				"Valid text was not committed");
		editor.setText("invalid");
		check(!editor.commit() && editor.isInvalid() && commits.get() == 1,
				"Invalid text was committed");
		editor.cancel();
		check(editor.getText().equals("34") && cancellations.get() == 1,
				"Cancel did not restore the last committed text");
	}

	private static void type(GLTextEditor editor, char character) {
		KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_TYPED,
				System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, character);
		editor.keyTyped(new GLKeyEvent(event));
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
		}
	}
}
