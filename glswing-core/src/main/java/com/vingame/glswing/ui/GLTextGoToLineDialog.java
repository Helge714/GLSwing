package com.vingame.glswing.ui;

import java.util.Objects;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.rendering.GLContext2D;

/** Theme-aware line-navigation dialog for multiline text editors. */
public final class GLTextGoToLineDialog {
	private GLTextGoToLineDialog() { }

	/** Opens a modeless line selector for the supplied editor. */
	public static GLDialog show(GLParent owner, GLTextArea target) {
		Objects.requireNonNull(owner, "Owner");
		Objects.requireNonNull(target, "Target editor");
		int lineCount = target.getLineCount();
		int currentLine = target.getDocument()
				.getLineOfOffset(target.getCaretPosition()) + 1;
		GLNumberSpinner line = new GLNumberSpinner(1,
				Math.max(2, lineCount), currentLine);
		line.setIntegerValuesOnly(true);
		line.setStep(1);
		if (lineCount == 1) line.setEnabled(false);
		line.getEditor().setCommitOnFocusLost(false);

		GLLocalizer localizer = owner.getGLLocalizer();
		GLLabel lineLabel = new GLLabel(localizer.text("glswing.text.lineNumber"));
		GLLabel range = new GLLabel(localizer.text("glswing.text.lineRange", lineCount));
		GLButton go = new GLButton(localizer.text("glswing.text.go"));
		GLButton cancel = new GLButton(localizer.text("glswing.text.cancel"));
		GLDialogButtonBar actions = new GLDialogButtonBar()
				.addButton(cancel).addButton(go);
		GLContainer content = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				float fieldX = 140;
				lineLabel.setBounds(16, 18, fieldX - 28, 28);
				line.setBounds(fieldX, 18, Math.max(1, width - fieldX - 16), 28);
				range.setBounds(16, 58, Math.max(1, width - 32), 28);
				actions.setBounds(16, 98, Math.max(1, width - 32), 28);
			}
		};
		content.addGL(lineLabel);
		content.addGL(line);
		content.addGL(range);
		content.addGL(actions);
		GLDialog dialog = new GLDialog(localizer.text(
				"glswing.text.goToLineTitle"), content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setModality(GLDialog.Modality.MODELESS);
		dialog.setSize(440, 180);
		dialog.setResizable(false);
		Runnable accept = () -> {
			target.goToLine(Math.min(lineCount,
					Math.max(1, (int) Math.round(line.getValue()))));
			dialog.close();
		};
		go.addActionListener(event -> accept.run());
		line.getEditor().addActionListener(event -> accept.run());
		cancel.addActionListener(event -> dialog.close());
		dialog.show(owner);
		content.requestGLFocus(line.getEditor());
		return dialog;
	}
}
