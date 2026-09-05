package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDepthState;
import com.vingame.glswing.rendering.GLImage;
import com.vingame.glswing.rendering.GLLighting2D;
import com.vingame.glswing.rendering.GLSprite;
import com.vingame.glswing.ui.GLLabel;

/** Interactive 2.5D showcase for sprite depth, cutouts, normals and picking. */
public final class DepthSpriteDemo {
	private static final float DESIGN_WIDTH = 940;
	private static final float DESIGN_HEIGHT = 620;

	private DepthSpriteDemo() { }

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Depth Sprite Demo");
		SwingUtilities.invokeLater(DepthSpriteDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwing - 2.5D Materials",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setGLClearColor(new Color(13, 18, 28));
		frame.setMinimumSize(new Dimension(720, 480));
		frame.setSize(960, 680);
		frame.setLocationRelativeTo(null);

		GLLabel heading = new GLLabel(
				"2.5D-Sprites: Color + pixelgenaue Tiefe + Normalmap");
		heading.setBounds(28, 18, 650, 24);
		frame.addGL(heading);
		GLLabel status = new GLLabel(
				"Über die Kugeln fahren: Picking folgt derselben Tiefenentscheidung");
		status.setBounds(28, 570, 780, 24);
		frame.addGL(status);

		SpriteSurface surface = new SpriteSurface(status::setText);
		surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		frame.addGL(surface);
		frame.getGLCanvas().addMouseWheelListener(event -> zoom(surface, event));

		long started = System.nanoTime();
		Timer lightTimer = new Timer(33, event -> {
			float seconds = (System.nanoTime() - started) / 1_000_000_000f;
			surface.setLightAngle(seconds * 0.55f);
		});
		lightTimer.start();
		frame.closedFuture().whenComplete((unused, error) -> lightTimer.stop());
		frame.setVisible(true);
	}

	private static void zoom(SpriteSurface surface, MouseWheelEvent event) {
		double factor = Math.pow(1.13, -event.getPreciseWheelRotation());
		surface.setZoom(Math.max(0.5f,
				Math.min(2.4f, (float) (surface.zoom * factor))));
	}

	private static final class SpriteSurface extends GLContainer {
		private final GLSprite red = sphere(new Color(240, 78, 92));
		private final GLSprite green = sphere(new Color(73, 215, 157));
		private final GLSprite blue = sphere(new Color(72, 151, 244));
		private final GLMouseTarget redTarget;
		private final GLMouseTarget greenTarget;
		private final GLMouseTarget blueTarget;
		private float lightAngle;
		private float zoom = 1;

		SpriteSurface(Consumer<String> status) {
			redTarget = target("Rot · Basis-Z 0,78", status);
			greenTarget = target("Grün · Basis-Z 0,58", status);
			blueTarget = target("Blau · Basis-Z 0,38", status);
		}

		void setZoom(float value) {
			if (zoom == value)
				return;
			zoom = value;
			repaintGL();
		}

		void setLightAngle(float value) {
			lightAngle = value;
			invalidateRecording();
		}

		@Override
		protected void transformComponent(GLContext2D context) {
			context.translate(DESIGN_WIDTH * 0.5f, DESIGN_HEIGHT * 0.5f);
			context.scale(zoom);
			context.translate(-DESIGN_WIDTH * 0.5f, -DESIGN_HEIGHT * 0.5f);
		}

		@Override
		protected void record(GLContext2D context) {
			context.clearDepth();
			context.setLighting(new GLLighting2D(Color.WHITE, 0.24f,
					new GLLighting2D.DirectionalLight(
							(float) Math.cos(lightAngle) * 0.55f,
							(float) Math.sin(lightAngle) * 0.55f,
							0.84f, Color.WHITE, 1.05f)));
							

			// Deliberately front-to-back: later, lower objects must not overwrite.
			draw(context, red, redTarget, 378, 120, 250, 250, 0.78f);
			draw(context, green, greenTarget, 260, 225, 250, 250, 0.58f);
			draw(context, blue, blueTarget, 500, 255, 250, 250, 0.38f);

			// Ordinary Context geometry can participate at one fixed depth as well.
			context.setDepthState(GLDepthState.READ_WRITE);
			context.setDepth(0.08f);
			context.fillRoundRect(175, 385, 590, 105, 28,
					new Color(29, 39, 57));
			context.setDepthState(GLDepthState.DISABLED);
		}

		private static void draw(GLContext2D context, GLSprite sprite,
				GLMouseTarget target, float x, float y,
				float width, float height, float depth) {
			context.setMouseTarget(target, x, y);
			context.drawSprite(sprite, x, y, width, height, Color.WHITE, depth);
			context.clearMouseTarget();
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	private static GLMouseTarget target(String name, Consumer<String> status) {
		return new GLMouseTarget() {
			@Override public void mouseEntered(GLMouseEvent event) {
				status.accept(name + " · lokale Position "
						+ Math.round(event.getLocalX()) + ", "
						+ Math.round(event.getLocalY()));
			}
			@Override public void mouseMoved(GLMouseEvent event) {
				mouseEntered(event);
			}
			@Override public void mouseExited(GLMouseEvent event) {
				status.accept("Mausrad: Zoom · Licht bewegt sich automatisch");
			}
		};
	}

	private static GLSprite sphere(Color color) {
		// The demo draws a sphere at 250 px and permits more than 2x zoom. Keep
		// the generated material maps at a matching source resolution so their
		// one-texel coverage edge is never magnified into visible MSAA bands.
		int size = 512;
		BufferedImage colorMap = new BufferedImage(size, size,
				BufferedImage.TYPE_INT_ARGB);
		BufferedImage depthMap = new BufferedImage(size, size,
				BufferedImage.TYPE_INT_ARGB);
		BufferedImage normalMap = new BufferedImage(size, size,
				BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++) {
				float nx = (x + 0.5f) / size * 2 - 1;
				float ny = (y + 0.5f) / size * 2 - 1;
				float radiusSquared = nx * nx + ny * ny;
				if (radiusSquared >= 1) {
					colorMap.setRGB(x, y, 0);
					continue;
				}
				float radius = (float) Math.sqrt(radiusSquared);
				float nz = (float) Math.sqrt(1 - radiusSquared);
				// Signed distance to the circle edge in source-image pixels. Only
				// the outermost texel carries partial coverage; texture filtering
				// and alpha-to-coverage handle the final subpixel edge on screen.
				float edgeDistancePixels = (1 - radius) * size * 0.5f;
				int alpha = Math.round(Math.min(1, edgeDistancePixels) * 255);
				colorMap.setRGB(x, y, new Color(color.getRed(), color.getGreen(),
						color.getBlue(), alpha).getRGB());
				int depth = Math.round(nz * 255);
				depthMap.setRGB(x, y, new Color(depth, depth, depth).getRGB());
				normalMap.setRGB(x, y, new Color(
						Math.round((nx * 0.5f + 0.5f) * 255),
						Math.round((ny * 0.5f + 0.5f) * 255),
						Math.round(nz * 255)).getRGB());
			}
		return GLSprite.builder(GLImage.of(colorMap))
				.depthMap(GLImage.of(depthMap))
				.normalMap(GLImage.of(normalMap))
				.alphaMode(GLAlphaMode.CUTOUT)
				.alphaCutoff(0.04f)
				.depthRange(-0.04f, 0.12f)
				.lit(true)
				.build();
	}
}
