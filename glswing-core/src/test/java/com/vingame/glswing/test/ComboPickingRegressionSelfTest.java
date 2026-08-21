package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Comparator;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.ui.GLSlider;

/** Reproduces sort -> view transform -> immediate combo click on a GLJPanel. */
public final class ComboPickingRegressionSelfTest {
	private ComboPickingRegressionSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> {
			holder.panel = new TestPanel();
			holder.frame = new JFrame("Combo picking regression");
			holder.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			holder.frame.add(holder.panel);
			holder.frame.setSize(720, 500);
			holder.frame.setLocationRelativeTo(null);
			holder.frame.setVisible(true);
			holder.animator = new FPSAnimator(holder.panel, 144, true);
			holder.animator.start();
		});
		try {
			Thread.sleep(250);
			holder.panel.display();
			Thread.sleep(50);
			holder.panel.display();
			SwingUtilities.invokeAndWait(() -> {
				TestPanel panel = holder.panel;
				panel.combo.sortItems(Comparator.reverseOrder());
				int oldX = Math.round(panel.screenX(panel.combo.getX()
						+ panel.combo.getWidth() * 0.5f));
				int oldY = Math.round(panel.screenY(panel.combo.getY()
						+ panel.combo.getHeight() * 0.5f));
				long when = System.currentTimeMillis();
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
						when, 0, oldX, oldY, 0, false, MouseEvent.NOBUTTON));
				panel.dispatchEvent(new MouseWheelEvent(panel,
						MouseEvent.MOUSE_WHEEL, when + 1, 0, oldX, oldY,
						0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 0));
			});
			Thread.sleep(250);
			holder.panel.display();
			Thread.sleep(50);
			holder.panel.display();
			SwingUtilities.invokeAndWait(() -> {
				TestPanel panel = holder.panel;
				if (panel.combo.getControlState() != GLControlState.HOVERED)
					throw new AssertionError(
							"Combo hover was not restored from the transformed pick frame");
				int x = Math.round(panel.screenX(panel.combo.getX()
						+ panel.combo.getWidth() * 0.5f));
				int y = Math.round(panel.screenY(panel.combo.getY()
						+ panel.combo.getHeight() * 0.5f));
				long when = System.currentTimeMillis();
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
						when + 2, 0, x, y, 1, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
						when + 3, 0, x, y, 1, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED,
						when + 4, 0, x, y, 1, false, MouseEvent.BUTTON1));
				if (!panel.combo.isPopupVisible())
					throw new AssertionError(
							"Combo popup did not open after sorting and zooming");
				panel.combo.hidePopup();
				float sliderHeight = panel.slider.getHeight();
				panel.slider.setBounds(panel.getWidth() * 0.5f - 125,
						panel.getHeight() * 0.5f - sliderHeight * 0.5f,
						250, sliderHeight);
				panel.zoom = 1;
				panel.repaint();
			});
			holder.panel.display();
			Thread.sleep(100);
			holder.panel.display();
			SwingUtilities.invokeAndWait(() -> {
				TestPanel panel = holder.panel;
				int x = Math.round(panel.screenX(panel.slider.getX()
						+ panel.slider.getWidth() * 0.5f));
				int y = Math.round(panel.screenY(panel.slider.getY()
						+ panel.slider.getHeight() * 0.5f));
				long when = System.currentTimeMillis();
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
						when, 0, x, y, 0, false, MouseEvent.NOBUTTON));
				for (int index = 0; index < 3; index++)
					panel.dispatchEvent(new MouseWheelEvent(panel,
							MouseEvent.MOUSE_WHEEL, when + 1 + index, 0, x, y,
							0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
			});
			Thread.sleep(250);
			holder.panel.display();
			Thread.sleep(50);
			holder.panel.display();
			SwingUtilities.invokeAndWait(() -> {
				TestPanel panel = holder.panel;
				int startX = Math.round(panel.screenX(panel.slider.getX()
						+ panel.slider.getWidth() * 0.35f));
				int x = Math.round(panel.screenX(panel.slider.getX()
						+ panel.slider.getWidth() * 0.5f));
				int y = Math.round(panel.screenY(panel.slider.getY()
						+ panel.slider.getHeight() * 0.5f));
				long when = System.currentTimeMillis();
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
						when, 0, startX, y, 0, false, MouseEvent.NOBUTTON));
				if (panel.slider.getControlState() != GLControlState.HOVERED)
					throw new AssertionError(
							"Slider was not hovered at enlarged zoom");
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
						when + 1, 0, startX, y, 1, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED,
						when + 2, MouseEvent.BUTTON1_DOWN_MASK, x, y,
						0, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
						when + 3, 0, x, y, 1, false, MouseEvent.BUTTON1));
				if (panel.slider.getControlState() != GLControlState.HOVERED)
					throw new AssertionError(
							"Slider did not remain hovered after operating it");
				panel.stationaryPointerX = x;
				panel.stationaryPointerY = y;
				for (int index = 0; index < 3; index++)
					panel.dispatchEvent(new MouseWheelEvent(panel,
							MouseEvent.MOUSE_WHEEL, when + 4 + index, 0, x, y,
							0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
			});
			Thread.sleep(250);
			holder.panel.display();
			Thread.sleep(50);
			holder.panel.display();
			SwingUtilities.invokeAndWait(() -> {
				TestPanel panel = holder.panel;
				int oldPointerX = panel.stationaryPointerX;
				int oldPointerY = panel.stationaryPointerY;
				int y = Math.round(panel.screenY(panel.slider.getY()
						+ panel.slider.getHeight() * 0.5f));
				int startX = Math.round(panel.screenX(panel.slider.getX()
						+ panel.slider.getWidth() * 0.5f));
				int endX = Math.round(panel.screenX(panel.slider.getX()
						+ panel.slider.getWidth() * 0.85f));
				long when = System.currentTimeMillis();
				if (panel.slider.getControlState() != GLControlState.HOVERED) {
					var currentPick = panel.pickResult(oldPointerX, oldPointerY);
					throw new AssertionError(
							"Slider lost hover at the invariant zoom center"
									+ " (zoom=" + panel.zoom + ", pointer="
									+ oldPointerX + "," + oldPointerY + ", target="
									+ currentPick.getTarget() + ", local="
									+ currentPick.getLocalX() + ","
									+ currentPick.getLocalY() + ")");
				}
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
						when + 1, 0, oldPointerX, oldPointerY, 1, false,
						MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED,
						when + 2, MouseEvent.BUTTON1_DOWN_MASK, endX, y,
						0, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
						when + 3, 0, endX, y, 1, false, MouseEvent.BUTTON1));
				if (panel.slider.getValue() < 75)
					throw new AssertionError(
							"Slider did not react after hover and zoom-out: "
									+ panel.slider.getValue());
			});
			System.out.println("Combo picking regression self-test passed.");
		} finally {
			if (holder.animator != null && holder.animator.isStarted())
				holder.animator.stop();
			SwingUtilities.invokeAndWait(holder.frame::dispose);
		}
	}

	private static final class Holder {
		private JFrame frame;
		private TestPanel panel;
		private FPSAnimator animator;
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final GLComboBox<String> combo = new GLComboBox<>(List.of(
				"Gamma", "Alpha", "Beta", "Delta"));
		private final GLSlider slider = GLSlider.fixed("Value", 0, 100, 20);
		private float zoom = 1;
		private int stationaryPointerX;
		private int stationaryPointerY;

		private TestPanel() {
			combo.setBounds(120, 95, 180, combo.getPreferredGLSize().height());
			addGL(combo);
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
			context.clear(new Color(25, 28, 36));
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
