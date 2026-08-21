package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.history.GLSaveManager;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLTextEditor;

/** Integration checks for user-level control edits reaching the root history. */
public final class GLControlHistorySelfTest {

	private GLControlHistorySelfTest() {
	}

	public static void main(String[] args) {
		TestPanel panel = new TestPanel();
		panel.setSize(640, 480);
		testCheckBox(panel);
		testComboBox(panel);
		testSlider(panel);
		testTextEditor(panel);
		testNodeDeletion(panel);
		System.out.println("GL control-history self-test passed.");
	}

	private static void testCheckBox(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLSaveManager().reset();
		GLCheckBox checkBox = new GLCheckBox("Enabled");
		panel.addGL(checkBox);
		checkBox.mouseClicked(click(checkBox, MouseEvent.BUTTON1));
		check(checkBox.isSelected(), "Checkbox click did not change its value");
		check(panel.getGLSaveManager().undo() && !checkBox.isSelected(),
				"Checkbox click was not undoable");
		check(panel.getGLSaveManager().redo() && checkBox.isSelected(),
				"Checkbox click was not redoable");
	}

	private static void testComboBox(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLSaveManager().reset();
		GLComboBox<String> combo = new GLComboBox<>(List.of("A", "B"));
		panel.addGL(combo);
		combo.getPopupMenu().getItems().get(1).doClick();
		check("B".equals(combo.getSelectedItem()),
				"Combo-box action did not select its item");
		check(panel.getGLSaveManager().undo()
				&& "A".equals(combo.getSelectedItem()),
				"Combo-box selection was not undoable");
		check(panel.getGLSaveManager().redo()
				&& "B".equals(combo.getSelectedItem()),
				"Combo-box selection was not redoable");
	}

	private static void testSlider(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLSaveManager().reset();
		GLSlider slider = GLSlider.fixed("Value", 0, 10, 2);
		slider.setDefaultValue(7);
		panel.addGL(slider);
		slider.getResetMenuItem().doClick();
		check(slider.getValue() == 7, "Slider reset did not change its value");
		check(panel.getGLSaveManager().undo() && slider.getValue() == 2,
				"Slider reset was not undoable");
		check(panel.getGLSaveManager().redo() && slider.getValue() == 7,
				"Slider reset was not redoable");
	}

	private static void testTextEditor(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLSaveManager().reset();
		GLTextEditor editor = new GLTextEditor("Before");
		panel.addGL(editor);
		editor.focusGained();
		editor.setText("After");
		check(editor.commit(), "Text editor rejected valid text");
		check(panel.getGLSaveManager().undo()
				&& "Before".equals(editor.getText()),
				"Committed editor text was not undoable");
		check(panel.getGLSaveManager().redo()
				&& "After".equals(editor.getText()),
				"Committed editor text was not redoable");
	}

	private static void testNodeDeletion(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLSaveManager().reset();
		GLNodeEditor editor = new GLNodeEditor();
		GLNode first = new GLNode("First");
		GLNode second = new GLNode("Second");
		first.addOutput("value", new GLLabel("Value"));
		second.addInput("value", new GLLabel("Value"));
		editor.addNode(first, 20, 30);
		editor.addNode(second, 240, 80);
		var connection = editor.connect(first.findPort("value"),
				second.findPort("value"));
		editor.setSelectedNodes(List.of(first, second));
		panel.addGL(editor);
		first.getContextMenu().getItems().get(1).doClick();
		GLSaveManager history = panel.getGLSaveManager();
		check(editor.getNodes().isEmpty(), "Node menu did not delete its selection");
		check(history.undo() && editor.getNodes().equals(List.of(first, second))
				&& editor.getSelectedNodes().equals(List.of(first, second))
				&& editor.getConnections().equals(List.of(connection)),
				"Deleted nodes and their selection were not restored");
		check(history.redo() && editor.getNodes().isEmpty(),
				"Restored node deletion was not redoable");
		history.undo();
		history.reset();
		KeyEvent delete = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), 0, KeyEvent.VK_DELETE,
				KeyEvent.CHAR_UNDEFINED);
		editor.keyPressed(new GLKeyEvent(delete));
		check(delete.isConsumed() && editor.getNodes().isEmpty(),
				"Delete did not remove the selected nodes");
		check(history.undo() && editor.getNodes().equals(List.of(first, second)),
				"Keyboard node deletion was not undoable");
	}

	private static GLMouseEvent click(GLCheckBox target, int button) {
		Canvas source = new Canvas();
		MouseEvent original = new MouseEvent(source, MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, 5, 5, 1, false, button);
		GLPickResult pick = new GLPickResult(1, target, 5, 5, 5, 5,
				1, 0, 0, 1, 0, 0);
		return new GLMouseEvent(original, pick, 0, 0);
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
