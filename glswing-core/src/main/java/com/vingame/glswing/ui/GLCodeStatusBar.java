package com.vingame.glswing.ui;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/** Status line for the active editor in a {@link GLCodeWorkspace}. */
public final class GLCodeStatusBar extends GLContainer {
	private static final float HEIGHT = 28;
	private final GLLabel position = label();
	private final GLLabel selection = label();
	private final GLLabel language = label();
	private final GLLabel indentation = label();
	private final GLLabel diagnostics = label();
	private final GLLabel intelligence = label();
	private final GLTextCaretListener caretListener = (source, caret, start, end) -> refresh();
	private final Runnable stateListener = this::refresh;
	private GLCodeEditor editor;
	private int line;
	private int column;
	private int selectionLength;
	private int errorCount;
	private int warningCount;

	public GLCodeStatusBar() {
		addGL(position); addGL(selection); addGL(language); addGL(indentation);
		addGL(diagnostics); addGL(intelligence);
		setIntrinsicPreferredGLSize(new GLSize(760, HEIGHT));
		refresh();
	}

	public GLCodeEditor getEditor() { return editor; }
	public void setEditor(GLCodeEditor value) {
		if (editor == value) return;
		if (editor != null) {
			editor.removeCaretListener(caretListener);
			editor.removeEditorStateListener(stateListener);
		}
		editor = value;
		if (editor != null) {
			editor.addCaretListener(caretListener);
			editor.addEditorStateListener(stateListener);
		}
		refresh();
	}

	public int getLine() { return line; }
	public int getColumn() { return column; }
	public int getSelectionLength() { return selectionLength; }
	public int getErrorCount() { return errorCount; }
	public int getWarningCount() { return warningCount; }

	private void refresh() {
		if (editor == null) {
			line = column = selectionLength = errorCount = warningCount = 0;
			position.setText(""); selection.setText(""); language.setText("");
			indentation.setText(""); diagnostics.setText(""); intelligence.setText("");
			return;
		}
		int caret = editor.getCaretPosition();
		int logicalLine = editor.getDocument().getLineOfOffset(caret);
		line = logicalLine + 1;
		column = caret - editor.getDocument().getLineStart(logicalLine) + 1;
		selectionLength = editor.getCarets().stream()
				.mapToInt(value -> value.end() - value.start()).sum();
		errorCount = (int) editor.getDiagnostics().stream()
				.filter(value -> value.severity() == GLCodeDiagnostic.Severity.ERROR).count();
		warningCount = (int) editor.getDiagnostics().stream()
				.filter(value -> value.severity() == GLCodeDiagnostic.Severity.WARNING).count();
		position.setText(text("glswing.code.status.position", line, column));
		selection.setText(selectionLength == 0 ? ""
				: text("glswing.code.status.selection", selectionLength));
		language.setText(editor.getLanguage().id());
		indentation.setText(text("glswing.code.status.spaces", editor.getIndentationSize()));
		diagnostics.setText(text("glswing.code.status.diagnostics", errorCount, warningCount));
		intelligence.setText(!editor.isEditable() ? text("glswing.code.status.readOnly")
				: text(editor.getDiagnosticProvider() == null
						? "glswing.code.status.intelligenceOff"
						: "glswing.code.status.intelligenceOn"));
		layoutLabels();
	}

	private String text(String key, Object... parameters) {
		return getGLLocalizer().text(key, parameters);
	}

	private static GLLabel label() {
		GLLabel result = new GLLabel("");
		result.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
		return result;
	}

	@Override protected void resized(float width, float height) { layoutLabels(); }
	private void layoutLabels() {
		float x = 8;
		GLLabel[] labels = { position, selection, language, indentation, diagnostics,
				intelligence };
		float[] minimums = { 104, 82, 92, 92, 142, 0 };
		for (int index = 0; index < labels.length; index++) {
			float width = index == labels.length - 1 ? Math.max(0, getWidth() - x - 8)
					: minimums[index];
			labels[index].setBounds(x, 0, width, getHeight());
			x += width;
		}
	}

	@Override protected void record(GLContext2D context) {
		GLTextEditorStyle style = getGLTheme().textEditorStyle();
		context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
				style.backgroundColor());
		context.fillUIRoundRect(0, 0, getWidth(), 1, 0, style.borderColor());
	}

	@Override protected void themeChanged() {
		GLLabelStyle base = getGLTheme().labelStyle();
		GLLabelStyle style = new GLLabelStyle(base.font(), base.textColor(), GLInsets.ZERO);
		for (GLLabel label : new GLLabel[] { position, selection, language,
				indentation, diagnostics, intelligence }) label.setGLStyle(style);
		invalidateRecording();
	}

	@Override protected void localizerChanged() { refresh(); }
}
