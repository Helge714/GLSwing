package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLSliderGroup;
import com.vingame.glswing.ui.GLTextEditor;

/** CPU checks for grouped layout, drag linking and the FREE hover cursor. */
public final class GLSliderGroupSelfTest {
	private GLSliderGroupSelfTest() {
	}

	public static void main(String[] args) {
		testLayoutAndGestureLinking();
		testLinkedEditingHistory();
		testCompatibilityChecks();
		testFreeSliderCursor();
		testOptionalFreeSliderBounds();
		System.out.println("GL slider group self-test passed.");
	}

	private static void testOptionalFreeSliderBounds() {
		GLSlider slider = GLSlider.free("Bounded", 0, 10, 4);
		slider.setValueBounds(0.0, null);
		slider.setValue(-8);
		check(close(slider.getValue(), 0)
				&& slider.getValueMinimum().orElseThrow() == 0
				&& slider.getValueMaximum().isEmpty(),
				"A FREE slider did not enforce its optional lower bound");
		slider.setValueBounds(null, 8.0);
		slider.setValue(12);
		check(close(slider.getValue(), 8)
				&& slider.getValueMinimum().isEmpty()
				&& slider.getValueMaximum().orElseThrow() == 8,
				"A FREE slider did not enforce its optional upper bound");
	}

