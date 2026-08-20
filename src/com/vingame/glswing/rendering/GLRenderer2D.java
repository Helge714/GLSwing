package com.vingame.glswing.rendering;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.batch.FilledBatch;
import com.vingame.glswing.rendering.batch.SpriteBatch;
import com.vingame.glswing.rendering.batch.TextBatch;
import com.vingame.glswing.rendering.batch.TextureBatch;
import com.vingame.glswing.rendering.batch.UIQuadBatch;
import com.vingame.glswing.rendering.internal.LayerStack;
import com.vingame.glswing.rendering.internal.LayerEffectCommand;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.PickFrameRecords;
import com.vingame.glswing.rendering.internal.PersistentPickRecords;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;
import com.vingame.glswing.rendering.internal.TexturedVertexFormat;
import com.vingame.glswing.rendering.internal.UIQuadInstanceFormat;
import com.vingame.glswing.rendering.internal.VertexModifier;

/** Owns the GPU rendering state and batches for one GLSwing panel. */
public final class GLRenderer2D {

	private static final int MAX_ADAPTIVE_ICON_UPLOADS_PER_FRAME = 1;
	private static final Runnable NO_REPAINT_REQUEST = () -> { };

	private final FilledBatch filledBatch;
	private final TextureBatch textureBatch;
	private final TextBatch textBatch;
	private final UIQuadBatch uiQuadBatch;
	private final SpriteBatch spriteBatch;
	private final PickFramebuffer picking;
	private final LayerStack layers;
	private final GLTextureManager textureManager = new GLTextureManager();
	private final PickFrameRecords pickRecords = new PickFrameRecords();
	private final Set<GLRenderTarget> renderTargets =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<GLSpriteBuffer, GLStaticSpriteResource> staticSpriteResources =
			new IdentityHashMap<>();
	private final List<RenderTargetState> renderTargetStack = new ArrayList<>();
	private final GLContext2D context;
	private GL3 gl;
	private final GLTransform projection = new GLTransform();
	private final GLTransform worldToScreen = new GLTransform();
	private final GLTransform screenToWorld = new GLTransform();
	private float[] projectionMatrix;
	private float logicalWidth;
	private float logicalHeight;
	private float colorDeviceScaleX = 1;
	private float colorDeviceScaleY = 1;
	private boolean frameActive;
	private int drawCallCount;
	private BatchType activeBatch = BatchType.NONE;
	private ShaderProgram filledShaderOverride;
	private ShaderProgram textureShaderOverride;
	private ShaderProgram textShaderOverride;
	private ShaderProgram uiShaderOverride;
	private ShaderProgram spriteShaderOverride;
	private final Map<String, ShaderUniformValue> filledUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> textureUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> textUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> uiUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> spriteUniforms = new LinkedHashMap<>();
	private GLDepthState depthState = GLDepthState.DISABLED;
	private float depth = 0.5f;
	private GLLighting2D lighting = GLLighting2D.DEFAULT;
	private GLDepthState pendingSpriteDepthState = GLDepthState.DISABLED;
	private GLAlphaMode pendingSpriteAlphaMode = GLAlphaMode.OPAQUE;
	private final GLTransform transform = new GLTransform();
	private final GLTransform pickTransform = new GLTransform();
	private GLTransform recordingReplayBase;
	private final GLTransform recordingLocalTransform = new GLTransform();
	private final GLTransform recordingParameterTransform = new GLTransform();
	private final GLTransform recordingVertexTransform = new GLTransform();
	private final GLTransform recordingColorTransform = new GLTransform();
	private final VertexModifier recordingVertexModifier = new VertexModifier();
	private final VertexModifier recordingColorModifier = new VertexModifier();
	private ReplayParameterState[] replayParameterStack = new ReplayParameterState[8];
	private int replayParameterDepth;
	private float replayTintRed = 1;
	private float replayTintGreen = 1;
	private float replayTintBlue = 1;
	private float replayTintAlpha = 1;
	private boolean replayColorOverride;
	private float replayColorRed;
	private float replayColorGreen;
	private float replayColorBlue;
	private float replayColorAlpha;
	private float replayOpacity = 1;
	private boolean colorRenderingEnabled;
	private boolean pickingRenderingEnabled;
	private boolean rebuildingPickingFrame;
	private float texturePickAlphaThreshold;
	private float[] redrawBounds;
	private final List<ScissorState> clipStack = new ArrayList<>();
	private final List<LayerPathState> layerPathStates = new ArrayList<>();
	private float layerMinimumX;
	private float layerMinimumY;
	private float layerMaximumX;
	private float layerMaximumY;
	private Runnable asynchronousResourceRepaint = NO_REPAINT_REQUEST;
	private int adaptiveIconUploads;

	private GLRenderer2D(FilledBatch filledBatch, TextureBatch textureBatch,
			TextBatch textBatch, UIQuadBatch uiQuadBatch, SpriteBatch spriteBatch,
			PickFramebuffer picking, LayerStack layers) {
		this.filledBatch = filledBatch;
		this.textureBatch = textureBatch;
		this.textBatch = textBatch;
		this.uiQuadBatch = uiQuadBatch;
		this.spriteBatch = spriteBatch;
		this.picking = picking;
		this.layers = layers;
		context = new GLContext2D(this);
	}

	public static GLRenderer2D create(GL3 gl) {
		FilledBatch filledBatch = FilledBatch.create(gl);
		TextureBatch textureBatch = null;
		TextBatch textBatch = null;
		UIQuadBatch uiQuadBatch = null;
		SpriteBatch spriteBatch = null;
		PickFramebuffer picking = null;
		LayerStack layers = null;
		try {
			textureBatch = TextureBatch.create(gl);
			textBatch = TextBatch.create(gl);
			uiQuadBatch = UIQuadBatch.create(gl);
			spriteBatch = SpriteBatch.create(gl);
			picking = PickFramebuffer.create(gl);
			layers = LayerStack.create(gl);
			return new GLRenderer2D(filledBatch, textureBatch, textBatch,
					uiQuadBatch, spriteBatch, picking, layers);
		} catch (RuntimeException error) {
			if (layers != null)
				layers.dispose(gl);
			if (picking != null)
				picking.dispose(gl);
			if (spriteBatch != null)
				spriteBatch.dispose(gl);
			if (textBatch != null)
				textBatch.dispose(gl);
			if (uiQuadBatch != null)
				uiQuadBatch.dispose(gl);
			if (textureBatch != null)
				textureBatch.dispose(gl);
			filledBatch.dispose(gl);
			throw error;
		}
	}

	public GLContext2D beginFrame(GL3 gl, float logicalWidth, float logicalHeight,
			GLTransform initialProjection) {
		return beginFrame(gl, logicalWidth, logicalHeight, initialProjection, true);
	}

	/** Starts a frame while optionally retaining the previous picking surface. */
	public GLContext2D beginFrame(GL3 gl, float logicalWidth, float logicalHeight,
			GLTransform initialProjection, boolean rebuildPicking) {
		if (frameActive)
			throw new IllegalStateException("A renderer frame is already active");
		if (initialProjection == null)
			throw new IllegalArgumentException("Initial projection must not be null");

		this.gl = gl;
		if (!renderTargetStack.isEmpty())
			throw new IllegalStateException(
					"A previous frame still has open render targets");
		releaseClosedRenderTargets(gl);
		releaseClosedSpriteBuffers(gl);
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
		int[] colorViewport = new int[4];
		gl.glGetIntegerv(GL.GL_VIEWPORT, colorViewport, 0);
		colorDeviceScaleX = Math.max(0.0001f, colorViewport[2] / logicalWidth);
		colorDeviceScaleY = Math.max(0.0001f, colorViewport[3] / logicalHeight);
		projection.setTransform(initialProjection);
		projectionMatrix = projection.toMatrix4();
		updateScreenTransforms();
		if (rebuildPicking)
			pickRecords.beginFrame();
		picking.pollSnapshots(gl);
		rebuildingPickingFrame = rebuildPicking;
		picking.beginFrame(gl, Math.max(1, Math.round(logicalWidth)),
				Math.max(1, Math.round(logicalHeight)), rebuildPicking);
		// Color layers must match the physical drawable pixels. Projection and
		// picking intentionally continue to use logical Swing coordinates.
		layers.beginFrame(Math.max(1, colorViewport[2]), Math.max(1, colorViewport[3]),
				logicalWidth, logicalHeight);
		drawCallCount = 0;
		frameActive = true;
		filledBatch.begin();
		textureBatch.begin();
		textBatch.begin();
		uiQuadBatch.begin();
		spriteBatch.begin();
		activeBatch = BatchType.NONE;
		filledShaderOverride = null;
		textureShaderOverride = null;
		textShaderOverride = null;
		uiShaderOverride = null;
		spriteShaderOverride = null;
		filledUniforms.clear();
		textureUniforms.clear();
		textUniforms.clear();
		uiUniforms.clear();
		spriteUniforms.clear();
		depthState = GLDepthState.DISABLED;
		depth = 0.5f;
		lighting = GLLighting2D.DEFAULT;
		pendingSpriteDepthState = GLDepthState.DISABLED;
		pendingSpriteAlphaMode = GLAlphaMode.OPAQUE;
		transform.setToIdentity();
		recordingReplayBase = null;
		replayParameterDepth = 0;
		colorRenderingEnabled = true;
		pickingRenderingEnabled = true;
		texturePickAlphaThreshold = GLContext2D.DEFAULT_TEXTURE_PICK_ALPHA_THRESHOLD;
		redrawBounds = null;
		adaptiveIconUploads = 0;
		clipStack.clear();
		layerPathStates.clear();
		context.reset(logicalWidth, logicalHeight);

		gl.glEnable(GL.GL_MULTISAMPLE);
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		gl.glClearDepth(0);
		applyDepthState(depthState);
		gl.glDepthRange(depth, depth);
		return context;
	}

	/** Restricts retained-node CPU submission to one logical screen rectangle. */
	public void setRedrawBounds(float[] bounds) {
		requireFrame();
		if (bounds != null && (bounds.length < 4
				|| !Float.isFinite(bounds[0]) || !Float.isFinite(bounds[1])
				|| !Float.isFinite(bounds[2]) || !Float.isFinite(bounds[3])
				|| bounds[2] < 0 || bounds[3] < 0))
			throw new IllegalArgumentException("Redraw bounds must be a finite rectangle");
		redrawBounds = bounds == null ? null : bounds.clone();
	}

