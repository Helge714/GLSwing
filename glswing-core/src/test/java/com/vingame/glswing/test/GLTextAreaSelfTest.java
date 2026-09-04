package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLTextArea;
import com.vingame.glswing.ui.GLTextDocument;

/** CPU checks for multiline document structure and editing semantics. */
public final class GLTextAreaSelfTest {
	private GLTextAreaSelfTest() { }

	public static void main(String[] args) {
		testDocumentLinesAndNormalization();
		testMultilineEditingAndHistory();
		testUnicodeAndVisualLineNavigation();
		testTrailingSpaceDoesNotMoveFittingWord();
		testAlignmentAndManualHyphenBreak();
		testRichFormattingAndScrollBar();
		testFocusSessionCommitsToOuterHistory();
		System.out.println("GL text area self-test passed.");
	}

	private static void testDocumentLinesAndNormalization() {
		GLTextDocument document = new GLTextDocument("one\r\ntwo\rthree\n");
		check(document.getText().equals("one\ntwo\nthree\n"),
				"Document did not normalize newline forms");
		check(document.getLineCount() == 4
				&& document.getLineText(1).equals("two")
				&& document.getLineOfOffset(8) == 2,
				"Document line index is inconsistent");
		int[] notifications = { 0 };
		document.addDocumentListener((source, change) -> notifications[0]++);
		document.replace(4, 7, "second");
		check(document.getLineText(1).equals("second") && notifications[0] == 1,
				"Document replacement was not reported exactly once");
	}

