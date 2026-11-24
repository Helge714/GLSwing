package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.rendering.GLFont;

/** CPU checks for fixed/free slider models and cursor-center transforms. */
public final class GLSliderSelfTest {

	private GLSliderSelfTest() {
	}

	public static void main(String[] args) {
		testFixedSlider();
		testFreeSlider();
		testIndependentPresentations();
		testFreeBarRangeExpansion();
		testIntegerBarSnapping();
		testClickToEdit();
		testFreeDragRequestsRecentering();
		testLocalToScreenConversion();
		System.out.println("GL slider self-test passed.");
	}

	private static void testClickToEdit() {
		GLSlider slider = GLSlider.fixed("Fixed", 0, 10, 2.5);
		GLFontSpec sliderFont = new GLFontSpec(
				GLFont.SANS_SERIF, GLFont.BOLD, 9);
		slider.setGLStyle(slider.getGLStyle().withFont(sliderFont));
		slider.setBounds(0, 0, 100, 22);
		Canvas source = new Canvas();
		GLPickResult center = pick(slider, 50, 11);
		slider.mousePressed(mouse(source, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, center, 0, 0, null));
		slider.mouseReleased(mouse(source, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, center, 0, 0, null));
		slider.mouseClicked(mouse(source, MouseEvent.MOUSE_CLICKED,
				MouseEvent.BUTTON1, center, 0, 0, null));
		check(slider.isValueEditing() && slider.getGLChildren().size() == 1
				&& slider.getGLChildren().get(0) instanceof GLTextEditor,
				"A slider click did not open the inline editor");

		GLTextEditor editor = (GLTextEditor) slider.getGLChildren().get(0);
		check(editor.getX() == 0 && editor.getY() == 0
				&& editor.getWidth() == slider.getWidth()
				&& editor.getHeight() == slider.getHeight(),
				"Inline editor does not fully cover the slider");
		check(editor.getGLStyle().cornerRadius()
				== slider.getGLStyle().cornerRadius(),
				"Inline editor did not inherit the slider shape");
		check(editor.getGLStyle().font().equals(sliderFont),
				"Inline editor did not inherit the slider font");
		check(editor.getInputFilter().test("-1,5")
				&& !editor.getInputFilter().test("not a number"),
				"Slider editor does not use the numeric input format");
		editor.setText("invalid");
		check(!editor.commit() && editor.getText().equals("2.5")
				&& !slider.isValueEditing()
				&& slider.isValidationFeedbackActive(),
				"Invalid slider input did not close with slider feedback");
		slider.startValueEditing();
		editor = (GLTextEditor) slider.getGLChildren().get(0);
		editor.setText("4,5");
		check(editor.commit(), "A valid localized number was rejected");
		check(Math.abs(slider.getValue() - 4.5) < 0.0001
				&& !slider.isValueEditing() && slider.getGLChildren().isEmpty(),
				"Committed editor text did not update and close the slider editor");
		slider.setValueFormatter(value -> String.format("%.1f %%", value));
		slider.startValueEditing();
		editor = (GLTextEditor) slider.getGLChildren().get(0);
		check(editor.getInputFilter().test("6.5 %")
				&& editor.getInputFilter().test("€ 6.5"),
				"Slider editor rejected a decorated numeric value");
		editor.setText("6.5 %");
		check(editor.commit() && Math.abs(slider.getValue() - 6.5) < 0.0001,
				"Slider editor did not accept its formatted unit suffix");
	}

	private static void testFreeDragRequestsRecentering() {
		GLSlider slider = GLSlider.free("Free", 0);
		slider.setBounds(0, 0, 100, 22);
		slider.setDragSensitivity(0.1);
		slider.setPreciseDragFactor(0.2);
		slider.setCoarseDragFactor(5);
		check(slider.getDragSensitivity() == 0.1
				&& slider.getPreciseDragFactor() == 0.2
				&& slider.getCoarseDragFactor() == 5,
				"FREE drag configuration was not retained");
		Canvas source = new Canvas();
		GLPickResult center = pick(slider, 50, 11);
		slider.mouseEntered(mouse(source, MouseEvent.MOUSE_ENTERED,
				MouseEvent.NOBUTTON, center, 0, 0, null));
		slider.mousePressed(mouse(source, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, center, 0, 0, null));

		AtomicReference<Point2D.Float> warp = new AtomicReference<>();
		GLPickResult dragged = pick(slider, 60, 11);
		slider.mouseDragged(mouse(source, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, dragged, 10, 0,
				(x, y) -> warp.set(new Point2D.Float(x, y))));
		check(Math.abs(slider.getValue() - 1) < 0.0001,
				"FREE drag changed the wrong amount");
		check(warp.get() != null && Math.abs(warp.get().x - 50) < 0.001f
				&& Math.abs(warp.get().y - 11) < 0.001f,
				"FREE drag did not request a warp to its center");

		slider.mouseDragged(mouseWithModifiers(source,
				MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, dragged,
				10, 0, MouseEvent.SHIFT_DOWN_MASK,
				(x, y) -> warp.set(new Point2D.Float(x, y))));
		check(Math.abs(slider.getValue() - 1.2) < 0.0001,
				"Shift did not apply the precise FREE drag factor");

		slider.mouseDragged(mouseWithModifiers(source,
				MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, dragged,
				10, 0, MouseEvent.CTRL_DOWN_MASK,
				(x, y) -> warp.set(new Point2D.Float(x, y))));
		check(Math.abs(slider.getValue() - 6.2) < 0.0001,
				"Control did not apply the coarse FREE drag factor");

		slider.mouseDragged(mouseWithModifiers(source,
				MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, dragged,
				10, 0, MouseEvent.SHIFT_DOWN_MASK | MouseEvent.CTRL_DOWN_MASK,
				(x, y) -> warp.set(new Point2D.Float(x, y))));
		check(Math.abs(slider.getValue() - 6.4) < 0.0001,
				"Shift must take precedence when both drag modifiers are held");

		slider.mouseReleased(mouse(source, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, center, 0, 0, null));
	}

