package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.rendering.GLContext2D;

/** AWT regression test for the owned JDialog plus direct GLCanvas host. */
public final class GLCanvasDialogSelfTest {
	private GLCanvasDialogSelfTest() {
	}

	public static void main(String[] args) throws Exception {
        boolean opaqueThemed = java.util.Arrays.asList(args).contains("--opaque-themed");
        if (opaqueThemed) System.setProperty("glswing.dialog.translucent", "false");
		TestCanvas[] canvas = new TestCanvas[1];
		JFrame[] frame = new JFrame[1];
		FPSAnimator[] animator = new FPSAnimator[1];
		SwingUtilities.invokeAndWait(() -> {
			canvas[0] = new TestCanvas();
			canvas[0].setPreferredSize(new Dimension(480, 300));
			frame[0] = new JFrame("GLDialog canvas owner test");
			frame[0].add(canvas[0]);
			frame[0].pack();
			frame[0].setVisible(true);
			animator[0] = new FPSAnimator(canvas[0], 144, true);
			animator[0].start();
		});
		try {
			GLContainer rootContent = content();
			GLDialog dialog = new GLDialog("Owned canvas dialog", rootContent);
			if (opaqueThemed) dialog.setDecoration(GLDialog.Decoration.THEMED);
			dialog.setSize(300, 180);
			dialog.setModality(GLDialog.Modality.MODELESS);
			dialog.show(canvas[0]);
			await(dialog::isShowing, "Canvas dialog did not become visible");
			Window[] owned = frame[0].getOwnedWindows();
			if (owned.length != 1 || !(owned[0] instanceof JDialog))
				throw new AssertionError("Canvas dialog is not an owned JDialog");
			if (!containsDirectCanvas((JDialog) owned[0]))
				throw new AssertionError("Owned JDialog does not contain a direct GLCanvas");

			GLDialog nested = new GLDialog("Nested canvas dialog", content());
			if (opaqueThemed) nested.setDecoration(GLDialog.Decoration.THEMED);
			nested.setSize(260, 150);
			nested.setModality(GLDialog.Modality.MODELESS);
			nested.show(rootContent);
			await(nested::isShowing, "Nested Canvas dialog did not become visible");
			Window[] nestedWindows = owned[0].getOwnedWindows();
			if (nestedWindows.length != 1
					|| !(nestedWindows[0] instanceof JDialog nestedDialog)
					|| !containsDirectCanvas(nestedDialog))
				throw new AssertionError(
						"Nested Canvas dialog is not owned by the first dialog");
			nested.close();
			nested.closedFuture().get(20, TimeUnit.SECONDS);
			dialog.close();
			dialog.closedFuture().get(20, TimeUnit.SECONDS);
			System.out.println("GL Canvas dialog self-test passed.");
		} finally {
			Thread stop = new Thread(animator[0]::stop, "canvas-dialog-test-stop");
			stop.start();
			stop.join(20_000);
			SwingUtilities.invokeAndWait(frame[0]::dispose);
		}
	}

	private static GLContainer content() {
		return new GLContainer() {
			@Override
			protected void record(GLContext2D context) {
				context.fillRect(0, 0, getWidth(), getHeight(),
						new Color(40, 45, 55));
			}
		};
	}

	private static boolean containsDirectCanvas(JDialog dialog) {
		return findCanvas(dialog.getContentPane());
	}

	private static boolean findCanvas(java.awt.Container container) {
		for (java.awt.Component child : container.getComponents()) {
			if (child instanceof GLSwingContainerCanvas)
				return true;
			if (child instanceof java.awt.Container nested && findCanvas(nested))
				return true;
		}
		return false;
	}

	private static void await(Check condition, String failure)
			throws InterruptedException {
		long deadline = System.nanoTime() + 5_000_000_000L;
		while (!condition.value() && System.nanoTime() < deadline)
			Thread.sleep(10);
		if (!condition.value())
			throw new AssertionError(failure);
	}

	@FunctionalInterface
	private interface Check {
		boolean value();
	}

	private static final class TestCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(20, 24, 32));
		}
	}
}
