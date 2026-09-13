package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.ui.GLDefaultListModel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLListSelectionModel;
import com.vingame.glswing.ui.GLListStyle;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTheme;

/** CPU checks for list models, selection and viewport virtualization. */
public final class GLListSelfTest {
	private GLListSelfTest() { }

	public static void main(String[] args) {
		testMutableModelAndSelectionIndices();
		testKeyboardSelection();
		testViewportVirtualization();
		testInlineEditingLifecycle();
		testOptionalReordering();
		testThemeValues();
		System.out.println("GL list self-test passed.");
	}

	private static void testMutableModelAndSelectionIndices() {
		GLDefaultListModel<String> model =
				new GLDefaultListModel<>(List.of("A", "B", "C", "D"));
		GLList<String> list = new GLList<>(model);
		GLListSelectionModel selection = list.getSelectionModel();
		selection.setSelectionMode(
				GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
		AtomicInteger changes = new AtomicInteger();
		selection.addSelectionListener((source, oldValue, newValue) ->
				changes.incrementAndGet());
		selection.setSelectionInterval(1, 2);
		model.insertElementAt("X", 1);
		check(arrayEquals(selection.getSelectedIndices(), 2, 3),
				"Insertion did not shift selected indices");
		model.removeElementAt(2);
		check(arrayEquals(selection.getSelectedIndices(), 2),
				"Removal did not discard and shift selected indices");
		check(changes.get() >= 3, "Selection listeners missed model adjustments");
		list.setItems(List.of("Only"));
		check(list.getItemCount() == 1 && list.getSelectedIndices().length == 0,
				"Replacing items did not trim selection");
	}

	private static void testKeyboardSelection() {
		GLList<String> list = new GLList<>(List.of("A", "B", "C", "D"));
		list.getSelectionModel().setSelectionMode(
				GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
		list.keyPressed(key(KeyEvent.VK_DOWN, 0));
		check(list.getSelectedIndex() == 0, "Down did not select the first row");
		list.keyPressed(key(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK));
		check(arrayEquals(list.getSelectedIndices(), 0, 1),
				"Shift+Down did not extend selection");
		list.keyPressed(key(KeyEvent.VK_END, 0));
		check(list.getSelectedIndex() == 3,
				"End did not move selection to the final row");
	}

	private static void testViewportVirtualization() {
		List<Integer> values = new ArrayList<>();
		for (int index = 0; index < 10_000; index++) values.add(index);
		GLList<Integer> list = new GLList<>(values);
		GLScrollPane pane = new GLScrollPane(list);
		pane.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		pane.setBounds(0, 0, 300, 200);
		check(list.getGLChildren().size() < 20,
				"List attached cells for non-visible model elements");
		GLContainer hoveredCell = list.getGLChildren().stream()
				.min(java.util.Comparator.comparingDouble(GLContainer::getY))
				.orElseThrow();
		float pointerViewportY = hoveredCell.getY() + 5;
		hoveredCell.mouseEntered(mouse(hoveredCell, 5, 5));
		check(list.getHoveredIndex() == 0,
				"Cell hover did not reach the list model");
		pane.setScrollOffset(0, 28 * 5_000);
		check(list.getFirstVisibleIndex() >= 4_999
				&& list.getFirstVisibleIndex() <= 5_001
				&& list.getLastVisibleIndex() <= 5_009,
				"Visible cell range did not follow the viewport");
		check(list.getGLChildren().size() < 20,
				"Scrolling grew the attached cell count");
		int expectedHover = (int) Math.floor((28 * 5_000
				+ pointerViewportY - list.getGLStyle().borderWidth())
				/ list.getRowHeight());
		check(list.getHoveredIndex() == expectedHover,
				"Stationary-pointer hover did not follow scrolled content");
	}

	private static void testInlineEditingLifecycle() {
		GLDefaultListModel<String> model =
				new GLDefaultListModel<>(List.of("A", "B"));
		GLList<String> list = new GLList<>(model);
		list.setBounds(0, 0, 200, 80);
		AtomicInteger commits = new AtomicInteger();
		AtomicInteger cancellations = new AtomicInteger();
		var acceptCommit = new java.util.concurrent.atomic.AtomicBoolean(false);
		list.setCellEditor(new GLList.CellEditor<>() {
			@Override public GLContainer createEditor(GLList<String> source,
					String value, int index, GLList.EditController controller) {
				return empty();
			}
			@Override public void layoutEditor(GLList<String> source,
					String value, int index, GLContainer editor,
					float width, float height) {
				editor.setBounds(2, 2, width - 4, height - 4);
			}
			@Override public boolean commitEditor(GLList<String> source,
					String value, int index, GLContainer editor) {
				commits.incrementAndGet();
				return acceptCommit.get();
			}
			@Override public void cancelEditor(GLList<String> source,
					String value, int index, GLContainer editor) {
				cancellations.incrementAndGet();
			}
		});
		check(list.startEditingAt(0) && list.getEditingIndex() == 0,
				"List did not start inline editing");
		check(!list.commitEditing() && commits.get() == 1 && list.isEditing(),
				"Rejected commit did not retain its inline editor");
		acceptCommit.set(true);
		check(list.commitEditing() && commits.get() == 2 && !list.isEditing(),
				"List did not commit and remove its inline editor");
		check(list.startEditingAt(1), "List did not restart inline editing");
		list.cancelEditing();
		check(cancellations.get() == 1 && !list.isEditing(),
				"List did not cancel and remove its inline editor");
		check(list.startEditingAt(0), "List did not start model-change edit");
		model.setElementAt("Updated", 0);
		check(cancellations.get() == 2 && !list.isEditing(),
				"Model changes did not cancel their inline editor");
	}

	private static void testOptionalReordering() {
		GLDefaultListModel<String> model =
				new GLDefaultListModel<>(List.of("A", "B", "C"));
		GLList<String> list = new GLList<>(model);
		list.setSelectedIndex(0);
		check(list.moveSelectedItemBy(2)
				&& model.elements().equals(List.of("B", "C", "A"))
				&& list.getSelectedIndex() == 2,
				"Mutable list did not reorder its selected item");
		AtomicInteger requestedTarget = new AtomicInteger(-1);
		list.setReorderHandler((source, item, from, to) -> {
			requestedTarget.set(to); return false;
		});
		check(!list.moveSelectedItemBy(-1) && requestedTarget.get() == 1
				&& model.elements().equals(List.of("B", "C", "A")),
				"Rejected domain reorder mutated the list model");
		list.setCellTextAlignment(GLList.CellTextAlignment.RIGHT);
		check(list.getCellTextAlignment() == GLList.CellTextAlignment.RIGHT,
				"List text alignment was not retained");
	}

	private static void testThemeValues() {
		check(GLTheme.DEFAULT.values().contains("list.rowHeight"),
				"Default theme lacks list metrics");
		check(GLListStyle.fromTheme(GLTheme.DEFAULT.withCornerRadius(9))
				.cornerRadius() == 9,
				"Theme-wide radius did not reach lists");
	}

	private static GLKeyEvent key(int code, int modifiers) {
		return new GLKeyEvent(new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED));
	}

	private static GLContainer empty() {
		return new GLContainer() {
			@Override protected void record(
					com.vingame.glswing.rendering.GLContext2D context) { }
		};
	}
	private static GLMouseEvent mouse(GLContainer target, float x, float y) {
		MouseEvent event = new MouseEvent(new Canvas(), MouseEvent.MOUSE_ENTERED,
				System.currentTimeMillis(), 0, 0, 0, 0, false);
		GLPickResult pick = new GLPickResult(0, target, 0, 0, x, y,
				1, 0, 0, 1, 0, 0);
		return new GLMouseEvent(event, pick, 0, 0);
	}
	private static boolean arrayEquals(int[] actual, int... expected) {
		return java.util.Arrays.equals(actual, expected);
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
