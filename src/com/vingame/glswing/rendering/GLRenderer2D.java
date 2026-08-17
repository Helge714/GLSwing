package com.vingame.glswing.rendering;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.batch.FilledBatch;
import com.vingame.glswing.rendering.batch.TextBatch;
import com.vingame.glswing.rendering.batch.TextureBatch;
import com.vingame.glswing.rendering.batch.UIQuadBatch;
import com.vingame.glswing.rendering.internal.LayerStack;
import com.vingame.glswing.rendering.internal.LayerEffectCommand;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.PickFrameRecords;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;
import com.vingame.glswing.rendering.internal.TexturedVertexFormat;
import com.vingame.glswing.rendering.internal.UIQuadInstanceFormat;
import com.vingame.glswing.rendering.internal.VertexModifier;

/** Owns the GPU rendering state and batches for one GLSwing panel. */
public final class GLRenderer2D {

	private final FilledBatch filledBatch;
	private final TextureBatch textureBatch;
	private final TextBatch textBatch;
	private final UIQuadBatch uiQuadBatch;
	private final PickFramebuffer picking;
	private final LayerStack layers;
	private final GLTextureManager textureManager = new GLTextureManager();
	private final PickFrameRecords pickRecords = new PickFrameRecords();
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
	private final Map<String, ShaderUniformValue> filledUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> textureUniforms = new LinkedHashMap<>();
	private final Map<String, ShaderUniformValue> textUniforms = new LinkedHashMap<>();
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

	private GLRenderer2D(FilledBatch filledBatch, TextureBatch textureBatch,
			TextBatch textBatch, UIQuadBatch uiQuadBatch,
			PickFramebuffer picking, LayerStack layers) {
		this.filledBatch = filledBatch;
		this.textureBatch = textureBatch;
		this.textBatch = textBatch;
		this.uiQuadBatch = uiQuadBatch;
		this.picking = picking;
		this.layers = layers;
		context = new GLContext2D(this);
	}

	public static GLRenderer2D create(GL3 gl) {
		FilledBatch filledBatch = FilledBatch.create(gl);
		TextureBatch textureBatch = null;
		TextBatch textBatch = null;
		UIQuadBatch uiQuadBatch = null;
		PickFramebuffer picking = null;
		LayerStack layers = null;
		try {
			textureBatch = TextureBatch.create(gl);
			textBatch = TextBatch.create(gl);
			uiQuadBatch = UIQuadBatch.create(gl);
			picking = PickFramebuffer.create(gl);
			layers = LayerStack.create(gl);
			return new GLRenderer2D(filledBatch, textureBatch, textBatch,
					uiQuadBatch, picking, layers);
		} catch (RuntimeException error) {
			if (layers != null)
				layers.dispose(gl);
			if (picking != null)
				picking.dispose(gl);
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
		activeBatch = BatchType.NONE;
		filledShaderOverride = null;
		textureShaderOverride = null;
		textShaderOverride = null;
		filledUniforms.clear();
		textureUniforms.clear();
		textUniforms.clear();
		transform.setToIdentity();
		recordingReplayBase = null;
		replayParameterDepth = 0;
		colorRenderingEnabled = true;
		pickingRenderingEnabled = true;
		texturePickAlphaThreshold = GLContext2D.DEFAULT_TEXTURE_PICK_ALPHA_THRESHOLD;
		redrawBounds = null;
		clipStack.clear();
		context.reset(logicalWidth, logicalHeight);

		gl.glEnable(GL.GL_MULTISAMPLE);
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
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
		if (rebuildingPickingFrame && openLayers == 0)
			picking.requestSnapshot(gl, pickRecords);
		if (openLayers != 0)
			layers.abortFrame(gl);
		while (!clipStack.isEmpty())
			restoreScissor(clipStack.remove(clipStack.size() - 1));
		gl.glDisable(GL.GL_BLEND);
		frameActive = false;
		rebuildingPickingFrame = false;
		gl = null;
		if (openLayers != 0)
			throw new IllegalStateException("Frame ended with " + openLayers
					+ " unclosed layer" + (openLayers == 1 ? "" : "s"));
		if (openClips != 0)
			throw new IllegalStateException("Frame ended with " + openClips
					+ " unclosed clip" + (openClips == 1 ? "" : "s"));
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
			float alpha = color.getAlpha() / 255f;
			float premultiply = layers.depth() == 0 ? 1 : alpha;
			gl.glClearColor(color.getRed() / 255f * premultiply,
					color.getGreen() / 255f * premultiply,
					color.getBlue() / 255f * premultiply, alpha);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		}
		if (pickingRenderingEnabled)
			picking.clear(gl);
	}

