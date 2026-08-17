package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLFontSpec;

/** Demonstrates free positioning, dragging and click-to-front Z ordering. */
public final class DraggableZOrderDemo {

	private DraggableZOrderDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(DraggableZOrderDemo::showWindow);
	}

	private static void showWindow() {
		JLabel status = new JLabel(
				"Ein Rechteck mit der linken Maustaste greifen und verschieben",
				SwingConstants.CENTER);
		ZOrderPanel panel = new ZOrderPanel(status);
		panel.setPreferredSize(new Dimension(820, 520));

		JFrame frame = new JFrame("GLSwingNext - Drag & Z-Order");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static final class ZOrderPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final JLabel status;

		private ZOrderPanel(JLabel status) {
			this.status = status;
			addBox("Rot", new Color(218, 82, 91), 90, 85, 230, 155);
			addBox("Blau", new Color(61, 132, 224), 245, 145, 245, 165);
			addBox("Gruen", new Color(68, 179, 126), 410, 75, 225, 175);
			addBox("Gold", new Color(225, 165, 67), 325, 270, 250, 150);
		}

		private void addBox(String name, Color color,
				float x, float y, float width, float height) {
			DraggableRectangle rectangle = new DraggableRectangle(
					this, status, name, color);
			rectangle.setBounds(x, y, width, height);
			addGL(rectangle);
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(19, 25, 37));
			context.setStroke(new GLStroke(1)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			for (int x = 0; x < getWidth(); x += 40)
				context.drawLine(x, 0, x, getHeight(), new Color(39, 49, 67));
			for (int y = 0; y < getHeight(); y += 40)
				context.drawLine(0, y, getWidth(), y, new Color(39, 49, 67));
		}
	}

	private static final class DraggableRectangle extends GLContainer {
		private static final GLFontSpec FONT =
				new GLFontSpec(GLFont.SANS_SERIF, GLFont.BOLD, 17);

		private final ZOrderPanel host;
		private final JLabel status;
		private final String name;
		private final Color color;
		private boolean hovered;
		private boolean dragging;

		private DraggableRectangle(ZOrderPanel host, JLabel status,
				String name, Color color) {
			this.host = host;
			this.status = status;
			this.name = name;
			this.color = color;
		}

		@Override
		protected void record(GLContext2D context) {
			Color body = dragging ? color.brighter()
					: hovered ? blend(color, Color.WHITE, 0.13f) : color;
			context.fillRoundRect(0, 0, getWidth(), getHeight(), 13, body);
			context.fillRoundRect(0, 0, getWidth(), 34, 13,
					new Color(255, 255, 255, dragging ? 55 : 34));
			context.setStroke(new GLStroke(dragging ? 4 : 2)
					.withJoin(GLStroke.Join.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12,
					new Color(236, 242, 250));

			GLFont font = getGLFont(FONT);
			String text = name + "  |  Ebene " + getZOrder();
			context.drawText(font, text, 16, 22 + font.getAscent() * 0.15f,
					new Color(248, 250, 253));
		}

		@Override
		public void mouseEntered(GLMouseEvent event) {
			hovered = true;
			host.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			invalidateRecording();
		}

		@Override
		public void mouseExited(GLMouseEvent event) {
			hovered = false;
			if (!dragging)
				host.setCursor(Cursor.getDefaultCursor());
			invalidateRecording();
		}

		@Override
		public void mousePressed(GLMouseEvent event) {
			if (event.getButton() != MouseEvent.BUTTON1)
				return;
			bringToFront();
			dragging = true;
			host.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			// Every shifted sibling receives another display index. Refresh the
			// small label in all four cached recordings to keep it truthful.
			for (GLContainer child : host.getGLChildren())
				child.invalidateRecording();
			setStatus(name + " liegt jetzt vorne (Index " + getZOrder() + ")");
		}

		@Override
		public void mouseDragged(GLMouseEvent event) {
			if (!dragging
					|| (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0)
				return;
			float nextX = clamp(getX() + event.getDeltaX(),
					0, Math.max(0, host.getWidth() - getWidth()));
			float nextY = clamp(getY() + event.getDeltaY(),
					0, Math.max(0, host.getHeight() - getHeight()));
			setLocation(nextX, nextY);
			setStatus(String.format("%s: %.0f / %.0f | vorne: Index %d",
					name, nextX, nextY, getZOrder()));
		}

		@Override
		public void mouseReleased(GLMouseEvent event) {
			if (event.getButton() != MouseEvent.BUTTON1)
				return;
			dragging = false;
			host.setCursor(hovered
					? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
					: Cursor.getDefaultCursor());
			invalidateRecording();
			setStatus(name + " abgelegt | Z-Reihenfolge: " + orderDescription());
		}

		private String orderDescription() {
			return host.getGLChildrenInPaintOrder().stream()
					.map(child -> ((DraggableRectangle) child).name)
					.reduce((left, right) -> left + " < " + right)
					.orElse("leer");
		}

		private void setStatus(String text) {
			if (SwingUtilities.isEventDispatchThread())
				status.setText(text);
			else
				SwingUtilities.invokeLater(() -> status.setText(text));
		}

		private static Color blend(Color first, Color second, float amount) {
			float inverse = 1 - amount;
			return new Color(
					Math.round(first.getRed() * inverse + second.getRed() * amount),
					Math.round(first.getGreen() * inverse + second.getGreen() * amount),
					Math.round(first.getBlue() * inverse + second.getBlue() * amount),
					first.getAlpha());
		}

		private static float clamp(float value, float minimum, float maximum) {
			return Math.max(minimum, Math.min(maximum, value));
		}
	}
}
