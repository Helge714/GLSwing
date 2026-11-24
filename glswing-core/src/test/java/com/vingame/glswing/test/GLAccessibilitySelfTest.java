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
import com.vingame.glswing.ui.GLDefaultTreeModel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTextArea;
import com.vingame.glswing.ui.GLTree;

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

		GLPopupMenu popup = new GLPopupMenu();
		GLMenuItem menuItem = popup.addItem("Open");
		check(popup.getAccessibleContext().getAccessibleRole()
				== AccessibleRole.POPUP_MENU, "Popup-menu role was not exposed");
		check(menuItem.getAccessibleContext().getAccessibleRole()
				== AccessibleRole.MENU_ITEM, "Menu-item role was not exposed");
		check("Open".equals(menuItem.getAccessibleContext().getAccessibleName()),
				"Menu-item text was not exposed");

		GLMenuBar menuBar = new GLMenuBar();
		var menu = menuBar.addMenu("File", popup);
		check(menuBar.getAccessibleContext().getAccessibleRole()
				== AccessibleRole.MENU_BAR, "Menu-bar role was not exposed");
		check(menu.getAccessibleContext().getAccessibleRole()
				== AccessibleRole.MENU, "Top-level menu role was not exposed");

		GLList<String> list = new GLList<>(java.util.List.of("Alpha", "Beta"));
		list.setBounds(0, 0, 180, 80);
		AccessibleContext listContext = list.getAccessibleContext();
		check(listContext.getAccessibleChildrenCount() == 2,
				"Visible list rows were not exposed");
		AccessibleContext row = listContext.getAccessibleChild(0).getAccessibleContext();
		check(row.getAccessibleRole() == AccessibleRole.LIST_ITEM
				&& "Alpha".equals(row.getAccessibleName()),
				"List-row semantics were not exposed");
		row.getAccessibleAction().doAccessibleAction(0);
		check(list.getSelectedIndex() == 0
				&& row.getAccessibleStateSet().contains(AccessibleState.SELECTED),
				"Accessible list-row selection failed");

		GLTabbedPane tabs = new GLTabbedPane();
		tabs.addTab("First", new Container());
		tabs.addTab("Second", new Container());
		tabs.setBounds(0, 0, 320, 200);
		AccessibleContext tabsContext = tabs.getAccessibleContext();
		AccessibleContext secondTab = null;
		for (int index = 0; index < tabsContext.getAccessibleChildrenCount(); index++) {
			AccessibleContext candidate = tabsContext.getAccessibleChild(index)
					.getAccessibleContext();
			if (candidate.getAccessibleRole() == AccessibleRole.PAGE_TAB
					&& "Second".equals(candidate.getAccessibleName())) secondTab = candidate;
		}
		check(secondTab != null, "Individual tab semantics were not exposed");
		secondTab.getAccessibleAction().doAccessibleAction(0);
		check(tabs.getSelectedIndex() == 1
				&& secondTab.getAccessibleStateSet().contains(AccessibleState.SELECTED),
				"Accessible tab selection failed");

		GLDefaultTreeModel<String> treeModel = new GLDefaultTreeModel<>(
				java.util.List.of("Root"));
		treeModel.addChild("Root", "Leaf");
		GLTree<String> tree = new GLTree<>(treeModel);
		tree.setBounds(0, 0, 200, 120);
		AccessibleContext treeContext = tree.getAccessibleContext();
		check(treeContext.getAccessibleChildrenCount() == 1,
				"Visible tree nodes were not promoted through layout wrappers");
		AccessibleContext branch = treeContext.getAccessibleChild(0)
				.getAccessibleContext();
		check("Root".equals(branch.getAccessibleName())
				&& branch.getAccessibleStateSet().contains(AccessibleState.COLLAPSED),
				"Tree-branch name or collapsed state was not exposed");
		tree.setExpanded("Root", true);
		check(branch.getAccessibleStateSet().contains(AccessibleState.EXPANDED),
				"Expanded tree-branch state was not exposed");

		Container wrapper = new Container();
		wrapper.setAccessibilityTransparent(true);
		wrapper.setLocation(7, 9);
		GLButton nested = new GLButton("Nested");
		nested.setBounds(3, 4, 40, 20);
		wrapper.addGL(nested);
		Container flattenedRoot = new Container();
		flattenedRoot.addGL(wrapper);
		check(flattenedRoot.getAccessibleContext().getAccessibleChild(0) == nested,
				"Decorative wrapper was not flattened");
		check(nested.getAccessibleContext().getAccessibleComponent().getLocation()
				.equals(new java.awt.Point(10, 13)),
				"Flattened child geometry was not relative to its semantic parent");

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
