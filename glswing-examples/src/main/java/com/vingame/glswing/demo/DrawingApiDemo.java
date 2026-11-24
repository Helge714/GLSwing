package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;

/** Compact showcase for filled, stroked and per-vertex-colored shapes. */
public final class DrawingApiDemo {

	private DrawingApiDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(DrawingApiDemo::showWindow);
	}

	private static void showWindow() {
		DrawingPanel panel = new DrawingPanel();
		panel.setPreferredSize(new Dimension(1000, 600));

		JFrame frame = new JFrame("GLSwing - Drawing API");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(new JLabel("Filled and stroked shapes share one batched draw call", SwingConstants.CENTER),
				BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static final class DrawingPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
			context.clear();
			context.fillRect(50, 60, 220, 140, new Color(55, 125, 220));
			context.setStrokeWidth(7);
			context.setStrokeJoin(GLStroke.Join.ROUND);
			context.drawRect(50, 60, 220, 140, new Color(185, 220, 255));

			context.fillRect(690, 350, 240, 170, new Color(235, 145, 45));
			context.drawLine(720, 390, new Color(255, 245, 180, 220),
					900, 480, new Color(245, 70, 110, 90));
			context.fillTriangle(400, 60, 550, 220, 300, 240, new Color(50, 190, 115));
			context.fillPolygon(new float[] {
					70, 330,
					280, 300,
					230, 390,
					320, 490,
					140, 520,
					100, 430
			}, new Color(175, 85, 210));
			context.drawPolygon(new float[] {
					70, 330,
					280, 300,
					230, 390,
					320, 490,
					140, 520,
					100, 430
			}, new Color(225, 190, 245));

			context.fillPolygon(new float[] {
					650, 60,
					920, 270,
					650, 270,
					920, 60
			}, new Color[] {
					new Color(245, 70, 80),
					new Color(250, 210, 55),
					new Color(55, 205, 120),
					new Color(80, 120, 245)
			});
			context.setStrokeWidth(5);
			context.drawPolygon(new float[] {
					650, 60,
					920, 270,
					650, 270,
					920, 60
			}, new Color[] {
					new Color(255, 120, 130, 210),
					new Color(255, 225, 100, 170),
					new Color(100, 235, 160, 130),
					new Color(120, 160, 255, 90)
			});
		}
	}
}
