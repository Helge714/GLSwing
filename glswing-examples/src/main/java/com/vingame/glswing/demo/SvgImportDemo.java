package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLSvgDocument;
import com.vingame.glswing.ui.GLLabel;

/** Loads and displays a complete SVG document from the classpath. */
public final class SvgImportDemo {

	private static final String ICON_RESOURCE =
			"com/vingame/glswing/icons/test_icon.svg";
	private static final float DESIGN_WIDTH = 900;
	private static final float DESIGN_HEIGHT = 680;

	private SvgImportDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("SVG Import Demo");
		SwingUtilities.invokeLater(SvgImportDemo::show);
	}

	private static void show() {
		GLSvgDocument document = GLSvgDocument.loadResource(ICON_RESOURCE);
		GLFrame frame = new GLFrame("GLSwing - SVG Import",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setGLClearColor(new Color(18, 23, 34));
		frame.setMinimumSize(new Dimension(620, 520));
		frame.setSize(920, 720);
		frame.setLocationRelativeTo(null);

		SvgSurface surface = new SvgSurface(document);
		surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		frame.addGL(surface);

		GLLabel title = new GLLabel("Vollständiger SVG-Import aus resources");
		title.setBounds(30, 18, 400, 24);
		frame.addGL(title);
		GLLabel details = new GLLabel("viewBox, Paths, Clipping, Fill-Rules und lineare Gradients | Mausrad: Zoom");
		details.setBounds(30, 45, 700, 22);
		frame.addGL(details);

		frame.getGLCanvas().addMouseWheelListener(event -> zoom(surface, event));
		frame.setVisible(true);
	}

	private static void zoom(SvgSurface surface, MouseWheelEvent event) {
		double factor = Math.pow(1.16, -event.getPreciseWheelRotation());
		surface.setZoom(Math.max(0.25f,
				Math.min(5, (float) (surface.getZoom() * factor))));
	}

	private static final class SvgSurface extends GLContainer {
		private final GLSvgDocument document;
		private volatile float zoom = 1;

		SvgSurface(GLSvgDocument document) {
			this.document = document;
		}

		float getZoom() { return zoom; }

		void setZoom(float zoom) {
			if (Float.compare(this.zoom, zoom) == 0)
				return;
			this.zoom = zoom;
			invalidateRecording();
		}

		@Override
		protected void record(GLContext2D context) {
			context.translate(DESIGN_WIDTH * 0.5f, DESIGN_HEIGHT * 0.5f);
			context.scale(zoom);
			context.translate(-DESIGN_WIDTH * 0.5f, -DESIGN_HEIGHT * 0.5f);
			document.draw(context, 170, 75, 560, 560);
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}
}
