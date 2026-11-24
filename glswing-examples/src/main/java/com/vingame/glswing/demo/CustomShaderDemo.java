package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;

/** Demonstrates independent filled, texture and text shaders with uniforms. */
public final class CustomShaderDemo {

	private static final String VERTEX_SHADER = """
			#version 330 core

			layout(location = 0) in vec2 aPosition;
			layout(location = 1) in vec4 aColor;

			uniform mat4 uProjection;

			out vec4 vColor;

			void main() {
			    vColor = aColor;
			    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
			}
			""";

	private static final String FRAGMENT_SHADER = """
			#version 330 core

			in vec4 vColor;
			uniform float uGamma;
			out vec4 fragColor;

			void main() {
			    vec3 inverted = vec3(1.0) - vColor.rgb;
			    vec3 corrected = pow(max(inverted, vec3(0.0)), vec3(1.0 / uGamma));
			    fragColor = vec4(corrected, vColor.a);
			}
			""";

	private static final String TEXTURED_VERTEX_SHADER = """
			#version 330 core

			layout(location = 0) in vec2 aPosition;
			layout(location = 1) in vec2 aTexCoord;
			layout(location = 2) in vec4 aColor;

			uniform mat4 uProjection;

			out vec2 vTexCoord;
			out vec4 vColor;

			void main() {
			    vTexCoord = aTexCoord;
			    vColor = aColor;
			    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
			}
			""";

	private static final String TEXTURE_FRAGMENT_SHADER = """
			#version 330 core

			in vec2 vTexCoord;
			in vec4 vColor;

			uniform sampler2D uTexture;
			uniform float uGamma;

			out vec4 fragColor;

			void main() {
			    vec4 sampled = texture(uTexture, vTexCoord);
			    vec3 corrected = pow(max(sampled.rgb, vec3(0.0)), vec3(1.0 / uGamma));
			    fragColor = vec4(corrected, sampled.a) * vColor;
			}
			""";

	private static final String TEXT_FRAGMENT_SHADER = """
			#version 330 core

			in vec2 vTexCoord;
			in vec4 vColor;

			uniform sampler2D uTexture;
			uniform float uMsdfPixelRange;
			uniform float uGamma;

			out vec4 fragColor;

			void main() {
			    vec3 msdf = texture(uTexture, vTexCoord).rgb;
			    float distance = max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b)) - 0.5;
			    vec2 unitRange = vec2(uMsdfPixelRange) / vec2(textureSize(uTexture, 0));
			    vec2 screenSize = vec2(1.0) / max(fwidth(vTexCoord), vec2(0.000001));
			    float pixelRange = max(0.5 * dot(unitRange, screenSize), 1.0);
			    float coverage = clamp(distance * pixelRange + 0.5, 0.0, 1.0);
			    coverage = pow(coverage, 1.0 / uGamma);
			    fragColor = vec4(vColor.rgb, vColor.a * coverage);
			}
			""";

	private CustomShaderDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(CustomShaderDemo::showWindow);
	}

	private static void showWindow() {
		ShaderPanel panel = new ShaderPanel();
		panel.setPreferredSize(new Dimension(960, 650));
		JLabel status = new JLabel(
				"Separate Filled-, Texture- und Text-Shader mit eigenen Uniforms",
				SwingConstants.CENTER);

		JFrame frame = new JFrame("GLSwing - Custom Shader");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Timer drawCallUpdate = new Timer(500,
				event -> status.setText("Drawcalls: " + panel.getLastDrawCallCount()
						+ " | jeder Shaderwechsel beendet nur den betroffenen Batch"));
		drawCallUpdate.setRepeats(false);
		drawCallUpdate.start();
	}

	private static final class ShaderPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private ShaderProgram invertedShader;
		private ShaderProgram textureShader;
		private ShaderProgram textShader;
		private GLTexture texture;
		private GLFont font;

		@Override
		protected void initialize(GL3 gl) {
			invertedShader = ShaderProgram.fromSource(gl, VERTEX_SHADER, FRAGMENT_SHADER);
			textureShader = ShaderProgram.fromSource(gl,
					TEXTURED_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER);
			textShader = ShaderProgram.fromSource(gl,
					TEXTURED_VERTEX_SHADER, TEXT_FRAGMENT_SHADER);
			texture = GLTexture.create(gl, createTestImage(96));
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 22);
		}

		@Override
		protected void render(GLContext2D context) {
			context.clear(new Color(18, 25, 38));
			context.drawText(font, "FILLED", 55, 58, Color.WHITE);
			context.fillRect(55, 80, 250, 130, new Color(55, 125, 220));

			context.setFilledShader(invertedShader);
			context.setFilledUniform("uGamma", 2.2f);
			context.fillRect(355, 80, 250, 130, new Color(55, 125, 220));
			context.setFilledShader(invertedShader); // Same instance: deliberately no flush.
			context.fillTriangle(375, 190, 585, 190, 480, 95,
					new Color(225, 80, 90, 170));
			context.setFilledUniform("uGamma", 1f); // Flushes the previous custom batch.
			context.fillRect(655, 80, 250, 130, new Color(70, 210, 140, 180));

			context.useDefaultFilledShader();
			context.drawText(font, "TEXTURE", 55, 270, Color.WHITE);
			context.setTextureShader(textureShader);
			context.setTextureUniform("uGamma", 2.2f);
			context.drawTexture(texture, 55, 292, 250, 150);
			context.setTextureUniform("uGamma", 0.75f);
			context.drawTexture(texture, 355, 292, 250, 150,
					new Color(190, 225, 255));
			context.useDefaultTextureShader();
			context.drawTexture(texture, 655, 292, 250, 150);

			context.drawText(font, "TEXT", 55, 505, Color.WHITE);
			context.drawText(font, "Default MSDF", 55, 555,
					new Color(225, 235, 250));
			context.setTextShader(textShader);
			context.setTextUniform("uGamma", 1.65f);
			context.drawText(font, "Gamma 1.65", 355, 555,
					new Color(90, 215, 165));
			context.setTextUniform("uGamma", 0.75f);
			context.drawText(font, "Gamma 0.75", 655, 555,
					new Color(255, 190, 82));
			context.useDefaultTextShader();
		}

		@Override
		protected void disposeResources(GL3 gl) {
			if (invertedShader != null) {
				invertedShader.dispose(gl);
				invertedShader = null;
			}
			if (textureShader != null) {
				textureShader.dispose(gl);
				textureShader = null;
			}
			if (textShader != null) {
				textShader.dispose(gl);
				textShader = null;
			}
			if (texture != null) {
				texture.dispose(gl);
				texture = null;
			}
			if (font != null) {
				font.dispose(gl);
				font = null;
			}
		}
	}

	private static BufferedImage createTestImage(int size) {
		BufferedImage image = new BufferedImage(size, size,
				BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				Color base = x < size / 2
						? (y < size / 2 ? new Color(235, 70, 85)
								: new Color(70, 120, 235))
						: (y < size / 2 ? new Color(65, 205, 120)
								: new Color(245, 205, 65));
				int adjustment = (((x / 8) + (y / 8)) & 1) == 0 ? 18 : -18;
				image.setRGB(x, y, new Color(
						clamp(base.getRed() + adjustment),
						clamp(base.getGreen() + adjustment),
						clamp(base.getBlue() + adjustment)).getRGB());
			}
		}
		return image;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