	float[] screenBounds(float x, float y, float width, float height) {
		requireFrame();
		float[] corners = { x, y, x + width, y,
				x + width, y + height, x, y + height };
		worldToScreen.copy().concatenate(transform).transform(corners, 0, 4);
		float minimumX = corners[0];
		float maximumX = corners[0];
		float minimumY = corners[1];
		float maximumY = corners[1];
		for (int index = 2; index < corners.length; index += 2) {
			minimumX = Math.min(minimumX, corners[index]);
			maximumX = Math.max(maximumX, corners[index]);
			minimumY = Math.min(minimumY, corners[index + 1]);
			maximumY = Math.max(maximumY, corners[index + 1]);
		}
		return new float[] { minimumX, minimumY,
				maximumX - minimumX, maximumY - minimumY };
	}

	boolean intersectsRedrawBounds(float x, float y, float width, float height) {
		float[] damage = redrawBounds;
		if (damage == null)
			return true;
		float[] bounds = screenBounds(x, y, width, height);
		// Logical edges that merely touch can still occupy the same raster pixel:
		// the physical scissor rounds outward and fonts/layout commonly produce
		// fractional coordinates. Include one device pixel so a front sibling is
		// never culled while a back sibling can overwrite its antialiased edge.
		float toleranceX = 1f / colorDeviceScaleX;
		float toleranceY = 1f / colorDeviceScaleY;
		return bounds[0] <= damage[0] + damage[2] + toleranceX
				&& bounds[0] + bounds[2] >= damage[0] - toleranceX
				&& bounds[1] <= damage[1] + damage[3] + toleranceY
				&& bounds[1] + bounds[3] >= damage[1] - toleranceY;
	}

	public void endFrame() {
		requireFrame();
		context.requireRecordingEnded();
		flush();
		int openLayers = layers.depth();
		int openClips = clipStack.size();
		int openRenderTargets = renderTargetStack.size();
		if (rebuildingPickingFrame && openLayers == 0 && openRenderTargets == 0)
			picking.requestSnapshot(gl, pickRecords);
		if (openLayers != 0)
			layers.abortFrame(gl);
		layerPathStates.clear();
		while (!clipStack.isEmpty())
			restoreScissor(clipStack.remove(clipStack.size() - 1));
		while (!renderTargetStack.isEmpty()) {
			RenderTargetState state = renderTargetStack.remove(
					renderTargetStack.size() - 1);
			if (state.target.isPickingEnabled())
				picking.popExternalSurface();
			restoreRenderTarget(state);
		}
		gl.glDisable(GL.GL_BLEND);
		gl.glDisable(GL.GL_DEPTH_TEST);
		gl.glDepthMask(false);
		gl.glDepthRange(0, 1);
		frameActive = false;
		rebuildingPickingFrame = false;
		gl = null;
		if (openLayers != 0)
			throw new IllegalStateException("Frame ended with " + openLayers
					+ " unclosed layer" + (openLayers == 1 ? "" : "s"));
		if (openClips != 0)
			throw new IllegalStateException("Frame ended with " + openClips
					+ " unclosed clip" + (openClips == 1 ? "" : "s"));
		if (openRenderTargets != 0)
			throw new IllegalStateException("Frame ended with " + openRenderTargets
					+ " unclosed render target"
					+ (openRenderTargets == 1 ? "" : "s"));
	}

	public void flush() {
		requireFrame();
		flushActiveBatch();
		activeBatch = BatchType.NONE;
	}

	void clear(Color color) {
		requireFrame();
		if (color == null)
			throw new IllegalArgumentException("Clear color must not be null");
		flush();
		if (colorRenderingEnabled) {
			layers.includeCurrentLimit();
			float alpha = color.getAlpha() / 255f;
			float premultiply = layers.depth() == 0 ? 1 : alpha;
			gl.glClearColor(color.getRed() / 255f * premultiply,
					color.getGreen() / 255f * premultiply,
					color.getBlue() / 255f * premultiply, alpha);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		}
		if (pickingRenderingEnabled) {
			if (activeRenderTargetPickingRecords() == null)
				picking.clear(gl);
			else
				activeRenderTarget().clearPicking(this, gl);
		}
	}

	void clearDepth() {
		requireFrame();
		if (activeRenderTarget() != null && !activeRenderTarget().isDepthEnabled())
			throw new IllegalStateException(
					"The active render target was created without depth support");
		flush();
		gl.glDepthMask(true);
		try {
			if (colorRenderingEnabled)
				gl.glClearBufferfv(GL3.GL_DEPTH, 0, new float[] { 0 }, 0);
			if (pickingRenderingEnabled)
				picking.clearDepth(gl);
		} finally {
			gl.glDepthMask(depthState.writeEnabled());
		}
	}

	void setDepthState(GLDepthState state) {
		requireFrame();
		if (state == null)
			throw new IllegalArgumentException("Depth state must not be null");
		if (state.equals(depthState))
			return;
		if (state.testEnabled() && activeRenderTarget() != null
				&& !activeRenderTarget().isDepthEnabled())
			throw new IllegalStateException(
					"The active render target was created without depth support");
		flush();
		depthState = state;
		applyDepthState(state);
	}

	GLDepthState getDepthState() {
		requireFrame();
		return depthState;
	}

	void setDepth(float value) {
		requireFrame();
		if (!Float.isFinite(value) || value < 0 || value > 1)
			throw new IllegalArgumentException("Depth must be between 0 and 1");
		if (depth == value)
			return;
		flush();
		depth = value;
		gl.glDepthRange(value, value);
	}

	float getDepth() {
		requireFrame();
		return depth;
	}

	void setLighting(GLLighting2D value) {
		requireFrame();
		if (value == null)
			throw new IllegalArgumentException("Lighting must not be null");
		if (value.equals(lighting))
			return;
		if (activeBatch == BatchType.SPRITE)
			flushActiveBatch();
		lighting = value;
	}

	GLLighting2D getLighting() {
		requireFrame();
		return lighting;
	}

	void beginLayer() {
		requireFrame();
		requireNoRenderTargetForLayer();
		flush();
		layers.beginAutoLayer(gl);
		layerPathStates.add(captureLayerPathState());
	}

	void beginClip(float x, float y, float width, float height) {
		requireFrame();
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width < 0 || height < 0)
			throw new IllegalArgumentException(
					"Clip bounds must be finite and non-negative");
		flush();
		boolean enabled = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
		int[] previous = new int[4];
		gl.glGetIntegerv(GL.GL_SCISSOR_BOX, previous, 0);
		clipStack.add(new ScissorState(enabled, previous));

