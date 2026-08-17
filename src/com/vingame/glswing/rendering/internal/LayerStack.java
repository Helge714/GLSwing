package com.vingame.glswing.rendering.internal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.GLBlurEffect;
import com.vingame.glswing.rendering.GLBlendMode;
import com.vingame.glswing.rendering.GLColorEffect;
import com.vingame.glswing.rendering.GLDropShadowEffect;
import com.vingame.glswing.rendering.GLInnerOutlineEffect;
import com.vingame.glswing.rendering.GLInnerShadowEffect;
import com.vingame.glswing.rendering.GLLayerEffect;
import com.vingame.glswing.rendering.GLOutlineEffect;
import com.vingame.glswing.rendering.GLRoundedClipEffect;
import com.vingame.glswing.rendering.GLShaderEffect;
import com.vingame.glswing.rendering.ShaderProgram;

/** Internal full-frame offscreen targets and compositor for nested layers. */
public final class LayerStack {

	private static final float[] TRANSPARENT = { 0, 0, 0, 0 };

	private final ShaderProgram compositeProgram;
	private final ShaderProgram blurProgram;
	private final ShaderProgram shadowProgram;
	private final ShaderProgram outlineProgram;
	private final ShaderProgram innerOutlineProgram;
	private final ShaderProgram innerShadowProgram;
	private final ShaderProgram colorProgram;
	private final ShaderProgram roundedClipProgram;
	private final List<LayerTarget> targets = new ArrayList<>();
	private final List<TargetState> parents = new ArrayList<>();
	private final List<PixelBounds> layerBounds = new ArrayList<>();
	private final EffectTarget[] effectTargets = new EffectTarget[3];
	private int compositeVao;
	private int width;
	private int height;
	private float logicalWidth;
	private float logicalHeight;
	private int samples;

	private LayerStack(ShaderProgram compositeProgram, ShaderProgram blurProgram,
			ShaderProgram shadowProgram, ShaderProgram outlineProgram,
			ShaderProgram innerOutlineProgram, ShaderProgram innerShadowProgram,
			ShaderProgram colorProgram, ShaderProgram roundedClipProgram,
			int compositeVao, int samples) {
		this.compositeProgram = compositeProgram;
		this.blurProgram = blurProgram;
		this.shadowProgram = shadowProgram;
		this.outlineProgram = outlineProgram;
		this.innerOutlineProgram = innerOutlineProgram;
		this.innerShadowProgram = innerShadowProgram;
		this.colorProgram = colorProgram;
		this.roundedClipProgram = roundedClipProgram;
		this.compositeVao = compositeVao;
		this.samples = samples;
	}

	public static LayerStack create(GL3 gl) {
		ShaderProgram composite = ShaderProgram.fromBuiltInResources(
				gl, "layer.vert", "layer.frag");
		ShaderProgram blur = null;
		ShaderProgram shadow = null;
		ShaderProgram outline = null;
		ShaderProgram innerOutline = null;
		ShaderProgram innerShadow = null;
		ShaderProgram color = null;
		ShaderProgram roundedClip = null;
		int[] vao = new int[1];
		try {
			blur = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_blur.frag");
			shadow = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_shadow.frag");
			outline = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_outline.frag");
			innerOutline = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_inner_outline.frag");
			innerShadow = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_inner_shadow.frag");
			color = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_color.frag");
			roundedClip = ShaderProgram.fromBuiltInResources(
					gl, "layer.vert", "layer_rounded_clip.frag");
			gl.glGenVertexArrays(1, vao, 0);
			if (vao[0] == 0)
				throw new IllegalStateException("Could not create the layer compositor VAO");
			int[] framebufferSamples = new int[1];
			gl.glGetIntegerv(GL3.GL_SAMPLES, framebufferSamples, 0);
			return new LayerStack(composite, blur, shadow, outline,
					innerOutline, innerShadow, color,
					roundedClip, vao[0], Math.max(1, framebufferSamples[0]));
		} catch (RuntimeException error) {
			if (vao[0] != 0)
				gl.glDeleteVertexArrays(1, vao, 0);
			if (roundedClip != null)
				roundedClip.dispose(gl);
			if (color != null)
				color.dispose(gl);
			if (innerShadow != null)
				innerShadow.dispose(gl);
			if (innerOutline != null)
				innerOutline.dispose(gl);
			if (outline != null)
				outline.dispose(gl);
			if (shadow != null)
				shadow.dispose(gl);
			if (blur != null)
				blur.dispose(gl);
			composite.dispose(gl);
			throw error;
		}
	}

