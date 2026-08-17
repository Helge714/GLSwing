package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLImage;
import com.vingame.glswing.rendering.GLImageOptions;
import com.vingame.glswing.ui.GLLabel;

/** Visual smoke test for RGBA atlases, MSDF icons and multi-texture UI batching. */
public final class ImageAtlasDemo {

	private static final float DESIGN_WIDTH = 900;
	private static final float DESIGN_HEIGHT = 620;

	private ImageAtlasDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Image / Icon Atlas Demo");
		SwingUtilities.invokeLater(ImageAtlasDemo::show);
	}

	private static void show() {
		GLIcon msdfIcon = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/link_icon.svg");
		GLIcon rgbaIcon = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/test_icon.svg", GLIcon.Format.RGBA);
		GLImage[] tiles = new GLImage[30];
		for (int index = 0; index < tiles.length; index++)
			tiles[index] = checkerImage(index);

		GLFrame frame = new GLFrame("GLSwingNext - Images, Icons und Atlanten",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setGLClearColor(new Color(17, 22, 32));
		frame.setMinimumSize(new Dimension(640, 480));
		frame.setSize(920, 680);
		frame.setLocationRelativeTo(null);

		AtlasSurface surface = new AtlasSurface(msdfIcon, rgbaIcon, tiles);
		surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
		frame.addGL(surface);

		GLLabel status = new GLLabel("");
		status.setBounds(24, 18, 520, 22);
		frame.addGL(status);
		Timer statusTimer = new Timer(250, event -> status.setText(String.format(
				"%.0f FPS | %d Drawcalls | Mausrad: Zoom",
				frame.getFramesPerSecond(), frame.getLastDrawCallCount())));
		statusTimer.start();
		frame.closedFuture().whenComplete((unused, error) -> statusTimer.stop());
		frame.getGLCanvas().addMouseWheelListener(
				event -> zoom(surface, event));
		frame.setVisible(true);
	}

	private static void zoom(AtlasSurface surface, MouseWheelEvent event) {
		double factor = Math.pow(1.14, -event.getPreciseWheelRotation());
		surface.setZoom(Math.max(0.35f,
				Math.min(4, (float) (surface.getZoom() * factor))));
	}

	private static GLImage checkerImage(int index) {
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Color first = Color.getHSBColor(index / 30f, 0.7f, 0.95f);
		Color second = new Color(255 - first.getRed(), 255 - first.getGreen(),
				255 - first.getBlue());
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				image.setRGB(x, y, (((x / 6) + (y / 6)) & 1) == 0
						? first.getRGB() : second.getRGB());
		return GLImage.of(image, GLImageOptions.pixelArt());
	}

	private static final class AtlasSurface extends GLContainer {
		private final GLIcon msdfIcon;
		private final GLIcon rgbaIcon;
		private final GLImage[] tiles;
		private float zoom = 1;

		AtlasSurface(GLIcon msdfIcon, GLIcon rgbaIcon, GLImage[] tiles) {
			this.msdfIcon = msdfIcon;
			this.rgbaIcon = rgbaIcon;
			this.tiles = tiles;
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

			context.fillUIRoundRect(55, 72, 790, 490, 18,
					new Color(29, 37, 53));
			context.drawUIRoundRect(55, 72, 790, 490, 18, 2,
					new Color(70, 86, 112));

			Color[] iconColors = {
				new Color(105, 190, 255), new Color(255, 143, 105),
				new Color(126, 225, 164), new Color(224, 154, 255)
			};
			for (int index = 0; index < iconColors.length; index++) {
				float size = 42 + index * 16;
				context.drawIcon(msdfIcon, 92 + index * 115,
						112 + (90 - size) * 0.5f, size, size, iconColors[index]);
			}
			context.drawIcon(rgbaIcon, 625, 96, 150, 150);

			for (int index = 0; index < tiles.length; index++) {
				float x = 92 + (index % 10) * 66;
				float y = 280 + (index / 10) * 76;
				context.fillUIRoundRect(x - 5, y - 5, 58, 58, 9,
						new Color(42, 53, 73));
				context.drawUIImage(tiles[index], x, y, 48, 48);
			}
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}
}
