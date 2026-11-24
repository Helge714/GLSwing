package com.vingame.glswing.ui;

/** One source-completion proposal and the range replaced when it is accepted. */
public record GLCodeCompletion(String label, String detail, String insertText,
		int start, int end, int caretOffset, java.util.List<GLCodeTextEdit> additionalEdits) {
	public GLCodeCompletion(String label, String detail, String insertText,
			int start, int end) {
		this(label, detail, insertText, start, end, insertText.length(), java.util.List.of());
	}
	public GLCodeCompletion {
		if (label == null || label.isBlank() || detail == null || insertText == null
				|| start < 0 || end < start || caretOffset < 0
				|| caretOffset > insertText.length() || additionalEdits == null)
			throw new IllegalArgumentException("Invalid code completion");
		additionalEdits = java.util.List.copyOf(additionalEdits);
	}
}
