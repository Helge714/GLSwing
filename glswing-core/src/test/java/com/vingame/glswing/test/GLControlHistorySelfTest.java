package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.history.GLHistoryManager;
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
		testIndependentHistoryScopes(panel);
		System.out.println("GL control-history self-test passed.");
	}

	private static void testCheckBox(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLHistoryManager().reset();
		GLCheckBox checkBox = new GLCheckBox("Enabled");
		panel.addGL(checkBox);
		checkBox.mouseClicked(click(checkBox, MouseEvent.BUTTON1));
		check(checkBox.isSelected(), "Checkbox click did not change its value");
		check(panel.getGLHistoryManager().undo() && !checkBox.isSelected(),
				"Checkbox click was not undoable");
		check(panel.getGLHistoryManager().redo() && checkBox.isSelected(),
				"Checkbox click was not redoable");
	}

	private static void testComboBox(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLHistoryManager().reset();
		GLComboBox<String> combo = new GLComboBox<>(List.of("A", "B"));
		panel.addGL(combo);
		combo.getPopupMenu().getItems().get(1).doClick();
		check("B".equals(combo.getSelectedItem()),
				"Combo-box action did not select its item");
		check(panel.getGLHistoryManager().undo()
				&& "A".equals(combo.getSelectedItem()),
				"Combo-box selection was not undoable");
		check(panel.getGLHistoryManager().redo()
				&& "B".equals(combo.getSelectedItem()),
				"Combo-box selection was not redoable");
	}

	private static void testSlider(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLHistoryManager().reset();
		GLSlider slider = GLSlider.fixed("Value", 0, 10, 2);
		slider.setDefaultValue(7);
		panel.addGL(slider);
		slider.getResetMenuItem().doClick();
		check(slider.getValue() == 7, "Slider reset did not change its value");
		check(panel.getGLHistoryManager().undo() && slider.getValue() == 2,
				"Slider reset was not undoable");
		check(panel.getGLHistoryManager().redo() && slider.getValue() == 7,
				"Slider reset was not redoable");
	}

	private static void testTextEditor(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLHistoryManager().reset();
		GLTextEditor editor = new GLTextEditor("Before");
		panel.addGL(editor);
		editor.focusGained();
		editor.setText("After");
		check(editor.commit(), "Text editor rejected valid text");
		check(panel.getGLHistoryManager().undo()
				&& "Before".equals(editor.getText()),
				"Committed editor text was not undoable");
		check(panel.getGLHistoryManager().redo()
				&& "After".equals(editor.getText()),
				"Committed editor text was not redoable");
	}

	private static void testNodeDeletion(TestPanel panel) {
		panel.removeAllGL();
		panel.getGLHistoryManager().reset();
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
		GLHistoryManager history = panel.getGLHistoryManager();
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

	private static void testIndependentHistoryScopes(TestPanel panel) {
		panel.removeAllGL();
		GLHistoryManager hostHistory = panel.getGLHistoryManager();
		hostHistory.reset();
		GLHistoryManager firstHistory = new GLHistoryManager();
		GLHistoryManager secondHistory = new GLHistoryManager();
		TestContainer firstScope = new TestContainer();
		TestContainer secondScope = new TestContainer();
		firstScope.setGLHistoryManager(firstHistory);
		secondScope.setGLHistoryManager(secondHistory);
		GLCheckBox first = new GLCheckBox("First");
		GLCheckBox second = new GLCheckBox("Second");
		firstScope.addGL(first);
		secondScope.addGL(second);
		panel.addGL(firstScope);
		panel.addGL(secondScope);

		first.mouseClicked(click(first, MouseEvent.BUTTON1));
		second.mouseClicked(click(second, MouseEvent.BUTTON1));
		check(!hostHistory.canUndo(), "Scoped edits leaked into host history");
		check(firstHistory.undo() && !first.isSelected() && second.isSelected(),
				"First history scope did not undo independently");
		check(secondHistory.undo() && !second.isSelected(),
				"Second history scope did not undo independently");

		firstScope.clearGLHistoryManager();
		check(first.getGLHistoryManager() == hostHistory
				&& !firstScope.hasExplicitGLHistoryManager(),
				"Clearing a history scope did not restore host inheritance");
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

	private static final class TestContainer extends GLContainer {
		@Override
		protected void record(GLContext2D context) {
		}
	}
}