	private static void testMultilineEditingAndHistory() {
		GLTextArea area = new GLTextArea("first");
		area.setLineWrap(false);
		area.setTabSize(2);
		area.focusGained();
		area.setCaretPosition(area.getText().length());
		press(area, KeyEvent.VK_ENTER, 0);
		type(area, 'x');
		press(area, KeyEvent.VK_TAB, 0);
		check(area.getText().equals("first\nx\t")
				&& area.getDocument().getLineCount() == 2,
				"Text area did not insert multiline content");
		KeyEvent undo = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK,
				KeyEvent.VK_Z, KeyEvent.CHAR_UNDEFINED);
		check(area.handleEditingHistoryKeyPressed(undo,
				com.vingame.glswing.input.GLInputMaps.DEFAULT),
				"Focused text area did not claim its local history shortcut");
		check(area.getText().equals("first\nx"),
				"Text area local undo did not restore the previous edit");
		area.focusLost();
	}

	private static void testUnicodeAndVisualLineNavigation() {
		GLTextArea area = new GLTextArea("A😀B");
		area.setCaretPosition(3);
		press(area, KeyEvent.VK_BACK_SPACE, 0);
		check(area.getText().equals("AB"),
				"Backspace split a supplementary Unicode character");

		area.setText("one two three four five six");
		area.setBounds(0, 0, 55, 100);
		area.setLineWrap(true);
		area.setCaretPosition(0);
		press(area, KeyEvent.VK_DOWN, 0);
		check(area.getCaretPosition() > 0,
				"Down navigation ignored a soft-wrapped visual line");
	}

	private static void testTrailingSpaceDoesNotMoveFittingWord() {
		GLTextArea area = new GLTextArea("a word");
		var font = area.getGLStyle().font().measurementFont();
		float wordWidth = font.measureText("a word");
		float spaceWidth = font.measureText(" ");
		float padding = area.getGLStyle().padding().horizontal();
		area.setBounds(0, 0, wordWidth + padding + spaceWidth * 0.5f, 100);
		area.setLineWrap(true);
		area.setCaretPosition(area.getText().length());
		type(area, ' ');
		int continuationStart = area.getCaretPosition();
		press(area, KeyEvent.VK_HOME, 0);
		check(area.getCaretPosition() == continuationStart,
				"The first overflowing separator did not move the caret to the continuation row");
		type(area, ' ');
		press(area, KeyEvent.VK_HOME, 0);
		check(area.getCaretPosition() == continuationStart,
				"A second space was consumed as a wrap delimiter instead of indentation");
		area.setCaretPosition(area.getText().length());
		type(area, 'x');
		check(area.getText().endsWith("  x"),
				"Continuation-row indentation changed the document text");
	}

	private static void testFocusSessionCommitsToOuterHistory() {
		TestPanel panel = new TestPanel();
		GLTextArea area = new GLTextArea("before");
		panel.addGL(area);
		panel.requestGLFocus(area);
		area.setCaretPosition(area.getText().length());
		type(area, '!');
		panel.requestGLFocus(null);
		check(panel.getGLHistoryManager().canUndo(),
				"Leaving the text area did not commit its session to outer history");
		panel.getGLHistoryManager().undo();
		check(area.getText().equals("before"),
				"Outer undo did not restore the text-area session baseline");
		panel.getGLHistoryManager().redo();
		check(area.getText().equals("before!"),
				"Outer redo did not restore the completed text-area session");
	}

	private static void testRichFormattingAndScrollBar() {
		TestPanel panel = new TestPanel();
		GLTextArea area = new GLTextArea("plain bold italic");
		panel.addGL(area);
		panel.requestGLFocus(area);
		area.setCaretPosition(6);
		press(area, KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK);
		var boldItem = area.getContextMenu().getItems().get(1).getSubMenu()
				.getItems().get(0);
		boldItem.doClick();
		check(area.getDocument().getStyleAt(6).bold(),
				"Bold formatting was not applied to the selection");
		check(boldItem.getAction() != null && boldItem.getAction().isSelected(),
				"Bold action did not expose the active selection style");
		panel.getGLHistoryManager().undo();
		check(!area.getDocument().getStyleAt(6).bold(),
				"Bold formatting was not undoable through the enclosing history");
		panel.getGLHistoryManager().redo();
		check(area.getDocument().getStyleAt(6).bold(),
				"Bold formatting was not redoable through the enclosing history");

		area.setBounds(0, 0, 180, 45);
		area.setText("one\ntwo\nthree\nfour\nfive");
		check(area.isVerticalScrollBarVisible(),
				"Overflowing text did not expose its vertical scrollbar");
		area.setScrollOffset(0, 10);
		check(area.getVerticalScrollOffset() > 0,
				"The integrated vertical scrollbar has no scrollable range");
	}

	private static void testAlignmentAndManualHyphenBreak() {
		GLTextArea area = new GLTextArea("alpha-beta");
		area.setTextAlignment(com.vingame.glswing.ui.GLTextAlignment.JUSTIFIED);
		check(area.getTextAlignment() == com.vingame.glswing.ui.GLTextAlignment.JUSTIFIED
				&& area.getContextMenu() != null,
				"Text-area alignment or its context menu is unavailable");
		var font = area.getGLStyle().font().measurementFont();
		float padding = area.getGLStyle().padding().horizontal();
		float scrollBar = com.vingame.glswing.ui.GLScrollBarStyle
				.fromTheme(area.getGLTheme()).thickness() + 2;
		area.setBounds(0, 0,
				font.measureText("alpha-") + padding + scrollBar + 1, 100);
		area.setCaretPosition(0);
		press(area, KeyEvent.VK_DOWN, 0);
		press(area, KeyEvent.VK_HOME, 0);
		check(area.getCaretPosition() == "alpha-".length(),
				"A manual hyphen was not used as a preferred wrap boundary");

		TestPanel panel = new TestPanel();
		GLTextArea historyArea = new GLTextArea("alignment");
		panel.addGL(historyArea);
		var centerItem = historyArea.getContextMenu().getItems().get(0).getSubMenu()
				.getItems().get(1);
		centerItem.doClick();
		check(historyArea.getTextAlignment()
				== com.vingame.glswing.ui.GLTextAlignment.CENTER,
				"The alignment menu did not apply its command");
		check(centerItem.getAction() != null && centerItem.getAction().isSelected(),
				"Alignment action did not expose the active alignment");
		panel.getGLHistoryManager().undo();
		check(historyArea.getTextAlignment()
				== com.vingame.glswing.ui.GLTextAlignment.LEFT,
				"Alignment menu changes were not undoable");
		panel.getGLHistoryManager().redo();
		check(historyArea.getTextAlignment()
				== com.vingame.glswing.ui.GLTextAlignment.CENTER,
				"Alignment menu changes were not redoable");
		historyArea.setEditable(false);
		check(historyArea.getContextMenu().getItems().stream()
				.flatMap(item -> item.getSubMenu().getItems().stream())
				.noneMatch(com.vingame.glswing.ui.GLMenuItem::isEnabled),
				"Read-only text area retained enabled formatting actions");
	}

	private static void press(GLTextArea area, int code, int modifiers) {
		area.keyPressed(new GLKeyEvent(new KeyEvent(new Canvas(),
				KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
				code, KeyEvent.CHAR_UNDEFINED)));
	}
	private static void type(GLTextArea area, char value) {
		area.keyTyped(new GLKeyEvent(new KeyEvent(new Canvas(),
				KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
				KeyEvent.VK_UNDEFINED, value)));
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		@Override protected void render(GLContext2D context) { }
	}
}
