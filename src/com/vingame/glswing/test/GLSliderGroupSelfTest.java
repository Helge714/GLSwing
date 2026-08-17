package com.vingame.glswing.test;

import java.awt.Cursor;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLSliderGroup;

/** CPU checks for grouped layout, drag linking and the FREE hover cursor. */
public final class GLSliderGroupSelfTest {
	private GLSliderGroupSelfTest() {
	}

	public static void main(String[] args) {
		testLayoutAndGestureLinking();
		testCompatibilityChecks();
		testFreeSliderCursor();
		System.out.println("GL slider group self-test passed.");
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
		JPanel source = new JPanel();
		Cursor original = source.getCursor();
		slider.mouseEntered(event(source, slider, MouseEvent.MOUSE_ENTERED,
				MouseEvent.NOBUTTON, 10, 10, 0, 0));
		check(source.getCursor().getType() == Cursor.E_RESIZE_CURSOR,
				"FREE slider did not install a horizontal resize cursor");
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
}
