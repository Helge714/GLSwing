package com.vingame.glswing.ui;

import java.awt.event.KeyEvent;
import java.util.Objects;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.rendering.GLContext2D;

/** Theme-aware Find and Replace dialog for multiline text and code editors. */
public final class GLTextSearchDialog {
	private GLTextSearchDialog() { }

	/** Opens a modeless dialog so selected matches remain visible in the editor. */
	public static GLDialog show(GLParent owner, GLTextArea target,
			boolean replaceVisible) {
		Objects.requireNonNull(owner, "Owner");
		Objects.requireNonNull(target, "Target editor");
		GLCheckBox matchCase = new GLCheckBox(owner.getGLLocalizer().text(
				"glswing.text.matchCase"));
		Runnable[] refresh = new Runnable[1];
		String[] submittedQuery = { null };
		boolean[] submittedMatchCase = { false };
		GLTextEditor find = new GLTextEditor(target.getSelectedText()) {
			@Override protected void controlKeyPressed(GLKeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_UP) {
					target.findPrevious(getText(), matchCase.isSelected());
					submittedQuery[0] = getText();
					submittedMatchCase[0] = matchCase.isSelected();
					if (refresh[0] != null) refresh[0].run();
					event.consume();
				} else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
					target.findNext(getText(), matchCase.isSelected());
					submittedQuery[0] = getText();
					submittedMatchCase[0] = matchCase.isSelected();
					if (refresh[0] != null) refresh[0].run();
					event.consume();
				} else super.controlKeyPressed(event);
			}
		};
		GLTextEditor replacement = new GLTextEditor();
		// These actions are commands bound to Enter. A focus-loss commit would run
		// them again before the clicked dialog button receives its own action.
		find.setCommitOnFocusLost(false);
		replacement.setCommitOnFocusLost(false);
		GLLabel findLabel = new GLLabel(owner.getGLLocalizer().text(
				"glswing.text.findLabel"));
		GLLabel replacementLabel = new GLLabel(owner.getGLLocalizer().text(
				"glswing.text.replaceLabel"));
		GLLabel result = new GLLabel("0 / 0");
		result.setAlignment(GLLabel.Alignment.RIGHT);
		result.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
		GLButton previous = button(owner, "glswing.text.findPrevious");
		GLButton next = button(owner, "glswing.text.findNext");
		GLButton replace = button(owner, "glswing.text.replaceCurrent");
		GLButton all = button(owner, "glswing.text.replaceAll");
		GLButton close = button(owner, "glswing.text.close");
		GLDialogButtonBar actions = new GLDialogButtonBar();
		if (replaceVisible) actions.addButton(replace).addButton(all);
		actions.addButton(previous).addButton(next).addButton(close);
		GLContainer content = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				float fieldX = 130, resultWidth = 68;
				float fieldWidth = Math.max(1, width - fieldX - resultWidth - 24);
				findLabel.setBounds(16, 18, 104, 28);
				find.setBounds(fieldX, 18, fieldWidth, 28);
				result.setBounds(width - resultWidth - 16, 18, resultWidth, 28);
				if (replaceVisible) {
					replacementLabel.setBounds(16, 58, 104, 28);
					replacement.setBounds(fieldX, 58, fieldWidth, 28);
					matchCase.setBounds(16, 98, width - 32, 28);
					actions.setBounds(16, 140, width - 32, 28);
				} else {
					matchCase.setBounds(16, 58, width - 32, 28);
					actions.setBounds(16, 100, width - 32, 28);
				}
			}
		};
		content.addGL(findLabel); content.addGL(find); content.addGL(result);
		if (replaceVisible) { content.addGL(replacementLabel); content.addGL(replacement); }
		content.addGL(matchCase); content.addGL(actions);
		GLDialog dialog = new GLDialog(owner.getGLLocalizer().text(
				"glswing.text.findTitle"), content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setModality(GLDialog.Modality.MODELESS);
		dialog.setSize(620, replaceVisible ? 220 : 180);
		dialog.setResizable(false);
		refresh[0] = () -> {
			int count = target.getSearchMatchCount();
			int active = target.getActiveSearchMatchIndex();
			result.setText((active < 0 ? 0 : active + 1) + " / " + count);
			boolean searchable = !find.getText().isEmpty();
			previous.setEnabled(searchable);
			next.setEnabled(searchable);
			replace.setEnabled(searchable && target.isEditable());
			all.setEnabled(searchable && target.isEditable());
		};
		Runnable submitNext = () -> {
			String query = find.getText();
			boolean exact = matchCase.isSelected();
			if (query.equals(submittedQuery[0]) && exact == submittedMatchCase[0])
				target.findNext(query, exact);
			else target.startSearch(query, exact);
			submittedQuery[0] = query;
			submittedMatchCase[0] = exact;
			refresh[0].run();
		};
		previous.addActionListener(event -> {
			target.findPrevious(find.getText(), matchCase.isSelected());
			submittedQuery[0] = find.getText();
			submittedMatchCase[0] = matchCase.isSelected();
			refresh[0].run();
		});
		next.addActionListener(event -> submitNext.run());
		find.addActionListener(event -> submitNext.run());
		Runnable replaceCurrent = () -> {
			String query = find.getText();
			boolean exact = matchCase.isSelected();
			if (!query.equals(submittedQuery[0]) || exact != submittedMatchCase[0])
				target.startSearch(query, exact);
			target.replaceCurrent(query, replacement.getText(), exact);
			submittedQuery[0] = query;
			submittedMatchCase[0] = exact;
			refresh[0].run();
		};
		replace.addActionListener(event -> replaceCurrent.run());
		replacement.addActionListener(event -> replaceCurrent.run());
		all.addActionListener(event -> {
			target.replaceAll(find.getText(), replacement.getText(),
					matchCase.isSelected());
			target.startSearch(find.getText(), matchCase.isSelected());
			submittedQuery[0] = find.getText();
			submittedMatchCase[0] = matchCase.isSelected();
			refresh[0].run();
		});
		find.addTextChangeListener(event -> {
			submittedQuery[0] = null;
			target.clearSearch();
			refresh[0].run();
		});
		matchCase.addActionListener(event -> {
			submittedQuery[0] = null;
			target.clearSearch();
			refresh[0].run();
		});
		close.addActionListener(event -> dialog.close());
		dialog.closedFuture().whenComplete((ignored, error) -> target.clearSearch());
		refresh[0].run();
		dialog.show(owner);
		return dialog;
	}

	private static GLButton button(GLParent owner, String key) {
		return new GLButton(owner.getGLLocalizer().text(key));
	}
}
