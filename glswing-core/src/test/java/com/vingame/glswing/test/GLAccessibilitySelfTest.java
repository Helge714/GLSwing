package com.vingame.glswing.test;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLTextArea;

/** Platform-independent checks for the virtual Java accessibility tree. */
public final class GLAccessibilitySelfTest {
	private GLAccessibilitySelfTest() { }

	public static void main(String[] args) {
		Container root = new Container();
		GLButton button = new GLButton("Run");
		root.addGL(button);
		AccessibleContext rootContext = root.getAccessibleContext();
		check(rootContext.getAccessibleRole() == AccessibleRole.PANEL,
				"Container role was not exposed");
		check(rootContext.getAccessibleChildrenCount() == 1,
				"Accessible child was not exposed");
		check(rootContext.getAccessibleChild(0) == button,
				"Accessible hierarchy returned the wrong child");

		AccessibleContext buttonContext = button.getAccessibleContext();
		check(buttonContext.getAccessibleRole() == AccessibleRole.PUSH_BUTTON,
				"Button role was not exposed");
		check("Run".equals(buttonContext.getAccessibleName()),
				"Button text was not used as its accessible name");
		button.setAccessibleName("Run benchmark");
		button.setAccessibleDescription("Starts the rendering benchmark");
		check("Run benchmark".equals(buttonContext.getAccessibleName()),
				"Explicit accessible name was ignored");
		check("Starts the rendering benchmark".equals(
				buttonContext.getAccessibleDescription()),
				"Accessible description was ignored");
		int[] clicks = { 0 };
		button.addActionListener(event -> clicks[0]++);
		AccessibleAction buttonAction = buttonContext.getAccessibleAction();
		check(buttonAction != null && buttonAction.doAccessibleAction(0)
				&& clicks[0] == 1, "Button accessibility action did not click");

		GLCheckBox checkBox = new GLCheckBox("Enabled");
		check(!checkBox.getAccessibleContext().getAccessibleStateSet()
				.contains(AccessibleState.CHECKED),
				"Unchecked check box exposed a checked state");
		checkBox.getAccessibleContext().getAccessibleAction().doAccessibleAction(0);
		check(checkBox.isSelected() && checkBox.getAccessibleContext()
				.getAccessibleStateSet().contains(AccessibleState.CHECKED),
				"Check-box action did not update its accessible state");

		GLSlider slider = GLSlider.fixed("Opacity", 0, 1, 0.25);
		AccessibleValue value = slider.getAccessibleContext().getAccessibleValue();
		check(value != null && value.getCurrentAccessibleValue().doubleValue() == 0.25,
				"Slider value was not exposed");
		check(value.setCurrentAccessibleValue(0.75)
				&& slider.getValue() == 0.75,
				"Assistive technology could not change the slider value");

		GLTextArea textArea = new GLTextArea("alpha beta");
		textArea.select(0, 5);
		AccessibleText accessibleText = textArea.getAccessibleContext()
				.getAccessibleText();
		check(accessibleText != null && accessibleText.getCharCount() == 10,
				"Text content was not exposed");
		check("alpha".equals(accessibleText.getSelectedText()),
				"Text selection was not exposed");
		check("alpha".equals(accessibleText.getAtIndex(
				AccessibleText.WORD, 2)), "Word navigation was not exposed");

		button.setAccessibilityEnabled(false);
		check(rootContext.getAccessibleChildrenCount() == 0,
				"Decorative node remained in the accessible tree");
		System.out.println("GL accessibility self-test passed.");
	}

	private static final class Container extends GLContainer {
		@Override protected void record(GLContext2D context) { }
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
