package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLSlider;

/** Showcase for the standard button, checkbox and both slider modes. */
public final class SingleButtonDemo {

	private SingleButtonDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(SingleButtonDemo::showWindow);
	}

	private static void showWindow() {
		JLabel status = new JLabel("", SwingConstants.CENTER);
		SingleButtonPanel panel = new SingleButtonPanel(status);
		JFrame frame = new JFrame("GLSwingNext - Standardbutton");
		frame.setLayout(new BorderLayout());
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setSize(new Dimension(440, 260));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static final class SingleButtonPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private static final float MIN_ZOOM = 0.25f;
		private static final float MAX_ZOOM = 8;
		private final GLButton button = new GLButton("Klick mich");
		private final GLButton button2 = new GLButton("Disabled");
		private final GLCheckBox checkBox = new GLCheckBox("Option aktiviert", true);
		private final GLSlider fixedSlider = GLSlider.fixed("Lautstärke", 0, 10, 5);
		private final GLSlider freeSlider = GLSlider.free("Offset", 0);
		private final JLabel status;
		private volatile float zoom = 1;
		private int clickCount;

		private SingleButtonPanel(JLabel status) {
			this.status = status;
			setGLLayout(new GLGridLayout());
			button.addActionListener(event ->
					button.setText("Geklickt: " + ++clickCount));
			addGL(button, GLConstraints.auto()
					.weight(1, 1).alignCenter());
			button2.setEnabled(false);
			addGL(button2, GLConstraints.auto()
					.weight(1, 1).alignCenter());
			addGL(checkBox, GLConstraints.auto()
					.weight(1, 1).alignCenter());
			fixedSlider.setStep(1);
			addGL(fixedSlider, GLConstraints.auto()
					.weight(1, 1).alignCenter());
			freeSlider.setStep(1);
			freeSlider.setDragSensitivity(0.1);
			freeSlider.setPreciseDragFactor(0.1);
			freeSlider.setCoarseDragFactor(10);
			addGL(freeSlider, GLConstraints.auto()
					.weight(1, 1).alignCenter());

			MouseAdapter zoomControls = new MouseAdapter() {
				@Override
				public void mouseWheelMoved(MouseWheelEvent event) {
					double factor = Math.pow(1.2, -event.getPreciseWheelRotation());
					zoom = clamp((float) (zoom * factor), MIN_ZOOM, MAX_ZOOM);
					updateStatus();
					repaint();
				}

			};
			addMouseWheelListener(zoomControls);
			addMouseListener(zoomControls);
			updateStatus();
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(30, 30, 30));
		}

		@Override
		protected void render(GLContext2D context) {
			super.render(context);
			float currentZoom = zoom;
			float centerX = getWidth() * 0.5f;
			float centerY = getHeight() * 0.5f;
			context.translate(centerX, centerY);
			context.scale(currentZoom);
			context.translate(-centerX, -centerY);
		}

		private void updateStatus() {
			status.setText(String.format(
					"Zoom: %.2fx | Free-Slider: Shift fein, Strg grob | Slider-Rechtsklick: Reset",
					zoom));
		}

		private static float clamp(float value, float minimum, float maximum) {
			return Math.max(minimum, Math.min(maximum, value));
		}
	}

}
