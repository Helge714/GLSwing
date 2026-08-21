package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLPopupMenuListener;
import com.vingame.glswing.ui.GLSlider;

/** Repeated native right-click regression for popup teardown and recreation. */
public final class PopupRapidRightClickRobotSelfTest {

	private PopupRapidRightClickRobotSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> {
			holder.panel = new TestPanel();
			holder.panel.setPreferredSize(new Dimension(720, 420));
			holder.frame = new JFrame("Rapid popup right-click regression");
			holder.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			holder.frame.add(holder.panel);
			holder.frame.pack();
			holder.frame.setLocationRelativeTo(null);
			holder.frame.setAlwaysOnTop(true);
			holder.frame.setVisible(true);
			holder.animator = new FPSAnimator(holder.panel, 144, true);
			holder.animator.start();
		});

		try {
			Robot robot = new Robot();
			robot.setAutoDelay(18);
			Thread.sleep(400);
			Point openTarget = target(holder.panel, 0.62f);
			Point closeTarget = target(holder.panel, 0.08f);
			for (int index = 0; index < 15; index++) {
				robot.mouseMove(openTarget.x, openTarget.y);
				robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
				robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
				robot.mouseMove(closeTarget.x, closeTarget.y);
				robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
				robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
			}
			Thread.sleep(500);
			SwingUtilities.invokeAndWait(() -> {
				if (!holder.frame.isDisplayable())
					throw new AssertionError("Frame was disposed during rapid popup input");
				if (holder.panel.opened.get() == 0 || holder.panel.closed.get() == 0)
					throw new AssertionError("Popup did not complete open/close cycles: "
							+ holder.panel.opened.get() + " opened, "
							+ holder.panel.closed.get() + " closed");
			});
			System.out.println("Rapid popup right-click regression self-test passed.");
		} finally {
			if (holder.animator != null && holder.animator.isStarted())
				holder.animator.stop();
			SwingUtilities.invokeAndWait(holder.frame::dispose);
		}
	}

	private static Point target(TestPanel panel, float horizontal) throws Exception {
		Point[] result = new Point[1];
		SwingUtilities.invokeAndWait(() -> {
			Point origin = panel.getLocationOnScreen();
			result[0] = new Point(origin.x + Math.round(panel.slider.getX()
					+ panel.slider.getWidth() * horizontal),
					origin.y + Math.round(panel.slider.getY()
							+ panel.slider.getHeight() * 0.5f));
		});
		return result[0];
	}

	private static final class Holder {
		private JFrame frame;
		private TestPanel panel;
		private FPSAnimator animator;
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final GLSlider slider = GLSlider.fixed("Value", 0, 100, 40);
		private final AtomicInteger opened = new AtomicInteger();
		private final AtomicInteger closed = new AtomicInteger();

		private TestPanel() {
			slider.setBounds(235, 195, 250, slider.getPreferredGLSize().height());
			GLPopupMenu popup = slider.getContextMenu();
			popup.addPopupMenuListener(new GLPopupMenuListener() {
				@Override
				public void popupMenuOpened(GLPopupMenu menu) {
					opened.incrementAndGet();
				}

				@Override
				public void popupMenuClosed(GLPopupMenu menu) {
					closed.incrementAndGet();
				}
			});
			addGL(slider);
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(24, 29, 40));
		}
	}
}
