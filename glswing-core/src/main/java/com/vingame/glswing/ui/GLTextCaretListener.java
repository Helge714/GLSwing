package com.vingame.glswing.ui;

/** Receives caret and selection changes from a multiline text control. */
@FunctionalInterface
public interface GLTextCaretListener {
	void caretChanged(GLTextArea source, int caret, int selectionStart,
			int selectionEnd);
}