	private static GLPickResult pick(GLSlider slider, float x, float y) {
		return new GLPickResult(1, slider, x, y, x, y,
				1, 0, 0, 1, 0, 0);
	}

	private static GLMouseEvent mouse(Canvas source, int id, int button,
			GLPickResult pick, float deltaX, float deltaY,
			GLMouseEvent.PointerWarpRequest warp) {
		return mouseWithModifiers(source, id, button, pick, deltaX, deltaY,
				0, warp);
	}

	private static GLMouseEvent mouseWithModifiers(Canvas source, int id,
			int button, GLPickResult pick, float deltaX, float deltaY,
			int modifiersEx, GLMouseEvent.PointerWarpRequest warp) {
		MouseEvent original = new MouseEvent(source, id, System.currentTimeMillis(),
				modifiersEx, Math.round(pick.getScreenX()), Math.round(pick.getScreenY()),
				1, false, button);
		return new GLMouseEvent(original, pick, deltaX, deltaY, warp);
	}

	private static void testFixedSlider() {
		GLSlider slider = GLSlider.fixed("Fixed", 0, 10, 20);
		check(slider.getMode() == GLSlider.Mode.FIXED, "Wrong fixed mode");
		check(slider.getValue() == 10 && slider.getDefaultValue() == 10,
				"Initial fixed value or default was not clamped");
		AtomicInteger changes = new AtomicInteger();
		slider.addChangeListener(event -> changes.incrementAndGet());
		slider.setValue(-4);
		check(slider.getValue() == 0 && changes.get() == 1,
				"Fixed value did not clamp or notify");
		slider.setRange(-5, 5);
		slider.setValue(2.5);
		check(slider.getMinimum() == -5 && slider.getMaximum() == 5
				&& slider.getValue() == 2.5 && slider.getDefaultValue() == 5,
				"Fixed range update did not clamp value and default");
		slider.setDefaultValue(4);
		slider.setValue(-2);
		slider.setBounds(0, 0, 100, 22);
		TestPanel panel = new TestPanel();
		panel.setSize(300, 200);
		panel.addGL(slider);
		AtomicInteger customActions = new AtomicInteger();
		slider.getContextMenu().addItem("Custom",
				event -> customActions.incrementAndGet());
		check(slider.getContextMenu().getItems().size() == 2,
				"Slider context menu could not be extended");
		GLPickResult center = pick(slider, 50, 11);
		slider.mouseClicked(mouse(new Canvas(), MouseEvent.MOUSE_CLICKED,
				MouseEvent.BUTTON3, center, 0, 0, null));
		check(slider.getValue() == -2 && slider.getContextMenu().isShowing(),
				"Right-click reset immediately instead of opening the context menu");
		slider.getResetMenuItem().doClick();
		check(slider.getValue() == 4 && !slider.getContextMenu().isShowing(),
				"Reset menu item did not restore the slider default");
		check(customActions.get() == 0,
				"Opening the slider context menu fired a custom action");
		slider.setGLLocalizer(GLLocalizer.GERMAN);
		check(slider.getResetMenuItem().getText().equals("Zurücksetzen"),
				"Slider reset text did not follow the theme strings");
	}

	private static void testFreeSlider() {
		GLSlider slider = GLSlider.free("Free", 4);
		check(slider.getMode() == GLSlider.Mode.FREE, "Wrong free mode");
		slider.setStep(2.5);
		slider.increment();
		slider.decrement();
		check(slider.getValue() == 4, "FREE step controls changed the wrong amount");
		slider.setDefaultValue(-3.5);
		slider.setValue(12);
		slider.reset();
		check(slider.getValue() == -3.5,
				"FREE reset did not restore its configured default");
		slider.setValueFormatter(value -> "v=" + (int) value);
		boolean rejectedRange = false;
		try {
			slider.getMinimum();
		} catch (IllegalStateException expected) {
			rejectedRange = true;
		}
		check(rejectedRange, "FREE slider exposed a fixed range");
	}