		float[] bounds = screenBounds(x, y, width, height);
		int[] viewport = new int[4];
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
		int minimumX = viewport[0] + (int) Math.floor(bounds[0] * colorDeviceScaleX);
		int maximumX = viewport[0] + (int) Math.ceil(
				(bounds[0] + bounds[2]) * colorDeviceScaleX);
		int minimumY = viewport[1] + (int) Math.floor(
				(logicalHeight - bounds[1] - bounds[3]) * colorDeviceScaleY);
		int maximumY = viewport[1] + (int) Math.ceil(
				(logicalHeight - bounds[1]) * colorDeviceScaleY);
		minimumX = Math.max(viewport[0], minimumX);
		minimumY = Math.max(viewport[1], minimumY);
		maximumX = Math.min(viewport[0] + viewport[2], maximumX);
		maximumY = Math.min(viewport[1] + viewport[3], maximumY);
		if (enabled) {
			minimumX = Math.max(minimumX, previous[0]);
			minimumY = Math.max(minimumY, previous[1]);
			maximumX = Math.min(maximumX, previous[0] + previous[2]);
			maximumY = Math.min(maximumY, previous[1] + previous[3]);
		}
		gl.glEnable(GL.GL_SCISSOR_TEST);
		gl.glScissor(minimumX, minimumY,
				Math.max(0, maximumX - minimumX),
				Math.max(0, maximumY - minimumY));
	}

	void endClip() {
		requireFrame();
		if (clipStack.isEmpty())
			throw new IllegalStateException(
					"endClip() requires a matching beginClip()");
		flush();
		restoreScissor(clipStack.remove(clipStack.size() - 1));
	}

	int clipDepth() {
		requireFrame();
		return clipStack.size();
	}

	private void restoreScissor(ScissorState state) {
		if (state.enabled()) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			int[] bounds = state.bounds();
			gl.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
		} else {
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
	}

	void beginLayer(float x, float y, float width, float height) {
		requireFrame();
		requireNoRenderTargetForLayer();
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"Layer bounds must be finite and have a positive size");
		flush();
		float[] corners = {
				x, y,
				x + width, y,
				x + width, y + height,
				x, y + height
		};
		GLTransform userToScreen = worldToScreen.copy().concatenate(transform);
		userToScreen.transform(corners, 0, 4);
		float minimumX = corners[0];
		float maximumX = corners[0];
		float minimumY = corners[1];
		float maximumY = corners[1];
		for (int index = 2; index < corners.length; index += 2) {
			minimumX = Math.min(minimumX, corners[index]);
			maximumX = Math.max(maximumX, corners[index]);
			minimumY = Math.min(minimumY, corners[index + 1]);
			maximumY = Math.max(maximumY, corners[index + 1]);
		}
		layers.beginLayer(gl, minimumX, minimumY,
				maximumX - minimumX, maximumY - minimumY);
		layerPathStates.add(captureLayerPathState());
	}

	void beginLayer(float x, float y, float width, float height,
			float screenInset) {
		requireFrame();
		requireNoRenderTargetForLayer();
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width <= 0 || height <= 0
				|| !Float.isFinite(screenInset) || screenInset < 0)
			throw new IllegalArgumentException(
					"Layer bounds and screen inset must be finite and valid");
		flush();
		float[] corners = {
				x, y,
				x + width, y,
				x + width, y + height,
				x, y + height
		};
		GLTransform userToScreen = worldToScreen.copy().concatenate(transform);
		userToScreen.transform(corners, 0, 4);
		float minimumX = corners[0];
		float maximumX = corners[0];
		float minimumY = corners[1];
		float maximumY = corners[1];
		for (int index = 2; index < corners.length; index += 2) {
			minimumX = Math.min(minimumX, corners[index]);
			maximumX = Math.max(maximumX, corners[index]);
			minimumY = Math.min(minimumY, corners[index + 1]);
			maximumY = Math.max(maximumY, corners[index + 1]);
		}
		float maximumInset = Math.max(0,
				Math.min(maximumX - minimumX, maximumY - minimumY) * 0.5f
						- 0.0001f);
		float inset = Math.min(screenInset, maximumInset);
		layers.beginLayer(gl, minimumX + inset, minimumY + inset,
				maximumX - minimumX - inset * 2,
				maximumY - minimumY - inset * 2);
		layerPathStates.add(captureLayerPathState());
	}

	void endLayer(float opacity) {
		endLayer(new GLComposite(opacity));
	}

	void endLayer(GLComposite composite) {
		requireFrame();
		if (composite == null)
			throw new IllegalArgumentException("Layer composite must not be null");
		if (layers.depth() == 0)
			throw new IllegalStateException("endLayer() requires a matching beginLayer()");
		LayerPathState layerPathState = layerPathStates.get(
				layerPathStates.size() - 1);
		List<LayerEffectCommand> effects = new ArrayList<>();
		for (GLLayerEffect effect : composite.getEffects()) {
			Map<String, ShaderUniformValue> uniforms = Map.of();
			if (effect instanceof GLShaderEffect custom) {
				ShaderProgram shader = custom.getShader();
				if (shader.isDisposed())
				throw new IllegalArgumentException("Layer shader has already been disposed");
				shader.validateForLayerComposite(gl);
				uniforms = custom.uniformValues();
				for (Map.Entry<String, ShaderUniformValue> uniform : uniforms.entrySet())
					shader.validateUserUniform(gl, uniform.getKey(), uniform.getValue());
			}
			if (effect instanceof GLPathClipEffect clip) {
				float[] vertices = PathGeometryCompiler.fill(
						clip.pathValue(), Color.WHITE, layerPathState.curveTolerance,
						layerPathState.transform, layerPathState.worldToScreen);
				if (vertices.length == 0)
					throw new IllegalArgumentException(
							"Path clip has no filled area");
				effects.add(new LayerEffectCommand(effect, uniforms, vertices,
						layerPathState.projection.toMatrix4()));
			} else {
				effects.add(new LayerEffectCommand(effect, uniforms));
			}
		}
		flush();
		applyDepthState(GLDepthState.DISABLED);
		try {
			drawCallCount += layers.endLayer(gl, composite.getOpacity(),
					composite.getBlendMode(), effects);
		} finally {
			applyDepthState(depthState);
			gl.glDepthRange(depth, depth);
		}
		layerPathStates.remove(layerPathStates.size() - 1);
	}

	private LayerPathState captureLayerPathState() {
		return new LayerPathState(transform.copy(), worldToScreen.copy(),
				projection.copy(), context.getCurveTolerance());
	}

	int layerDepth() {
		requireFrame();
		return layers.depth();
	}

	void setOutputMask(boolean renderColor, boolean renderPicking) {
		requireFrame();
		if (renderPicking && !renderTargetStack.isEmpty()
				&& !activeRenderTarget().isPickingEnabled())
			throw new IllegalStateException(
					"This render target was created without picking support");
		if (colorRenderingEnabled == renderColor
				&& pickingRenderingEnabled == renderPicking)
			return;
		flush();
		colorRenderingEnabled = renderColor;
		pickingRenderingEnabled = renderPicking;
	}

	boolean isColorRenderingEnabled() {
		requireFrame();
		return colorRenderingEnabled;
	}

	boolean isPickingRenderingEnabled() {
		requireFrame();
		return pickingRenderingEnabled;
	}

	void setTexturePickAlphaThreshold(float threshold) {
		requireFrame();
		if (!Float.isFinite(threshold) || threshold < 0 || threshold > 1)
			throw new IllegalArgumentException(
					"Texture pick alpha threshold must be between 0 and 1");
		if (texturePickAlphaThreshold == threshold)
			return;
		if (activeBatch == BatchType.TEXTURE || activeBatch == BatchType.UI)
			drawCallCount += flushBatch(activeBatch);
		texturePickAlphaThreshold = threshold;
	}

	float getTexturePickAlphaThreshold() {
		requireFrame();
		return texturePickAlphaThreshold;
	}

	public int drawCallCount() {
		return drawCallCount;
	}

	public void dispose(GL3 gl) {
		if (frameActive)
			throw new IllegalStateException("Cannot dispose the renderer during a frame");
		for (GLRenderTarget target : new ArrayList<>(renderTargets))
			target.release(this, gl);
		renderTargets.clear();
		for (GLStaticSpriteResource resource : staticSpriteResources.values())
			resource.dispose(gl);
		staticSpriteResources.clear();
		filledBatch.dispose(gl);
		textureBatch.dispose(gl);
		textBatch.dispose(gl);
		uiQuadBatch.dispose(gl);
		spriteBatch.dispose(gl);
		picking.dispose(gl);
		layers.dispose(gl);
		textureManager.dispose(gl);
		asynchronousResourceRepaint = NO_REPAINT_REQUEST;
	}

	/** Sets the thread-safe host callback used when an async resource becomes ready. */
	public void setAsynchronousResourceRepaint(Runnable repaintRequester) {
		asynchronousResourceRepaint = repaintRequester == null
				? NO_REPAINT_REQUEST : repaintRequester;
	}

	GLTextureRegion resolveImage(GLImage image) {
		requireFrame();
		return textureManager.resolve(gl, image);
	}

	/** Draws one immutable sprite buffer from renderer-local static GPU storage. */
	void drawSpriteBuffer(GLSpriteBuffer buffer) {
		requireFrame();
		if (buffer == null)
			throw new IllegalArgumentException("Sprite buffer must not be null");
		if (buffer.isClosed())
			throw new IllegalStateException("Sprite buffer is closed");
		if (buffer.isEmpty())
			return;
		flush();
		GLTransform current = currentTransform();
		float minX = buffer.getMinX(), minY = buffer.getMinY();
		float maxX = buffer.getMaxX(), maxY = buffer.getMaxY();
		includeLayerQuad(
				current.transformX(minX, minY), current.transformY(minX, minY),
				current.transformX(maxX, minY), current.transformY(maxX, minY),
				current.transformX(maxX, maxY), current.transformY(maxX, maxY),
				current.transformX(minX, maxY), current.transformY(minX, maxY));
		GLStaticSpriteResource resource = staticSpriteResources.get(buffer);
		if (resource == null) {
			resource = GLStaticSpriteResource.create(gl, this, buffer);
			staticSpriteResources.put(buffer, resource);
		}
		textureManager.prepareForSampling(gl);
		try {
			drawCallCount += resource.draw(gl, this, projectionMatrix,
					current.toMatrix4(), lighting, picking,
					colorRenderingEnabled, pickingRenderingEnabled);
		} finally {
			applyDepthState(depthState);
			gl.glDepthRange(depth, depth);
			activeBatch = BatchType.NONE;
		}
	}

	ShaderProgram defaultSpriteProgram() {
		return spriteBatch.defaultProgram();
	}

	void validateSpriteMaterial(GLMaterial2D material) {
		if (material == null)
			throw new IllegalArgumentException("Sprite material must not be null");
		ShaderProgram shader = material.getShader();
		if (shader == null)
			return;
		if (shader.isDisposed())
			throw new IllegalArgumentException("Sprite shader is disposed");
		shader.validateForSpriteBatch(gl);
		for (Map.Entry<String, ShaderUniformValue> uniform
				: material.uniformValues().entrySet()) {
			if (isManagedUniform(BatchType.SPRITE, uniform.getKey()))
				throw new IllegalArgumentException("Uniform " + uniform.getKey()
						+ " is managed by the sprite renderer");
			shader.validateUserUniform(gl, uniform.getKey(), uniform.getValue());
		}
	}

	void validateSpriteDepthState(GLDepthState state) {
		if (state.testEnabled() && activeRenderTarget() != null
				&& !activeRenderTarget().isDepthEnabled())
			throw new IllegalStateException(
					"The active render target was created without depth support");
	}

	void beginRenderTarget(GLRenderTarget target) {
		requireFrame();
		if (target == null)
			throw new IllegalArgumentException("Render target must not be null");
		if (layers.depth() != 0)
			throw new IllegalStateException(
					"Render targets cannot begin inside a temporary layer");
		if (!clipStack.isEmpty())
			throw new IllegalStateException(
					"Render targets cannot begin inside a clip");
		flush();
		renderTargets.add(target);
		int framebuffer = target.framebuffer(this, gl);
		RenderTargetState parent = RenderTargetState.capture(this, gl, target);
		renderTargetStack.add(parent);
		boolean pickingSurfacePushed = false;
		try {
			if (target.isPickingEnabled()) {
				picking.pushExternalSurface(target.pickingFramebuffer(this, gl),
						target.getWidth(), target.getHeight());
				pickingSurfacePushed = true;
			}
			gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, framebuffer);
			gl.glViewport(0, 0, target.getWidth(), target.getHeight());
			gl.glDisable(GL.GL_SCISSOR_TEST);
			logicalWidth = target.getWidth();
			logicalHeight = target.getHeight();
			colorDeviceScaleX = 1;
			colorDeviceScaleY = 1;
			projection.setTransform(Projection2D.TOP_LEFT_PIXELS.create(
					logicalWidth, logicalHeight));
			projectionMatrix = projection.toMatrix4();
			updateScreenTransforms();
			transform.setToIdentity();
			depthState = GLDepthState.DISABLED;
			depth = 0.5f;
			applyDepthState(depthState);
			gl.glDepthRange(depth, depth);
			colorRenderingEnabled = true;
			pickingRenderingEnabled = target.isPickingEnabled();
			redrawBounds = null;
		} catch (RuntimeException error) {
			if (pickingSurfacePushed)
				picking.popExternalSurface();
			renderTargetStack.remove(renderTargetStack.size() - 1);
			restoreRenderTarget(parent);
			throw error;
		}
	}

	void endRenderTarget() {
		requireFrame();
		if (renderTargetStack.isEmpty())
			throw new IllegalStateException(
					"endRenderTarget() requires a matching beginRenderTarget()");
		if (layers.depth() != 0)
			throw new IllegalStateException(
					"A temporary layer must end before its render target");
		if (!clipStack.isEmpty())
			throw new IllegalStateException(
					"A clip must end before its render target");
		RenderTargetState state = renderTargetStack.remove(
				renderTargetStack.size() - 1);
		try {
			flush();
		} finally {
			if (state.target.isPickingEnabled())
				picking.popExternalSurface();
			restoreRenderTarget(state);
		}
	}

	int renderTargetDepth() {
		requireFrame();
		return renderTargetStack.size();
	}

	GLTexture resolveRenderTargetTexture(GLRenderTarget target) {
		requireFrame();
		if (target == null)
			throw new IllegalArgumentException("Render target must not be null");
		if (!renderTargetStack.isEmpty()
				&& renderTargetStack.get(renderTargetStack.size() - 1).target == target)
			throw new IllegalStateException(
					"A render target cannot sample from itself while being written");
		renderTargets.add(target);
		return target.texture(this, gl);
	}

	/**
	 * Presents a target-local ID texture through the same transformed quad as its
	 * color texture and maps its compact local records into the active destination.
	 */
	void addRenderTargetPicking(GLRenderTarget target, GLTexture colorTexture,
			float[] vertices, float x, float y, float width, float height,
			int fallbackPickId, GLMouseTarget fallbackMouseTarget,
			float fallbackOriginX, float fallbackOriginY, float tintAlpha) {
		requireFrame();
		if (!pickingRenderingEnabled || !target.isPickingEnabled())
			return;
		if (vertices == null || vertices.length != 24)
			throw new IllegalArgumentException(
					"Render-target picking requires six x/y/u/v vertices");

		// The caller flushed preceding geometry before submitting this target quad.
		// Render that quad only to color; its picking image is presented by the
		// specialized pass below rather than by the ordinary texture shader.
		pickingRenderingEnabled = false;
		try {
			flush();
		} finally {
			pickingRenderingEnabled = true;
		}

		GLTransform targetToDestination = transform.copy()
				.translate(x, y)
				.scale(width / target.getWidth(), height / target.getHeight());
		GLTransform targetToSurface = worldToScreen.copy()
				.concatenate(targetToDestination);
		PersistentPickRecords source = target.pickingRecords();
		PersistentPickRecords destination = activeRenderTargetPickingRecords();
		int recordBase = destination == null
				? pickRecords.append(source, targetToSurface)
				: source.appendTo(destination, targetToSurface);
		int fallbackRecord = createPickRecord(fallbackPickId,
				fallbackMouseTarget, fallbackOriginX, fallbackOriginY);
		if (source.size() == 0 && fallbackRecord == 0)
			return;

		picking.drawRenderTarget(gl, projectionMatrix, vertices,
				colorTexture, target.pickingTexture(this, gl), recordBase,
				fallbackRecord, tintAlpha, texturePickAlphaThreshold);
		drawCallCount++;
	}

	private void restoreRenderTarget(RenderTargetState state) {
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
		gl.glBindFramebuffer(GL3.GL_READ_FRAMEBUFFER, state.readFramebuffer);
		gl.glViewport(state.viewport[0], state.viewport[1],
				state.viewport[2], state.viewport[3]);
		if (state.scissorEnabled) {
			gl.glEnable(GL.GL_SCISSOR_TEST);
			gl.glScissor(state.scissor[0], state.scissor[1],
					state.scissor[2], state.scissor[3]);
		} else {
			gl.glDisable(GL.GL_SCISSOR_TEST);
		}
		logicalWidth = state.logicalWidth;
		logicalHeight = state.logicalHeight;
		colorDeviceScaleX = state.colorDeviceScaleX;
		colorDeviceScaleY = state.colorDeviceScaleY;
		redrawBounds = state.redrawBounds == null
				? null : state.redrawBounds.clone();
		applyState(state.rendererState);
	}

	private void releaseClosedRenderTargets(GL3 gl) {
		for (GLRenderTarget target : new ArrayList<>(renderTargets)) {
			if (!target.isClosed())
				continue;
			target.release(this, gl);
			renderTargets.remove(target);
		}
	}

	private void releaseClosedSpriteBuffers(GL3 gl) {
		for (Map.Entry<GLSpriteBuffer, GLStaticSpriteResource> entry
				: new ArrayList<>(staticSpriteResources.entrySet())) {
			if (!entry.getKey().isClosed())
				continue;
			entry.getValue().dispose(gl);
			staticSpriteResources.remove(entry.getKey());
		}
	}

	private void requireNoRenderTargetForLayer() {
		if (!renderTargetStack.isEmpty())
			throw new IllegalStateException(
					"Temporary layers inside persistent render targets are not supported yet");
	}

	public int readPickId(GL3 gl, int x, int y) {
		return readPickResult(gl, x, y).getId();
	}

	public GLPickResult readPickResult(GL3 gl, int x, int y) {
		if (frameActive)
			throw new IllegalStateException("Picking is only available after the frame has ended");
		return pickRecords.resolve(picking.readId(gl, x, y), x, y);
	}

	/** Latest completed full-buffer snapshot, or null while none matches the host. */
	public GLPickResult readPickSnapshot(int x, int y,
			int expectedWidth, int expectedHeight) {
		return picking.readSnapshot(x, y, expectedWidth, expectedHeight);
	}

	/** Returns whether the CPU picking snapshot matches the newest submitted frame. */
	public boolean isLatestPickSnapshotReady() {
		return picking.isLatestSnapshotReady();
	}

	/** Polls asynchronous picking transfers without waiting for the GPU. */
	public void pollPickSnapshots(GL3 gl) {
		if (frameActive)
			throw new IllegalStateException("Cannot poll picking during an active frame");
		picking.pollSnapshots(gl);
	}

	void addFilled(float x1, float y1, float x2, float y2, float x3, float y3,
			float red, float green, float blue, float alpha,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		includeLayerTriangle(x1, y1, x2, y2, x3, y3);
		activateBatch(BatchType.FILLED);
		ensureFilledCapacity(3);
		filledBatch.addTriangle(x1, y1, x2, y2, x3, y3,
				red, green, blue, alpha, pickRecord);
	}

	void addFilled(float x1, float y1,
			float red1, float green1, float blue1, float alpha1,
			float x2, float y2, float red2, float green2, float blue2, float alpha2,
			float x3, float y3, float red3, float green3, float blue3, float alpha3,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		includeLayerTriangle(x1, y1, x2, y2, x3, y3);
		activateBatch(BatchType.FILLED);
		ensureFilledCapacity(3);
		filledBatch.addTriangle(
				x1, y1, red1, green1, blue1, alpha1,
				x2, y2, red2, green2, blue2, alpha2,
				x3, y3, red3, green3, blue3, alpha3, pickRecord);
	}

	void addFilled(float x1, float y1, float x2, float y2, float x3, float y3,
			float x4, float y4, float x5, float y5, float x6, float y6,
			float red, float green, float blue, float alpha,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		includeLayerSixPoints(x1, y1, x2, y2, x3, y3,
				x4, y4, x5, y5, x6, y6);
		activateBatch(BatchType.FILLED);
		ensureFilledCapacity(6);
		filledBatch.addTriangles(x1, y1, x2, y2, x3, y3,
				x4, y4, x5, y5, x6, y6, red, green, blue, alpha, pickRecord);
	}

	void addFilled(float[] vertices, int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (vertices == null || vertices.length == 0
				|| vertices.length % FilledBatch.FLOATS_PER_VERTEX != 0
				|| vertices.length / FilledBatch.FLOATS_PER_VERTEX % 3 != 0)
			throw new IllegalArgumentException("Filled vertex data is invalid");
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		addTriangleVerticesRaw(vertices, pickRecord);
	}

	/** Adds vertices already compiled in final replay/world space. */
	void addReplayCompiledFilled(float[] vertices, int pickId,
			GLMouseTarget mouseTarget, float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (recordingReplayBase == null)
			throw new IllegalStateException(
					"Replay-compiled geometry requires an active recording replay");
		if (vertices == null || vertices.length % FilledBatch.FLOATS_PER_VERTEX != 0
				|| vertices.length / FilledBatch.FLOATS_PER_VERTEX % 3 != 0)
			throw new IllegalArgumentException("Filled vertex data is invalid");
		if (vertices.length == 0)
			return;
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		addTriangleVerticesRaw(vertices, pickRecord, recordingColorModifier);
	}

	/** Internal live transform reference used by the shared geometry compiler. */
	GLTransform currentTransform() {
		requireFrame();
		return transform;
	}

	GLTransform currentWorldToScreen() {
		requireFrame();
		return worldToScreen;
	}

	GLTransform currentScreenToWorld() {
		requireFrame();
		return screenToWorld;
	}

	ShaderProgram currentFilledShader() {
		requireFrame();
		return filledShaderOverride;
	}

	ShaderProgram currentTextureShader() {
		requireFrame();
		return textureShaderOverride;
	}

	ShaderProgram currentTextShader() {
		requireFrame();
		return textShaderOverride;
	}

	ShaderProgram currentUIShader() {
		requireFrame();
		return uiShaderOverride;
	}

	StateSnapshot captureState() {
		requireFrame();
		return new StateSnapshot(
				projection, transform,
				colorRenderingEnabled, pickingRenderingEnabled,
				texturePickAlphaThreshold, depthState, depth, lighting,
				filledShaderOverride, filledUniforms,
				textureShaderOverride, textureUniforms,
				textShaderOverride, textUniforms,
				uiShaderOverride, uiUniforms);
	}

	void beginRecordingReplay(StateSnapshot state, StateSnapshot outerState,
			GLRenderParameters parameters) {
		requireFrame();
		if (recordingReplayBase != null)
			throw new IllegalStateException("Nested recording replay is not supported yet");
		if (outerState == null)
			throw new IllegalArgumentException("Outer renderer state must not be null");
		GLTransform replayBase = outerState.transform.copy();
		GLTransform inheritedProjection = outerState.projection.copy();
		// A recording is local geometry, so its ambient projection must come from
		// the frame in which it is replayed. Otherwise a recording created before
		// a resize restores the old aspect ratio. Explicit setProjection commands
		// inside the recording still replay normally after this initial state.
		// The output mask is ambient for the same reason: a recording built during
		// a color-only damage frame must not permanently disable picking when it is
		// replayed in a later full frame. Explicit setOutputMask commands remain
		// part of the command stream and can still override them afterwards.
		applyState(state, inheritedProjection,
				outerState.renderColor, outerState.renderPicking);
		recordingReplayBase = replayBase;
		recordingLocalTransform.setTransform(state.transform);
		recordingParameterTransform.setToIdentity();
		replayParameterDepth = 0;
		replayTintRed = replayTintGreen = replayTintBlue = replayTintAlpha = 1;
		replayColorOverride = false;
		replayOpacity = 1;
		if (parameters != null)
			concatenateRecordingParameters(parameters);
		updateRecordingReplayState();
	}

	void endRecordingReplay() {
		if (recordingReplayBase == null)
			return;
		if (replayParameterDepth != 0)
			throw new IllegalStateException("Recording replay ended inside a parameter region");
		transform.setTransform(recordingReplayBase);
		recordingReplayBase = null;
	}

	void pushRecordingParameters(GLRenderParameters parameters) {
		if (recordingReplayBase == null)
			throw new IllegalStateException("Render parameters require recording replay");
		if (parameters == null)
			throw new IllegalArgumentException("Render parameters must not be null");
		ensureReplayParameterCapacity(replayParameterDepth + 1);
		ReplayParameterState state = replayParameterStack[replayParameterDepth];
		if (state == null) {
			state = new ReplayParameterState();
			replayParameterStack[replayParameterDepth] = state;
		}
		state.capture(this);
		replayParameterDepth++;

		concatenateRecordingParameters(parameters);
		updateRecordingReplayState();
	}

	private void concatenateRecordingParameters(GLRenderParameters parameters) {
		recordingParameterTransform.concatenate(parameters.transformValue());
		Color tint = parameters.getTint();
		replayTintRed *= tint.getRed() / 255f;
		replayTintGreen *= tint.getGreen() / 255f;
		replayTintBlue *= tint.getBlue() / 255f;
		replayTintAlpha *= tint.getAlpha() / 255f;
		Color color = parameters.getColor();
		if (color != null) {
			replayColorOverride = true;
			replayColorRed = color.getRed() / 255f;
			replayColorGreen = color.getGreen() / 255f;
			replayColorBlue = color.getBlue() / 255f;
			replayColorAlpha = color.getAlpha() / 255f;
		}
		replayOpacity *= parameters.getOpacity();
	}

	void popRecordingParameters() {
		if (recordingReplayBase == null || replayParameterDepth == 0)
			throw new IllegalStateException("No recording parameter region is open");
		ReplayParameterState state = replayParameterStack[--replayParameterDepth];
		state.restore(this);
		updateRecordingReplayState();
	}

	void applyState(StateSnapshot state) {
		applyState(state, null);
	}

	private void applyState(StateSnapshot state,
			GLTransform inheritedProjection) {
		applyState(state, inheritedProjection,
				state.renderColor, state.renderPicking);
	}

	private void applyState(StateSnapshot state,
			GLTransform inheritedProjection,
			boolean targetRenderColor, boolean targetRenderPicking) {
		requireFrame();
		if (state == null)
			throw new IllegalArgumentException("Recorded renderer state must not be null");
		if (state.depthState.testEnabled() && activeRenderTarget() != null
				&& !activeRenderTarget().isDepthEnabled())
			throw new IllegalStateException(
					"Recorded depth state requires a depth-enabled render target");
		validateRecordedBatchState(BatchType.FILLED,
				state.filledShader, state.filledUniforms);
		validateRecordedBatchState(BatchType.TEXTURE,
				state.textureShader, state.textureUniforms);
		validateRecordedBatchState(BatchType.TEXT,
				state.textShader, state.textUniforms);
		validateRecordedBatchState(BatchType.UI,
				state.uiShader, state.uiUniforms);

		GLTransform targetProjection = inheritedProjection == null
				? state.projection : inheritedProjection;
		boolean projectionChanged = !projection.equals(targetProjection);
		boolean outputsChanged = colorRenderingEnabled != targetRenderColor
				|| pickingRenderingEnabled != targetRenderPicking;
		boolean textureThresholdChanged =
				texturePickAlphaThreshold != state.texturePickAlphaThreshold;
		boolean depthChanged = !depthState.equals(state.depthState)
				|| depth != state.depth;
		boolean lightingChanged = !lighting.equals(state.lighting);
		boolean filledStateChanged = filledShaderOverride != state.filledShader
				|| !filledUniforms.equals(state.filledUniforms);
		boolean textureStateChanged = textureShaderOverride != state.textureShader
				|| !textureUniforms.equals(state.textureUniforms);
		boolean textStateChanged = textShaderOverride != state.textShader
				|| !textUniforms.equals(state.textUniforms);
		boolean uiStateChanged = uiShaderOverride != state.uiShader
				|| !uiUniforms.equals(state.uiUniforms);
		boolean activeStateChanged = switch (activeBatch) {
		case FILLED -> filledStateChanged;
		case TEXTURE -> textureStateChanged || textureThresholdChanged;
		case TEXT -> textStateChanged;
		case UI -> uiStateChanged || textureThresholdChanged;
		case SPRITE -> false;
		case NONE -> false;
		};
		if (projectionChanged || outputsChanged || activeStateChanged
				|| depthChanged || lightingChanged)
			flush();

		if (projectionChanged) {
			projection.setTransform(targetProjection);
			projectionMatrix = projection.toMatrix4();
			updateScreenTransforms();
		}
		transform.setTransform(state.transform);
		colorRenderingEnabled = targetRenderColor;
		pickingRenderingEnabled = targetRenderPicking;
		texturePickAlphaThreshold = state.texturePickAlphaThreshold;
		depthState = state.depthState;
		depth = state.depth;
		lighting = state.lighting;
		applyDepthState(depthState);
		gl.glDepthRange(depth, depth);
		filledShaderOverride = state.filledShader;
		filledUniforms.clear();
		filledUniforms.putAll(state.filledUniforms);
		textureShaderOverride = state.textureShader;
		textureUniforms.clear();
		textureUniforms.putAll(state.textureUniforms);
		textShaderOverride = state.textShader;
		textUniforms.clear();
		textUniforms.putAll(state.textUniforms);
		uiShaderOverride = state.uiShader;
		uiUniforms.clear();
		uiUniforms.putAll(state.uiUniforms);
	}

	void addTextured(GLTexture texture,
			float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float u0, float v0, float u1, float v1,
			float red, float green, float blue, float alpha,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		includeLayerQuad(x1, y1, x2, y2, x3, y3, x4, y4);
		activateBatch(BatchType.TEXTURE);
		int textureLimit = textureShaderOverride == null ? 16 : 1;
		if (!textureBatch.accepts(texture, textureLimit) || !textureBatch.canAdd(6))
			drawCallCount += flushBatch(BatchType.TEXTURE);

		textureBatch.addQuad(texture,
				x1, y1, x2, y2, x3, y3, x4, y4,
				u0, v0, u1, v1, red, green, blue, alpha, pickRecord,
				textureLimit);
	}

	void addTextured(GLTexture texture, float[] vertices,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (vertices == null || vertices.length == 0
				|| vertices.length % TexturedVertexFormat.FLOATS_PER_VERTEX != 0
				|| vertices.length / TexturedVertexFormat.FLOATS_PER_VERTEX % 3 != 0)
			throw new IllegalArgumentException("Texture vertex data is invalid");
		if (texture == null || texture.isDisposed())
			throw new IllegalArgumentException("Texture must be live");
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		VertexModifier modifier = activeRecordingVertexModifier();
		includeLayerPoints(vertices, TexturedVertexFormat.FLOATS_PER_VERTEX, modifier);
		activateBatch(BatchType.TEXTURE);
		int textureLimit = textureShaderOverride == null ? 16 : 1;
		int remaining = vertices.length / TexturedVertexFormat.FLOATS_PER_VERTEX;
		int offset = 0;
		while (remaining > 0) {
			if (!textureBatch.accepts(texture, textureLimit))
				drawCallCount += flushBatch(BatchType.TEXTURE);
			int chunk = triangleChunk(textureBatch.remainingCapacity(), remaining);
			if (chunk == 0) {
				drawCallCount += flushBatch(BatchType.TEXTURE);
				continue;
			}
			textureBatch.addVertices(texture, vertices, offset, chunk,
					pickRecord, modifier, textureLimit);
			offset += chunk * TexturedVertexFormat.FLOATS_PER_VERTEX;
			remaining -= chunk;
		}
	}

	void prepareFont(GLFont font) {
		requireFrame();
		if (font == null || font.isDisposed())
			throw new IllegalArgumentException("Font must be live");
		font.ensureInitialized(gl);
	}

	void addText(GLTexture atlas, float pixelRange, float[] vertices,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (vertices == null || vertices.length == 0
				|| vertices.length % TexturedVertexFormat.FLOATS_PER_VERTEX != 0)
			throw new IllegalArgumentException("Text vertex data is invalid");
		int vertexCount = vertices.length / TexturedVertexFormat.FLOATS_PER_VERTEX;
		if (vertexCount % 3 != 0)
			throw new IllegalArgumentException("Text vertex data is invalid");
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		VertexModifier modifier = activeRecordingVertexModifier();
		includeLayerPoints(vertices, TexturedVertexFormat.FLOATS_PER_VERTEX, modifier);
		activateBatch(BatchType.TEXT);
		int remaining = vertexCount;
		int offset = 0;
		while (remaining > 0) {
			if (!textBatch.accepts(atlas, pixelRange))
				drawCallCount += flushBatch(BatchType.TEXT);
			int chunk = triangleChunk(textBatch.remainingCapacity(), remaining);
			if (chunk == 0) {
				drawCallCount += flushBatch(BatchType.TEXT);
				continue;
			}
			textBatch.addVertices(atlas, pixelRange, vertices, offset, chunk,
					pickRecord, modifier);
			offset += chunk * TexturedVertexFormat.FLOATS_PER_VERTEX;
			remaining -= chunk;
		}
	}

	void addUI(GLTexture texture, float[] instances,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		addUI(texture, instances, pickId, mouseTarget,
				automaticOriginX, automaticOriginY,
				activeRecordingVertexModifier());
	}

	private void addUI(GLTexture texture, float[] instances,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY,
			VertexModifier modifier) {
		requireFrame();
		if (instances == null || instances.length == 0
				|| instances.length % UIQuadInstanceFormat.FLOATS_PER_INSTANCE != 0)
			throw new IllegalArgumentException("UI instance data is invalid");
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
		includeLayerUIInstances(instances, modifier);
		activateBatch(BatchType.UI);
		int remaining = instances.length / UIQuadInstanceFormat.FLOATS_PER_INSTANCE;
		int offset = 0;
		while (remaining > 0) {
			if (!uiQuadBatch.accepts(texture))
				drawCallCount += flushBatch(BatchType.UI);
			int chunk = Math.min(uiQuadBatch.remainingCapacity(), remaining);
			if (chunk == 0) {
				drawCallCount += flushBatch(BatchType.UI);
				continue;
			}
			uiQuadBatch.add(texture, instances, offset, chunk,
					pickRecord, modifier);
			offset += chunk * UIQuadInstanceFormat.FLOATS_PER_INSTANCE;
			remaining -= chunk;
		}
	}

	/** Resolves an adaptive RGBA icon in the current replay/view space. */
	void addUIIcon(GLIcon icon, float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		float requiredPixels = physicalExtent(x, y, width, height);
		GLImage image = icon.uiImageFor(requiredPixels, asynchronousResourceRepaint);
		if (!textureManager.isResolved(image)) {
			GLImage resolvedFallback = icon.bestUIImageFor(requiredPixels,
					textureManager::isResolved);
			if (resolvedFallback != null
					&& adaptiveIconUploads >= MAX_ADAPTIVE_ICON_UPLOADS_PER_FRAME) {
				image = resolvedFallback;
				// The worker's repaint may have woken only this frame. Keep the
				// damage chain alive until every ready upgrade got its upload turn.
				asynchronousResourceRepaint.run();
			} else if (resolvedFallback != null) {
				adaptiveIconUploads++;
			}
		}
		GLTextureRegion region = resolveImage(image);
		float[] instance = icon.getFormat() == GLIcon.Format.MSDF
				? UIQuadInstanceFormat.distanceField(x, y, width, height,
						region.u0(), region.v0(), region.u1(), region.v1(),
						icon.pixelRange(), color, currentTransform())
				: UIQuadInstanceFormat.texture(x, y, width, height,
						region.u0(), region.v0(), region.u1(), region.v1(),
						color, currentTransform());
		VertexModifier modifier = recordingReplayBase == null
				? null : recordingColorModifier;
		addUI(region.texture(), instance, pickId, mouseTarget,
				automaticOriginX, automaticOriginY, modifier);
	}

	/** Resolves an icon as RGBA and submits it through the shaderable texture batch. */
	void addIcon(GLIcon icon, float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		float requiredPixels = physicalExtent(x, y, width, height);
		GLImage image = icon.textureImageFor(
				requiredPixels, asynchronousResourceRepaint);
		if (!textureManager.isResolved(image)) {
			GLImage resolvedFallback = icon.bestTextureImageFor(requiredPixels,
					textureManager::isResolved);
			if (resolvedFallback != null
					&& adaptiveIconUploads >= MAX_ADAPTIVE_ICON_UPLOADS_PER_FRAME) {
				image = resolvedFallback;
				asynchronousResourceRepaint.run();
			} else if (resolvedFallback != null) {
				adaptiveIconUploads++;
			}
		}
		GLTextureRegion region = resolveImage(image);
		float x2 = x + width;
		float y2 = y + height;
		GLTransform current = currentTransform();
		float x1t = current.transformX(x, y);
		float y1t = current.transformY(x, y);
		float x2t = current.transformX(x2, y);
		float y2t = current.transformY(x2, y);
		float x3t = current.transformX(x2, y2);
		float y3t = current.transformY(x2, y2);
		float x4t = current.transformX(x, y2);
		float y4t = current.transformY(x, y2);
		addTextured(region.texture(),
				x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t,
				region.u0(), region.v0(), region.u1(), region.v1(),
				color.getRed() / 255f, color.getGreen() / 255f,
				color.getBlue() / 255f, color.getAlpha() / 255f,
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
	}

	/** Resolves and submits one depth-aware material quad. */
	void addSprite(GLMaterial2D material, float x, float y,
			float width, float height, Color tint, float baseDepth,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (material == null)
			throw new IllegalArgumentException("Sprite material must not be null");
		if (!Float.isFinite(baseDepth) || baseDepth < 0 || baseDepth > 1)
			throw new IllegalArgumentException("Sprite depth must be between 0 and 1");
		if (material.getDepthState().testEnabled() && activeRenderTarget() != null
				&& !activeRenderTarget().isDepthEnabled())
			throw new IllegalStateException(
					"The active render target was created without depth support");
		validateSpriteMaterial(material);
		ShaderProgram shader = material.getShader();

		GLTextureRegion color = resolveImage(material.getColorMap());
		GLTextureRegion depthRegion = material.hasDepthMap()
				? resolveImage(material.getDepthMap()) : color;
		GLTextureRegion normal = material.hasNormalMap()
				? resolveImage(material.getNormalMap()) : color;
		boolean stateChanged = spriteShaderOverride != shader
				|| !spriteUniforms.equals(material.uniformValues())
				|| !pendingSpriteDepthState.equals(material.getDepthState())
				|| pendingSpriteAlphaMode != material.getAlphaMode();
		activateBatch(BatchType.SPRITE);
		if (stateChanged)
			drawCallCount += flushBatch(BatchType.SPRITE);
		spriteShaderOverride = shader;
		spriteUniforms.clear();
		spriteUniforms.putAll(material.uniformValues());
		pendingSpriteDepthState = material.getDepthState();
		pendingSpriteAlphaMode = material.getAlphaMode();
		if (!spriteBatch.accepts(color.texture(), depthRegion.texture(), normal.texture())
				|| !spriteBatch.canAdd())
			drawCallCount += flushBatch(BatchType.SPRITE);

		float x2 = x + width;
		float y2 = y + height;
		GLTransform current = currentTransform();
		float x1t = current.transformX(x, y);
		float y1t = current.transformY(x, y);
		float x2t = current.transformX(x2, y);
		float y2t = current.transformY(x2, y);
		float x3t = current.transformX(x2, y2);
		float y3t = current.transformY(x2, y2);
		float x4t = current.transformX(x, y2);
		float y4t = current.transformY(x, y2);
		includeLayerQuad(x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t);
		int pickRecord = createPickRecord(pickId, mouseTarget,
				automaticOriginX, automaticOriginY);
		int flags = (material.hasDepthMap() ? SpriteBatch.FLAG_DEPTH_MAP : 0)
				| (material.hasNormalMap() ? SpriteBatch.FLAG_NORMAL_MAP : 0)
				| (material.isLit() ? SpriteBatch.FLAG_LIT : 0)
				| (material.getAlphaMode() == GLAlphaMode.CUTOUT
						? SpriteBatch.FLAG_CUTOUT : 0);
		float red = tint.getRed() / 255f;
		float green = tint.getGreen() / 255f;
		float blue = tint.getBlue() / 255f;
		float alpha = tint.getAlpha() / 255f;
		if (recordingReplayBase != null) {
			red = recordingColorModifier.red(red);
			green = recordingColorModifier.green(green);
			blue = recordingColorModifier.blue(blue);
			alpha = recordingColorModifier.alpha(alpha);
		}
		spriteBatch.addQuad(color.texture(), depthRegion.texture(), normal.texture(),
				x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t,
				color.u0(), color.v0(), color.u1(), color.v1(),
				depthRegion.u0(), depthRegion.v0(), depthRegion.u1(), depthRegion.v1(),
				normal.u0(), normal.v0(), normal.u1(), normal.v1(),
				red, green, blue, alpha, baseDepth,
				material.getDepthBackOffset(), material.getDepthFrontOffset(),
				material.getAlphaCutoff(), flags, pickRecord);
	}

	private float physicalExtent(float x, float y, float width, float height) {
		GLTransform userToScreen = worldToScreen.copy().concatenate(transform);
		float x00 = userToScreen.transformX(x, y);
		float y00 = userToScreen.transformY(x, y);
		float x10 = userToScreen.transformX(x + width, y);
		float y10 = userToScreen.transformY(x + width, y);
		float x01 = userToScreen.transformX(x, y + height);
		float y01 = userToScreen.transformY(x, y + height);
		float horizontal = (float) Math.hypot(
				(x10 - x00) * colorDeviceScaleX,
				(y10 - y00) * colorDeviceScaleY);
		float vertical = (float) Math.hypot(
				(x01 - x00) * colorDeviceScaleX,
				(y01 - y00) * colorDeviceScaleY);
		return Math.max(horizontal, vertical);
	}

	void setFilledShader(ShaderProgram shader) {
		requireFrame();
		if (shader != null && shader.isDisposed())
			throw new IllegalArgumentException("Filled shader has already been disposed");
		if (filledShaderOverride == shader)
			return;
		if (shader != null)
			shader.validateForFilledBatch(gl);
		if (activeBatch == BatchType.FILLED)
			drawCallCount += filledBatch.flush(
					gl, projectionMatrix, filledShaderOverride, filledUniforms, picking,
					colorRenderingEnabled, pickingRenderingEnabled);
		filledShaderOverride = shader;
		filledUniforms.clear();
	}

	void setTextureShader(ShaderProgram shader) {
		requireFrame();
		if (shader != null && shader.isDisposed())
			throw new IllegalArgumentException("Texture shader has already been disposed");
		if (textureShaderOverride == shader)
			return;
		if (shader != null)
			shader.validateForTextureBatch(gl);
		if (activeBatch == BatchType.TEXTURE)
			drawCallCount += flushBatch(BatchType.TEXTURE);
		textureShaderOverride = shader;
		textureUniforms.clear();
	}

	void setTextShader(ShaderProgram shader) {
		requireFrame();
		if (shader != null && shader.isDisposed())
			throw new IllegalArgumentException("Text shader has already been disposed");
		if (textShaderOverride == shader)
			return;
		if (shader != null)
			shader.validateForTextBatch(gl);
		if (activeBatch == BatchType.TEXT)
			drawCallCount += textBatch.flush(
					gl, projectionMatrix, textShaderOverride, textUniforms, picking,
					colorRenderingEnabled, pickingRenderingEnabled);
		textShaderOverride = shader;
		textUniforms.clear();
	}

	void setUIShader(ShaderProgram shader) {
		requireFrame();
		if (shader != null && shader.isDisposed())
			throw new IllegalArgumentException("UI shader has already been disposed");
		if (uiShaderOverride == shader)
			return;
		if (shader != null)
			shader.validateForUIBatch(gl);
		if (activeBatch == BatchType.UI)
			drawCallCount += flushBatch(BatchType.UI);
		uiShaderOverride = shader;
		uiUniforms.clear();
	}

	void setFilledUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.FILLED, name, value, filledShaderOverride, filledUniforms);
	}

	void setTextureUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.TEXTURE, name, value, textureShaderOverride, textureUniforms);
	}

	void setTextUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.TEXT, name, value, textShaderOverride, textUniforms);
	}

	void setUIUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.UI, name, value, uiShaderOverride, uiUniforms);
	}

	GLTransform getTransform() {
		requireFrame();
		return transform.copy();
	}

	void setTransform(GLTransform newTransform) {
		requireFrame();
		if (recordingReplayBase == null) {
			transform.setTransform(newTransform);
		} else {
			recordingLocalTransform.setTransform(newTransform);
			updateRecordingReplayState();
		}
	}

	void resetTransform() {
		requireFrame();
		if (recordingReplayBase == null) {
			transform.setToIdentity();
		} else {
			recordingLocalTransform.setToIdentity();
			updateRecordingReplayState();
		}
	}

	void translate(float x, float y) {
		requireFrame();
		if (recordingReplayBase == null)
			transform.translate(x, y);
		else {
			recordingLocalTransform.translate(x, y);
			updateRecordingReplayState();
		}
	}

	void rotate(float radians) {
		requireFrame();
		if (recordingReplayBase == null)
			transform.rotate(radians);
		else {
			recordingLocalTransform.rotate(radians);
			updateRecordingReplayState();
		}
	}

	void rotate(float radians, float anchorX, float anchorY) {
		requireFrame();
		if (recordingReplayBase == null)
			transform.rotate(radians, anchorX, anchorY);
		else {
			recordingLocalTransform.rotate(radians, anchorX, anchorY);
			updateRecordingReplayState();
		}
	}

	void scale(float scaleX, float scaleY) {
		requireFrame();
		if (recordingReplayBase == null)
			transform.scale(scaleX, scaleY);
		else {
			recordingLocalTransform.scale(scaleX, scaleY);
			updateRecordingReplayState();
		}
	}

	GLTransform getProjection() {
		requireFrame();
		return projection.copy();
	}

	void setProjection(GLTransform newProjection) {
		requireFrame();
		if (newProjection == null)
			throw new IllegalArgumentException("Projection must not be null");
		if (projection.equals(newProjection))
			return;
		flush();
		projection.setTransform(newProjection);
		projectionMatrix = projection.toMatrix4();
		updateScreenTransforms();
	}

	private void addTriangleVerticesRaw(float[] triangleVertices, int pickId) {
		addTriangleVerticesRaw(triangleVertices, pickId,
				activeRecordingVertexModifier());
	}

	private void addTriangleVerticesRaw(float[] triangleVertices, int pickId,
			VertexModifier modifier) {
		includeLayerPoints(triangleVertices, FilledBatch.FLOATS_PER_VERTEX, modifier);
		activateBatch(BatchType.FILLED);
		int remaining = triangleVertices.length / FilledBatch.FLOATS_PER_VERTEX;
		int offset = 0;
		while (remaining > 0) {
			int chunk = triangleChunk(filledBatch.remainingCapacity(), remaining);
			if (chunk == 0) {
				drawCallCount += flushBatch(BatchType.FILLED);
				continue;
			}
			filledBatch.addVertices(triangleVertices, offset, chunk,
					pickId, modifier);
			offset += chunk * FilledBatch.FLOATS_PER_VERTEX;
			remaining -= chunk;
		}
	}

	private static int triangleChunk(int capacity, int remaining) {
		int chunk = Math.min(capacity, remaining);
		return chunk - chunk % 3;
	}

	private VertexModifier activeRecordingVertexModifier() {
		return recordingReplayBase == null ? null : recordingVertexModifier;
	}

	private void includeLayerTriangle(float x1, float y1,
			float x2, float y2, float x3, float y3) {
		if (!tracksAutomaticLayer())
			return;
		beginLayerBoundsScan();
		includeLayerPoint(x1, y1, null);
		includeLayerPoint(x2, y2, null);
		includeLayerPoint(x3, y3, null);
		finishLayerBoundsScan();
	}

	private void includeLayerQuad(float x1, float y1,
			float x2, float y2, float x3, float y3, float x4, float y4) {
		if (!tracksAutomaticLayer())
			return;
		beginLayerBoundsScan();
		includeLayerPoint(x1, y1, null);
		includeLayerPoint(x2, y2, null);
		includeLayerPoint(x3, y3, null);
		includeLayerPoint(x4, y4, null);
		finishLayerBoundsScan();
	}

	private void includeLayerSixPoints(float x1, float y1,
			float x2, float y2, float x3, float y3,
			float x4, float y4, float x5, float y5, float x6, float y6) {
		if (!tracksAutomaticLayer())
			return;
		beginLayerBoundsScan();
		includeLayerPoint(x1, y1, null);
		includeLayerPoint(x2, y2, null);
		includeLayerPoint(x3, y3, null);
		includeLayerPoint(x4, y4, null);
		includeLayerPoint(x5, y5, null);
		includeLayerPoint(x6, y6, null);
		finishLayerBoundsScan();
	}

	private void includeLayerUIInstances(float[] instances,
			VertexModifier modifier) {
		if (!tracksAutomaticLayer())
			return;
		beginLayerBoundsScan();
		for (int offset = 0; offset < instances.length;
				offset += UIQuadInstanceFormat.FLOATS_PER_INSTANCE) {
			float x00 = instances[offset];
			float y00 = instances[offset + 1];
			float x10 = instances[offset + 2];
			float y10 = instances[offset + 3];
			float x01 = instances[offset + 4];
			float y01 = instances[offset + 5];
			includeLayerPoint(x00, y00, modifier);
			includeLayerPoint(x10, y10, modifier);
			includeLayerPoint(x01, y01, modifier);
			includeLayerPoint(x10 + x01 - x00,
					y10 + y01 - y00, modifier);
		}
		finishLayerBoundsScan();
	}

	private void includeLayerPoints(float[] vertices, int stride,
			VertexModifier modifier) {
		if (!tracksAutomaticLayer())
			return;
		beginLayerBoundsScan();
		for (int offset = 0; offset < vertices.length; offset += stride)
			includeLayerPoint(vertices[offset], vertices[offset + 1], modifier);
		finishLayerBoundsScan();
	}

	private void beginLayerBoundsScan() {
		layerMinimumX = Float.POSITIVE_INFINITY;
		layerMinimumY = Float.POSITIVE_INFINITY;
		layerMaximumX = Float.NEGATIVE_INFINITY;
		layerMaximumY = Float.NEGATIVE_INFINITY;
	}

	private void includeLayerPoint(float x, float y, VertexModifier modifier) {
		if (modifier != null) {
			float transformedX = modifier.transformX(x, y);
			y = modifier.transformY(x, y);
			x = transformedX;
		}
		float screenX = worldToScreen.transformX(x, y);
		float screenY = worldToScreen.transformY(x, y);
		layerMinimumX = Math.min(layerMinimumX, screenX);
		layerMinimumY = Math.min(layerMinimumY, screenY);
		layerMaximumX = Math.max(layerMaximumX, screenX);
		layerMaximumY = Math.max(layerMaximumY, screenY);
	}

	private void finishLayerBoundsScan() {
		includeLayerScreenBounds(layerMinimumX, layerMinimumY,
				layerMaximumX, layerMaximumY);
	}

	private boolean tracksAutomaticLayer() {
		return colorRenderingEnabled && layers.tracksAutomaticBounds();
	}

	private void includeLayerScreenBounds(float minimumX, float minimumY,
			float maximumX, float maximumY) {
		if (minimumX != Float.POSITIVE_INFINITY)
			layers.includeLogicalBounds(minimumX, minimumY,
					Math.max(0, maximumX - minimumX),
					Math.max(0, maximumY - minimumY));
	}

	private void updateRecordingReplayState() {
		recordingVertexTransform.setTransform(recordingReplayBase)
				.concatenate(recordingParameterTransform);
		recordingVertexModifier.set(recordingVertexTransform,
				replayTintRed, replayTintGreen, replayTintBlue, replayTintAlpha,
				replayColorOverride, replayColorRed, replayColorGreen,
				replayColorBlue, replayColorAlpha, replayOpacity);
		recordingColorModifier.set(recordingColorTransform,
				replayTintRed, replayTintGreen, replayTintBlue, replayTintAlpha,
				replayColorOverride, replayColorRed, replayColorGreen,
				replayColorBlue, replayColorAlpha, replayOpacity);
		transform.setTransform(recordingVertexTransform)
				.concatenate(recordingLocalTransform);
	}

	private void ensureReplayParameterCapacity(int required) {
		if (required <= replayParameterStack.length)
			return;
		ReplayParameterState[] expanded = new ReplayParameterState[
				Math.max(required, replayParameterStack.length * 2)];
		System.arraycopy(replayParameterStack, 0, expanded, 0,
				replayParameterStack.length);
		replayParameterStack = expanded;
	}

	private void updateScreenTransforms() {
		GLTransform clipToScreen = new GLTransform(
				logicalWidth / 2, 0,
				0, -logicalHeight / 2,
				logicalWidth / 2, logicalHeight / 2);
		worldToScreen.setTransform(clipToScreen.concatenate(projection));
		try {
			screenToWorld.setTransform(worldToScreen.createInverse());
		} catch (IllegalStateException error) {
			throw new IllegalStateException(
					"SCREEN strokes require an invertible view projection", error);
		}
	}

	private int createPickRecord(int pickId, GLMouseTarget mouseTarget,
			float originX, float originY) {
		return createPickRecord(pickId, mouseTarget, null, originX, originY);
	}

	int createPickRecord(int pickId, GLMouseTarget mouseTarget,
			GLTransform localTransform, float originX, float originY) {
		if (pickId != GLContext2D.NO_PICK_ID && mouseTarget != null)
			throw new IllegalArgumentException(
					"Pick ID and mouse target are mutually exclusive");
		if (pickId == GLContext2D.NO_PICK_ID && mouseTarget == null)
			return 0;
		if (!pickingRenderingEnabled)
			return 0;
		pickTransform.setTransform(worldToScreen)
				.concatenate(transform);
		if (localTransform != null)
			pickTransform.concatenate(localTransform);
		pickTransform.translate(originX, originY);
		PersistentPickRecords targetRecords = activeRenderTargetPickingRecords();
		return targetRecords == null
				? pickRecords.add(pickId, mouseTarget, pickTransform)
				: targetRecords.add(pickId, mouseTarget, pickTransform);
	}

	private GLRenderTarget activeRenderTarget() {
		return renderTargetStack.isEmpty() ? null
				: renderTargetStack.get(renderTargetStack.size() - 1).target;
	}

	private PersistentPickRecords activeRenderTargetPickingRecords() {
		GLRenderTarget target = activeRenderTarget();
		return target != null && target.isPickingEnabled()
				? target.pickingRecords() : null;
	}

	private void validateRecordedBatchState(BatchType batch, ShaderProgram shader,
			Map<String, ShaderUniformValue> uniforms) {
		if (shader == null) {
			if (!uniforms.isEmpty())
				throw new IllegalStateException(
						"Recorded " + batch.displayName + " uniforms require a custom shader");
			return;
		}
		if (shader.isDisposed())
			throw new IllegalStateException(
					"A shader referenced by the recording has been disposed");
		switch (batch) {
		case FILLED -> shader.validateForFilledBatch(gl);
		case TEXTURE -> shader.validateForTextureBatch(gl);
		case TEXT -> shader.validateForTextBatch(gl);
		case UI -> shader.validateForUIBatch(gl);
		case SPRITE -> shader.validateForSpriteBatch(gl);
		case NONE -> throw new IllegalArgumentException("NONE has no shader state");
		}
		for (Map.Entry<String, ShaderUniformValue> uniform : uniforms.entrySet())
			shader.validateUserUniform(gl, uniform.getKey(), uniform.getValue());
	}

	private void ensureFilledCapacity(int vertices) {
		if (!filledBatch.canAdd(vertices))
			drawCallCount += filledBatch.flush(
					gl, projectionMatrix, filledShaderOverride, filledUniforms, picking,
					colorRenderingEnabled, pickingRenderingEnabled);
		if (!filledBatch.canAdd(vertices))
			throw new IllegalArgumentException(
					"Filled geometry exceeds the batch vertex capacity");
	}

	private void activateBatch(BatchType batch) {
		if (activeBatch == batch)
			return;
		flushActiveBatch();
		activeBatch = batch;
	}

	private void flushActiveBatch() {
		drawCallCount += flushBatch(activeBatch);
	}

	private void setUserUniform(BatchType batch, String name, ShaderUniformValue value,
			ShaderProgram shader, Map<String, ShaderUniformValue> uniforms) {
		requireFrame();
		if (shader == null)
			throw new IllegalStateException("Set a custom " + batch.displayName +
					" shader before assigning extra uniforms");
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Uniform name must not be blank");
		if (isManagedUniform(batch, name))
			throw new IllegalArgumentException("Uniform " + name + " is managed by the renderer");
		if (value == null)
			throw new IllegalArgumentException("Uniform value must not be null");
		ShaderUniformValue previous = uniforms.get(name);
		if (value.equals(previous))
			return;

		shader.validateUserUniform(gl, name, value);
		if (activeBatch == batch)
			drawCallCount += flushBatch(batch);
		uniforms.put(name, value);
	}

	private int flushBatch(BatchType batch) {
		if (batch == BatchType.TEXTURE || batch == BatchType.UI
				|| batch == BatchType.SPRITE)
			textureManager.prepareForSampling(gl);
		return switch (batch) {
		case FILLED -> filledBatch.flush(
				gl, projectionMatrix, filledShaderOverride, filledUniforms, picking,
				colorRenderingEnabled, pickingRenderingEnabled);
		case TEXTURE -> textureBatch.flush(
				gl, projectionMatrix, textureShaderOverride, textureUniforms, picking,
				colorRenderingEnabled, pickingRenderingEnabled,
				texturePickAlphaThreshold);
		case TEXT -> textBatch.flush(
				gl, projectionMatrix, textShaderOverride, textUniforms, picking,
				colorRenderingEnabled, pickingRenderingEnabled);
		case UI -> uiQuadBatch.flush(gl, projectionMatrix,
				uiShaderOverride, uiUniforms, picking,
				colorRenderingEnabled, pickingRenderingEnabled,
				texturePickAlphaThreshold, colorDeviceScaleX, colorDeviceScaleY);
		case SPRITE -> flushSpriteBatch();
		case NONE -> 0;
		};
	}

	private int flushSpriteBatch() {
		applyDepthState(pendingSpriteDepthState);
		gl.glDepthRange(0, 1);
		try {
			return spriteBatch.flush(gl, projectionMatrix,
					spriteShaderOverride, spriteUniforms, lighting,
					pendingSpriteAlphaMode, picking,
					colorRenderingEnabled, pickingRenderingEnabled);
		} finally {
			applyDepthState(depthState);
			gl.glDepthRange(depth, depth);
		}
	}

	private static boolean isManagedUniform(BatchType batch, String name) {
		return name.equals("uProjection")
				|| batch == BatchType.SPRITE && name.equals("uModel")
				|| (batch == BatchType.TEXTURE || batch == BatchType.TEXT)
						&& name.equals("uTexture")
				|| batch == BatchType.TEXT && name.equals("uMsdfPixelRange")
				|| batch == BatchType.UI && (name.equals("uDeviceScale")
						|| name.equals("uTextures") || name.startsWith("uTextures["))
				|| batch == BatchType.SPRITE && (name.startsWith("uColorMaps")
						|| name.startsWith("uDepthMaps")
						|| name.startsWith("uNormalMaps")
						|| name.equals("uAmbientColor")
						|| name.equals("uAmbientIntensity")
						|| name.equals("uDirectionalDirection")
						|| name.equals("uDirectionalColor")
						|| name.equals("uDirectionalIntensity"));
	}

	void applyDepthState(GLDepthState state) {
		if (state.testEnabled())
			gl.glEnable(GL.GL_DEPTH_TEST);
		else
			gl.glDisable(GL.GL_DEPTH_TEST);
		gl.glDepthMask(state.writeEnabled());
		int function = switch (state.function()) {
		case LESS -> GL.GL_LESS;
		case LESS_OR_EQUAL -> GL.GL_LEQUAL;
		case GREATER -> GL.GL_GREATER;
		case GREATER_OR_EQUAL -> GL.GL_GEQUAL;
		case ALWAYS -> GL.GL_ALWAYS;
		};
		gl.glDepthFunc(function);
	}

	private void requireFrame() {
		if (!frameActive)
			throw new IllegalStateException("Rendering is only allowed during an active frame");
	}

	private enum BatchType {
		NONE("batch"),
		FILLED("filled"),
		TEXTURE("texture"),
		TEXT("text"),
		UI("UI"),
		SPRITE("sprite");

		private final String displayName;

		BatchType(String displayName) {
			this.displayName = displayName;
		}
	}

	private static final class ReplayParameterState {
		private final GLTransform transform = new GLTransform();
		private float tintRed;
		private float tintGreen;
		private float tintBlue;
		private float tintAlpha;
		private boolean colorOverride;
		private float colorRed;
		private float colorGreen;
		private float colorBlue;
		private float colorAlpha;
		private float opacity;

		private void capture(GLRenderer2D renderer) {
			transform.setTransform(renderer.recordingParameterTransform);
			tintRed = renderer.replayTintRed;
			tintGreen = renderer.replayTintGreen;
			tintBlue = renderer.replayTintBlue;
			tintAlpha = renderer.replayTintAlpha;
			colorOverride = renderer.replayColorOverride;
			colorRed = renderer.replayColorRed;
			colorGreen = renderer.replayColorGreen;
			colorBlue = renderer.replayColorBlue;
			colorAlpha = renderer.replayColorAlpha;
			opacity = renderer.replayOpacity;
		}

		private void restore(GLRenderer2D renderer) {
			renderer.recordingParameterTransform.setTransform(transform);
			renderer.replayTintRed = tintRed;
			renderer.replayTintGreen = tintGreen;
			renderer.replayTintBlue = tintBlue;
			renderer.replayTintAlpha = tintAlpha;
			renderer.replayColorOverride = colorOverride;
			renderer.replayColorRed = colorRed;
			renderer.replayColorGreen = colorGreen;
			renderer.replayColorBlue = colorBlue;
			renderer.replayColorAlpha = colorAlpha;
			renderer.replayOpacity = opacity;
		}
	}

	private static final class RenderTargetState {
		private final GLRenderTarget target;
		private final StateSnapshot rendererState;
		private final float logicalWidth;
		private final float logicalHeight;
		private final float colorDeviceScaleX;
		private final float colorDeviceScaleY;
		private final float[] redrawBounds;
		private final int drawFramebuffer;
		private final int readFramebuffer;
		private final int[] viewport;
		private final boolean scissorEnabled;
		private final int[] scissor;

		private RenderTargetState(GLRenderTarget target,
				StateSnapshot rendererState,
				float logicalWidth, float logicalHeight,
				float colorDeviceScaleX, float colorDeviceScaleY,
				float[] redrawBounds,
				int drawFramebuffer, int readFramebuffer,
				int[] viewport, boolean scissorEnabled, int[] scissor) {
			this.target = target;
			this.rendererState = rendererState;
			this.logicalWidth = logicalWidth;
			this.logicalHeight = logicalHeight;
			this.colorDeviceScaleX = colorDeviceScaleX;
			this.colorDeviceScaleY = colorDeviceScaleY;
			this.redrawBounds = redrawBounds == null ? null : redrawBounds.clone();
			this.drawFramebuffer = drawFramebuffer;
			this.readFramebuffer = readFramebuffer;
			this.viewport = viewport;
			this.scissorEnabled = scissorEnabled;
			this.scissor = scissor;
		}

		private static RenderTargetState capture(GLRenderer2D renderer,
				GL3 gl, GLRenderTarget target) {
			int[] draw = new int[1];
			int[] read = new int[1];
			int[] viewport = new int[4];
			int[] scissor = new int[4];
			gl.glGetIntegerv(GL3.GL_DRAW_FRAMEBUFFER_BINDING, draw, 0);
			gl.glGetIntegerv(GL3.GL_READ_FRAMEBUFFER_BINDING, read, 0);
			gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
			gl.glGetIntegerv(GL.GL_SCISSOR_BOX, scissor, 0);
			return new RenderTargetState(target, renderer.captureState(),
					renderer.logicalWidth, renderer.logicalHeight,
					renderer.colorDeviceScaleX, renderer.colorDeviceScaleY,
					renderer.redrawBounds, draw[0], read[0], viewport,
					gl.glIsEnabled(GL.GL_SCISSOR_TEST), scissor);
		}
	}

	static final class StateSnapshot {
		final GLTransform projection;
		final GLTransform transform;
		final boolean renderColor;
		final boolean renderPicking;
		final float texturePickAlphaThreshold;
		final GLDepthState depthState;
		final float depth;
		final GLLighting2D lighting;
		final ShaderProgram filledShader;
		final Map<String, ShaderUniformValue> filledUniforms;
		final ShaderProgram textureShader;
		final Map<String, ShaderUniformValue> textureUniforms;
		final ShaderProgram textShader;
		final Map<String, ShaderUniformValue> textUniforms;
		final ShaderProgram uiShader;
		final Map<String, ShaderUniformValue> uiUniforms;

		StateSnapshot(GLTransform projection, GLTransform transform,
				boolean renderColor, boolean renderPicking,
				float texturePickAlphaThreshold, GLDepthState depthState,
				float depth, GLLighting2D lighting,
				ShaderProgram filledShader,
				Map<String, ShaderUniformValue> filledUniforms,
				ShaderProgram textureShader,
				Map<String, ShaderUniformValue> textureUniforms,
				ShaderProgram textShader,
				Map<String, ShaderUniformValue> textUniforms,
				ShaderProgram uiShader,
				Map<String, ShaderUniformValue> uiUniforms) {
			this.projection = projection.copy();
			this.transform = transform.copy();
			this.renderColor = renderColor;
			this.renderPicking = renderPicking;
			this.texturePickAlphaThreshold = texturePickAlphaThreshold;
			this.depthState = depthState;
			this.depth = depth;
			this.lighting = lighting;
			this.filledShader = filledShader;
			this.filledUniforms = Map.copyOf(filledUniforms);
			this.textureShader = textureShader;
			this.textureUniforms = Map.copyOf(textureUniforms);
			this.textShader = textShader;
			this.textUniforms = Map.copyOf(textUniforms);
			this.uiShader = uiShader;
			this.uiUniforms = Map.copyOf(uiUniforms);
		}
	}

	private record ScissorState(boolean enabled, int[] bounds) {
	}

	private record LayerPathState(GLTransform transform,
			GLTransform worldToScreen, GLTransform projection,
			float curveTolerance) {
	}
}
