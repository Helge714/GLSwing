package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.GLTransform;

/** Demonstrates direct object targets, local coordinates and drag capture. */
public final class PickingDemo {

	private PickingDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(PickingDemo::showWindow);
	}

	private static void showWindow() {
		PickingPanel panel = new PickingPanel();
		panel.setPreferredSize(new Dimension(1000, 620));
		JLabel status = new JLabel("Maus über ein Objekt bewegen", SwingConstants.CENTER);

		JFrame frame = new JFrame("GLSwingNext - direkte Maus-Targets");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Timer statusTimer = new Timer(50,
				event -> status.setText("Hover: " + panel.targetName(panel.hoveredTarget) + "    Auswahl: "
						+ panel.targetName(panel.selectedTarget) + "    " + panel.lastInteraction
						+ String.format("    Zoom: %.2fx    Rotation: %.1f°    Verschiebung: %.0f / %.0f", panel.zoom,
								Math.toDegrees(panel.rotation), panel.translationX, panel.translationY)
						+ "    | Linksklick/-ziehen, Mausrad, Shift+Mausrad, Rechtsziehen"));
		statusTimer.start();
	}

	private static final class PickingPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final DemoTarget cardTarget = new DemoTarget("Card");
		private final DemoTarget triangleTarget = new DemoTarget("Triangle");
		private final DemoTarget textureTarget = new DemoTarget("Texture ring");
		private final DemoTarget textTarget = new DemoTarget("MSDF text");
		private volatile DemoTarget hoveredTarget;
		private volatile DemoTarget selectedTarget;
		private volatile String lastInteraction = "Noch kein Ereignis";
		private volatile float zoom = 1;
		private volatile float rotation;
		private volatile float translationX;
		private volatile float translationY;
		private Point dragAnchor;
		private float dragStartX;
		private float dragStartY;
		private GLTexture ringTexture;
		private GLFont font;

		PickingPanel() {
			MouseAdapter viewControls = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event) && event.getClickCount() == 2) {
						zoom = 1;
						rotation = 0;
						translationX = 0;
						translationY = 0;
						lastInteraction = "Ansicht zurückgesetzt";
						repaint();
					}
				}

				@Override
				public void mousePressed(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event)) {
						dragAnchor = event.getPoint();
						dragStartX = translationX;
						dragStartY = translationY;
					}
				}

				@Override
				public void mouseReleased(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event))
						dragAnchor = null;
				}

				@Override
				public void mouseDragged(MouseEvent event) {
					if (dragAnchor == null)
						return;
					translationX = dragStartX + event.getX() - dragAnchor.x;
					translationY = dragStartY + event.getY() - dragAnchor.y;
					repaint();
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent event) {
					if (event.isShiftDown()) {
						rotation += event.getPreciseWheelRotation() * 0.08f;
					} else {
						double factor = Math.pow(1.2, -event.getPreciseWheelRotation());
						zoom = clamp((float) (zoom * factor), 0.2f, 8);
					}
					repaint();
				}
			};
			addMouseListener(viewControls);
			addMouseMotionListener(viewControls);
			addMouseWheelListener(viewControls);
		}

		@Override
		protected void initialize(GL3 gl) {
			ringTexture = GLTexture.create(gl, createRingTexture(160));
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 42);
		}

		@Override
		protected void render(GLContext2D context) {
			context.clear();
			context.setTransform(
					new GLTransform().translate(getWidth() / 2f + translationX, getHeight() / 2f + translationY)
							.rotate(rotation).scale(zoom).translate(-500, -310));
			context.setStroke(new GLStroke(3).withJoin(GLStroke.Join.ROUND).withWidthSpace(GLStroke.WidthSpace.SCREEN));

			// Stateful: every following draw command belongs to the card target.
			context.setMouseTarget(cardTarget);
			context.fillRect(65, 80, 260, 190, colorFor(cardTarget, new Color(57, 116, 199)));
			context.drawRect(65, 80, 260, 190, new Color(180, 220, 255));
			context.drawText(font, "CARD", 112, 195, Color.WHITE);

			// One-shot: the target applies only to this individual command.
			float[] triangle = { 385, 265, 520, 72, 650, 265 };
			context.fillPolygon(triangle, colorFor(triangleTarget, new Color(235, 137, 55)), triangleTarget);
			context.drawPolygon(triangle, new Color(255, 224, 170), triangleTarget);

			context.clearMouseTarget();
			context.drawTexture(ringTexture, 720, 75, 210, 210, colorFor(textureTarget, Color.WHITE), textureTarget);
			context.drawText(font, "Nur die Buchstaben sind pickbar", 115, 425,
					colorFor(textTarget, new Color(104, 218, 170)), textTarget);

			context.setStroke(new GLStroke(2).withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawLine(90, 500, 910, 540, new Color(154, 174, 205, 150), textTarget);
		}

		private Color colorFor(DemoTarget target, Color normal) {
			if (target == selectedTarget)
				return new Color(245, 95, 125, normal.getAlpha());
			if (target == hoveredTarget)
				return normal.brighter();
			return normal;
		}

		private String targetName(DemoTarget target) {
			return target == null ? "none" : target.name;
		}

		private final class DemoTarget implements GLMouseTarget {
			private final String name;

			DemoTarget(String name) {
				this.name = name;
			}

			@Override
			public void mouseEntered(GLMouseEvent event) {
				hoveredTarget = this;
				showCoordinates("entered", event);
			}

			@Override
			public void mouseExited(GLMouseEvent event) {
				if (hoveredTarget == this)
					hoveredTarget = null;
				showCoordinates("exited", event);
			}

			@Override
			public void mouseMoved(GLMouseEvent event) {
				showCoordinates("moved", event);
			}

			@Override
			public void mousePressed(GLMouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event.getOriginalEvent()))
					showCoordinates("pressed", event);
			}

			@Override
			public void mouseDragged(GLMouseEvent event) {
				if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0)
					showCoordinates(String.format("dragged Δ %.0f/%.0f", event.getDeltaX(), event.getDeltaY()), event);
			}

			@Override
			public void mouseReleased(GLMouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event.getOriginalEvent()))
					showCoordinates("released", event);
			}

			@Override
			public void mouseClicked(GLMouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event.getOriginalEvent())) {
					selectedTarget = this;
					showCoordinates("clicked", event);
				}
			}

			@Override
			public void mouseWheelMoved(GLMouseWheelEvent event) {
				showCoordinates(String.format("wheel %.1f", event.getPreciseWheelRotation()), event);
			}

			private void showCoordinates(String action, GLMouseEvent event) {
				lastInteraction = event.hasLocalPosition()
						? String.format("%s.%s lokal %.1f / %.1f", name, action, event.getLocalX(), event.getLocalY())
						: name + "." + action + " (singuläre Transformation)";
				repaint();
			}
		}

		@Override
		protected void disposeResources(GL3 gl) {
			if (font != null) {
				font.dispose(gl);
				font = null;
			}
			if (ringTexture != null) {
				ringTexture.dispose(gl);
				ringTexture = null;
			}
		}
	}

	private static BufferedImage createRingTexture(int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		float center = (size - 1) / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float distance = (float) Math.hypot(x - center, y - center) / center;
				float edge = Math.min(1, Math.min((distance - 0.42f) * 24, (0.92f - distance) * 24));
				int alpha = Math.max(0, Math.min(255, Math.round(edge * 255)));
				image.setRGB(x, y, new Color(175, 104, 235, alpha).getRGB());
			}
		}
		return image;
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