	public void beginFrame(int width, int height, float logicalWidth, float logicalHeight) {
		if (depth() != 0)
			throw new IllegalStateException("A previous frame still has open layers");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Layer dimensions must be positive");
		if (!Float.isFinite(logicalWidth) || !Float.isFinite(logicalHeight)
				|| logicalWidth <= 0 || logicalHeight <= 0)
			throw new IllegalArgumentException("Logical layer dimensions must be positive and finite");
		this.width = width;
		this.height = height;
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
	}

	public void beginLayer(GL3 gl) {
		beginLayer(gl, 0, 0, logicalWidth, logicalHeight);
	}

	public void beginLayer(GL3 gl, float x, float y,
			float layerWidth, float layerHeight) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(layerWidth) || !Float.isFinite(layerHeight)
				|| layerWidth <= 0 || layerHeight <= 0)
			throw new IllegalArgumentException(
					"Layer bounds must be finite and have a positive size");
		int depth = depth();
		TargetState parent = TargetState.capture(gl);
		PixelBounds bounds = logicalBounds(x, y, layerWidth, layerHeight);
		LayerTarget target;
		if (depth == targets.size()) {
			target = LayerTarget.create(gl);
			targets.add(target);
		} else {
			target = targets.get(depth);
		}
		try {
			target.ensureSize(gl, width, height, samples);
			target.beginDrawing(gl, bounds);
		} catch (RuntimeException error) {
			parent.restore(gl);
			throw error;
		}
		parents.add(parent);
		layerBounds.add(bounds);
		configureLayerDrawing(gl);
	}

	/** Processes and composites the top layer, returning all issued effect draw calls. */
	public int endLayer(GL3 gl, float opacity, GLBlendMode blendMode,
			List<LayerEffectCommand> effects) {
		int depth = depth();
		if (depth == 0)
			throw new IllegalStateException("endLayer() requires a matching beginLayer()");

		LayerTarget target = targets.get(depth - 1);
		PixelBounds contentBounds = layerBounds.remove(depth - 1);
		float logicalPadding = 0;
		for (LayerEffectCommand effect : effects)
			logicalPadding += effect.effect().getPadding();
		PixelBounds processingBounds = contentBounds.expand(
				(int) Math.ceil(logicalPadding * width / logicalWidth),
				(int) Math.ceil(logicalPadding * height / logicalHeight),
				width, height);
		target.resolve(gl, contentBounds, processingBounds);
		TargetState parent = parents.remove(depth - 1);
		TextureSource input = target;
		int drawCalls = 0;
		try {
			if (effects.isEmpty()) {
				prepareFinalPass(gl, parent, blendMode, processingBounds);
				drawProgram(gl, input, compositeProgram, opacity, Map.of());
				drawCalls++;
				return drawCalls;
			}

			for (int index = 0; index < effects.size(); index++) {
				LayerEffectCommand command = effects.get(index);
				GLLayerEffect effect = command.effect();
				boolean finalEffect = index == effects.size() - 1;
				if (effect instanceof GLBlurEffect blur) {
					EffectTarget horizontal = effectTargetExcluding(gl, input);
					prepareIntermediatePass(gl, horizontal, processingBounds);
					drawBlur(gl, input, blur.getRadius(), 1, 0, 1);
					drawCalls++;

					if (finalEffect) {
						prepareFinalPass(gl, parent, blendMode, processingBounds);
						drawBlur(gl, horizontal, blur.getRadius(), 0, 1, opacity);
					} else {
						EffectTarget output = effectTargetExcluding(gl, horizontal);
						prepareIntermediatePass(gl, output, processingBounds);
						drawBlur(gl, horizontal, blur.getRadius(), 0, 1, 1);
						input = output;
					}
					drawCalls++;
				} else if (effect instanceof GLDropShadowEffect shadow) {
					TextureSource source = input;
					EffectTarget horizontal = effectTargetExcluding(gl, source);
					prepareIntermediatePass(gl, horizontal, processingBounds);
					drawBlur(gl, source, shadow.getRadius(), 1, 0, 1);
					drawCalls++;

					if (finalEffect) {
						prepareFinalPass(gl, parent, blendMode, processingBounds);
						drawShadow(gl, source, horizontal, shadow, opacity);
					} else {
						EffectTarget output = effectTargetExcluding(
								gl, source, horizontal);
						prepareIntermediatePass(gl, output, processingBounds);
						drawShadow(gl, source, horizontal, shadow, 1);
						input = output;
					}
					drawCalls++;
				} else if (effect instanceof GLInnerShadowEffect shadow) {
					TextureSource source = input;
					EffectTarget horizontal = effectTargetExcluding(gl, source);
					prepareIntermediatePass(gl, horizontal, processingBounds);
					drawBlur(gl, source, shadow.getRadius(), 1, 0, 1);
					drawCalls++;

					if (finalEffect) {
						prepareFinalPass(gl, parent, blendMode, processingBounds);
						drawInnerShadow(gl, source, horizontal, shadow, opacity);
					} else {
						EffectTarget output = effectTargetExcluding(
								gl, source, horizontal);
						prepareIntermediatePass(gl, output, processingBounds);
						drawInnerShadow(gl, source, horizontal, shadow, 1);
						input = output;
					}
					drawCalls++;
				} else {
					if (finalEffect) {
						prepareFinalPass(gl, parent, blendMode, processingBounds);
						drawEffect(gl, input, command, opacity, contentBounds);
					} else {
						EffectTarget output = effectTargetExcluding(gl, input);
						prepareIntermediatePass(gl, output, processingBounds);
						drawEffect(gl, input, command, 1, contentBounds);
						input = output;
					}
					drawCalls++;
				}
			}
			return drawCalls;
		} finally {
			parent.restore(gl);
			configureBatchDrawing(gl);
		}
	}

	public int depth() {
		return parents.size();
	}

	/** Restores the frame's original render target while discarding open layers. */
	public void abortFrame(GL3 gl) {
		if (parents.isEmpty())
			return;
		parents.get(0).restore(gl);
		parents.clear();
		layerBounds.clear();
		configureBatchDrawing(gl);
	}

	public void dispose(GL3 gl) {
		if (depth() != 0)
			throw new IllegalStateException("Cannot dispose while layers are open");
		for (LayerTarget target : targets)
			target.dispose(gl);
		targets.clear();
		for (int index = 0; index < effectTargets.length; index++) {
			if (effectTargets[index] != null) {
				effectTargets[index].dispose(gl);
				effectTargets[index] = null;
			}
		}
		if (compositeVao != 0) {
			gl.glDeleteVertexArrays(1, new int[] { compositeVao }, 0);
			compositeVao = 0;
		}
		compositeProgram.dispose(gl);
		blurProgram.dispose(gl);
		shadowProgram.dispose(gl);
		outlineProgram.dispose(gl);
		innerOutlineProgram.dispose(gl);
		innerShadowProgram.dispose(gl);
		colorProgram.dispose(gl);
		roundedClipProgram.dispose(gl);
	}

	private EffectTarget effectTarget(GL3 gl, int index) {
		EffectTarget target = effectTargets[index];
		if (target == null) {
			target = EffectTarget.create(gl);
			effectTargets[index] = target;
		}
		target.ensureSize(gl, width, height);
		return target;
	}

	private EffectTarget effectTargetExcluding(GL3 gl, TextureSource... excluded) {
		for (int index = 0; index < effectTargets.length; index++) {
			EffectTarget candidate = effectTarget(gl, index);
			boolean available = true;
			for (TextureSource source : excluded) {
				if (candidate == source) {
					available = false;
					break;
				}
			}
			if (available)
				return candidate;
		}
		throw new IllegalStateException("No free layer effect target");
	}

	private static void prepareIntermediatePass(GL3 gl, EffectTarget output,
			PixelBounds bounds) {
		output.beginDrawing(gl, bounds);
		gl.glDisable(GL.GL_BLEND);
	}

	private static void prepareFinalPass(GL3 gl, TargetState parent,
			GLBlendMode blendMode, PixelBounds bounds) {
		parent.restore(gl);
		applyScissor(gl, bounds.intersect(parent.clipBounds()));
		gl.glEnable(GL.GL_BLEND);
		configureCompositeBlend(gl, blendMode);
	}

	private PixelBounds logicalBounds(float x, float y,
			float layerWidth, float layerHeight) {
		float scaleX = width / logicalWidth;
		float scaleY = height / logicalHeight;
		int minimumX = clamp((int) Math.floor(x * scaleX), 0, width);
		int maximumX = clamp((int) Math.ceil((x + layerWidth) * scaleX), 0, width);
		int top = clamp((int) Math.floor(y * scaleY), 0, height);
		int bottom = clamp((int) Math.ceil((y + layerHeight) * scaleY), 0, height);
		return new PixelBounds(minimumX, height - bottom,
				Math.max(0, maximumX - minimumX), Math.max(0, bottom - top));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static void applyScissor(GL3 gl, PixelBounds bounds) {
		gl.glEnable(GL.GL_SCISSOR_TEST);
		gl.glScissor(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	private static void clearFramebuffer(GL3 gl, int framebuffer,
			int width, int height, PixelBounds bounds) {
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
		gl.glViewport(0, 0, width, height);
		applyScissor(gl, bounds);
		gl.glClearBufferfv(GL3.GL_COLOR, 0, TRANSPARENT, 0);
	}

	private void drawBlur(GL3 gl, TextureSource input, float radius,
			float directionX, float directionY, float opacity) {
		blurProgram.use(gl);
		blurProgram.setFloat(gl, "uRadius", radius);
		blurProgram.setVector2(gl, "uDirection", directionX, directionY);
		drawProgram(gl, input, blurProgram, opacity, Map.of());
	}

	private void drawShadow(GL3 gl, TextureSource source,
			TextureSource horizontallyBlurred, GLDropShadowEffect shadow,
			float opacity) {
		shadowProgram.use(gl);
		shadowProgram.setVector2(gl, "uOffset", shadow.getOffsetX(), shadow.getOffsetY());
		shadowProgram.setFloat(gl, "uRadius", shadow.getRadius());
		shadowProgram.setVector2(gl, "uDirection", 0, 1);
		setColor(shadowProgram, gl, "uShadowColor", shadow.getColor());
		drawProgram(gl, source, shadowProgram, opacity, Map.of(),
				horizontallyBlurred, "uBlurTexture");
	}

	private void drawInnerShadow(GL3 gl, TextureSource source,
			TextureSource horizontallyBlurred, GLInnerShadowEffect shadow,
			float opacity) {
		innerShadowProgram.use(gl);
		innerShadowProgram.setVector2(
				gl, "uOffset", shadow.getOffsetX(), shadow.getOffsetY());
		innerShadowProgram.setFloat(gl, "uRadius", shadow.getRadius());
		innerShadowProgram.setVector2(gl, "uDirection", 0, 1);
		setColor(innerShadowProgram, gl, "uShadowColor", shadow.getColor());
		drawProgram(gl, source, innerShadowProgram, opacity, Map.of(),
				horizontallyBlurred, "uBlurTexture");
	}

	private void drawEffect(GL3 gl, TextureSource input,
			LayerEffectCommand command, float opacity, PixelBounds contentBounds) {
		GLLayerEffect effect = command.effect();
		ShaderProgram program;
		if (effect instanceof GLOutlineEffect outline) {
			program = outlineProgram;
			program.use(gl);
			program.setFloat(gl, "uWidth", outline.getWidth());
			setColor(program, gl, "uOutlineColor", outline.getColor());
		} else if (effect instanceof GLInnerOutlineEffect outline) {
			program = innerOutlineProgram;
			program.use(gl);
			program.setFloat(gl, "uWidth", outline.getWidth());
			setColor(program, gl, "uOutlineColor", outline.getColor());
		} else if (effect instanceof GLColorEffect color) {
			program = colorProgram;
			program.use(gl);
			program.setFloat(gl, "uBrightness", color.getBrightness());
			program.setFloat(gl, "uContrast", color.getContrast());
			program.setFloat(gl, "uSaturation", color.getSaturation());
			program.setFloat(gl, "uGamma", color.getGamma());
			program.setFloat(gl, "uInvert", color.getInvert());
			Color tint = color.getTint();
			program.setVector3(gl, "uTint", tint.getRed() / 255f,
					tint.getGreen() / 255f, tint.getBlue() / 255f);
			program.setFloat(gl, "uTintStrength", color.getTintStrength());
		} else if (effect instanceof GLRoundedClipEffect clip) {
			program = roundedClipProgram;
			program.use(gl);
			program.setVector4(gl, "uClipBounds",
					contentBounds.x, contentBounds.y,
					contentBounds.width, contentBounds.height);
			program.setVector4(gl, "uCornerRadii",
					clip.radii().topLeft(), clip.radii().topRight(),
					clip.radii().bottomRight(), clip.radii().bottomLeft());
		} else if (effect instanceof GLShaderEffect custom) {
			program = custom.getShader();
			program.use(gl);
		} else {
			throw new IllegalArgumentException("Unsupported layer effect: " + effect);
		}
		drawProgram(gl, input, program, opacity, command.uniforms());
	}

	private void drawProgram(GL3 gl, TextureSource input, ShaderProgram program,
			float opacity, Map<String, ShaderUniformValue> userUniforms) {
		drawProgram(gl, input, program, opacity, userUniforms, null, null);
	}

	private void drawProgram(GL3 gl, TextureSource input, ShaderProgram program,
			float opacity, Map<String, ShaderUniformValue> userUniforms,
			TextureSource secondaryInput, String secondaryUniform) {
		program.use(gl);
		program.setInteger(gl, "uTexture", 0);
		if (secondaryInput != null)
			program.setInteger(gl, secondaryUniform, 1);
		program.setFloat(gl, "uOpacity", opacity);
		if (program.hasUniform(gl, "uResolution"))
			program.setVector2(gl, "uResolution", width, height);
		if (program.hasUniform(gl, "uTexelSize"))
			program.setVector2(gl, "uTexelSize",
					1f / width, 1f / height);
		if (program.hasUniform(gl, "uLogicalResolution"))
			program.setVector2(gl, "uLogicalResolution", logicalWidth, logicalHeight);
		if (program.hasUniform(gl, "uDeviceScale"))
			program.setVector2(gl, "uDeviceScale",
					width / logicalWidth, height / logicalHeight);
		for (Map.Entry<String, ShaderUniformValue> uniform : userUniforms.entrySet())
			uniform.getValue().apply(gl, program, uniform.getKey());
		input.bindTexture(gl, 0);
		if (secondaryInput != null)
			secondaryInput.bindTexture(gl, 1);
		gl.glBindVertexArray(compositeVao);
		gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
		gl.glBindVertexArray(0);
		if (secondaryInput != null) {
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		}
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		gl.glUseProgram(0);
	}

	private static void setColor(ShaderProgram program, GL3 gl,
			String uniform, Color color) {
		program.setVector4(gl, uniform, color.getRed() / 255f,
				color.getGreen() / 255f, color.getBlue() / 255f,
				color.getAlpha() / 255f);
	}

	private void configureBatchDrawing(GL3 gl) {
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendEquationSeparate(GL.GL_FUNC_ADD, GL.GL_FUNC_ADD);
		if (depth() > 0) {
			configureLayerDrawing(gl);
		} else {
			gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		}
	}

	private static void configureLayerDrawing(GL3 gl) {
		// Batch shaders output straight alpha. These factors store the layer in
		// premultiplied form, which makes nested compositing mathematically stable.
		gl.glBlendEquationSeparate(GL.GL_FUNC_ADD, GL.GL_FUNC_ADD);
		gl.glBlendFuncSeparate(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA,
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static void configureCompositeBlend(GL3 gl, GLBlendMode blendMode) {
		gl.glBlendEquationSeparate(GL.GL_FUNC_ADD, GL.GL_FUNC_ADD);
		switch (blendMode) {
		case NORMAL -> gl.glBlendFuncSeparate(
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA,
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
		case ADD -> gl.glBlendFuncSeparate(
				GL.GL_ONE, GL.GL_ONE,
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
		case MULTIPLY -> gl.glBlendFuncSeparate(
				GL.GL_DST_COLOR, GL.GL_ONE_MINUS_SRC_ALPHA,
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
		case SCREEN -> gl.glBlendFuncSeparate(
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_COLOR,
				GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
		case SUBTRACT -> {
			gl.glBlendEquationSeparate(GL.GL_FUNC_REVERSE_SUBTRACT, GL.GL_FUNC_ADD);
			gl.glBlendFuncSeparate(
					GL.GL_ONE, GL.GL_ONE,
					GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
		}
		case ERASE -> gl.glBlendFuncSeparate(
				GL.GL_ZERO, GL.GL_ONE_MINUS_SRC_ALPHA,
				GL.GL_ZERO, GL.GL_ONE_MINUS_SRC_ALPHA);
		}
	}

	/** Integer framebuffer rectangle using OpenGL's bottom-left origin. */
	private static final class PixelBounds {
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		private PixelBounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = Math.max(0, width);
			this.height = Math.max(0, height);
		}

		static PixelBounds full(int width, int height) {
			return new PixelBounds(0, 0, width, height);
		}

		PixelBounds expand(int xPadding, int yPadding,
				int maximumWidth, int maximumHeight) {
			int minimumX = Math.max(0, x - xPadding);
			int minimumY = Math.max(0, y - yPadding);
			int maximumX = Math.min(maximumWidth, x + width + xPadding);
			int maximumY = Math.min(maximumHeight, y + height + yPadding);
			return fromEdges(minimumX, minimumY, maximumX, maximumY);
		}

		PixelBounds intersect(PixelBounds other) {
			int minimumX = Math.max(x, other.x);
			int minimumY = Math.max(y, other.y);
			int maximumX = Math.min(x + width, other.x + other.width);
			int maximumY = Math.min(y + height, other.y + other.height);
			return fromEdges(minimumX, minimumY, maximumX, maximumY);
		}

		PixelBounds union(PixelBounds other) {
			if (isEmpty())
				return other;
			if (other == null || other.isEmpty())
				return this;
			int minimumX = Math.min(x, other.x);
			int minimumY = Math.min(y, other.y);
			int maximumX = Math.max(x + width, other.x + other.width);
			int maximumY = Math.max(y + height, other.y + other.height);
			return fromEdges(minimumX, minimumY, maximumX, maximumY);
		}

		boolean isEmpty() {
			return width == 0 || height == 0;
		}

		private static PixelBounds fromEdges(
				int minimumX, int minimumY, int maximumX, int maximumY) {
			return new PixelBounds(minimumX, minimumY,
					Math.max(0, maximumX - minimumX),
					Math.max(0, maximumY - minimumY));
		}
	}

	private static final class TargetState {
		private final int drawFramebuffer;
		private final int readFramebuffer;
		private final int[] viewport;
		private final boolean scissorEnabled;
		private final int[] scissor;

		private TargetState(int drawFramebuffer, int readFramebuffer, int[] viewport,
				boolean scissorEnabled, int[] scissor) {
			this.drawFramebuffer = drawFramebuffer;
			this.readFramebuffer = readFramebuffer;
			this.viewport = viewport;
			this.scissorEnabled = scissorEnabled;
			this.scissor = scissor;
		}

		static TargetState capture(GL3 gl) {
			int[] draw = new int[1];
			int[] read = new int[1];
			int[] viewport = new int[4];
			int[] scissor = new int[4];
			gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, draw, 0);
			gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, read, 0);
			gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
			gl.glGetIntegerv(GL.GL_SCISSOR_BOX, scissor, 0);
			return new TargetState(draw[0], read[0], viewport,
					gl.glIsEnabled(GL.GL_SCISSOR_TEST), scissor);
		}

		void restore(GL3 gl) {
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, readFramebuffer);
			gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
			if (scissorEnabled) {
				gl.glEnable(GL.GL_SCISSOR_TEST);
				gl.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
			} else {
				gl.glDisable(GL.GL_SCISSOR_TEST);
			}
		}

		PixelBounds clipBounds() {
			return scissorEnabled
					? new PixelBounds(scissor[0], scissor[1], scissor[2], scissor[3])
					: new PixelBounds(viewport[0], viewport[1], viewport[2], viewport[3]);
		}
	}

	private interface TextureSource {
		void bindTexture(GL3 gl, int unit);
	}

	private static final class EffectTarget implements TextureSource {
		private int framebuffer;
		private int colorTexture;
		private int width;
		private int height;
		private PixelBounds dirtyBounds;

		static EffectTarget create(GL3 gl) {
			int[] framebuffer = new int[1];
			int[] texture = new int[1];
			gl.glGenFramebuffers(1, framebuffer, 0);
			gl.glGenTextures(1, texture, 0);
			if (framebuffer[0] == 0 || texture[0] == 0) {
				if (texture[0] != 0)
					gl.glDeleteTextures(1, texture, 0);
				if (framebuffer[0] != 0)
					gl.glDeleteFramebuffers(1, framebuffer, 0);
				throw new IllegalStateException("Could not create a layer effect target");
			}
			EffectTarget target = new EffectTarget();
			target.framebuffer = framebuffer[0];
			target.colorTexture = texture[0];
			return target;
		}

		void ensureSize(GL3 gl, int requestedWidth, int requestedHeight) {
			if (width == requestedWidth && height == requestedHeight)
				return;
			width = requestedWidth;
			height = requestedHeight;
			gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
			gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height,
					0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
			gl.glFramebufferTexture2D(GL3.GL_DRAW_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0,
					GL.GL_TEXTURE_2D, colorTexture, 0);
			gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
			checkComplete(gl, "layer effect framebuffer");
			clearFramebuffer(gl, framebuffer, width, height,
					PixelBounds.full(width, height));
			dirtyBounds = null;
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		}

		void beginDrawing(GL3 gl, PixelBounds bounds) {
			if (dirtyBounds != null)
				clearFramebuffer(gl, framebuffer, width, height, dirtyBounds);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, framebuffer);
			gl.glViewport(0, 0, width, height);
			applyScissor(gl, bounds);
			dirtyBounds = bounds;
		}

		@Override
		public void bindTexture(GL3 gl, int unit) {
			gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
			gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture);
		}

		void dispose(GL3 gl) {
			if (colorTexture != 0) {
				gl.glDeleteTextures(1, new int[] { colorTexture }, 0);
				colorTexture = 0;
			}
			if (framebuffer != 0) {
				gl.glDeleteFramebuffers(1, new int[] { framebuffer }, 0);
				framebuffer = 0;
			}
		}

		private static void checkComplete(GL3 gl, String name) {
			int status = gl.glCheckFramebufferStatus(GL3.GL_DRAW_FRAMEBUFFER);
			if (status != GL3.GL_FRAMEBUFFER_COMPLETE)
				throw new IllegalStateException(name + " is incomplete: 0x"
						+ Integer.toHexString(status));
		}
	}

	private static final class LayerTarget implements TextureSource {
		private int drawFramebuffer;
		private int resolveFramebuffer;
		private int colorTexture;
		private int multisampleColorBuffer;
		private int width;
		private int height;
		private int samples;
		private PixelBounds dirtyBounds;

		static LayerTarget create(GL3 gl) {
			int[] ids = new int[2];
			gl.glGenFramebuffers(2, ids, 0);
			int drawFramebuffer = ids[0];
			int resolveFramebuffer = ids[1];
			int[] texture = new int[1];
			gl.glGenTextures(1, texture, 0);
			if (drawFramebuffer == 0 || resolveFramebuffer == 0 || texture[0] == 0) {
				if (texture[0] != 0)
					gl.glDeleteTextures(1, texture, 0);
				gl.glDeleteFramebuffers(2, ids, 0);
				throw new IllegalStateException("Could not create an offscreen layer target");
			}
			LayerTarget target = new LayerTarget();
			target.drawFramebuffer = drawFramebuffer;
			target.resolveFramebuffer = resolveFramebuffer;
			target.colorTexture = texture[0];
			return target;
		}

		void ensureSize(GL3 gl, int requestedWidth, int requestedHeight, int requestedSamples) {
			if (width == requestedWidth && height == requestedHeight
					&& samples == requestedSamples)
				return;
			width = requestedWidth;
			height = requestedHeight;
			samples = requestedSamples;

			gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
			gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height,
					0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);

			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
			gl.glFramebufferTexture2D(GL3.GL_DRAW_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0,
					GL.GL_TEXTURE_2D, colorTexture, 0);
			gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
			checkComplete(gl, "layer resolve framebuffer");

			if (samples > 1) {
				if (multisampleColorBuffer == 0) {
					int[] renderbuffer = new int[1];
					gl.glGenRenderbuffers(1, renderbuffer, 0);
					multisampleColorBuffer = renderbuffer[0];
				}
				gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, multisampleColorBuffer);
				gl.glRenderbufferStorageMultisample(
						GL.GL_RENDERBUFFER, samples, GL.GL_RGBA8, width, height);
				gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
				gl.glFramebufferRenderbuffer(GL3.GL_DRAW_FRAMEBUFFER,
						GL3.GL_COLOR_ATTACHMENT0, GL.GL_RENDERBUFFER,
						multisampleColorBuffer);
				gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
				checkComplete(gl, "multisampled layer framebuffer");
			} else {
				gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
				gl.glFramebufferTexture2D(GL3.GL_DRAW_FRAMEBUFFER,
						GL3.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, colorTexture, 0);
				gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
				checkComplete(gl, "layer framebuffer");
			}

			gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, 0);
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
			PixelBounds fullBounds = PixelBounds.full(width, height);
			clearFramebuffer(gl, drawFramebuffer, width, height, fullBounds);
			if (samples > 1)
				clearFramebuffer(gl, resolveFramebuffer, width, height, fullBounds);
			dirtyBounds = null;
		}

		void beginDrawing(GL3 gl, PixelBounds bounds) {
			PixelBounds clearBounds = dirtyBounds == null
					? bounds : dirtyBounds.union(bounds);
			clearFramebuffer(gl, drawFramebuffer, width, height, clearBounds);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
			gl.glViewport(0, 0, width, height);
			applyScissor(gl, bounds);
		}

		void resolve(GL3 gl, PixelBounds contentBounds,
				PixelBounds processingBounds) {
			if (samples <= 1) {
				dirtyBounds = contentBounds;
				return;
			}
			PixelBounds clearBounds = dirtyBounds == null
					? processingBounds : dirtyBounds.union(processingBounds);
			clearFramebuffer(gl, resolveFramebuffer, width, height, clearBounds);
			gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, drawFramebuffer);
			gl.glReadBuffer(GL3.GL_COLOR_ATTACHMENT0);
			gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
			gl.glDrawBuffers(1, new int[] { GL3.GL_COLOR_ATTACHMENT0 }, 0);
			applyScissor(gl, contentBounds);
			gl.glBlitFramebuffer(contentBounds.x, contentBounds.y,
					contentBounds.x + contentBounds.width,
					contentBounds.y + contentBounds.height,
					contentBounds.x, contentBounds.y,
					contentBounds.x + contentBounds.width,
					contentBounds.y + contentBounds.height,
					GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
			dirtyBounds = contentBounds;
		}

		@Override
		public void bindTexture(GL3 gl, int unit) {
			gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
			gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture);
		}

		void dispose(GL3 gl) {
			if (multisampleColorBuffer != 0) {
				gl.glDeleteRenderbuffers(1, new int[] { multisampleColorBuffer }, 0);
				multisampleColorBuffer = 0;
			}
			if (colorTexture != 0) {
				gl.glDeleteTextures(1, new int[] { colorTexture }, 0);
				colorTexture = 0;
			}
			if (drawFramebuffer != 0 || resolveFramebuffer != 0) {
				gl.glDeleteFramebuffers(2,
						new int[] { drawFramebuffer, resolveFramebuffer }, 0);
				drawFramebuffer = 0;
				resolveFramebuffer = 0;
			}
		}

		private static void checkComplete(GL3 gl, String name) {
			int status = gl.glCheckFramebufferStatus(GL3.GL_DRAW_FRAMEBUFFER);
			if (status != GL3.GL_FRAMEBUFFER_COMPLETE)
				throw new IllegalStateException(name + " is incomplete: 0x"
						+ Integer.toHexString(status));
		}
	}
}
