package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLScrollBar;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLScrollPaneStyle;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTabbedPaneStyle;
import com.vingame.glswing.ui.GLTheme;

/** CPU checks for scrollbar ranges, scroll content and tab selection. */
public final class GLScrollTabsSelfTest {
	private GLScrollTabsSelfTest() { }

	public static void main(String[] args) {
		testScrollBarModel();
		testScrollPaneContent();
		testTabs();
		testThemeValues();
		System.out.println("GL scroll/tabs self-test passed.");
	}

	private static void testScrollBarModel() {
		GLScrollBar bar = new GLScrollBar(GLScrollBar.Orientation.VERTICAL);
		AtomicInteger changes = new AtomicInteger();
		bar.addScrollListener((source, oldValue, newValue) -> changes.incrementAndGet());
		bar.setValues(80, 25, 100);
		check(bar.getValue() == 75, "Scrollbar value was not clamped to maximum extent");
		bar.setValue(-5);
		check(bar.getValue() == 0, "Scrollbar value was not clamped to zero");
		check(changes.get() == 2, "Scrollbar emitted an unexpected change count");
	}

	private static void testScrollPaneContent() {
		GLContainer content = empty();
		content.setPreferredGLSize(800, 600);
		GLScrollPane pane = new GLScrollPane(content);
		check(pane.getContent() == content,
				"ScrollPane did not retain its content");
		check(content.getGLParent() != null,
				"ScrollPane content was not attached to its viewport");
		pane.setScrollUnit(17);
		check(pane.getScrollUnit() == 17, "Scroll unit was not retained");
		pane.setContent(null);
		check(content.getGLParent() == null,
				"Removed ScrollPane content remained attached");
	}

	private static void testTabs() {
		GLTabbedPane tabs = new GLTabbedPane();
		GLContainer first = empty();
		GLContainer second = empty();
		tabs.addTab("First", first);
		tabs.addTab("Second", second);
		check(tabs.getSelectedIndex() == 0 && first.isVisible() && !second.isVisible(),
				"Initial tab visibility is incorrect");
		AtomicInteger changes = new AtomicInteger();
		tabs.addSelectionListener((source, oldIndex, newIndex) -> changes.incrementAndGet());
		tabs.setSelectedIndex(1);
		check(!first.isVisible() && second.isVisible() && changes.get() == 1,
				"Tab selection did not update content visibility");
		tabs.setEnabledAt(1, false);
		check(tabs.getSelectedIndex() == 0,
				"Disabling the selected tab did not select an enabled replacement");
		tabs.removeTabAt(0);
		check(tabs.getTabCount() == 1 && tabs.getComponentAt(0) == second,
				"Removing a tab corrupted the tab list");
	}

	private static void testThemeValues() {
		check(GLTheme.DEFAULT.values().contains("scrollBar.thickness"),
				"Default theme lacks scrollbar values");
		check(GLTheme.DEFAULT.values().contains("tabbedPane.tabHeight"),
				"Default theme lacks tabbed-pane values");
		GLTheme rounded = GLTheme.DEFAULT.withCornerRadius(11);
		check(GLScrollPaneStyle.fromTheme(rounded).cornerRadius() == 11,
				"Theme-wide radius did not reach ScrollPane");
		check(GLTabbedPaneStyle.fromTheme(rounded).cornerRadius() == 11,
				"Theme-wide radius did not reach TabbedPane");
	}

	private static GLContainer empty() {
		return new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
