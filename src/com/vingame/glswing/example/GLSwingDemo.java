package com.vingame.glswing.example;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.context.GLContext;
import com.vingame.glswing.rendering.context.GLStroke;
import com.vingame.glswing.rendering.context.GLStroke.LineJoin;

/** A small interactive showcase for rendering, transforms and GPU picking. */
public final class GLSwingDemo {

	private GLSwingDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(GLSwingDemo::showWindow);
	}

	private static void showWindow() {
		JFrame frame = new JFrame("GLSwing demo");
		DemoPanel panel = new DemoPanel();
		JLabel status = new JLabel("Move the mouse over a shape", SwingConstants.CENTER);

		panel.setPreferredSize(new Dimension(900, 600));
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		new Timer(50, _ -> status.setText(description(panel.lastID))).start();
	}

	private static String description(int id) {
		return switch (id) {
		case 1 -> "ID 1: blue rectangle";
		case 2 -> "ID 2: orange polygon";
		case 3 -> "ID 3: green outline";
		default -> "No selectable shape";
		};
	}

	private static final class DemoPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void setupDisplay(GLContext gl) {
			gl.ortho(getWidth(), getHeight());
			gl.enableIdRendering();

			gl.fillRect(70, 70, 260, 160, hoverColor(1, new Color(55, 125, 220)), 1);

			float[] polygon = { 470, 80, 700, 125, 760, 285, 585, 350, 430, 235 };
			gl.fillPolygon(polygon, hoverColor(2, new Color(235, 145, 45)), 2);

			gl.setStroke(new GLStroke(0.5f, LineJoin.ROUND, 12f));
			gl.drawPolygon(new float[] { 120, 390, 300, 330, 390, 500, 210, 535 }, true,
					hoverColor(3, new Color(45, 175, 105)), 3);
		}

		private Color hoverColor(int id, Color normal) {
			return lastID == id ? normal.brighter() : normal;
		}
	}
}
