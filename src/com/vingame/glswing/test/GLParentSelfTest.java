package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;

/** Checks the shared panel/container hierarchy without creating a native window. */
public final class GLParentSelfTest {

	private GLParentSelfTest() {
	}

	public static void main(String[] args) {
		TestPanel panel = new TestPanel();
		TestContainer parent = new TestContainer();
		TestContainer child = new TestContainer();
		AtomicInteger attachments = child.attachments;
		AtomicInteger detachments = child.detachments;

		panel.addGL(child);
		if (child.getGLParent() != panel || panel.getGLChildren().get(0) != child)
			throw new AssertionError("The panel is not the child's actual GL parent");

		parent.addGL(child);
		if (child.getGLParent() != parent || !panel.getGLChildren().isEmpty())
			throw new AssertionError("Moving a child did not update both GL parents");

		panel.addGL(parent);
		try {
			child.addGL(parent);
			throw new AssertionError("A hierarchy cycle was accepted");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}

		panel.removeAllGL();
		if (parent.getGLParent() != null || !panel.getGLChildren().isEmpty())
			throw new AssertionError("removeAllGL did not detach every direct child");
		if (attachments.get() != 2 || detachments.get() != 1)
			throw new AssertionError("Unexpected attachment lifecycle counts");

		testZOrder();

		System.out.println("GL parent self-test passed.");
	}

	private static void testZOrder() {
		TestPanel panel = new TestPanel();
		TestContainer first = new TestContainer();
		TestContainer second = new TestContainer();
		TestContainer third = new TestContainer();
		panel.addGL(first);
		panel.addGL(second);
		panel.addGL(third);

		first.bringToFront();
		assertOrder(panel.getGLChildrenInPaintOrder(), second, third, first);
		first.moveBackward();
		assertOrder(panel.getGLChildrenInPaintOrder(), second, first, third);
		third.sendToBack();
		assertOrder(panel.getGLChildrenInPaintOrder(), third, second, first);
		second.setZOrder(2);
		assertOrder(panel.getGLChildrenInPaintOrder(), third, first, second);
		assertOrder(panel.getGLChildren(), first, second, third);
		if (second.getZOrder() != 2)
			throw new AssertionError("Container reported the wrong Z-order");

		for (int repetition = 0; repetition < 10_000; repetition++)
			second.bringToFront();
		if (second.getZOrder() != panel.getGLChildren().size() - 1)
			throw new AssertionError("Repeated bringToFront grew beyond the child list");
	}

	private static void assertOrder(java.util.List<GLContainer> actual,
			GLContainer... expected) {
		if (actual.size() != expected.length)
			throw new AssertionError("Unexpected hierarchy size");
		for (int index = 0; index < expected.length; index++)
			if (actual.get(index) != expected[index])
				throw new AssertionError("Unexpected GL paint order at index " + index);
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
		}
	}

	private static final class TestContainer extends GLContainer {
		private final AtomicInteger attachments = new AtomicInteger();
		private final AtomicInteger detachments = new AtomicInteger();

		@Override
		protected void record(GLContext2D context) {
		}

		@Override
		protected void attached(GLParent parent) {
			attachments.incrementAndGet();
		}

		@Override
		protected void detached(GLParent formerParent) {
			detachments.incrementAndGet();
		}
	}
}
