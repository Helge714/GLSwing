package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.ui.GLSlider;

/** Real AWT input regression for zoom-in -> slider drag -> zoom-out -> drag. */
public final class SliderZoomRobotRegressionSelfTest {

	private SliderZoomRobotRegressionSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> {
			holder.panel = new TestPanel();
			holder.panel.setPreferredSize(new Dimension(720, 500));
			holder.frame = new JFrame("Slider zoom robot regression");
			holder.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			holder.frame.add(holder.panel);
			holder.frame.pack();
			holder.frame.setLocationRelativeTo(null);
			holder.frame.setAlwaysOnTop(true);
			holder.frame.setVisible(true);
			holder.frame.toFront();
			holder.panel.requestFocusInWindow();
			holder.animator = new FPSAnimator(holder.panel, 144, true);
			holder.animator.start();
		});

		try {
			Robot robot = new Robot();
			robot.setAutoDelay(45);
			Thread.sleep(400);

			Point initialCenter = point(holder.panel, 0.5f);
			robot.mouseMove(initialCenter.x - 40, initialCenter.y - 40);
			robot.mouseMove(initialCenter.x, initialCenter.y);
			Thread.sleep(150);
			assertHovered(holder.panel, initialCenter, "initial hover");

			robot.mouseWheel(-3);
			Thread.sleep(400);
			Point dragStart = point(holder.panel, 0.35f);
			Point enlargedCenter = point(holder.panel, 0.5f);
			robot.mouseMove(dragStart.x, dragStart.y);
			robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			robot.mouseMove(enlargedCenter.x, enlargedCenter.y);
			robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			Thread.sleep(300);
			assertHovered(holder.panel, enlargedCenter,
					"hover after enlarged slider drag");

			// Keep the physical pointer where the first drag ended. The slider remains
			// underneath this point when the centered view returns to 1x.
			robot.mouseWheel(3);
			Thread.sleep(500);
			assertHovered(holder.panel, enlargedCenter,
					"hover after zooming out with a stationary pointer");

			Point dragEnd = point(holder.panel, 0.85f);
			robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			robot.mouseMove(dragEnd.x, dragEnd.y);
			robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			Thread.sleep(300);
			if (holder.panel.slider.getValue() < 75)
				throw new AssertionError("Slider stayed unusable after zoom cycle: value="
						+ holder.panel.slider.getValue());

			System.out.println("Slider zoom robot regression self-test passed.");
		} finally {
			if (holder.animator != null && holder.animator.isStarted())
				holder.animator.stop();
			SwingUtilities.invokeAndWait(holder.frame::dispose);
		}
	}

	private static Point point(TestPanel panel, float progress) throws Exception {
		Point[] result = new Point[1];
		SwingUtilities.invokeAndWait(() -> {
			Point origin = panel.getLocationOnScreen();
			float logicalX = panel.slider.getX() + panel.slider.getWidth() * progress;
			float logicalY = panel.slider.getY() + panel.slider.getHeight() * 0.5f;
			result[0] = new Point(origin.x + Math.round(panel.screenX(logicalX)),
					origin.y + Math.round(panel.screenY(logicalY)));
		});
		return result[0];
	}

	private static void assertHovered(TestPanel panel, Point screen,
			String phase) throws Exception {
		String[] failure = new String[1];
		SwingUtilities.invokeAndWait(() -> {
			Point origin = panel.getLocationOnScreen();
			int x = screen.x - origin.x;
			int y = screen.y - origin.y;
			if (panel.slider.getControlState() != GLControlState.HOVERED)
				failure[0] = phase + ": " + diagnostic(panel, x, y);
		});
		if (failure[0] != null)
			throw new AssertionError(failure[0]);
	}

	private static String diagnostic(TestPanel panel, int x, int y) {
		GLPickResult pick = panel.pickResult(x, y);
		int centerX = Math.round(panel.screenX(
				panel.slider.getX() + panel.slider.getWidth() * 0.5f));
		int centerY = Math.round(panel.screenY(
				panel.slider.getY() + panel.slider.getHeight() * 0.5f));
		GLPickResult centerPick = panel.pickResult(centerX, centerY);
		return "zoom=" + panel.zoom + ", control="
				+ panel.slider.getControlState() + ", pointer=" + x + "," + y
				+ ", target=" + pick.getTarget() + ", local="
				+ pick.getLocalX() + "," + pick.getLocalY()
				+ ", sliderBounds=" + panel.slider.getX() + ","
				+ panel.slider.getY() + "," + panel.slider.getWidth() + ","
				+ panel.slider.getHeight() + ", editing="
				+ panel.slider.isValueEditing() + ", value="
				+ panel.slider.getValue() + ", center=" + centerX + ","
				+ centerY + ", centerTarget=" + centerPick.getTarget()
				+ ", panel=" + panel.getWidth() + "x" + panel.getHeight();
	}

	private static final class Holder {
		private JFrame frame;
		private TestPanel panel;
		private FPSAnimator animator;
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final GLSlider slider = GLSlider.fixed("Value", 0, 100, 20);
		private volatile float zoom = 1;

		private TestPanel() {
			slider.setBounds(290, 245, 250, slider.getPreferredGLSize().height());
			addGL(slider);
			addMouseWheelListener(event -> {
				double factor = Math.pow(1.15, -event.getPreciseWheelRotation());
				zoom = Math.max(0.6f, Math.min(1.8f, (float) (zoom * factor)));
				repaint();
			});
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(24, 29, 40));
		}

		@Override
		protected void render(GLContext2D context) {
			super.render(context);
			float centerX = getWidth() * 0.5f;
			float centerY = getHeight() * 0.5f;
			context.translate(centerX, centerY);
			context.scale(zoom);
			context.translate(-centerX, -centerY);
		}

		private float screenX(float logicalX) {
			float center = getWidth() * 0.5f;
			return center + (logicalX - center) * zoom;
		}

		private float screenY(float logicalY) {
			float center = getHeight() * 0.5f;
			return center + (logicalY - center) * zoom;
		}
	}
}
