package com.vingame.glswing.test;

import java.awt.Color;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.ShaderProgram;

/** GPU integration checks for custom MSDF text shaders and user uniforms. */
public final class TextShaderGpuSelfTest {

	private static final String VERTEX_SHADER = """
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

	private static final String FRAGMENT_SHADER = """
			#version 330 core
			in vec2 vTexCoord;
			in vec4 vColor;
			uniform sampler2D uTexture;
			uniform float uMsdfPixelRange;
			uniform float uGamma;
			out vec4 fragColor;
			void main() {
			    vec3 sampleValue = texture(uTexture, vTexCoord).rgb;
			    float distance = max(min(sampleValue.r, sampleValue.g),
			            min(max(sampleValue.r, sampleValue.g), sampleValue.b)) - 0.5;
			    vec2 unitRange = vec2(uMsdfPixelRange) / vec2(textureSize(uTexture, 0));
			    vec2 screenSize = vec2(1.0) / max(fwidth(vTexCoord), vec2(0.000001));
			    float pixelRange = max(0.5 * dot(unitRange, screenSize), 1.0);
			    float coverage = clamp(distance * pixelRange + 0.5, 0.0, 1.0);
			    fragColor = vec4(vColor.rgb, vColor.a * pow(coverage, 1.0 / uGamma));
			}
			""";

	private TextShaderGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, 240, 100);
		drawable.display();

		GLContext glContext = drawable.getContext();
		if (glContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make the offscreen OpenGL context current");

		GLRenderer2D renderer = null;
		GLFont font = null;
		ShaderProgram shader = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, 240, 100);
			renderer = GLRenderer2D.create(gl);
			font = new GLFont(GLFont.SANS_SERIF, GLFont.BOLD, 28);
			shader = ShaderProgram.fromSource(gl, VERTEX_SHADER, FRAGMENT_SHADER);

			GLContext2D context = renderer.beginFrame(gl, 240, 100,
					Projection2D.TOP_LEFT_PIXELS.create(240, 100));
			context.clear(Color.BLACK);
			context.setTextShader(shader);
			expectFailure(() -> context.setTextUniform("uTexture", 0),
					"renderer-managed sampler uniform");
			expectFailure(() -> context.setTextUniform("uGamma", 1),
					"wrong text uniform type");
			context.setTextUniform("uGamma", 1.4f);
			context.drawText(font, "A", 15, 55, Color.WHITE);
			context.setTextUniform("uGamma", 0.8f);
			context.drawText(font, "B", 80, 55, Color.CYAN);
			context.useDefaultTextShader();
			context.drawText(font, "C", 145, 55, Color.ORANGE);
			renderer.endFrame();

			if (renderer.drawCallCount() != 3)
				throw new AssertionError("Expected 3 text draw calls, got " + renderer.drawCallCount());
			int error = gl.glGetError();
			if (error != GL.GL_NO_ERROR)
				throw new AssertionError("OpenGL error after custom text rendering: 0x"
						+ Integer.toHexString(error));
			System.out.println("Text shader GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (font != null)
				font.dispose(gl);
			if (shader != null)
				shader.dispose(gl);
			if (renderer != null)
				renderer.dispose(gl);
			glContext.release();
			drawable.destroy();
		}
	}

	private static void expectFailure(Runnable action, String description) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("Expected rejection of " + description);
	}
}
