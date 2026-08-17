package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.layout.GLLayouts;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** Responsive nested grid/free-layout example with automatic recording rebuilds. */
public final class LayoutDemo {

	private LayoutDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(LayoutDemo::showWindow);
	}

	private static void showWindow() {
		LayoutPanel panel = new LayoutPanel();
		panel.packGL();
		JLabel status = new JLabel("Fenster skalieren: Layout läuft nur bei Bedarf",
				SwingConstants.CENTER);
		JButton visibility = new JButton("Eine Karte ausblenden");
		visibility.addActionListener(event -> {
			boolean visible = !panel.optionalCard.isVisible();
			panel.optionalCard.setVisible(visible);
			visibility.setText(visible
					? "Eine Karte ausblenden" : "Karte wieder einblenden");
		});
		JButton pack = new JButton("packGL() + frame.pack()");
		JFrame frame = new JFrame("GLSwingNext - leichtes Layoutsystem");
		pack.addActionListener(event -> {
			panel.packGL();
			frame.pack();
		});
		JPanel controls = new JPanel(new BorderLayout());
		controls.add(visibility, BorderLayout.WEST);
		controls.add(status, BorderLayout.CENTER);
		controls.add(pack, BorderLayout.EAST);

		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(controls, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setMinimumSize(new java.awt.Dimension(560, 430));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Timer statistics = new Timer(250, event -> {
			GLSize preferred = panel.getPreferredGLSize();
			status.setText(String.format(
					"Panel %d x %d | preferred %.0f x %.0f | %d Drawcalls",
					panel.getWidth(), panel.getHeight(), preferred.width(), preferred.height(),
					panel.getLastDrawCallCount()));
		});
		statistics.start();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				statistics.stop();
			}
		});
	}

	private static final class LayoutPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final LayoutBox optionalCard;
		private GLFont titleFont;
		private GLFont bodyFont;

		private LayoutPanel() {
			setGLLayout(new GLGridLayout(3).withGap(14));
			setGLPadding(18);

			LayoutBox header = box("Responsive GLSwing Layout",
					new Color(39, 80, 140), 620, 72);
			LayoutBox sidebar = box("Sidebar",
					new Color(39, 113, 105), 155, 330);
			sidebar.setMinimumGLSize(120, 180);
			sidebar.setMaximumGLSize(210, Float.POSITIVE_INFINITY);
			LayoutBox content = box("Grid-Content",
					new Color(34, 47, 68), 430, 330);
			content.setGLLayout(new GLGridLayout(2).withGap(10));
			content.setGLPadding(14);

			content.addGL(box("AUTO 1", new Color(67, 126, 214), 170, 105),
					GLConstraints.auto().weight(1, 1).fillBoth());
			optionalCard = box("AUTO 2", new Color(139, 86, 184), 170, 105);
			content.addGL(optionalCard,
					GLConstraints.auto().weight(1, 1).fillBoth());
			content.addGL(box("AUTO 3", new Color(196, 99, 61), 170, 105),
					GLConstraints.auto().weight(1, 1).fillBoth());
			content.addGL(freeCanvas(),
					GLConstraints.auto().weight(1, 1).fillBoth());

			LayoutBox footer = box("Footer über drei Spalten",
					new Color(57, 69, 91), 620, 54);
			addGL(header, GLConstraints.at(0, 0).span(3, 1)
					.fillHorizontal());
			addGL(sidebar, GLConstraints.at(0, 1)
					.weight(0, 1).fillBoth());
			addGL(content, GLConstraints.at(1, 1).span(2, 1)
					.weight(1, 1).fillBoth());
			addGL(footer, GLConstraints.at(0, 2).span(3, 1)
					.fillHorizontal());
		}

		private LayoutBox freeCanvas() {
			LayoutBox canvas = box("FREE", new Color(42, 62, 88), 170, 105);
			canvas.setGLLayout(GLLayouts.FREE);
			LayoutBox first = box("", new Color(79, 198, 218), 42, 32);
			LayoutBox second = box("", new Color(238, 166, 69), 54, 38);
			first.setBounds(22, 50, 42, 32);
			second.setBounds(88, 38, 54, 38);
			canvas.addGL(first);
			canvas.addGL(second);
			return canvas;
		}

		private LayoutBox box(String title, Color color, float width, float height) {
			LayoutBox box = new LayoutBox(this, title, color);
			box.setPreferredGLSize(width, height);
			box.setMinimumGLSize(Math.min(width, 90), Math.min(height, 55));
			return box;
		}

		@Override
		protected void initialize(GL3 gl) {
			titleFont = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 21);
			bodyFont = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 15);
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(12, 18, 29));
		}

		@Override
		protected void disposeResources(GL3 gl) {
			if (titleFont != null)
				titleFont.dispose(gl);
			if (bodyFont != null)
				bodyFont.dispose(gl);
		}
	}

	private static final class LayoutBox extends GLContainer {
		private final LayoutPanel panel;
		private final String title;
		private final Color color;

		private LayoutBox(LayoutPanel panel, String title, Color color) {
			this.panel = panel;
			this.title = title;
			this.color = color;
		}

		@Override
		protected void record(GLContext2D context) {
			context.fillRect(0, 0, getWidth(), getHeight(), color);
			context.setStroke(new GLStroke(2)
					.withJoin(GLStroke.Join.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawRect(0, 0, getWidth(), getHeight(),
					new Color(206, 226, 250, 180));
			if (!title.isEmpty() && panel.bodyFont != null)
				context.drawText(title.equals("Responsive GLSwing Layout")
						? panel.titleFont : panel.bodyFont,
						title, 14, 30, Color.WHITE);
		}
	}
}
