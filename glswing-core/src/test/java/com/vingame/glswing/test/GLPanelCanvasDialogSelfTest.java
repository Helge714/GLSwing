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
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;

/** Confirms that a GLJPanel owner deliberately receives a direct Canvas dialog. */
public final class GLPanelCanvasDialogSelfTest {
	private GLPanelCanvasDialogSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		TestPanel[] panel = new TestPanel[1];
		JFrame[] frame = new JFrame[1];
		FPSAnimator[] animator = new FPSAnimator[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new TestPanel();
			panel[0].setPreferredSize(new Dimension(480, 300));
			frame[0] = new JFrame("GLPanel to Canvas dialog test");
			frame[0].add(panel[0]);
			frame[0].pack();
			frame[0].setVisible(true);
			animator[0] = new FPSAnimator(panel[0], 60, true);
			animator[0].start();
		});
		try {
			GLDialog dialog = new GLDialog("Canvas dialog from GLPanel", content());
			dialog.setSize(300, 180);
			dialog.setModality(GLDialog.Modality.MODELESS);
			dialog.show(panel[0]);
			await(dialog::isShowing, "GLPanel-owned dialog did not become visible");
			Window[] owned = frame[0].getOwnedWindows();
			if (owned.length != 1 || !(owned[0] instanceof JDialog awtDialog)
					|| !findCanvas(awtDialog.getContentPane()))
				throw new AssertionError(
						"GLPanel dialog is not an owned direct-Canvas JDialog");
			dialog.close();
			dialog.closedFuture().get(5, TimeUnit.SECONDS);
			System.out.println("GLPanel to Canvas dialog self-test passed.");
		} finally {
			Thread stop = new Thread(animator[0]::stop, "panel-dialog-test-stop");
			stop.start();
			stop.join(5_000);
			SwingUtilities.invokeAndWait(frame[0]::dispose);
		}
	}

	private static GLContainer content() {
		return new GLContainer() {
			{
				GLLabel label = new GLLabel("Dialog label");
				label.setBounds(20, 20, 180, 24);
				addGL(label);
				GLButton button = new GLButton("Dialog button");
				button.setBounds(20, 60, 140, 26);
				addGL(button);
			}

			@Override
			protected void record(GLContext2D context) {
				context.fillRect(0, 0, getWidth(), getHeight(),
						new Color(40, 45, 55));
			}
		};
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

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
			context.clear(new Color(20, 24, 32));
		}
	}
}
