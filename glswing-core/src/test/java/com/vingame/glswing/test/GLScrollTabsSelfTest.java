package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.input.GLMouseWheelEvent;
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
		testScrollPaneLayoutAndOffsets();
		testScrollPaneWheelAxes();
		testScrollPanePolicies();
		testTabs();
		testThemeValues();
		System.out.println("GL scroll/tabs self-test passed.");
	}

	private static void testScrollPaneLayoutAndOffsets() {
		GLContainer content = empty();
		content.setPreferredGLSize(800, 600);
		GLScrollPane pane = new GLScrollPane(content);
		pane.setBounds(0, 0, 300, 200);
		check(pane.getVerticalScrollBar().isVisible(),
				"Tall content did not show the vertical scrollbar");
		check(pane.getHorizontalScrollBar().isVisible(),
				"Wide content did not show the horizontal scrollbar");
		check(pane.getVerticalScrollBar().getMaximumValue() > 300,
				"Vertical scroll range was not derived from content size");
		check(pane.getHorizontalScrollBar().getMaximumValue() > 400,
				"Horizontal scroll range was not derived from content size");

		pane.setScrollOffset(120, 90);
		check(content.getX() == -120 && content.getY() == -90,
				"Content position did not follow scroll offsets");
		pane.scrollRectToVisible(700, 500, 40, 30);
		check(pane.getHorizontalScrollOffset() > 400
				&& pane.getVerticalScrollOffset() > 300,
				"scrollRectToVisible did not reveal a distant rectangle");
		pane.setScrollOffset(Float.MAX_VALUE, Float.MAX_VALUE);
		check(pane.getHorizontalScrollOffset()
				== pane.getHorizontalScrollBar().getMaximumValue(),
				"Horizontal offset was not clamped");
		check(pane.getVerticalScrollOffset()
				== pane.getVerticalScrollBar().getMaximumValue(),
				"Vertical offset was not clamped");
	}

	private static void testScrollPaneWheelAxes() {
		GLContainer content = empty();
		content.setPreferredGLSize(800, 600);
		GLScrollPane pane = new GLScrollPane(content);
		pane.setBounds(0, 0, 300, 200);
		pane.setScrollUnit(10);
		GLMouseWheelEvent vertical = wheel(0, 1);
		pane.mouseWheelMoved(vertical);
		check(pane.getVerticalScrollOffset() == 30 && vertical.isConsumed(),
				"Unit wheel input did not scroll and consume vertically");
		GLMouseWheelEvent horizontal = wheel(InputEvent.SHIFT_DOWN_MASK, 1);
		pane.mouseWheelMoved(horizontal);
		check(pane.getHorizontalScrollOffset() == 30 && horizontal.isConsumed(),
				"Configured horizontal modifier did not select the horizontal axis");
	}

	private static void testScrollPanePolicies() {
		GLContainer content = empty();
		content.setPreferredGLSize(20, 20);
		GLScrollPane pane = new GLScrollPane(content);
		pane.setBounds(0, 0, 300, 200);
		check(!pane.getVerticalScrollBar().isVisible()
				&& !pane.getHorizontalScrollBar().isVisible(),
				"AS_NEEDED showed bars for fitting content");
		pane.setVerticalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.ALWAYS);
		check(pane.getVerticalScrollBar().isVisible(),
				"ALWAYS did not show the vertical bar");
		pane.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		content.setPreferredGLSize(900, 20);
		pane.setBounds(0, 0, 301, 200);
		check(!pane.getHorizontalScrollBar().isVisible(),
				"NEVER allowed the horizontal bar to appear");
	}

	private static GLMouseWheelEvent wheel(int modifiers, int rotation) {
		MouseWheelEvent event = new MouseWheelEvent(new Canvas(),
				MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers,
				0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
		return new GLMouseWheelEvent(event, GLPickResult.none());
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
		check(!tabs.areTabsClosable(), "Tabs were closable by default");
		tabs.setTabsClosable(true);
		check(tabs.areTabsClosable(), "Closable-tab option was not retained");
		GLContainer first = empty();
		GLContainer second = empty();
		tabs.addTab("First", first);
		tabs.addTab("Second", second);
		check(first.getConnectedShape() != null && second.getConnectedShape() != null,
				"Generic tab content did not inherit the rounded content clip");
		check(tabs.indexOfComponent(second) == 1,
				"Tabbed pane did not locate a content component");
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
		tabs.setClosedTabHistoryEnabled(true);
		tabs.removeTabAt(0);
		check(first.getConnectedShape() == null,
				"Removed tab content retained its tab-owned clip");
		check(tabs.getTabCount() == 1 && tabs.getComponentAt(0) == second,
				"Removing a tab corrupted the tab list");
		check(tabs.reopenLastClosedTab() && tabs.getTabCount() == 2
				&& tabs.getComponentAt(0) == first && tabs.getSelectedIndex() == 0,
				"Recently closed tab was not restored at its previous position");
		check(first.getConnectedShape() != null,
				"Reopened tab content did not recover its rounded content clip");
		check(!tabs.reopenLastClosedTab(),
				"Closed-tab history retained an already restored tab");
	}

	private static void testThemeValues() {
		check(GLTheme.DEFAULT.values().contains("scrollBar.thickness"),
				"Default theme lacks scrollbar values");
		check(GLTheme.DEFAULT.values().contains("scrollPane.viewportBorder")
				&& GLScrollPaneStyle.fromTheme(GLTheme.DEFAULT)
						.viewportBorderWidth() > 0,
				"Default ScrollPane lacks its viewport border");
		check(GLTheme.DEFAULT.values().contains("tabbedPane.tabHeight"),
				"Default theme lacks tabbed-pane values");
		check(GLTabbedPaneStyle.fromTheme(GLTheme.DEFAULT).contentPadding().horizontal() == 0
				&& GLTabbedPaneStyle.fromTheme(GLTheme.DEFAULT).contentPadding().vertical() == 0,
				"Tabbed content does not fill its content area by default");
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