	void beginLayer() {
		requireFrame();
		flush();
		layers.beginLayer(gl);
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
	}

	void beginLayer(float x, float y, float width, float height,
			float screenInset) {
		requireFrame();
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
			effects.add(new LayerEffectCommand(effect, uniforms));
		}
		flush();
		drawCallCount += layers.endLayer(gl, composite.getOpacity(),
				composite.getBlendMode(), effects);
	}

	int layerDepth() {
		requireFrame();
		return layers.depth();
	}

	void setRenderOutputs(boolean renderColor, boolean renderPicking) {
		requireFrame();
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
		filledBatch.dispose(gl);
		textureBatch.dispose(gl);
		textBatch.dispose(gl);
		uiQuadBatch.dispose(gl);
		picking.dispose(gl);
		layers.dispose(gl);
		textureManager.dispose(gl);
	}

	GLTextureRegion resolveImage(GLImage image) {
		requireFrame();
		return textureManager.resolve(gl, image);
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

	StateSnapshot captureState() {
		requireFrame();
		return new StateSnapshot(
				projection, transform,
				colorRenderingEnabled, pickingRenderingEnabled,
				texturePickAlphaThreshold,
				filledShaderOverride, filledUniforms,
				textureShaderOverride, textureUniforms,
				textShaderOverride, textUniforms);
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
		// Render outputs are ambient for the same reason: a recording built during
		// a color-only damage frame must not permanently disable picking when it is
		// replayed in a later full frame. Explicit setRenderOutputs commands remain
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
		validateRecordedBatchState(BatchType.FILLED,
				state.filledShader, state.filledUniforms);
		validateRecordedBatchState(BatchType.TEXTURE,
				state.textureShader, state.textureUniforms);
		validateRecordedBatchState(BatchType.TEXT,
				state.textShader, state.textUniforms);

		GLTransform targetProjection = inheritedProjection == null
				? state.projection : inheritedProjection;
		boolean projectionChanged = !projection.equals(targetProjection);
		boolean outputsChanged = colorRenderingEnabled != targetRenderColor
				|| pickingRenderingEnabled != targetRenderPicking;
		boolean textureThresholdChanged =
				texturePickAlphaThreshold != state.texturePickAlphaThreshold;
		boolean filledStateChanged = filledShaderOverride != state.filledShader
				|| !filledUniforms.equals(state.filledUniforms);
		boolean textureStateChanged = textureShaderOverride != state.textureShader
				|| !textureUniforms.equals(state.textureUniforms);
		boolean textStateChanged = textShaderOverride != state.textShader
				|| !textUniforms.equals(state.textUniforms);
		boolean activeStateChanged = switch (activeBatch) {
		case FILLED -> filledStateChanged;
		case TEXTURE -> textureStateChanged || textureThresholdChanged;
		case TEXT -> textStateChanged;
		case UI -> textureThresholdChanged;
		case NONE -> false;
		};
		if (projectionChanged || outputsChanged || activeStateChanged)
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
		filledShaderOverride = state.filledShader;
		filledUniforms.clear();
		filledUniforms.putAll(state.filledUniforms);
		textureShaderOverride = state.textureShader;
		textureUniforms.clear();
		textureUniforms.putAll(state.textureUniforms);
		textShaderOverride = state.textShader;
		textUniforms.clear();
		textUniforms.putAll(state.textUniforms);
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
		activateBatch(BatchType.TEXTURE);
		int textureLimit = textureShaderOverride == null ? 16 : 1;
		if (!textureBatch.accepts(texture, textureLimit) || !textureBatch.canAdd(6))
			drawCallCount += textureBatch.flush(
					gl, projectionMatrix, textureShaderOverride, textureUniforms, picking,
					colorRenderingEnabled, pickingRenderingEnabled,
					texturePickAlphaThreshold);

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
					pickRecord, activeRecordingVertexModifier(), textureLimit);
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
					pickRecord, activeRecordingVertexModifier());
			offset += chunk * TexturedVertexFormat.FLOATS_PER_VERTEX;
			remaining -= chunk;
		}
	}

	void addUI(GLTexture texture, float[] instances,
			int pickId, GLMouseTarget mouseTarget,
			float automaticOriginX, float automaticOriginY) {
		requireFrame();
		if (instances == null || instances.length == 0
				|| instances.length % UIQuadInstanceFormat.FLOATS_PER_INSTANCE != 0)
			throw new IllegalArgumentException("UI instance data is invalid");
		int pickRecord = createPickRecord(
				pickId, mouseTarget, automaticOriginX, automaticOriginY);
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
					pickRecord, activeRecordingVertexModifier());
			offset += chunk * UIQuadInstanceFormat.FLOATS_PER_INSTANCE;
			remaining -= chunk;
		}
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
			drawCallCount += textureBatch.flush(
					gl, projectionMatrix, textureShaderOverride, textureUniforms, picking,
					colorRenderingEnabled, pickingRenderingEnabled,
					texturePickAlphaThreshold);
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

	void setFilledUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.FILLED, name, value, filledShaderOverride, filledUniforms);
	}

	void setTextureUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.TEXTURE, name, value, textureShaderOverride, textureUniforms);
	}

	void setTextUniform(String name, ShaderUniformValue value) {
		setUserUniform(BatchType.TEXT, name, value, textShaderOverride, textUniforms);
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
		if (pickId != GLContext2D.NO_PICK_ID && mouseTarget != null)
			throw new IllegalArgumentException(
					"Pick ID and mouse target are mutually exclusive");
		if (pickId == GLContext2D.NO_PICK_ID && mouseTarget == null)
			return 0;
		if (!pickingRenderingEnabled)
			return 0;
		pickTransform.setTransform(worldToScreen)
				.concatenate(transform)
				.translate(originX, originY);
		return pickRecords.add(pickId, mouseTarget, pickTransform);
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
		case UI -> throw new IllegalArgumentException(
				"The standard UI batch does not use type-specific shader overrides");
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
		case UI -> uiQuadBatch.flush(gl, projectionMatrix, picking,
				colorRenderingEnabled, pickingRenderingEnabled,
				texturePickAlphaThreshold, colorDeviceScaleX, colorDeviceScaleY);
		case NONE -> 0;
		};
	}

	private static boolean isManagedUniform(BatchType batch, String name) {
		return name.equals("uProjection")
				|| batch != BatchType.FILLED && name.equals("uTexture")
				|| batch == BatchType.TEXT && name.equals("uMsdfPixelRange");
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
		UI("UI");

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

	static final class StateSnapshot {
		final GLTransform projection;
		final GLTransform transform;
		final boolean renderColor;
		final boolean renderPicking;
		final float texturePickAlphaThreshold;
		final ShaderProgram filledShader;
		final Map<String, ShaderUniformValue> filledUniforms;
		final ShaderProgram textureShader;
		final Map<String, ShaderUniformValue> textureUniforms;
		final ShaderProgram textShader;
		final Map<String, ShaderUniformValue> textUniforms;

		StateSnapshot(GLTransform projection, GLTransform transform,
				boolean renderColor, boolean renderPicking,
				float texturePickAlphaThreshold,
				ShaderProgram filledShader,
				Map<String, ShaderUniformValue> filledUniforms,
				ShaderProgram textureShader,
				Map<String, ShaderUniformValue> textureUniforms,
				ShaderProgram textShader,
				Map<String, ShaderUniformValue> textUniforms) {
			this.projection = projection.copy();
			this.transform = transform.copy();
			this.renderColor = renderColor;
			this.renderPicking = renderPicking;
			this.texturePickAlphaThreshold = texturePickAlphaThreshold;
			this.filledShader = filledShader;
			this.filledUniforms = Map.copyOf(filledUniforms);
			this.textureShader = textureShader;
			this.textureUniforms = Map.copyOf(textureUniforms);
			this.textShader = textShader;
			this.textUniforms = Map.copyOf(textUniforms);
		}
	}

	private record ScissorState(boolean enabled, int[] bounds) {
	}
}
