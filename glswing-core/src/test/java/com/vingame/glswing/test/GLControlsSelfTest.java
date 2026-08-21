package com.vingame.glswing.test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.node.GLNodeStyle;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLTheme;

/** CPU checks for control sizing, actions, theme inheritance and focus. */
public final class GLControlsSelfTest {

	private GLControlsSelfTest() {
	}

	public static void main(String[] args) {
		testDefaultButtonStyle();
		testIntrinsicSizingAndOverride();
		testActionsAndDisabledState();
		testThemeAndFocusLifecycle();
		testDetachedSubtreeFontResolution();
		System.out.println("GL controls self-test passed.");
	}

	private static void testDefaultButtonStyle() {
		var style = GLTheme.DEFAULT.buttonStyle();
		check(style.background().equals(GLTheme.DEFAULT.values()
				.getColor("state.normal", Color.BLACK)),
				"Default button did not use the normal-state token");
		check(style.textColor().equals(GLTheme.DEFAULT.values()
				.getColor("palette.text", Color.BLACK)),
				"Default button did not use the text token");
		check(style.borderColor().equals(GLTheme.DEFAULT.values()
				.getColor("palette.border", Color.BLACK)),
				"Default button did not use the border token");
		check(style.cornerRadius() >= 0 && style.borderWidth() >= 0,
				"Default button radius or border width is invalid");
		GLButton button = new GLButton("Default");
		check(!button.isFocusable(), "Buttons are focusable by default");
		check(button.getPreferredGLSize().height() == GLButton.DEFAULT_HEIGHT,
				"Default button height is not 14 pixels");
		check(GLTheme.DEFAULT.checkBoxStyle().boxSize()
				== GLCheckBox.DEFAULT_BOX_SIZE,
				"Default check box does not use its size constant");

		GLTheme rounded = GLTheme.DEFAULT.withCornerRadius(9);
		check(rounded.buttonStyle().cornerRadius() == 9
				&& rounded.checkBoxStyle().cornerRadius() == 9
				&& rounded.sliderStyle().cornerRadius() == 9
				&& rounded.textEditorStyle().cornerRadius() == 9
				&& rounded.menuStyle().cornerRadius() == 9
				&& rounded.menuBarStyle().itemCornerRadius() == 9
				&& rounded.comboBoxStyle().cornerRadius() == 9
				&& GLNodeStyle.fromTheme(rounded).cornerRadius() == 9,
				"Theme-wide corner radius did not reach every control style");
	}

	private static void testIntrinsicSizingAndOverride() {
		GLLabel label = new GLLabel("Kurz");
		GLSize shortSize = label.getPreferredGLSize();
		label.setText("Ein wesentlich laengerer Text");
		GLSize longSize = label.getPreferredGLSize();
		check(longSize.width() > shortSize.width(),
				"Label text did not update intrinsic width");

		GLButton button = new GLButton("Button");
		check(button.getPreferredGLSize().width() > 0
				&& button.getPreferredGLSize().height() > 0,
				"Button did not compute an intrinsic size");
		button.setPreferredGLSize(321, 54);
		button.setText("Different");
		check(button.getPreferredGLSize().equals(new GLSize(321, 54)),
				"Intrinsic update replaced the user preferred-size override");
	}

	private static void testActionsAndDisabledState() {
		GLButton button = new GLButton("Count");
		AtomicInteger actions = new AtomicInteger();
		button.addActionListener(event -> actions.incrementAndGet());
		button.doClick();
		button.setEnabled(false);
		button.doClick();
		check(actions.get() == 1, "Disabled button fired an action");
		check(!button.isFocusTraversable(),
				"Disabled button remained focus traversable");

		GLCheckBox checkBox = new GLCheckBox("Enabled");
		AtomicInteger checkBoxActions = new AtomicInteger();
		checkBox.addActionListener(event -> checkBoxActions.incrementAndGet());
		check(!checkBox.isFocusable(), "Check box is focusable by default");
		checkBox.doClick();
		check(checkBox.isSelected() && checkBoxActions.get() == 1,
				"Check box click did not toggle and fire an action");
		checkBox.setEnabled(false);
		checkBox.doClick();
		check(checkBox.isSelected() && checkBoxActions.get() == 1,
				"Disabled check box reacted to doClick");
	}

	private static void testThemeAndFocusLifecycle() {
		TestPanel panel = new TestPanel();
		GLTheme custom = GLTheme.DEFAULT.withButtonStyle(
				GLTheme.DEFAULT.buttonStyle().withBackground(Color.MAGENTA));
		panel.setGLTheme(custom);
		GLButton button = new GLButton("Themed");
		panel.addGL(button);
		check(button.getGLStyle() == custom.buttonStyle(),
				"Button did not inherit the panel theme");
		button.setFocusable(true);
		panel.requestGLFocus(button);
		check(button.isFocused(), "Button did not receive GL focus");
		panel.removeGL(button);
		check(!button.isFocused(), "Removed button retained GL focus");
	}

	private static void testDetachedSubtreeFontResolution() {
		TestPanel panel = new TestPanel();
		panel.setGLTheme(GLTheme.DEFAULT.withCornerRadius(9));
		GLContainer subtree = new GLContainer() {
			@Override
			protected void record(GLContext2D context) {
			}
		};
		GLLabel label = new GLLabel("Detached label");
		GLButton button = new GLButton("Detached button");
		subtree.addGL(label);
		subtree.addGL(button);
		panel.addGL(subtree);

		panel.removeGL(subtree);
		label.setText("Still measurable after detach");
		button.setText("Still measurable after detach");
		check(label.getPreferredGLSize().width() > 0
				&& button.getPreferredGLSize().width() > 0,
				"Detached control subtree could not resolve measurement fonts");
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
