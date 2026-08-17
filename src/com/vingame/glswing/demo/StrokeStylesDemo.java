package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLine;

/** Directly runnable showcase for solid and dashed strokes. */
public final class StrokeStylesDemo {

	private static final Color TEXT = new Color(205, 215, 232);
	private static final Color BLUE = new Color(80, 155, 255);
	private static final Color PINK = new Color(250, 105, 165);
	private static final float DESIGN_WIDTH = 960;
	private static final float DESIGN_HEIGHT = 620;
	private static final float MIN_ZOOM = 0.35f;
	private static final float MAX_ZOOM = 3.5f;

	private StrokeStylesDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Stroke Styles Demo");
		SwingUtilities.invokeLater(StrokeStylesDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwingNext - Stroke styles",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setGLClearColor(new Color(20, 25, 36));
		frame.setMinimumSize(new Dimension(760, 520));
		frame.setSize(980, 650);
		frame.setLocationRelativeTo(null);

		ZoomLayer zoomLayer = new ZoomLayer();
		zoomLayer.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		frame.addGL(zoomLayer);

		DirectStrokeExamples examples = new DirectStrokeExamples();
		examples.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		zoomLayer.addGL(examples);

		addLabel(zoomLayer, "Direkte Zeichen-API", 36, 26, 300, 24);
		addLabel(zoomLayer, "solid / WORLD", 36, 78, 140, 22);
		addLabel(zoomLayer, "dash { 22, 10 } / BUTT / WORLD", 36, 138, 250, 22);
		addLabel(zoomLayer, "dash { 2, 12 } / ROUND / SCREEN", 36, 198, 260, 22);
		addLabel(zoomLayer, "dash phase 13 / SQUARE / WORLD", 36, 258, 260, 22);
		addLabel(zoomLayer, "geschlossene und farbige Pfade", 36, 324, 250, 22);
		addLabel(zoomLayer, "Retained GLLine: oben SCREEN, unten WORLD",
				36, 452, 300, 24);

		GLLine retainedDash = new GLLine(GLLine.Orientation.HORIZONTAL, BLUE,
				new GLStroke(7).withCap(GLStroke.Cap.ROUND).withDash(18, 8)
						.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		retainedDash.setBounds(330, 458, 560, 20);
		zoomLayer.addGL(retainedDash);

		GLLine retainedDiagonal = new GLLine(GLLine.Orientation.DIAGONAL_UP, PINK,
				new GLStroke(5).withCap(GLStroke.Cap.SQUARE)
						.withJoin(GLStroke.Join.ROUND).withDash(14, 7, 3, 7));
		retainedDiagonal.setBounds(330, 506, 560, 72);
		zoomLayer.addGL(retainedDiagonal);

		addLabel(zoomLayer, "Mausrad: Zoom  |  GLLine passt sich seinen Bounds an",
				36, 565, 430, 22);
		frame.getGLCanvas().addMouseWheelListener(event -> zoom(zoomLayer, event));
		frame.setVisible(true);
	}

	private static void addLabel(GLContainer parent, String text,
			float x, float y, float width, float height) {
		GLLabel label = new GLLabel(text);
		label.setBounds(x, y, width, height);
		parent.addGL(label);
	}

	private static void zoom(ZoomLayer layer, MouseWheelEvent event) {
		double factor = Math.pow(1.16, -event.getPreciseWheelRotation());
		layer.setZoom(clamp((float) (layer.getZoom() * factor), MIN_ZOOM, MAX_ZOOM));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static final class ZoomLayer extends GLContainer {
		private volatile float zoom = 1;

		float getZoom() {
			return zoom;
		}

		void setZoom(float zoom) {
			if (Float.compare(this.zoom, zoom) == 0)
				return;
			this.zoom = zoom;
			repaintGL();
		}

		@Override
		protected void record(GLContext2D context) {
		}

		@Override
		protected void transformComponent(GLContext2D context) {
			context.translate(DESIGN_WIDTH * 0.5f, DESIGN_HEIGHT * 0.5f);
			context.scale(zoom);
			context.translate(-DESIGN_WIDTH * 0.5f, -DESIGN_HEIGHT * 0.5f);
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	private static final class DirectStrokeExamples extends GLContainer {
		@Override
		protected void record(GLContext2D context) {
			context.setStroke(new GLStroke(7).withCap(GLStroke.Cap.BUTT));
			context.drawLine(330, 88, 890, 88, TEXT);

			context.setStrokeDash(22, 10);
			context.drawLine(330, 148, 890, 148, BLUE);

			context.setStroke(new GLStroke(8)
					.withCap(GLStroke.Cap.ROUND)
					.withDash(2, 12)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawLine(330, 208, 890, 208, new Color(95, 220, 170));

			context.setStroke(new GLStroke(7)
					.withCap(GLStroke.Cap.SQUARE)
					.withDash(new float[] { 24, 9, 5, 9 }, 13));
			context.drawLine(330, 268, 890, 268, PINK);

			context.setStroke(new GLStroke(6)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withDash(18, 8));
			context.drawPolygon(new float[] {
					340, 338, 540, 312, 610, 406, 390, 414
			}, new Color(245, 190, 80));

			context.setStroke(new GLStroke(7)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withDash(26, 9));
			context.drawPolyline(new float[] {
					670, 405, 730, 325, 810, 405, 890, 325
			}, new Color[] {
					BLUE, new Color(120, 220, 180),
					new Color(245, 205, 85), PINK
			});
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}
}
