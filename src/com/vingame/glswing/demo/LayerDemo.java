package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** Compares per-command transparency with nested group opacity. */
public final class LayerDemo {

	private LayerDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(LayerDemo::showWindow);
	}

	private static void showWindow() {
		JLabel status = new JLabel("Mausrad: Deckkraft verändern", SwingConstants.CENTER);
		LayerPanel panel = new LayerPanel(status);
		panel.setPreferredSize(new Dimension(900, 620));

		JFrame frame = new JFrame("GLSwingNext - verschachtelte Layer");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static final class LayerPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final JLabel status;
		private volatile float opacity = 0.42f;
		private GLFont headingFont;
		private GLFont labelFont;

		LayerPanel(JLabel status) {
			this.status = status;
			addMouseWheelListener(event -> {
				opacity = clamp(opacity - (float) event.getPreciseWheelRotation() * 0.05f,
						0, 1);
				status.setText(String.format("Mausrad: Deckkraft %.0f %%", opacity * 100));
				repaint();
			});
		}

		@Override
		protected void initialize(GL3 gl) {
			headingFont = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 25);
			labelFont = new GLFont(GLFont.SANS_SERIF, GLFont.PLAIN, 18);
		}

		@Override
		protected void render(GLContext2D context) {
			context.clear(new Color(16, 22, 34));
			context.drawText(headingFont, "Alpha pro Linie", 105, 62, Color.WHITE);
			context.drawText(headingFont, "Alpha als Layer", 565, 62, Color.WHITE);
			context.drawText(labelFont,
					"Überlappungen werden mehrfach eingeblendet", 65, 96,
					new Color(170, 185, 207));
			context.drawText(labelFont,
					"Die fertige Gruppe wird einmal eingeblendet", 520, 96,
					new Color(170, 185, 207));

			context.setStroke(new GLStroke(44)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			Color directAlpha = withAlpha(new Color(72, 202, 235), opacity);
			drawAsterisk(context, 225, 235, directAlpha);

			context.beginLayer();
			drawAsterisk(context, 675, 235, new Color(72, 202, 235));
			context.endLayer(opacity);

			context.drawText(headingFont, "Verschachtelung", 345, 390, Color.WHITE);
			context.drawText(labelFont,
					"Inneres Grün: 55 %, gesamte äußere Gruppe: 75 %", 248, 423,
					new Color(170, 185, 207));

			context.beginLayer();
			context.fillRect(270, 452, 360, 115, new Color(234, 137, 60));
			context.beginLayer();
			context.fillRect(390, 432, 120, 155, new Color(76, 220, 145));
			context.endLayer(0.55f);
			context.endLayer(0.75f);
		}

		private static void drawAsterisk(GLContext2D context, float centerX,
				float centerY, Color color) {
			context.drawLine(centerX - 105, centerY, centerX + 105, centerY, color);
			context.drawLine(centerX - 58, centerY - 94,
					centerX + 58, centerY + 94, color);
			context.drawLine(centerX - 58, centerY + 94,
					centerX + 58, centerY - 94, color);
		}

		@Override
		protected void disposeResources(GL3 gl) {
			if (headingFont != null) {
				headingFont.dispose(gl);
				headingFont = null;
			}
			if (labelFont != null) {
				labelFont.dispose(gl);
				labelFont = null;
			}
		}
	}

	private static Color withAlpha(Color color, float alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.round(alpha * 255));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
