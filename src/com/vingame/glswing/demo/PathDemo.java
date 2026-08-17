package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLPath2D;
import com.vingame.glswing.rendering.GLSvgPathParser;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLLabel;

/** Zoomable showcase for mixed, multi-contour and stroked paths. */
public final class PathDemo {

	private static final float DESIGN_WIDTH = 980;
	private static final float DESIGN_HEIGHT = 620;
	private static final float MIN_ZOOM = 0.35f;
	private static final float MAX_ZOOM = 3.5f;

	private PathDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Path Demo");
		SwingUtilities.invokeLater(PathDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwingNext - Paths",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setGLClearColor(new Color(18, 23, 34));
		frame.setMinimumSize(new Dimension(760, 520));
		frame.setSize(1000, 660);
		frame.setLocationRelativeTo(null);

		ZoomLayer root = new ZoomLayer();
		root.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		frame.addGL(root);

		PathSurface surface = new PathSurface();
		surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		root.addGL(surface);
		addLabel(root, "GLPath2D aus echten SVG-d-Pfaddaten",
				34, 20, 520, 25);
		addLabel(root, "gemischter geschlossener Path", 55, 66, 250, 22);
		addLabel(root, "EVEN_ODD: Loch", 375, 66, 180, 22);
		addLabel(root, "NON_ZERO: gleiche Richtung", 655, 66, 240, 22);
		addLabel(root, "WORLD: Breite und Dashes zoomen mit",
				55, 354, 300, 22);
		addLabel(root, "SCREEN: kosmetischer Stroke bleibt konstant",
				55, 470, 340, 22);
		addLabel(root, "Mausrad: Zoom  |  Paths werden nur bei View-Änderungen neu abgeflacht",
				34, 584, 600, 22);

		frame.getGLCanvas().addMouseWheelListener(event -> zoom(root, event));
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
		layer.setZoom(Math.max(MIN_ZOOM,
				Math.min(MAX_ZOOM, (float) (layer.getZoom() * factor))));
	}

	private static final class PathSurface extends GLContainer {
		private final GLPath2D mixed = createMixedPath();
		private final GLPath2D evenOdd = createRing(470, 205,
				105, 46, GLPath2D.FillRule.EVEN_ODD);
		private final GLPath2D nonZero = createRing(750, 205,
				105, 46, GLPath2D.FillRule.NON_ZERO);
		private final GLPath2D worldCurve = createWave(65, 405);
		private final GLPath2D screenCurve = createWave(65, 520);

		@Override
		protected void record(GLContext2D context) {
			context.fillPath(mixed, new Color(75, 155, 245));
			context.setStroke(new GLStroke(6)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withDash(18, 7));
			context.drawPath(mixed, new Color(185, 220, 255));

			context.fillPath(evenOdd, new Color(85, 215, 165));
			context.fillPath(nonZero, new Color(240, 165, 75));
			context.setStroke(new GLStroke(4).withJoin(GLStroke.Join.ROUND));
			context.drawPath(evenOdd, new Color(190, 255, 225));
			context.drawPath(nonZero, new Color(255, 225, 180));

			context.setStroke(new GLStroke(7)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withDash(new float[] { 26, 10, 5, 10 }, 4)
					.withWidthSpace(GLStroke.WidthSpace.WORLD));
			context.drawPath(worldCurve, new Color(245, 100, 165));

			context.setStroke(new GLStroke(7)
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withDash(new float[] { 26, 10, 5, 10 }, 4)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawPath(screenCurve, new Color(110, 185, 255));
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	private static GLPath2D createMixedPath() {
		return GLSvgPathParser.parse(
				"M80 285 V145 Q165 70 250 145 C300 190 275 285 200 300 L135 250 Z");
	}

	private static GLPath2D createRing(float centerX, float centerY,
			float outerRadius, float innerRadius, GLPath2D.FillRule fillRule) {
		String data = circleData(centerX, centerY, outerRadius)
				+ " " + circleData(centerX, centerY, innerRadius);
		return GLSvgPathParser.parse(data, fillRule);
	}

	private static String circleData(
			float centerX, float centerY, float radius) {
		return "M" + (centerX + radius) + " " + centerY
				+ " A" + radius + " " + radius + " 0 1 1 "
				+ (centerX - radius) + " " + centerY
				+ " A" + radius + " " + radius + " 0 1 1 "
				+ (centerX + radius) + " " + centerY + " Z";
	}

	private static GLPath2D createWave(float x, float y) {
		return GLSvgPathParser.parse("M" + x + " " + y
				+ " q105 -42 210 0 c90 42 180 -42 270 0 h340");
	}

	private static final class ZoomLayer extends GLContainer {
		private volatile float zoom = 1;

		float getZoom() { return zoom; }

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
}
