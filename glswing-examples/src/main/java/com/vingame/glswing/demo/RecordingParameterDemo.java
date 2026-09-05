package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.GLTransform;

/** Interactive example for mutable and nested recording parameters. */
public final class RecordingParameterDemo {

	private RecordingParameterDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(RecordingParameterDemo::showWindow);
	}

	private static void showWindow() {
		ParameterPanel panel = new ParameterPanel();
		panel.setPreferredSize(new Dimension(900, 540));

		JSlider x = slider(20, 580, 290);
		JSlider rotation = slider(-180, 180, 0);
		JSlider scale = slider(50, 160, 100);
		JSlider opacity = slider(10, 100, 100);
		JCheckBox tint = new JCheckBox("gesamte Karte abdunkeln");
		JButton color = new JButton("Kartenfarbe wechseln");

		Runnable update = () -> {
			panel.setCardTransform(x.getValue(), 155,
					(float) Math.toRadians(rotation.getValue()), scale.getValue() / 100f);
			panel.cardParameters.setOpacity(opacity.getValue() / 100f);
			panel.cardParameters.setTint(tint.isSelected()
					? new Color(155, 180, 210) : Color.WHITE);
			panel.repaint();
		};
		x.addChangeListener(event -> update.run());
		rotation.addChangeListener(event -> update.run());
		scale.addChangeListener(event -> update.run());
		opacity.addChangeListener(event -> update.run());
		tint.addActionListener(event -> update.run());
		color.addActionListener(event -> {
			panel.nextCardColor();
			panel.repaint();
		});
		update.run();

		JPanel sliders = new JPanel(new GridLayout(4, 2, 8, 3));
		sliders.add(new JLabel("Position X"));
		sliders.add(x);
		sliders.add(new JLabel("Rotation"));
		sliders.add(rotation);
		sliders.add(new JLabel("Skalierung"));
		sliders.add(scale);
		sliders.add(new JLabel("Opacity"));
		sliders.add(opacity);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 2));
		actions.add(color);
		actions.add(tint);
		JPanel controls = new JPanel(new BorderLayout());
		controls.add(sliders, BorderLayout.CENTER);
		controls.add(actions, BorderLayout.SOUTH);

		JLabel status = new JLabel("Recording wird angelegt ...", SwingConstants.CENTER);
		JPanel south = new JPanel(new BorderLayout());
		south.add(controls, BorderLayout.CENTER);
		south.add(status, BorderLayout.SOUTH);

		JFrame frame = new JFrame("GLSwing - Recording Parameters");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(south, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Timer statistics = new Timer(250, event -> {
			GLRecording recording = panel.recording();
			status.setText(recording == null ? "Recording wird angelegt ..."
					: recording.getCommandCount() + " gespeicherte Befehle | "
							+ panel.getLastDrawCallCount() + " Drawcalls | "
							+ "Parameteränderungen ohne neues Recording");
		});
		statistics.start();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				statistics.stop();
			}
		});
	}

	private static JSlider slider(int minimum, int maximum, int value) {
		return new JSlider(minimum, maximum, value);
	}

	private static final class ParameterPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private static final float CARD_WIDTH = 320;
		private static final float CARD_HEIGHT = 220;

		private final GLRenderParameters cardParameters = new GLRenderParameters();
		private final GLRenderParameters surfaceParameters = new GLRenderParameters()
				.setColor(new Color(48, 89, 150));
		private final GLRenderParameters accentParameters = new GLRenderParameters()
				.setColor(new Color(95, 220, 255));
		private final Color[] cardColors = {
				new Color(48, 89, 150),
				new Color(116, 72, 160),
				new Color(28, 128, 111),
				new Color(184, 91, 58)
		};
		private GLFont titleFont;
		private GLFont bodyFont;
		private GLTexture texture;
		private int colorIndex;

		@Override
		protected void initialize(GL3 gl) {
			titleFont = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 30);
			bodyFont = new GLFont(GLFont.SANS_SERIF, GLFont.PLAIN, 17);
			texture = GLTexture.create(gl, checkerImage());
		}

		@Override
		protected void render(GLContext2D context) {
			context.clear(new Color(13, 19, 31));
			context.draw(getPanelRecording(), cardParameters);
		}

		@Override
		protected void record(GLContext2D context) {
			// The cardParameters are supplied later by draw(recording, parameters).
			context.fillRect(10, 12, CARD_WIDTH, CARD_HEIGHT,
					new Color(0, 0, 0, 90));

			context.withParameters(surfaceParameters, () ->
					context.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT, Color.WHITE));
			context.setStroke(new GLStroke(3)
					.withJoin(GLStroke.Join.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawRect(0, 0, CARD_WIDTH, CARD_HEIGHT,
					new Color(255, 255, 255, 180));

			context.withParameters(accentParameters, () -> {
				context.drawTexture(texture, 24, 30, 72, 72);
				context.fillRect(24, 118, 272, 5, Color.WHITE);
			});

			context.drawText(titleFont, "Parameter", 116, 62, Color.WHITE);
			context.drawText(bodyFont,
					"Ein Recording, beliebig oft veraendert",
					24, 157, new Color(224, 237, 252));
			context.drawText(bodyFont,
					"Filled + Texture + Text bleiben gebatcht",
					24, 187, new Color(184, 205, 230));
		}

		private GLRecording recording() {
			return getPanelRecording();
		}

		private void setCardTransform(float x, float y, float radians, float scale) {
			cardParameters.setTransform(new GLTransform()
					.translate(x + CARD_WIDTH / 2, y + CARD_HEIGHT / 2)
					.rotate(radians)
					.scale(scale)
					.translate(-CARD_WIDTH / 2, -CARD_HEIGHT / 2));
		}

		private void nextCardColor() {
			colorIndex = (colorIndex + 1) % cardColors.length;
			surfaceParameters.setColor(cardColors[colorIndex]);
		}

		@Override
		protected void disposeResources(GL3 gl) {
			if (titleFont != null)
				titleFont.dispose(gl);
			if (bodyFont != null)
				bodyFont.dispose(gl);
			if (texture != null)
				texture.dispose(gl);
		}
	}

	private static BufferedImage checkerImage() {
		BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int dx = x - 24;
				int dy = y - 24;
				boolean inside = dx * dx + dy * dy < 22 * 22;
				int alpha = inside ? ((x / 6 + y / 6) % 2 == 0 ? 255 : 150) : 0;
				image.setRGB(x, y, new Color(255, 255, 255, alpha).getRGB());
			}
		}
		return image;
	}
}