	private static void testIndependentPresentations() {
		GLSlider fixedButtons = GLSlider.fixed("Fixed buttons", 0, 10, 4,
				GLSlider.Presentation.BUTTONS);
		GLSlider freeBar = GLSlider.free("Free bar", 4,
				GLSlider.Presentation.BAR);
		check(fixedButtons.getMode() == GLSlider.Mode.FIXED
				&& fixedButtons.getPresentation() == GLSlider.Presentation.BUTTONS,
				"A fixed slider could not use button presentation");
		check(freeBar.getMode() == GLSlider.Mode.FREE
				&& freeBar.getPresentation() == GLSlider.Presentation.BAR,
				"A free slider could not use bar presentation");
		fixedButtons.increment();
		check(fixedButtons.getValue() == 5,
				"Fixed button presentation did not retain bounded step controls");
		freeBar.setPresentation(GLSlider.Presentation.BUTTONS);
		check(freeBar.getMode() == GLSlider.Mode.FREE
				&& freeBar.getPresentation() == GLSlider.Presentation.BUTTONS,
				"Changing presentation also changed the value mode");
	}

	private static void testFreeBarRangeExpansion() {
		GLSlider slider = GLSlider.free("Free bar", 0, 10, 5);
		check(slider.getDisplayMinimum() == 0 && slider.getDisplayMaximum() == 10
				&& slider.getVisibleMinimum() == 0 && slider.getVisibleMaximum() == 10,
				"Free bar did not retain its baseline display range");
		slider.setValue(20);
		check(slider.getVisibleMaximum() == 20,
				"Free bar did not expand to an out-of-range value");
		slider.setValue(15);
		check(slider.getVisibleMaximum() == 20,
				"Free bar discarded its expansion before returning to baseline");
		slider.setValue(10);
		check(slider.getVisibleMaximum() == 10,
				"Free bar did not restore its baseline range");
		slider.setValue(-4);
		check(slider.getVisibleMinimum() == -4,
				"Free bar did not expand its lower display edge");

		slider.setBounds(0, 0, 100, 22);
		slider.setValue(20);
		Canvas source = new Canvas();
		GLPickResult start = pick(slider, 100, 11);
		slider.mousePressed(mouse(source, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, start, 0, 0, null));
		GLPickResult middle = pick(slider, 50, 11);
		slider.mouseDragged(mouse(source, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, middle, -50, 0, null));
		check(slider.getValue() == 10 && slider.getVisibleMaximum() == 10,
				"Dragging into the baseline did not collapse the expanded range");
	}

	private static void testIntegerBarSnapping() {
		GLSlider slider = GLSlider.fixed("Whole", 0, 10, 2.5);
		slider.setIntegerValuesOnly(true);
		check(slider.getValue() == 3 && slider.isIntegerValuesOnly(),
				"Enabling integer values did not normalize the current value");
		slider.setValue(7.6);
		check(slider.getValue() == 8,
				"Integer-only assignment did not snap to a whole value");
		slider.setBounds(0, 0, 100, 22);
		Canvas source = new Canvas();
		GLPickResult start = pick(slider, 80, 11);
		slider.mousePressed(mouse(source, MouseEvent.MOUSE_PRESSED,
				MouseEvent.BUTTON1, start, 0, 0, null));
		GLPickResult dragged = pick(slider, 46, 11);
		slider.mouseDragged(mouse(source, MouseEvent.MOUSE_DRAGGED,
				MouseEvent.NOBUTTON, dragged, -34, 0, null));
		check(slider.getValue() == 5,
				"Integer-only bar dragging did not jump between whole values");
		slider.mouseReleased(mouse(source, MouseEvent.MOUSE_RELEASED,
				MouseEvent.BUTTON1, dragged, 0, 0, null));
		slider.startValueEditing();
		GLTextEditor editor = (GLTextEditor) slider.getGLChildren().get(0);
		check(!editor.getInputFilter().test("1.5")
				&& editor.getInputFilter().test("-12"),
				"Integer-only inline editing retained decimal input");
		editor.cancel();
	}

	private static void testLocalToScreenConversion() {
		GLPickResult pick = new GLPickResult(1, null, 0, 0, 0, 0,
				0.5f, 0, 0, 0.25f, -5, -10);
		Point2D.Float screen = pick.toScreen(10, 10);
		check(Math.abs(screen.x - 30) < 0.001f
				&& Math.abs(screen.y - 80) < 0.001f,
				"Local cursor center was not transformed back to screen space");
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