	private static void testLinkedEditingHistory() {
		GLSlider first = GLSlider.fixed("First", 0, 100, 10);
		GLSlider second = GLSlider.fixed("Second", 0, 100, 20);
		GLSliderGroup group = new GLSliderGroup(first, second);
		group.setBounds(0, 0, 180, 40);
		TestPanel panel = new TestPanel();
		panel.addGL(group);
		JPanel source = new JPanel();

		openLinkedEditor(source, first);
		GLTextEditor active = valueEditor(first);
		GLTextEditor mirror = valueEditor(second);
		type(active, '4');
		type(active, '2');
		check(active.getText().equals("42") && mirror.getText().equals("42"),
				"Linked editor did not mirror typed text");
		pressHistory(active, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		check(active.getText().equals("10") && mirror.getText().equals("10"),
				"Linked editing undo did not restore and mirror the baseline");
		pressHistory(active, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK
				| InputEvent.SHIFT_DOWN_MASK);
		check(active.getText().equals("42") && mirror.getText().equals("42"),
				"Linked editing redo did not restore the mirrored edit");
		check(active.commit(), "Linked slider edit did not commit");
		check(close(first.getValue(), 42) && close(second.getValue(), 42)
				&& !first.isValueEditing() && !second.isValueEditing(),
				"Linked commit did not update and close the complete group");
		check(panel.getGLHistoryManager().undo()
				&& close(first.getValue(), 10) && close(second.getValue(), 20),
				"One document undo did not restore all grouped slider values");

		openLinkedEditor(source, first);
		active = valueEditor(first);
		type(active, '9');
		active.cancel();
		check(close(first.getValue(), 10) && close(second.getValue(), 20)
				&& !panel.getGLHistoryManager().canUndo(),
				"Cancelling linked editing changed values or recorded history");
	}

	private static void openLinkedEditor(JPanel source, GLSlider first) {
		first.mousePressed(event(source, first, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 20, 10, 0, 0));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 21, 30, 1, 20));
		first.mouseReleased(event(source, first, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 21, 30, 0, 0));
	}

	private static GLTextEditor valueEditor(GLSlider slider) {
		return slider.getGLChildren().stream()
				.filter(GLTextEditor.class::isInstance)
				.map(GLTextEditor.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Slider editor was not opened"));
	}

	private static void type(GLTextEditor editor, char character) {
		KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_TYPED,
				System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, character);
		editor.keyTyped(new GLKeyEvent(event));
	}

	private static void pressHistory(GLTextEditor editor, int keyCode,
			int modifiers) {
		KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), modifiers, keyCode,
				KeyEvent.CHAR_UNDEFINED);
		check(editor.handleEditingHistoryKeyPressed(event, GLInputMaps.DEFAULT),
				"Linked editor did not consume its history shortcut");
	}

	private static void testLayoutAndGestureLinking() {
		GLSlider first = GLSlider.fixed("First", 0, 100, 10);
		GLSlider second = GLSlider.fixed("Second", 0, 100, 20);
		GLSlider third = GLSlider.fixed("Third", 0, 100, 30);
		GLSliderGroup group = new GLSliderGroup(first, second, third);
		group.setBounds(0, 0, 180, 60);
		check(first.getY() == 0 && first.getHeight() == 20
				&& second.getY() == 20 && second.getHeight() == 20
				&& third.getY() == 40 && third.getHeight() == 20,
				"Slider group did not create three adjacent rows");

		JPanel source = new JPanel();
		first.mousePressed(event(source, first, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 20, 10, 0, 0));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 21, 30, 1, 20));
		check(close(first.getValue(), 10) && close(second.getValue(), 20)
				&& close(third.getValue(), 30),
				"Mostly vertical collection already manipulated slider values");
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 144, 30, 123, 0));
		check(close(first.getValue(), 80) && close(second.getValue(), 80)
				&& close(third.getValue(), 30),
				"Horizontal sliding did not synchronize the collected rows");
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 90, 50, -54, 20));
		check(close(first.getValue(), 50) && close(second.getValue(), 50)
				&& close(third.getValue(), 30),
				"Group selection changed after horizontal sliding had begun");
		first.mouseReleased(event(source, first, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 90, 50, 0, 0));

		first.mousePressed(event(source, first, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 144, 10, 0, 0));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 45, 10, -99, 0));
		first.mouseReleased(event(source, first, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 45, 10, 0, 0));
		check(close(first.getValue(), 25) && close(second.getValue(), 50)
				&& close(third.getValue(), 30),
				"Linked sliders remained coupled after mouse release");

		first.mousePressed(event(source, first, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 45, 10, 0, 0));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 46, 50, 1, 40));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 46, 10, 0, -40));
		first.mouseReleased(event(source, first, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 46, 10, 0, 0));
		check(first.isValueEditing() && !second.isValueEditing()
				&& !third.isValueEditing(),
				"Rows left before sliding remained in the edit selection");
		first.cancelValueEditing();

		first.mousePressed(event(source, first, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, 45, 10, 0, 0));
		first.mouseDragged(event(source, first, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, 46, 30, 1, 20));
		first.mouseReleased(event(source, first, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, 46, 30, 0, 0));
		check(first.isValueEditing() && second.isValueEditing()
				&& !third.isValueEditing(),
				"Collected rows did not enter linked editing together");
		first.cancelValueEditing();
		check(!first.isValueEditing() && !second.isValueEditing(),
				"Cancelling linked editing did not close every editor");
	}

	private static void testCompatibilityChecks() {
		GLSliderGroup group = new GLSliderGroup(
				GLSlider.fixed("Base", 0, 10, 5));
		try {
			group.addSlider(GLSlider.fixed("Other range", 0, 20, 5));
			throw new AssertionError("Slider group accepted incompatible ranges");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		try {
			group.addSlider(GLSlider.free("Other mode", 5));
			throw new AssertionError("Slider group accepted mixed modes");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		group.setRange(-10, 10);
		for (GLSlider slider : group.getSliders())
			check(slider.getMinimum() == -10 && slider.getMaximum() == 10,
					"Atomic group range change left an incompatible slider behind");
	}

	private static void testFreeSliderCursor() {
		GLSlider slider = GLSlider.free("Free", 0);
		slider.setBounds(0, 0, 240, 28);
		JPanel source = new JPanel();
		Cursor original = source.getCursor();
		slider.mouseEntered(event(source, slider, MouseEvent.MOUSE_ENTERED,
				MouseEvent.NOBUTTON, 120, 10, 0, 0));
		check(source.getCursor().getType() == Cursor.E_RESIZE_CURSOR,
				"FREE slider center did not install a horizontal resize cursor");
		slider.mouseMoved(event(source, slider, MouseEvent.MOUSE_MOVED,
				MouseEvent.NOBUTTON, 10, 10, 0, 0));
		check(source.getCursor().getType() == Cursor.DEFAULT_CURSOR,
				"FREE slider arrow button retained the resize cursor");
		slider.mouseExited(event(source, slider, MouseEvent.MOUSE_EXITED,
				MouseEvent.NOBUTTON, 10, 10, 0, 0));
		check(source.getCursor().equals(original),
				"FREE slider did not restore the previous cursor");
	}

	private static GLMouseEvent event(JPanel source, GLSlider target, int id,
			int button, float localX, float localY, float deltaX, float deltaY) {
		MouseEvent original = new MouseEvent(source, id, System.currentTimeMillis(),
				button == MouseEvent.NOBUTTON ? MouseEvent.BUTTON1_DOWN_MASK : 0,
				Math.round(localX), Math.round(localY), 1, false, button);
		GLPickResult pick = new GLPickResult(0, target, localX, localY,
				localX, localY, 1, 0, 0, 1, 0, 0);
		return new GLMouseEvent(original, pick, deltaX, deltaY);
	}

	private static boolean close(double first, double second) {
		return Math.abs(first - second) < 0.0001;
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
