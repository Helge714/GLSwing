package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLKeyBinding;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.node.GLNodeStyle;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLAction;
import com.vingame.glswing.ui.GLActionMap;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLTheme;

/** CPU checks for control sizing, actions, theme inheritance and focus. */
public final class GLControlsSelfTest {

	private GLControlsSelfTest() {
	}

	public static void main(String[] args) {
		testDefaultButtonStyle();
		testIntrinsicSizingAndOverride();
		testActionsAndDisabledState();
		testSharedActions();
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
		check(button.isFocusable(), "Buttons are not focusable by default");
		check(button.getPreferredGLSize().height() == GLTheme.DEFAULT.buttonStyle().height(),
				"Default button height does not follow its theme");
		check(GLTheme.DEFAULT.checkBoxStyle().boxSize()
				== GLTheme.DEFAULT.values().getFloat("checkBox.boxSize", -1),
				"Default check box does not use its theme size");

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
		check(checkBox.isFocusable(), "Check box is not focusable by default");
		checkBox.doClick();
		check(checkBox.isSelected() && checkBoxActions.get() == 1,
				"Check box click did not toggle and fire an action");
		checkBox.setEnabled(false);
		checkBox.doClick();
		check(checkBox.isSelected() && checkBoxActions.get() == 1,
				"Disabled check box reacted to doClick");
	}

	private static void testSharedActions() {
		AtomicBoolean available = new AtomicBoolean(true);
		AtomicInteger invocations = new AtomicInteger();
		GLAction action = GLAction.builder("test.run")
				.textKey("test.action")
				.shortDescriptionKey("test.action.description")
				.enabledWhen(available::get)
				.onAction(invocations::incrementAndGet)
				.build();
		GLLocalizer localizer = GLLocalizer.ENGLISH
				.with("test.action", "Run")
				.with("test.action.description", "Run the test action");
		GLButton button = new GLButton(action);
		GLMenuItem item = new GLMenuItem(action);
		button.setGLLocalizer(localizer);
		item.setGLLocalizer(localizer);
		check("Run".equals(button.getText()) && "Run".equals(item.getText())
				&& "Run the test action".equals(button.getToolTipText()),
				"Shared action presentation was not localized consistently");
		button.doClick();
		check(invocations.get() == 1, "Action-backed button did not invoke its action");

		available.set(false); action.refreshEnabled();
		check(!button.isEnabled() && !item.isEnabled(),
				"Action availability did not update all bound controls");
		available.set(true); action.refreshEnabled();

		GLInputMap input = new GLInputMap(Map.of("test.run", List.of(
				new GLKeyBinding(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK))),
				Map.of(), Map.of());
		GLActionMap actions = new GLActionMap().add(action);
		item.setGLInputMap(input);
		check(item.getAcceleratorText().equals(action.acceleratorText(input)),
				"Action-backed menu item did not refresh its shortcut presentation");
		KeyEvent original = new KeyEvent(new TestPanel(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK,
				KeyEvent.VK_R, 'R');
		GLKeyEvent event = new GLKeyEvent(original);
		check(actions.handleKeyPressed(event, input, actions) && event.isConsumed()
				&& invocations.get() == 2,
				"Action map did not dispatch and consume a semantic shortcut");
		check(!action.acceleratorText(input).isBlank(),
				"Action did not expose its configured shortcut label");

		AtomicInteger toggleInvocations = new AtomicInteger();
		GLAction toggle = GLAction.builder("test.toggle")
				.text("Show guides").selected(false)
				.onAction(toggleInvocations::incrementAndGet).build();
		GLCheckBox toggleBox = new GLCheckBox(toggle);
		GLMenuItem toggleItem = new GLMenuItem(toggle);
		toggleBox.doClick();
		check(toggle.isSelected() && toggleBox.isSelected()
				&& toggleInvocations.get() == 1,
				"Selectable action did not synchronize its check box binding");
		toggleItem.doClick();
		check(!toggle.isSelected() && !toggleBox.isSelected()
				&& toggleInvocations.get() == 2,
				"Menu invocation did not synchronize the shared toggle state");
		AtomicBoolean externalSelection = new AtomicBoolean();
		GLAction derived = GLAction.builder("test.derived")
				.selectedWhen(externalSelection::get)
				.onAction(() -> externalSelection.set(true)).build();
		new GLActionMap().add(derived).refresh();
		derived.perform(derived);
		check(derived.isSelected(),
				"Derived action did not refresh selection after invocation");

		javax.swing.Action swing = new javax.swing.AbstractAction("Swing command") {
			private static final long serialVersionUID = 1L;
			@Override public void actionPerformed(java.awt.event.ActionEvent event) {
				invocations.incrementAndGet();
			}
		};
		GLAction adapted = GLAction.fromSwing("test.swing", swing);
		GLButton adaptedButton = new GLButton(adapted);
		swing.putValue(javax.swing.Action.NAME, "Updated command");
		swing.setEnabled(false);
		check("Updated command".equals(adaptedButton.getText())
				&& !adaptedButton.isEnabled(),
				"Swing Action changes were not reflected by the GLSwing adapter");
		check(!adapted.isSelectable(),
				"A Swing action without SELECTED_KEY became selectable");
		javax.swing.Action swingToggle = new javax.swing.AbstractAction("Swing toggle") {
			private static final long serialVersionUID = 1L;
			@Override public void actionPerformed(java.awt.event.ActionEvent event) { }
		};
		swingToggle.putValue(javax.swing.Action.SELECTED_KEY, false);
		GLAction adaptedToggle = GLAction.fromSwing("test.swing-toggle", swingToggle);
		adaptedToggle.perform(adaptedToggle);
		check(adaptedToggle.isSelected() && Boolean.TRUE.equals(swingToggle.getValue(
				javax.swing.Action.SELECTED_KEY)),
				"Swing SELECTED_KEY was not synchronized bidirectionally");
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

		GLContainer subtree = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
		GLButton first = new GLButton("First");
		GLCheckBox second = new GLCheckBox("Second");
		subtree.addGL(first);
		subtree.addGL(second);
		panel.addGL(subtree);
		pressTab(panel, InputEvent.CTRL_DOWN_MASK);
		check(first.isFocused(), "Ctrl+Tab did not focus the first control");
		pressTab(panel, InputEvent.CTRL_DOWN_MASK);
		check(second.isFocused(), "Ctrl+Tab did not follow component-tree order");
		pressTab(panel, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
		check(first.isFocused(), "Ctrl+Shift+Tab did not traverse backwards");
		subtree.setVisible(false);
		check(!first.isFocused(), "Hidden subtree retained keyboard focus");
	}

	private static void pressTab(TestPanel panel, int modifiers) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), modifiers, KeyEvent.VK_TAB, '\t');
		for (var listener : panel.getKeyListeners())
			listener.keyPressed(event);
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
