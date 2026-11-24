package com.vingame.glswing.test;

import java.awt.Dimension;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/** CPU checks for measuring, packing, arranging and layout invalidation. */
public final class GLLayoutSelfTest {

	private static final float EPSILON = 0.001f;

	private GLLayoutSelfTest() {
	}

	public static void main(String[] args) {
		testExplicitWeightedGrid();
		testAutomaticGridAndVisibility();
		testNestedMeasurementAndDirtyPropagation();
		testOverlayExcludedFromLayout();
		testPanelPack();
		System.out.println("GL layout self-test passed.");
	}

	private static void testOverlayExcludedFromLayout() {
		Box root = new Box();
		root.setGLLayout(new GLGridLayout());
		Box content = box(120, 80);
		Box overlay = box(500, 400);
		overlay.setBounds(0, 0, 500, 400);
		root.addGL(content, GLConstraints.auto().weight(1, 1).fillBoth());
		root.addGL(overlay, GLConstraints.overlay());
		assertSize(root.getPreferredGLSize(), 120, 80,
				"overlay excluded from measurement");
		root.setSize(300, 200);
		root.layoutGL();
		assertBounds(content, 0, 0, 300, 200,
				"managed content retained complete layout area");
		assertBounds(overlay, 0, 0, 500, 400,
				"overlay retained explicit bounds");
	}

	private static void testExplicitWeightedGrid() {
		Box root = new Box();
		Box flexible = box(50, 20);
		Box fixed = box(30, 20);
		root.setGLLayout(new GLGridLayout().withGap(10));
		root.setGLPadding(10);
		root.addGL(flexible, GLConstraints.at(0, 0)
				.weight(1, 1).fillBoth());
		root.addGL(fixed, GLConstraints.at(1, 0));

		assertSize(root.getPreferredGLSize(), 110, 40, "measured explicit grid");
		root.packGL();
		assertBounds(root, 0, 0, 110, 40, "packed root");

		root.setSize(210, 60);
		root.layoutGL();
		assertBounds(flexible, 10, 10, 150, 40, "weighted filled child");
		assertBounds(fixed, 170, 20, 30, 20, "centered fixed child");
	}

	private static void testAutomaticGridAndVisibility() {
		Box root = new Box();
		Box first = box(10, 10);
		Box second = box(10, 10);
		Box third = box(10, 10);
		root.setGLLayout(new GLGridLayout(2).withGap(2));
		root.addGL(first);
		root.addGL(second);
		root.addGL(third);
		assertSize(root.getPreferredGLSize(), 22, 22, "automatic two-column grid");
		root.packGL();
		assertBounds(first, 0, 0, 10, 10, "first automatic child");
		assertBounds(second, 12, 0, 10, 10, "second automatic child");
		assertBounds(third, 0, 12, 10, 10, "wrapped automatic child");

		first.setVisible(false);
		if (!root.isGLLayoutDirty())
			throw new AssertionError("Visibility did not invalidate the parent layout");
		root.packGL();
		assertSize(root.getPreferredGLSize(), 22, 10, "hidden child excluded from measure");
		assertBounds(second, 0, 0, 10, 10, "automatic reflow after hiding child");
	}

	private static void testNestedMeasurementAndDirtyPropagation() {
		Box outer = new Box();
		Box inner = new Box();
		Box leaf = box(40, 15);
		outer.setGLLayout(new GLGridLayout());
		inner.setGLLayout(new GLGridLayout());
		inner.setGLPadding(5);
		inner.addGL(leaf, GLConstraints.auto().fillBoth());
		outer.addGL(inner, GLConstraints.auto().fillBoth());
		assertSize(outer.getPreferredGLSize(), 50, 25, "nested preferred measurement");

		outer.layoutGL();
		leaf.setPreferredGLSize(70, 25);
		if (!inner.isGLLayoutDirty() || !outer.isGLLayoutDirty())
			throw new AssertionError("Preferred size did not propagate layout dirtiness");
		assertSize(outer.getPreferredGLSize(), 80, 35, "updated nested measurement");
	}

	private static void testPanelPack() {
		TestPanel panel = new TestPanel();
		panel.setGLLayout(new GLGridLayout(2).withGap(4));
		panel.setGLPadding(6);
		panel.addGL(box(30, 20));
		panel.addGL(box(40, 25));
		panel.packGL();
		Dimension preferred = panel.getPreferredSize();
		if (preferred.width != 86 || preferred.height != 37)
			throw new AssertionError("Panel pack expected 86x37, got "
					+ preferred.width + "x" + preferred.height);
	}

	private static Box box(float width, float height) {
		Box box = new Box();
		box.setPreferredGLSize(width, height);
		return box;
	}

	private static void assertSize(GLSize actual, float width, float height,
			String description) {
		if (Math.abs(actual.width() - width) > EPSILON
				|| Math.abs(actual.height() - height) > EPSILON)
			throw new AssertionError(description + ": expected " + width + "x" + height
					+ ", got " + actual.width() + "x" + actual.height());
	}

	private static void assertBounds(GLContainer actual,
			float x, float y, float width, float height, String description) {
		if (Math.abs(actual.getX() - x) > EPSILON
				|| Math.abs(actual.getY() - y) > EPSILON
				|| Math.abs(actual.getWidth() - width) > EPSILON
				|| Math.abs(actual.getHeight() - height) > EPSILON)
			throw new AssertionError(description + ": expected "
					+ x + "/" + y + " " + width + "x" + height + ", got "
					+ actual.getX() + "/" + actual.getY() + " "
					+ actual.getWidth() + "x" + actual.getHeight());
	}

	private static class Box extends GLContainer {
		@Override
		protected void record(GLContext2D context) {
		}
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
		}
	}
}
