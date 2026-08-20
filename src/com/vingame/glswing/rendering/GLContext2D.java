package com.vingame.glswing.rendering;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.rendering.internal.FilledVertexFormat;
import com.vingame.glswing.rendering.internal.CurvedShapeBuilder;
import com.vingame.glswing.rendering.internal.PolygonTessellator;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;
import com.vingame.glswing.rendering.internal.StrokeGeometryCompiler;
import com.vingame.glswing.rendering.internal.TexturedVertexFormat;
import com.vingame.glswing.rendering.internal.UIQuadInstanceFormat;

/** Graphics2D-style state and drawing API for one renderer frame. */
public final class GLContext2D {
	public static final int NO_PICK_ID = 0;
	public static final Color DEFAULT_CLEAR_COLOR = new Color(15, 20, 31);
	public static final float DEFAULT_TEXTURE_PICK_ALPHA_THRESHOLD = 0.1f;
	private static final int MINIMUM_NONLINEAR_GRADIENT_SEGMENTS = 16;

	private final GLRenderer2D renderer;
	private Color color = Color.BLACK;
	private GLStroke stroke = GLStroke.DEFAULT;
	private float curveTolerance = GLStroke.CURVE_TOLERANCE_NORMAL;
	private ShaderProgram filledShader;
	private ShaderProgram textureShader;
	private ShaderProgram textShader;
	private ShaderProgram uiShader;
	private int pickId = NO_PICK_ID;
	private GLMouseTarget mouseTarget;
	private boolean mouseOriginDefined;
	private float mouseOriginX;
	private float mouseOriginY;
	private float resolvedMouseOriginX;
	private float resolvedMouseOriginY;
	private float logicalWidth;
	private float logicalHeight;
	private GLRecording.Builder recording;
	private GLRenderer2D.StateSnapshot recordingOuterRendererState;
	private ContextState recordingOuterContextState;
	private final List<ContextState> renderTargetContextStates = new ArrayList<>();

	GLContext2D(GLRenderer2D renderer) {
		this.renderer = renderer;
	}

	void reset(float logicalWidth, float logicalHeight) {
		color = Color.BLACK;
		stroke = GLStroke.DEFAULT;
		curveTolerance = GLStroke.CURVE_TOLERANCE_NORMAL;
		filledShader = null;
		textureShader = null;
		textShader = null;
		uiShader = null;
		pickId = NO_PICK_ID;
		mouseTarget = null;
		mouseOriginDefined = false;
		recording = null;
		recordingOuterRendererState = null;
		recordingOuterContextState = null;
		renderTargetContextStates.clear();
		this.logicalWidth = logicalWidth;
		this.logicalHeight = logicalHeight;
	}

	public void setColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Drawing color must not be null");
		this.color = color;
	}

	public Color getColor() {
		return color;
	}

	/**
	 * Starts a local-coordinate recording. The current model transform is preserved
	 * outside the recording; recorded transform operations start at identity.
	 */
	public void beginRecording() {
		if (recording != null)
			throw new IllegalStateException("A recording is already active");
		recordingOuterRendererState = renderer.captureState();
		recordingOuterContextState = captureContextState();
		renderer.resetTransform();
		recording = GLRecording.builder(renderer);
	}

	/** Finishes and returns the current immutable recording. */
	public GLRecording endRecording() {
		if (recording == null)
			throw new IllegalStateException("No recording is active");
		try {
			return recording.finish();
		} finally {
			recording = null;
			restoreRecordingOuterState();
		}
	}

	/** Discards the active recording and restores the surrounding transform. */
	public void cancelRecording() {
		if (recording == null)
			throw new IllegalStateException("No recording is active");
		recording = null;
		restoreRecordingOuterState();
	}

	public boolean isRecording() {
		return recording != null;
	}

	/** Starts a mutable replay-parameter region inside the active recording. */
	public void beginParameters(GLRenderParameters parameters) {
		if (recording == null)
			throw new IllegalStateException(
					"Render parameter regions can only be defined while recording");
		if (parameters == null)
			throw new IllegalArgumentException("Render parameters must not be null");
		recording.beginParameters(parameters);
	}

	/** Ends the innermost replay-parameter region. */
	public void endParameters() {
		if (recording == null)
			throw new IllegalStateException(
					"Render parameter regions can only be ended while recording");
		recording.endParameters();
	}

	/**
	 * Records a safely delimited mutable parameter region. This is equivalent to
	 * {@link #beginParameters(GLRenderParameters)} and {@link #endParameters()},
	 * but also closes the region if {@code drawing} throws.
	 */
	public void withParameters(GLRenderParameters parameters, Runnable drawing) {
		if (drawing == null)
			throw new IllegalArgumentException("Parameter drawing must not be null");
		beginParameters(parameters);
		try {
			drawing.run();
		} finally {
			endParameters();
		}
	}

	/**
	 * Replays local recorded geometry below the current model transform. The
	 * surrounding transform is restored afterwards and compatible batches stay open.
	 */
	public void draw(GLRecording recording) {
		draw(recording, null);
	}

	/**
	 * Replays local recorded geometry with one mutable parameter set applied to the
	 * complete recording. The same recording may be drawn repeatedly with different
	 * parameter objects.
	 */
	public void draw(GLRecording recording, GLRenderParameters parameters) {
		if (recording == null)
			throw new IllegalArgumentException("Recording must not be null");
		if (this.recording != null)
			throw new IllegalStateException(
					"Drawing a recording while another recording is active is not supported yet");
		try {
			recording.replay(renderer, parameters);
		} finally {
			filledShader = renderer.currentFilledShader();
			textureShader = renderer.currentTextureShader();
			textShader = renderer.currentTextShader();
			uiShader = renderer.currentUIShader();
		}
	}

	/** Clears the currently enabled render outputs using the default dark color. */
	public void clear() {
		clear(DEFAULT_CLEAR_COLOR);
	}

	/**
	 * Flushes pending geometry, then clears the currently enabled render outputs.
	 * Calling this is optional and may occur at any point in a frame.
	 */
	public void clear(Color color) {
		Color actualColor = requireColor(color);
		submit(renderer -> renderer.clear(actualColor));
	}

	/** Returns an axis-aligned screen-space box for a local rectangle. */
	public float[] getScreenBounds(float x, float y, float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width < 0 || height < 0)
			throw new IllegalArgumentException("Bounds must be finite and non-negative");
		return renderer.screenBounds(x, y, width, height);
	}

	/** Returns whether a local rectangle intersects the current damage region. */
	public boolean intersectsRedrawBounds(float x, float y,
			float width, float height) {
		return renderer.intersectsRedrawBounds(x, y, width, height);
	}

	/**
	 * Restricts color and picking draws to the transformed screen-space bounding
	 * box of a local rectangle. Clips may be nested and are inexpensive scissor
	 * state changes; use a rounded layer effect when curved clipping is required.
	 */
	public void beginClip(float x, float y, float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width < 0 || height < 0)
			throw new IllegalArgumentException(
					"Clip bounds must be finite and non-negative");
		if (recording == null)
			renderer.beginClip(x, y, width, height);
		else
			recording.beginClip(target ->
					target.beginClip(x, y, width, height));
	}

	/** Restores the clip that was active before the matching beginClip call. */
	public void endClip() {
		if (recording == null)
			renderer.endClip();
		else
			recording.endClip(GLRenderer2D::endClip);
	}

	public int getClipDepth() {
		return renderer.clipDepth()
				+ (recording == null ? 0 : recording.openClips());
	}

	/**
	 * Starts a transparent offscreen color layer whose affected screen bounds
	 * are collected from the color geometry drawn into it. Layers may be nested;
	 * picking continues to be written directly to the independent picking
	 * framebuffer. Pending geometry is flushed before the render target changes.
	 */
	public void beginLayer() {
		if (recording == null) {
			renderer.beginLayer();
		} else {
			recording.beginLayer(GLRenderer2D::beginLayer);
		}
	}

	/**
	 * Starts a layer whose pixel work is limited to the given region in the
	 * current user coordinate system. Drawing is clipped to its transformed
	 * screen bounds; effects may extend beyond it by their declared padding.
	 */
	public void beginLayer(float x, float y, float width, float height) {
		validateLayerBounds(x, y, width, height);
		if (recording == null) {
			renderer.beginLayer(x, y, width, height);
		} else {
			recording.beginLayer(renderer -> renderer.beginLayer(x, y, width, height));
		}
	}

	/**
	 * Starts a bounded layer inset on every side by logical screen pixels. The
	 * inset is resolved during replay, so it remains stable under world zoom.
	 */
	public void beginLayer(float x, float y, float width, float height,
			float screenInset) {
		validateLayerBounds(x, y, width, height);
		if (!Float.isFinite(screenInset) || screenInset < 0)
			throw new IllegalArgumentException(
					"Layer screen inset must be finite and non-negative");
		if (recording == null) {
			renderer.beginLayer(x, y, width, height, screenInset);
		} else {
			recording.beginLayer(renderer -> renderer.beginLayer(
					x, y, width, height, screenInset));
		}
	}

	/** Composites the current layer into its parent at full opacity. */
	public void endLayer() {
		endLayer(1);
	}

	/**
	 * Composites the current layer into its parent with one shared opacity. This
	 * applies transparency once to the completed group instead of once per shape.
	 *
	 * @param opacity value from {@code 0} to {@code 1}, inclusive
	 */
	public void endLayer(float opacity) {
		endLayer(new GLComposite(opacity));
	}

	/** Composites the current layer using a blend mode and optional custom shader. */
	public void endLayer(GLComposite composite) {
		if (composite == null)
			throw new IllegalArgumentException("Layer composite must not be null");
		if (recording == null) {
			renderer.endLayer(composite);
		} else {
			recording.endLayer(renderer -> renderer.endLayer(composite));
		}
	}

	/** Composites the current layer at full opacity using {@code blendMode}. */
	public void endLayer(GLBlendMode blendMode) {
		endLayer(GLComposite.blend(blendMode));
	}

	/** Returns the number of currently open nested layers. */
	public int getLayerDepth() {
		return renderer.layerDepth() + (recording == null ? 0 : recording.openLayers());
	}

	/**
	 * Starts drawing into a persistent RGBA render target. Target coordinates use
	 * top-left pixels. Picking is available when the target was created with
	 * {@link GLRenderTargetOptions#withPicking(boolean)}. The surrounding state is
	 * restored by {@link #endRenderTarget()}.
	 */
	public void beginRenderTarget(GLRenderTarget target) {
		if (recording != null)
			throw new IllegalStateException(
					"Render-target updates cannot be captured in a recording");
		ContextState state = captureContextState();
		renderer.beginRenderTarget(target);
		renderTargetContextStates.add(state);
		logicalWidth = target.getWidth();
		logicalHeight = target.getHeight();
	}

	/** Restores the output and state active before the matching begin call. */
	public void endRenderTarget() {
		if (recording != null)
			throw new IllegalStateException(
					"A recording cannot contain render-target updates");
		if (renderTargetContextStates.isEmpty())
			throw new IllegalStateException(
					"endRenderTarget() requires a matching beginRenderTarget()");
		renderer.endRenderTarget();
		restoreContextState(renderTargetContextStates.remove(
				renderTargetContextStates.size() - 1));
	}

	public int getRenderTargetDepth() {
		return renderer.renderTargetDepth();
	}

	/** Runs one exception-safe persistent render-target pass. */
	public void withRenderTarget(GLRenderTarget target, Runnable drawing) {
		if (drawing == null)
			throw new IllegalArgumentException(
					"Render-target drawing must not be null");
		beginRenderTarget(target);
		try {
			drawing.run();
		} finally {
			endRenderTarget();
		}
	}

	/**
	 * Selects which framebuffer outputs receive subsequent draw commands. A change
	 * flushes pending geometry first. Both outputs are enabled at the start of each
	 * frame and both may be disabled temporarily.
	 */
	public void setOutputMask(boolean renderColor, boolean renderPicking) {
		executeAndRecord(renderer -> renderer.setOutputMask(renderColor, renderPicking));
	}

	public void setColorRenderingEnabled(boolean enabled) {
		setOutputMask(enabled, isPickingRenderingEnabled());
	}

	public boolean isColorRenderingEnabled() {
		return renderer.isColorRenderingEnabled();
	}

	public void setPickingRenderingEnabled(boolean enabled) {
		setOutputMask(isColorRenderingEnabled(), enabled);
	}

	public boolean isPickingRenderingEnabled() {
		return renderer.isPickingRenderingEnabled();
	}

	/**
	 * Sets the minimum effective alpha at which textured pixels are pickable.
	 * Texture alpha is multiplied by tint alpha before this comparison. Changing
	 * the value flushes a pending texture batch and does not affect filled shapes
	 * or text.
	 *
	 * @param threshold value from {@code 0} to {@code 1}, inclusive
	 */
	public void setTexturePickAlphaThreshold(float threshold) {
		executeAndRecord(renderer -> renderer.setTexturePickAlphaThreshold(threshold));
	}

	public float getTexturePickAlphaThreshold() {
		return renderer.getTexturePickAlphaThreshold();
	}

	/** Returns an independent copy of the current affine transform. */
	public GLTransform getTransform() {
		return renderer.getTransform();
	}

	/** Replaces the current affine transform by copying {@code transform}. */
	public void setTransform(GLTransform transform) {
		if (transform == null)
			throw new IllegalArgumentException("Transform must not be null");
		GLTransform stored = transform.copy();
		executeAndRecord(renderer -> renderer.setTransform(stored));
	}

	public void resetTransform() {
		executeAndRecord(GLRenderer2D::resetTransform);
	}

	public void translate(float x, float y) {
		executeAndRecord(renderer -> renderer.translate(x, y));
	}

	/** Rotates in radians; positive values appear clockwise in the top-left coordinate system. */
	public void rotate(float radians) {
		executeAndRecord(renderer -> renderer.rotate(radians));
	}

	/** Rotates in radians around an anchor in the current user coordinate system. */
	public void rotate(float radians, float anchorX, float anchorY) {
		executeAndRecord(renderer -> renderer.rotate(radians, anchorX, anchorY));
	}

	public void scale(float scale) {
		scale(scale, scale);
	}

	public void scale(float scaleX, float scaleY) {
		executeAndRecord(renderer -> renderer.scale(scaleX, scaleY));
	}

	/** Returns an independent copy of the current world-to-clip-space projection. */
	public GLTransform getProjection() {
		return renderer.getProjection();
	}

	/**
	 * Replaces the projection. Unlike a model-transform change, this flushes
	 * pending geometry because the projection is a draw-call uniform.
	 */
	public void setProjection(GLTransform projection) {
		if (projection == null)
			throw new IllegalArgumentException("Projection must not be null");
		GLTransform stored = projection.copy();
		executeAndRecord(renderer -> renderer.setProjection(stored));
	}

	/** Resolves and installs a projection provider for the current frame size. */
	public void setProjection(Projection2D projection) {
		setView(projection);
	}

	/** Resolves and installs a complete view-projection for the current frame. */
	public void setView(GLView2D view) {
		if (view == null)
			throw new IllegalArgumentException("View must not be null");
		GLTransform resolved = view.createViewProjection(logicalWidth, logicalHeight);
		if (resolved == null)
			throw new IllegalArgumentException("View provider returned null");
		setProjection(resolved);
	}

	public void setStrokeWidth(float strokeWidth) {
		stroke = stroke.withWidth(strokeWidth);
	}

	public float getStrokeWidth() {
		return stroke.getWidth();
	}

	public void setStrokeCap(GLStroke.Cap strokeCap) {
		stroke = stroke.withCap(strokeCap);
	}

	public GLStroke.Cap getStrokeCap() {
		return stroke.getCap();
	}

	public void setStrokeJoin(GLStroke.Join strokeJoin) {
		stroke = stroke.withJoin(strokeJoin);
	}

	public GLStroke.Join getStrokeJoin() {
		return stroke.getJoin();
	}

	/** Moves the stroke center along the path normal; zero keeps it centered. */
	public void setStrokeOffset(float strokeOffset) {
		stroke = stroke.withOffset(strokeOffset);
	}

	public float getStrokeOffset() {
		return stroke.getOffset();
	}

	public void setStrokeMiterLimit(float miterLimit) {
		stroke = stroke.withMiterLimit(miterLimit);
	}

	public float getStrokeMiterLimit() {
		return stroke.getMiterLimit();
	}

	public void setStrokeCurveTolerance(float curveTolerance) {
		stroke = stroke.withCurveTolerance(curveTolerance);
	}

	public float getStrokeCurveTolerance() {
		return stroke.getCurveTolerance();
	}

	public void setStrokeWidthSpace(GLStroke.WidthSpace widthSpace) {
		stroke = stroke.withWidthSpace(widthSpace);
	}

	public GLStroke.WidthSpace getStrokeWidthSpace() {
		return stroke.getWidthSpace();
	}

	/** Uses alternating visible and empty lengths, starting with a visible run. */
	public void setStrokeDash(float... dashArray) {
		stroke = stroke.withDash(dashArray);
	}

	/** Uses the supplied dash pattern and advances its start by {@code dashPhase}. */
	public void setStrokeDash(float[] dashArray, float dashPhase) {
		stroke = stroke.withDash(dashArray, dashPhase);
	}

	/** Returns a defensive copy, or {@code null} while the stroke is solid. */
	public float[] getStrokeDash() {
		return stroke.getDashArray();
	}

	public void setStrokeDashPhase(float dashPhase) {
		stroke = stroke.withDashPhase(dashPhase);
	}

	public float getStrokeDashPhase() {
		return stroke.getDashPhase();
	}

	public void clearStrokeDash() {
		stroke = stroke.withoutDash();
	}

	public void setStroke(GLStroke stroke) {
		if (stroke == null)
			throw new IllegalArgumentException("Stroke must not be null");
		this.stroke = stroke;
	}

	public GLStroke getStroke() {
		return stroke;
	}

	/** Sets the maximum screen-space deviation used to tessellate filled curves. */
	public void setCurveTolerance(float curveTolerance) {
		if (!Float.isFinite(curveTolerance) || curveTolerance <= 0)
			throw new IllegalArgumentException(
					"Curve tolerance must be finite and greater than zero");
		this.curveTolerance = curveTolerance;
	}

	public float getCurveTolerance() {
		return curveTolerance;
	}

	public void setPickId(int pickId) {
		this.pickId = requirePickId(pickId);
		mouseTarget = null;
		mouseOriginDefined = false;
	}

	public int getPickId() {
		return pickId;
	}

	public void clearPickId() {
		pickId = NO_PICK_ID;
	}

	/**
	 * Routes subsequent shapes to this object. The target is referenced only by
	 * the current picking frame and replaces a previously selected integer ID.
	 * The first following draw command supplies the automatic local origin; that
	 * origin remains stable until the target is changed or cleared.
	 */
	public void setMouseTarget(GLMouseTarget mouseTarget) {
		this.mouseTarget = requireMouseTarget(mouseTarget);
		pickId = NO_PICK_ID;
		mouseOriginDefined = false;
	}

	/**
	 * Routes subsequent shapes to this object using an explicit origin in the
	 * current user coordinate system for object-local mouse coordinates.
	 */
	public void setMouseTarget(GLMouseTarget mouseTarget, float originX, float originY) {
		if (!Float.isFinite(originX) || !Float.isFinite(originY))
			throw new IllegalArgumentException("Mouse target origin must be finite");
		this.mouseTarget = requireMouseTarget(mouseTarget);
		pickId = NO_PICK_ID;
		mouseOriginX = originX;
		mouseOriginY = originY;
		mouseOriginDefined = true;
	}

	public GLMouseTarget getMouseTarget() {
		return mouseTarget;
	}

	public void clearMouseTarget() {
		mouseTarget = null;
		mouseOriginDefined = false;
	}

	void resolveMouseOrigin(GLMouseTarget target, float automaticX, float automaticY) {
		if (target != null && target == mouseTarget) {
			if (!mouseOriginDefined) {
				mouseOriginX = automaticX;
				mouseOriginY = automaticY;
				mouseOriginDefined = true;
			}
			resolvedMouseOriginX = mouseOriginX;
			resolvedMouseOriginY = mouseOriginY;
		} else {
			resolvedMouseOriginX = automaticX;
			resolvedMouseOriginY = automaticY;
		}
	}

	/** Sets the shader used by subsequent filled shapes and strokes. */
	public void setFilledShader(ShaderProgram shader) {
		executeAndRecord(renderer -> renderer.setFilledShader(shader));
		filledShader = shader;
	}

	public ShaderProgram getFilledShader() {
		return filledShader;
	}

	public void useDefaultFilledShader() {
		setFilledShader(null);
	}

	/** Sets the shader used by subsequent textured quads. */
	public void setTextureShader(ShaderProgram shader) {
		executeAndRecord(renderer -> renderer.setTextureShader(shader));
		textureShader = shader;
	}

	public ShaderProgram getTextureShader() {
		return textureShader;
	}

	public void useDefaultTextureShader() {
		setTextureShader(null);
	}

	/** Sets the shader used by subsequent MSDF text. */
	public void setTextShader(ShaderProgram shader) {
		executeAndRecord(renderer -> renderer.setTextShader(shader));
		textShader = shader;
	}

	public ShaderProgram getTextShader() {
		return textShader;
	}

	public void useDefaultTextShader() {
		setTextShader(null);
	}

	/**
	 * Sets a complete shader replacement for the universal instanced UI batch.
	 * The shader must implement every UI instance mode and the documented UI
	 * attribute/uniform contract. Picking continues to use the internal pick shader.
	 */
	public void setUIShader(ShaderProgram shader) {
		executeAndRecord(renderer -> renderer.setUIShader(shader));
		uiShader = shader;
	}

	public ShaderProgram getUIShader() {
		return uiShader;
	}

	public void useDefaultUIShader() {
		setUIShader(null);
	}

	public void setFilledUniform(String name, float value) {
		setFilledUniform(name, ShaderUniformValue.of(value));
	}

	public void setFilledUniform(String name, float x, float y) {
		setFilledUniform(name, ShaderUniformValue.of(x, y));
	}

	public void setFilledUniform(String name, float x, float y, float z) {
		setFilledUniform(name, ShaderUniformValue.of(x, y, z));
	}

	public void setFilledUniform(String name, float x, float y, float z, float w) {
		setFilledUniform(name, ShaderUniformValue.of(x, y, z, w));
	}

	public void setFilledUniform(String name, int value) {
		setFilledUniform(name, ShaderUniformValue.of(value));
	}

	public void setFilledUniformMatrix4(String name, float[] matrix) {
		setFilledUniform(name, ShaderUniformValue.matrix4(matrix));
	}

	public void setTextureUniform(String name, float value) {
		setTextureUniform(name, ShaderUniformValue.of(value));
	}

	public void setTextureUniform(String name, float x, float y) {
		setTextureUniform(name, ShaderUniformValue.of(x, y));
	}

	public void setTextureUniform(String name, float x, float y, float z) {
		setTextureUniform(name, ShaderUniformValue.of(x, y, z));
	}

	public void setTextureUniform(String name, float x, float y, float z, float w) {
		setTextureUniform(name, ShaderUniformValue.of(x, y, z, w));
	}

	public void setTextureUniform(String name, int value) {
		setTextureUniform(name, ShaderUniformValue.of(value));
	}

	public void setTextureUniformMatrix4(String name, float[] matrix) {
		setTextureUniform(name, ShaderUniformValue.matrix4(matrix));
	}

	public void setTextUniform(String name, float value) {
		setTextUniform(name, ShaderUniformValue.of(value));
	}

	public void setTextUniform(String name, float x, float y) {
		setTextUniform(name, ShaderUniformValue.of(x, y));
	}

	public void setTextUniform(String name, float x, float y, float z) {
		setTextUniform(name, ShaderUniformValue.of(x, y, z));
	}

	public void setTextUniform(String name, float x, float y, float z, float w) {
		setTextUniform(name, ShaderUniformValue.of(x, y, z, w));
	}

	public void setTextUniform(String name, int value) {
		setTextUniform(name, ShaderUniformValue.of(value));
	}

	public void setTextUniformMatrix4(String name, float[] matrix) {
		setTextUniform(name, ShaderUniformValue.matrix4(matrix));
	}

	public void setUIUniform(String name, float value) {
		setUIUniform(name, ShaderUniformValue.of(value));
	}

	public void setUIUniform(String name, float x, float y) {
		setUIUniform(name, ShaderUniformValue.of(x, y));
	}

	public void setUIUniform(String name, float x, float y, float z) {
		setUIUniform(name, ShaderUniformValue.of(x, y, z));
	}

	public void setUIUniform(String name, float x, float y, float z, float w) {
		setUIUniform(name, ShaderUniformValue.of(x, y, z, w));
	}

	public void setUIUniform(String name, int value) {
		setUIUniform(name, ShaderUniformValue.of(value));
	}

	public void setUIUniformMatrix4(String name, float[] matrix) {
		setUIUniform(name, ShaderUniformValue.matrix4(matrix));
	}

	private void setFilledUniform(String name, ShaderUniformValue value) {
		executeAndRecord(renderer -> renderer.setFilledUniform(name, value));
	}

	private void setTextureUniform(String name, ShaderUniformValue value) {
		executeAndRecord(renderer -> renderer.setTextureUniform(name, value));
	}

	private void setTextUniform(String name, ShaderUniformValue value) {
		executeAndRecord(renderer -> renderer.setTextUniform(name, value));
	}

	private void setUIUniform(String name, ShaderUniformValue value) {
		executeAndRecord(renderer -> renderer.setUIUniform(name, value));
	}

	public void drawLine(float x1, float y1, float x2, float y2) {
		drawLine(x1, y1, x2, y2, color, pickId, mouseTarget);
	}

	public void drawLine(float x1, float y1, float x2, float y2, int pickId) {
		drawLine(x1, y1, x2, y2, color, pickId);
	}

	public void drawLine(float x1, float y1, float x2, float y2, Color color) {
		drawLine(x1, y1, x2, y2, color, pickId, mouseTarget);
	}

	public void drawLine(float x1, float y1, float x2, float y2,
			GLMouseTarget mouseTarget) {
		drawLine(x1, y1, x2, y2, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawLine(float x1, float y1, float x2, float y2,
			Color color, GLMouseTarget mouseTarget) {
		drawLine(x1, y1, x2, y2, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawLine(float x1, float y1, float x2, float y2,
			Color color, int pickId) {
		drawLine(x1, y1, x2, y2, color, requirePickId(pickId), null);
	}

	private void drawLine(float x1, float y1, float x2, float y2,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		GLStroke lineStroke = stroke;
		Color lineColor = requireColor(color);
		float[] vertices = StrokeGeometryCompiler.line(
				x1, y1, x2, y2, lineStroke, lineColor,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (lineStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN
				&& recordDynamicFilled(replayRenderer ->
						StrokeGeometryCompiler.line(x1, y1, x2, y2,
								lineStroke, lineColor,
								replayRenderer.currentTransform(),
								replayRenderer.currentWorldToScreen(),
								replayRenderer.currentScreenToWorld()),
						pickId, mouseTarget, x1, y1))
			return;
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, x1, y1);
	}

	public void drawLine(float x1, float y1, Color color1,
			float x2, float y2, Color color2) {
		drawLine(x1, y1, color1, x2, y2, color2, pickId, mouseTarget);
	}

	public void drawLine(float x1, float y1, Color color1,
			float x2, float y2, Color color2, int pickId) {
		drawLine(x1, y1, color1, x2, y2, color2, requirePickId(pickId), null);
	}

	public void drawLine(float x1, float y1, Color color1,
			float x2, float y2, Color color2, GLMouseTarget mouseTarget) {
		drawLine(x1, y1, color1, x2, y2, color2,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawLine(float x1, float y1, Color color1,
			float x2, float y2, Color color2, int pickId, GLMouseTarget mouseTarget) {
		GLStroke lineStroke = stroke;
		Color firstColor = requireColor(color1);
		Color secondColor = requireColor(color2);
		float[] vertices = StrokeGeometryCompiler.line(
				x1, y1, firstColor, x2, y2, secondColor, lineStroke,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (lineStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN
				&& recordDynamicFilled(replayRenderer ->
						StrokeGeometryCompiler.line(x1, y1, firstColor,
								x2, y2, secondColor, lineStroke,
								replayRenderer.currentTransform(),
								replayRenderer.currentWorldToScreen(),
								replayRenderer.currentScreenToWorld()),
						pickId, mouseTarget, x1, y1))
			return;
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, x1, y1);
	}

	public void drawPolyline(float[] points) {
		drawPolyline(points, color, pickId, mouseTarget);
	}

	public void drawPolyline(float[] points, int pickId) {
		drawPolyline(points, color, pickId);
	}

	public void drawPolyline(float[] points, GLMouseTarget mouseTarget) {
		drawPolyline(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawPolyline(float[] points, Color color) {
		drawPolyline(points, color, pickId, mouseTarget);
	}

	public void drawPolyline(float[] points, Color color, int pickId) {
		drawPolyline(points, color, requirePickId(pickId), null);
	}

	public void drawPolyline(float[] points, Color color, GLMouseTarget mouseTarget) {
		drawPolyline(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawPolyline(float[] points, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		GLStroke pathStroke = stroke;
		Color pathColor = requireColor(color);
		float[] vertices = StrokeGeometryCompiler.path(
				points, false, pathStroke, pathColor,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (recording != null
				&& pathStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN) {
			float[] recordedPoints = points.clone();
			if (recordDynamicFilled(replayRenderer ->
					StrokeGeometryCompiler.path(recordedPoints, false,
							pathStroke, pathColor,
							replayRenderer.currentTransform(),
							replayRenderer.currentWorldToScreen(),
							replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, points[0], points[1]))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	/** Draws a cubic Bezier curve using the current stroke and color. */
	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, color, pickId, mouseTarget);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color color) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, color, pickId, mouseTarget);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color color, int pickId) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, color, requirePickId(pickId), null);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color color, GLMouseTarget mouseTarget) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, color, NO_PICK_ID,
				requireMouseTarget(mouseTarget));
	}

	private void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		float[] points = cubicBezierPoints(x0, y0, control1X, control1Y,
				control2X, control2Y, x1, y1);
		drawPolyline(points, color, pickId, mouseTarget);
	}

	/** Draws a cubic Bezier with colors interpolated from start to end. */
	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor, pickId, mouseTarget);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor, int pickId) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor,
				requirePickId(pickId), null);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			GLMouseTarget mouseTarget) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor, NO_PICK_ID,
				requireMouseTarget(mouseTarget));
	}

	private void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			int pickId, GLMouseTarget mouseTarget) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor,
				GLColorInterpolation.SRGB, pickId, mouseTarget);
	}

	/** Draws a color-gradient cubic Bezier with a custom interpolation strategy. */
	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			GLColorInterpolator interpolation) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor, interpolation,
				pickId, mouseTarget);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			GLColorInterpolator interpolation, int pickId) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor, interpolation,
				requirePickId(pickId), null);
	}

	public void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			GLColorInterpolator interpolation, GLMouseTarget mouseTarget) {
		drawCubicBezier(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, startColor, endColor, interpolation,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawCubicBezier(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, Color startColor, Color endColor,
			GLColorInterpolator interpolation, int pickId,
			GLMouseTarget mouseTarget) {
		startColor = requireColor(startColor);
		endColor = requireColor(endColor);
		if (interpolation == null)
			throw new IllegalArgumentException(
					"Color interpolation must not be null");
		float[] points = cubicBezierPoints(x0, y0, control1X, control1Y,
				control2X, control2Y, x1, y1,
				interpolation == GLColorInterpolation.SRGB ? 1
						: MINIMUM_NONLINEAR_GRADIENT_SEGMENTS);
		Color[] colors = new Color[points.length / 2];
		for (int point = 0; point < colors.length; point++) {
			float amount = point / (float) (colors.length - 1);
			colors[point] = requireColor(interpolation.interpolate(
					startColor, endColor, amount));
		}
		drawPolyline(points, colors, pickId, mouseTarget);
	}

	/** Draws an open path using one interpolated color per x/y point pair. */
	public void drawPolyline(float[] points, Color[] colors) {
		drawPolyline(points, colors, pickId, mouseTarget);
	}

	public void drawPolyline(float[] points, Color[] colors, int pickId) {
		drawPolyline(points, colors, requirePickId(pickId), null);
	}

	public void drawPolyline(float[] points, Color[] colors, GLMouseTarget mouseTarget) {
		drawPolyline(points, colors, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawPolyline(float[] points, Color[] colors, int pickId,
			GLMouseTarget mouseTarget) {
		GLStroke pathStroke = stroke;
		float[] vertices = StrokeGeometryCompiler.path(
				points, false, pathStroke, colors,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (recording != null
				&& pathStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN) {
			float[] recordedPoints = points.clone();
			Color[] recordedColors = colors.clone();
			if (recordDynamicFilled(replayRenderer ->
					StrokeGeometryCompiler.path(recordedPoints, false,
							pathStroke, recordedColors,
							replayRenderer.currentTransform(),
							replayRenderer.currentWorldToScreen(),
							replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, points[0], points[1]))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	public void drawPolygon(float[] points) {
		drawPolygon(points, color, pickId, mouseTarget);
	}

	public void drawPolygon(float[] points, int pickId) {
		drawPolygon(points, color, pickId);
	}

	public void drawPolygon(float[] points, GLMouseTarget mouseTarget) {
		drawPolygon(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawPolygon(float[] points, Color color) {
		drawPolygon(points, color, pickId, mouseTarget);
	}

	public void drawPolygon(float[] points, Color color, int pickId) {
		drawPolygon(points, color, requirePickId(pickId), null);
	}

	public void drawPolygon(float[] points, Color color, GLMouseTarget mouseTarget) {
		drawPolygon(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawPolygon(float[] points, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		GLStroke pathStroke = stroke;
		Color pathColor = requireColor(color);
		float[] vertices = StrokeGeometryCompiler.path(
				points, true, pathStroke, pathColor,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (recording != null
				&& pathStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN) {
			float[] recordedPoints = points.clone();
			if (recordDynamicFilled(replayRenderer ->
					StrokeGeometryCompiler.path(recordedPoints, true,
							pathStroke, pathColor,
							replayRenderer.currentTransform(),
							replayRenderer.currentWorldToScreen(),
							replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, points[0], points[1]))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	/** Draws a closed path using one interpolated color per x/y point pair. */
	public void drawPolygon(float[] points, Color[] colors) {
		drawPolygon(points, colors, pickId, mouseTarget);
	}

	public void drawPolygon(float[] points, Color[] colors, int pickId) {
		drawPolygon(points, colors, requirePickId(pickId), null);
	}

	public void drawPolygon(float[] points, Color[] colors, GLMouseTarget mouseTarget) {
		drawPolygon(points, colors, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawPolygon(float[] points, Color[] colors, int pickId,
			GLMouseTarget mouseTarget) {
		GLStroke pathStroke = stroke;
		float[] vertices = StrokeGeometryCompiler.path(
				points, true, pathStroke, colors,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (recording != null
				&& pathStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN) {
			float[] recordedPoints = points.clone();
			Color[] recordedColors = colors.clone();
			if (recordDynamicFilled(replayRenderer ->
					StrokeGeometryCompiler.path(recordedPoints, true,
							pathStroke, recordedColors,
							replayRenderer.currentTransform(),
							replayRenderer.currentWorldToScreen(),
							replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, points[0], points[1]))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	public void fillPath(GLPath2D path) {
		fillPath(path, color, pickId, mouseTarget);
	}

	public void fillPath(GLPath2D path, Color color) {
		fillPath(path, color, pickId, mouseTarget);
	}

	public void fillPath(GLPath2D path, int pickId) {
		fillPath(path, color, requirePickId(pickId), null);
	}

	public void fillPath(GLPath2D path, Color color, int pickId) {
		fillPath(path, color, requirePickId(pickId), null);
	}

	public void fillPath(GLPath2D path, GLMouseTarget mouseTarget) {
		fillPath(path, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void fillPath(GLPath2D path, Color color, GLMouseTarget mouseTarget) {
		fillPath(path, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	/** Fills a path with a color source evaluated in local path coordinates. */
	public void fillPath(GLPath2D path, GLPaint2D paint) {
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");
		if (paint == null)
			throw new IllegalArgumentException("Path paint must not be null");
		if (path.isEmpty())
			return;
		float tolerance = curveTolerance;
		GLPath2D.Bounds bounds = path.getBounds();
		float[] vertices = PathGeometryCompiler.fill(path, paint, tolerance,
				renderer.currentTransform(), renderer.currentWorldToScreen());
		if (recording != null) {
			GLPath2D recordedPath = path.copy();
			if (recordDynamicFilled(replayRenderer -> PathGeometryCompiler.fill(
					recordedPath, paint, tolerance,
					replayRenderer.currentTransform(),
					replayRenderer.currentWorldToScreen()),
					pickId, mouseTarget, bounds.x(), bounds.y()))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget,
				bounds.x(), bounds.y());
	}

	private void fillPath(GLPath2D path, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");
		Color pathColor = requireColor(color);
		if (path.isEmpty())
			return;
		float tolerance = curveTolerance;
		GLPath2D.Bounds bounds = path.getBounds();
		float[] vertices = PathGeometryCompiler.fill(path, pathColor, tolerance,
				renderer.currentTransform(), renderer.currentWorldToScreen());
		if (recording != null) {
			GLPath2D recordedPath = path.copy();
			if (recordDynamicFilled(replayRenderer -> PathGeometryCompiler.fill(
					recordedPath, pathColor, tolerance,
					replayRenderer.currentTransform(),
					replayRenderer.currentWorldToScreen()),
					pickId, mouseTarget, bounds.x(), bounds.y()))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget,
				bounds.x(), bounds.y());
	}

	public void drawPath(GLPath2D path) {
		drawPath(path, color, pickId, mouseTarget);
	}

	public void drawPath(GLPath2D path, Color color) {
		drawPath(path, color, pickId, mouseTarget);
	}

	public void drawPath(GLPath2D path, int pickId) {
		drawPath(path, color, requirePickId(pickId), null);
	}

	public void drawPath(GLPath2D path, Color color, int pickId) {
		drawPath(path, color, requirePickId(pickId), null);
	}

	public void drawPath(GLPath2D path, GLMouseTarget mouseTarget) {
		drawPath(path, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawPath(GLPath2D path, Color color, GLMouseTarget mouseTarget) {
		drawPath(path, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawPath(GLPath2D path, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");
		Color pathColor = requireColor(color);
		if (path.isEmpty())
			return;
		GLStroke pathStroke = stroke;
		float tolerance = curveTolerance;
		GLPath2D.Bounds bounds = path.getBounds();
		float[] vertices = PathGeometryCompiler.stroke(path, pathStroke,
				pathColor, tolerance, renderer.currentTransform(),
				renderer.currentWorldToScreen(), renderer.currentScreenToWorld());
		if (recording != null) {
			GLPath2D recordedPath = path.copy();
			if (recordDynamicFilled(replayRenderer -> PathGeometryCompiler.stroke(
					recordedPath, pathStroke, pathColor, tolerance,
					replayRenderer.currentTransform(),
					replayRenderer.currentWorldToScreen(),
					replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, bounds.x(), bounds.y()))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget,
				bounds.x(), bounds.y());
	}

	public void drawRect(float x, float y, float width, float height) {
		drawRect(x, y, width, height, color, pickId, mouseTarget);
	}

	public void drawRect(float x, float y, float width, float height, int pickId) {
		drawRect(x, y, width, height, color, pickId);
	}

	public void drawRect(float x, float y, float width, float height,
			GLMouseTarget mouseTarget) {
		drawRect(x, y, width, height, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawRect(float x, float y, float width, float height, Color color) {
		drawRect(x, y, width, height, color, pickId, mouseTarget);
	}

	public void drawRect(float x, float y, float width, float height,
			Color color, int pickId) {
		drawRect(x, y, width, height, color, requirePickId(pickId), null);
	}

	public void drawRect(float x, float y, float width, float height,
			Color color, GLMouseTarget mouseTarget) {
		drawRect(x, y, width, height, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawRect(float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		drawPolygon(new float[] { x, y, x + width, y, x + width, y + height, x, y + height },
				color, pickId, mouseTarget);
	}

	/** Draws a rectangle using colors for top-left, top-right, bottom-right and bottom-left. */
	public void drawRect(float x, float y, float width, float height, Color[] colors) {
		drawRect(x, y, width, height, colors, pickId, mouseTarget);
	}

	public void drawRect(float x, float y, float width, float height,
			Color[] colors, int pickId) {
		drawRect(x, y, width, height, colors, requirePickId(pickId), null);
	}

	public void drawRect(float x, float y, float width, float height,
			Color[] colors, GLMouseTarget mouseTarget) {
		drawRect(x, y, width, height, colors, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawRect(float x, float y, float width, float height,
			Color[] colors, int pickId, GLMouseTarget mouseTarget) {
		drawPolygon(new float[] { x, y, x + width, y, x + width, y + height, x, y + height },
				colors, pickId, mouseTarget);
	}

	public void fillRoundRect(float x, float y, float width, float height, float radius) {
		fillRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, pickId, mouseTarget);
	}

	public void fillRoundRect(float x, float y, float width, float height,
			float radius, Color color) {
		fillRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, pickId, mouseTarget);
	}

	public void fillRoundRect(float x, float y, float width, float height,
			float radius, int pickId) {
		fillRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, requirePickId(pickId), null);
	}

	public void fillRoundRect(float x, float y, float width, float height,
			float radius, GLMouseTarget mouseTarget) {
		fillRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void fillRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii) {
		fillRoundRect(x, y, width, height, radii, color, pickId, mouseTarget);
	}

	public void fillRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, Color color) {
		fillRoundRect(x, y, width, height, radii, color, pickId, mouseTarget);
	}

	private void fillRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, Color color, int pickId, GLMouseTarget mouseTarget) {
		float maximumRadius = maximumRadius(radii);
		float[] points = CurvedShapeBuilder.roundedRectangle(x, y, width, height,
				radii, curveSegments(maximumRadius));
		fillCurvedShape(points, color, pickId, mouseTarget,
				Math.min(x, x + width), Math.min(y, y + height));
	}

	public void drawRoundRect(float x, float y, float width, float height, float radius) {
		drawRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, pickId, mouseTarget);
	}

	public void drawRoundRect(float x, float y, float width, float height,
			float radius, Color color) {
		drawRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, pickId, mouseTarget);
	}

	public void drawRoundRect(float x, float y, float width, float height,
			float radius, int pickId) {
		drawRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, requirePickId(pickId), null);
	}

	public void drawRoundRect(float x, float y, float width, float height,
			float radius, GLMouseTarget mouseTarget) {
		drawRoundRect(x, y, width, height, GLCornerRadii.all(radius),
				color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii) {
		drawRoundRect(x, y, width, height, radii, color, pickId, mouseTarget);
	}

	public void drawRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, Color color) {
		drawRoundRect(x, y, width, height, radii, color, pickId, mouseTarget);
	}

	private void drawRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, Color color, int pickId, GLMouseTarget mouseTarget) {
		float[] points = CurvedShapeBuilder.roundedRectangle(x, y, width, height,
				radii, curveSegments(maximumRadius(radii)));
		drawCurvedShape(points, color, pickId, mouseTarget,
				Math.min(x, x + width), Math.min(y, y + height));
	}

	public void fillEllipse(float x, float y, float width, float height) {
		fillEllipse(x, y, width, height, color, pickId, mouseTarget);
	}

	public void fillEllipse(float x, float y, float width, float height, Color color) {
		fillEllipse(x, y, width, height, color, pickId, mouseTarget);
	}

	public void fillEllipse(float x, float y, float width, float height, int pickId) {
		fillEllipse(x, y, width, height, color, requirePickId(pickId), null);
	}

	public void fillEllipse(float x, float y, float width, float height,
			GLMouseTarget mouseTarget) {
		fillEllipse(x, y, width, height, color,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillEllipse(float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		float[] points = CurvedShapeBuilder.ellipse(x, y, width, height,
				curveSegments(Math.max(Math.abs(width), Math.abs(height)) / 2));
		fillCurvedShape(points, color, pickId, mouseTarget,
				Math.min(x, x + width), Math.min(y, y + height));
	}

	public void drawEllipse(float x, float y, float width, float height) {
		drawEllipse(x, y, width, height, color, pickId, mouseTarget);
	}

	public void drawEllipse(float x, float y, float width, float height, Color color) {
		drawEllipse(x, y, width, height, color, pickId, mouseTarget);
	}

	public void drawEllipse(float x, float y, float width, float height, int pickId) {
		drawEllipse(x, y, width, height, color, requirePickId(pickId), null);
	}

	public void drawEllipse(float x, float y, float width, float height,
			GLMouseTarget mouseTarget) {
		drawEllipse(x, y, width, height, color,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawEllipse(float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		float[] points = CurvedShapeBuilder.ellipse(x, y, width, height,
				curveSegments(Math.max(Math.abs(width), Math.abs(height)) / 2));
		drawCurvedShape(points, color, pickId, mouseTarget,
				Math.min(x, x + width), Math.min(y, y + height));
	}

	public void fillCircle(float centerX, float centerY, float radius) {
		requireCircleRadius(radius);
		fillEllipse(centerX - radius, centerY - radius, radius * 2, radius * 2);
	}

	public void fillCircle(float centerX, float centerY, float radius, Color color) {
		requireCircleRadius(radius);
		fillEllipse(centerX - radius, centerY - radius, radius * 2, radius * 2, color);
	}

	public void fillCircle(float centerX, float centerY, float radius, int pickId) {
		requireCircleRadius(radius);
		fillEllipse(centerX - radius, centerY - radius,
				radius * 2, radius * 2, pickId);
	}

	public void fillCircle(float centerX, float centerY, float radius,
			GLMouseTarget mouseTarget) {
		requireCircleRadius(radius);
		fillEllipse(centerX - radius, centerY - radius,
				radius * 2, radius * 2, mouseTarget);
	}

	public void drawCircle(float centerX, float centerY, float radius) {
		requireCircleRadius(radius);
		drawEllipse(centerX - radius, centerY - radius, radius * 2, radius * 2);
	}

	public void drawCircle(float centerX, float centerY, float radius, Color color) {
		requireCircleRadius(radius);
		drawEllipse(centerX - radius, centerY - radius, radius * 2, radius * 2, color);
	}

	public void drawCircle(float centerX, float centerY, float radius, int pickId) {
		requireCircleRadius(radius);
		drawEllipse(centerX - radius, centerY - radius,
				radius * 2, radius * 2, pickId);
	}

	public void drawCircle(float centerX, float centerY, float radius,
			GLMouseTarget mouseTarget) {
		requireCircleRadius(radius);
		drawEllipse(centerX - radius, centerY - radius,
				radius * 2, radius * 2, mouseTarget);
	}

	/**
	 * Adds one analytic rounded rectangle to the shared instanced UI batch. This
	 * standard-control path intentionally ignores custom filled shaders.
	 */
	public void fillUIRoundRect(float x, float y, float width, float height,
			float radius, Color color) {
		float[] instances = UIQuadInstanceFormat.roundedRectangle(
				x, y, width, height, radius, 0, false, false,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Adds an analytic UI rectangle with one radius per corner. */
	public void fillUIRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, Color color) {
		float[] instances = UIQuadInstanceFormat.roundedRectangle(
				x, y, width, height, radii, requireColor(color),
				renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws an antialiased inner border through the shared instanced UI batch. */
	public void drawUIRoundRect(float x, float y, float width, float height,
			float radius, float strokeWidth, Color color) {
		drawUIRoundRect(x, y, width, height, radius, strokeWidth,
				color, GLStroke.WidthSpace.WORLD);
	}

	/** Draws an antialiased inner border with one radius per corner. */
	public void drawUIRoundRect(float x, float y, float width, float height,
			GLCornerRadii radii, float strokeWidth, Color color,
			GLStroke.WidthSpace widthSpace) {
		if (!Float.isFinite(strokeWidth) || strokeWidth <= 0)
			throw new IllegalArgumentException(
					"UI stroke width must be finite and positive");
		if (widthSpace == null)
			throw new IllegalArgumentException("UI width space must not be null");
		float[] instances = UIQuadInstanceFormat.roundedRectangleStroke(
				x, y, width, height, radii, strokeWidth,
				widthSpace == GLStroke.WidthSpace.SCREEN,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws an antialiased inner border in world or logical screen pixels. */
	public void drawUIRoundRect(float x, float y, float width, float height,
			float radius, float strokeWidth, Color color,
			GLStroke.WidthSpace widthSpace) {
		if (!Float.isFinite(strokeWidth) || strokeWidth <= 0)
			throw new IllegalArgumentException(
					"UI stroke width must be finite and positive");
		if (widthSpace == null)
			throw new IllegalArgumentException("UI width space must not be null");
		float[] instances = UIQuadInstanceFormat.roundedRectangle(
				x, y, width, height, radius, strokeWidth, true,
				widthSpace == GLStroke.WidthSpace.SCREEN,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/**
	 * Draws one rounded fill and inner border analytically without overlapping
	 * antialiased edges. The first replay color targets the fill only.
	 */
	public void fillAndDrawUIRoundRect(float x, float y, float width, float height,
			float radius, float strokeWidth, Color fillColor, Color borderColor) {
		fillAndDrawUIRoundRect(x, y, width, height, radius, strokeWidth,
				fillColor, borderColor, GLStroke.WidthSpace.WORLD);
	}

	/** Draws a combined fill and border in world or logical screen pixels. */
	public void fillAndDrawUIRoundRect(float x, float y, float width, float height,
			float radius, float strokeWidth, Color fillColor, Color borderColor,
			GLStroke.WidthSpace widthSpace) {
		if (widthSpace == null)
			throw new IllegalArgumentException("UI width space must not be null");
		float[] instances = UIQuadInstanceFormat.roundedRectangleWithBorder(
				x, y, width, height, radius, strokeWidth,
				widthSpace == GLStroke.WidthSpace.SCREEN,
				requireColor(fillColor), requireColor(borderColor),
				renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/**
	 * Draws an unpickable soft shadow for a rounded UI rectangle through the
	 * shared instanced UI batch. No layer, framebuffer or blur pass is created.
	 */
	public void drawUIRoundRectShadow(float x, float y, float width, float height,
			float radius, float softness, float offsetX, float offsetY, Color color) {
		float[] instances = UIQuadInstanceFormat.roundedRectangleShadow(
				x, y, width, height, radius, softness, offsetX, offsetY,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, NO_PICK_ID, null, x, y);
	}

	/** Adds MSDF glyph instances without switching away from standard UI shapes. */
	public void drawUIText(GLFont font, String text, float x, float baselineY,
			Color color) {
		renderer.prepareFont(font);
		float[] vertices = font.createTextVertices(
				text, x, baselineY, requireColor(color));
		if (vertices.length == 0)
			return;
		renderer.currentTransform().transformVertices(
				vertices, TexturedVertexFormat.FLOATS_PER_VERTEX);
		float[] instances = UIQuadInstanceFormat.msdfGlyphs(
				vertices, font.msdfPixelRange());
		submitUI(font.atlasTexture(), instances,
				pickId, mouseTarget, x, baselineY);
	}

	/** Adds an antialiased check mark to the shared standard-control batch. */
	public void drawUICheckMark(float x, float y, float width, float height,
			float strokeWidth, Color color) {
		float[] instances = UIQuadInstanceFormat.checkMark(
				x, y, width, height, strokeWidth,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws a vertically shaded progress fill clipped to one rounded outline. */
	public void fillUIProgressRoundRect(float x, float y, float width,
			float height, float radius, float progress,
			Color topColor, Color bottomColor) {
		float[] instances = UIQuadInstanceFormat.progressGradient(
				x, y, width, height, radius, progress,
				requireColor(topColor), requireColor(bottomColor),
				renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws a complete vertical light-to-dark gradient in one UI instance. */
	public void fillUIVerticalGradientRoundRect(float x, float y, float width,
			float height, float radius, Color lightColor, Color darkColor) {
		fillUIProgressRoundRect(x, y, width, height, radius, 1,
				lightColor, darkColor);
	}

	/** Vertical gradient with independently rounded top and bottom edges. */
	public void fillUIVerticalGradientRoundRect(float x, float y, float width,
			float height, float topRadius, float bottomRadius,
			Color lightColor, Color darkColor) {
		fillUIProgressRoundRect(x, y, width, height, topRadius, bottomRadius,
				1, lightColor, darkColor);
	}

	/** Progress gradient clipped to independently rounded top and bottom edges. */
	public void fillUIProgressRoundRect(float x, float y, float width,
			float height, float topRadius, float bottomRadius, float progress,
			Color topColor, Color bottomColor) {
		float[] instances = UIQuadInstanceFormat.progressGradient(
				x, y, width, height, topRadius, bottomRadius, progress,
				requireColor(topColor), requireColor(bottomColor),
				renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws an analytic left- or right-pointing chevron. */
	public void drawUIChevron(float x, float y, float width, float height,
			boolean pointsRight, float strokeWidth, Color color,
			GLStroke.WidthSpace widthSpace) {
		if (widthSpace == null)
			throw new IllegalArgumentException("UI width space must not be null");
		float[] instances = UIQuadInstanceFormat.chevron(
				x, y, width, height, pointsRight, strokeWidth,
				widthSpace == GLStroke.WidthSpace.SCREEN,
				requireColor(color), renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/**
	 * Makes a complete rectangular control region pickable without changing its
	 * color output. Useful when a label and the gap beside a control should react.
	 */
	public void addUIHitArea(float x, float y, float width, float height) {
		float[] instances = UIQuadInstanceFormat.hitArea(
				x, y, width, height, renderer.currentTransform());
		submitUI(null, instances, pickId, mouseTarget, x, y);
	}

	/** Draws a high-level image using automatic atlas or standalone storage. */
	public void drawImage(GLImage image, float x, float y) {
		drawImage(image, x, y, imageWidth(image), imageHeight(image), Color.WHITE);
	}

	public void drawImage(GLImage image, float x, float y,
			float width, float height) {
		drawImage(image, x, y, width, height, Color.WHITE);
	}

	public void drawImage(GLImage image, float x, float y,
			float width, float height, Color tint) {
		GLTextureRegion region = renderer.resolveImage(requireImage(image));
		drawResolvedImage(region, x, y, width, height, requireColor(tint),
				pickId, mouseTarget);
	}

	/**
	 * Draws an image through the universal UI batch, allowing it to share one
	 * draw call with standard controls and MSDF text.
	 */
	public void drawUIImage(GLImage image, float x, float y,
			float width, float height, Color tint) {
		GLTextureRegion region = renderer.resolveImage(requireImage(image));
		float[] instance = UIQuadInstanceFormat.texture(x, y, width, height,
				region.u0(), region.v0(), region.u1(), region.v1(), requireColor(tint),
				renderer.currentTransform());
		submitUI(region.texture(), instance, pickId, mouseTarget,
				Math.min(x, x + width), Math.min(y, y + height));
	}

	public void drawUIImage(GLImage image, float x, float y,
			float width, float height) {
		drawUIImage(image, x, y, width, height, Color.WHITE);
	}

	/** Draws an RGBA or tintable MSDF icon through the shared UI batch. */
	public void drawUIIcon(GLIcon icon, float x, float y,
			float width, float height, Color color) {
		submitIcon(icon, x, y, width, height, color, true);
	}

	public void drawUIIcon(GLIcon icon, float x, float y,
			float width, float height) {
		drawUIIcon(icon, x, y, width, height, Color.WHITE);
	}

	/**
	 * Draws an icon as an RGBA texture through the freely shaderable texture batch.
	 * Vector-backed icons retain adaptive resolution selection; MSDF icons use a
	 * lazily generated RGBA representation for this path.
	 */
	public void drawIcon(GLIcon icon, float x, float y,
			float width, float height, Color color) {
		submitIcon(icon, x, y, width, height, color, false);
	}

	public void drawIcon(GLIcon icon, float x, float y,
			float width, float height) {
		drawIcon(icon, x, y, width, height, Color.WHITE);
	}

	private void submitIcon(GLIcon icon, float x, float y,
			float width, float height, Color color, boolean uiBatch) {
		if (icon == null)
			throw new IllegalArgumentException("Icon must not be null");
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width == 0 || height == 0)
			throw new IllegalArgumentException(
					"Icon bounds must be finite and non-zero");
		Color actualColor = requireColor(color);
		float originX = Math.min(x, x + width);
		float originY = Math.min(y, y + height);
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			if (uiBatch)
				renderer.addUIIcon(icon, x, y, width, height, actualColor,
						pickId, mouseTarget, originX, originY);
			else
				renderer.addIcon(icon, x, y, width, height, actualColor,
						pickId, mouseTarget, originX, originY);
		} else if (uiBatch) {
			recording.addUIIcon(icon, x, y, width, height, actualColor,
					pickId, mouseTarget, originX, originY);
		} else {
			recording.addIcon(icon, x, y, width, height, actualColor,
					pickId, mouseTarget, originX, originY);
		}
	}

	private void drawResolvedImage(GLTextureRegion region, float x, float y,
			float width, float height, Color tint, int pickId,
			GLMouseTarget mouseTarget) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height))
			throw new IllegalArgumentException("Image bounds must be finite");
		if (width == 0 || height == 0)
			return;
		GLTransform transform = renderer.currentTransform();
		float x2 = x + width;
		float y2 = y + height;
		float x1t = transform.transformX(x, y);
		float y1t = transform.transformY(x, y);
		float x2t = transform.transformX(x2, y);
		float y2t = transform.transformY(x2, y);
		float x3t = transform.transformX(x2, y2);
		float y3t = transform.transformY(x2, y2);
		float x4t = transform.transformX(x, y2);
		float y4t = transform.transformY(x, y2);
		requireFiniteCoordinates(x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t);
		submitTextured(region.texture(), x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t,
				region.u0(), region.v0(), region.u1(), region.v1(),
				tint.getRed() / 255f, tint.getGreen() / 255f,
				tint.getBlue() / 255f, tint.getAlpha() / 255f,
				pickId, mouseTarget, Math.min(x, x2), Math.min(y, y2));
	}

	public void drawRenderTarget(GLRenderTarget target, float x, float y) {
		drawRenderTarget(target, x, y,
				targetWidth(target), targetHeight(target),
				Color.WHITE, pickId, mouseTarget);
	}

	public void drawRenderTarget(GLRenderTarget target, float x, float y,
			float width, float height) {
		drawRenderTarget(target, x, y, width, height,
				Color.WHITE, pickId, mouseTarget);
	}

	public void drawRenderTarget(GLRenderTarget target, float x, float y,
			float width, float height, Color tint) {
		drawRenderTarget(target, x, y, width, height,
				tint, pickId, mouseTarget);
	}

	public void drawRenderTarget(GLRenderTarget target, float x, float y,
			float width, float height, int pickId) {
		drawRenderTarget(target, x, y, width, height,
				Color.WHITE, requirePickId(pickId), null);
	}

	public void drawRenderTarget(GLRenderTarget target, float x, float y,
			float width, float height, GLMouseTarget mouseTarget) {
		drawRenderTarget(target, x, y, width, height,
				Color.WHITE, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawRenderTarget(GLRenderTarget target, float x, float y,
			float width, float height, Color tint, int pickId,
			GLMouseTarget mouseTarget) {
		if (recording != null)
			throw new IllegalStateException(
					"Render targets are dynamic resources and cannot be recorded");
		if (target == null || target.isClosed())
			throw new IllegalArgumentException("Render target must be live");
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height))
			throw new IllegalArgumentException(
					"Render-target bounds must be finite");
		if (width == 0 || height == 0)
			return;
		Color actualTint = requireColor(tint);
		GLTexture texture = renderer.resolveRenderTargetTexture(target);
		float x2 = x + width;
		float y2 = y + height;
		GLTransform transform = renderer.currentTransform();
		float x1t = transform.transformX(x, y);
		float y1t = transform.transformY(x, y);
		float x2t = transform.transformX(x2, y);
		float y2t = transform.transformY(x2, y);
		float x3t = transform.transformX(x2, y2);
		float y3t = transform.transformY(x2, y2);
		float x4t = transform.transformX(x, y2);
		float y4t = transform.transformY(x, y2);
		requireFiniteCoordinates(x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t);
		// FBO textures use OpenGL's bottom-left storage, while context coordinates
		// are top-left. Reversing V preserves orientation across feedback passes.
		boolean presentLocalPicking = target.isPickingEnabled()
				&& renderer.isPickingRenderingEnabled();
		if (presentLocalPicking)
			renderer.flush();
		submitTextured(texture,
				x1t, y1t, x2t, y2t, x3t, y3t, x4t, y4t,
				0, 1, 1, 0,
				actualTint.getRed() / 255f, actualTint.getGreen() / 255f,
				actualTint.getBlue() / 255f, actualTint.getAlpha() / 255f,
				presentLocalPicking ? NO_PICK_ID : pickId,
				presentLocalPicking ? null : mouseTarget,
				Math.min(x, x2), Math.min(y, y2));
		if (presentLocalPicking) {
			float[] pickVertices = {
				x1t, y1t, 0, 1,
				x2t, y2t, 1, 1,
				x3t, y3t, 1, 0,
				x1t, y1t, 0, 1,
				x3t, y3t, 1, 0,
				x4t, y4t, 0, 0
			};
			renderer.addRenderTargetPicking(target, texture, pickVertices,
					x, y, width, height, pickId, mouseTarget,
					Math.min(x, x2), Math.min(y, y2),
					actualTint.getAlpha() / 255f);
		}
	}

	public void drawTexture(GLTexture texture, float x, float y) {
		drawTexture(texture, x, y, textureWidth(texture), textureHeight(texture),
				Color.WHITE, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float x, float y, int pickId) {
		drawTexture(texture, x, y, textureWidth(texture), textureHeight(texture), Color.WHITE, pickId);
	}

	public void drawTexture(GLTexture texture, float x, float y, GLMouseTarget mouseTarget) {
		drawTexture(texture, x, y, textureWidth(texture), textureHeight(texture),
				Color.WHITE, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawTexture(GLTexture texture, float x, float y, Color tint) {
		drawTexture(texture, x, y, textureWidth(texture), textureHeight(texture),
				tint, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float x, float y, Color tint, int pickId) {
		drawTexture(texture, x, y, textureWidth(texture), textureHeight(texture), tint, pickId);
	}

	public void drawTexture(GLTexture texture, float x, float y, float width, float height) {
		drawTexture(texture, x, y, width, height, Color.WHITE, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float x, float y,
			float width, float height, int pickId) {
		drawTexture(texture, x, y, width, height, Color.WHITE, pickId);
	}

	public void drawTexture(GLTexture texture, float x, float y,
			float width, float height, GLMouseTarget mouseTarget) {
		drawTexture(texture, x, y, width, height, Color.WHITE,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void drawTexture(GLTexture texture, float x, float y,
			float width, float height, Color tint) {
		drawTexture(texture, x, y, width, height, tint, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float x, float y,
			float width, float height, Color tint, int pickId) {
		drawTexture(texture, x, y, width, height, tint, requirePickId(pickId), null);
	}

	public void drawTexture(GLTexture texture, float x, float y,
			float width, float height, Color tint, GLMouseTarget mouseTarget) {
		drawTexture(texture, x, y, width, height, tint,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawTexture(GLTexture texture, float x, float y,
			float width, float height, Color tint, int pickId, GLMouseTarget mouseTarget) {
		drawTexture(texture, 0, 0, textureWidth(texture), textureHeight(texture),
				x, y, width, height, tint, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight) {
		drawTexture(texture, sourceX, sourceY, sourceWidth, sourceHeight,
				destinationX, destinationY, destinationWidth, destinationHeight,
				Color.WHITE, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight, int pickId) {
		drawTexture(texture, sourceX, sourceY, sourceWidth, sourceHeight,
				destinationX, destinationY, destinationWidth, destinationHeight,
				Color.WHITE, pickId);
	}

	public void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight, Color tint) {
			drawTexture(texture, sourceX, sourceY, sourceWidth, sourceHeight,
				destinationX, destinationY, destinationWidth, destinationHeight,
				tint, pickId, mouseTarget);
	}

	public void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight, Color tint, int pickId) {
		drawTexture(texture, sourceX, sourceY, sourceWidth, sourceHeight, destinationX,
				destinationY, destinationWidth, destinationHeight, tint,
				requirePickId(pickId), null);
	}

	public void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight, Color tint,
			GLMouseTarget mouseTarget) {
		drawTexture(texture, sourceX, sourceY, sourceWidth, sourceHeight, destinationX,
				destinationY, destinationWidth, destinationHeight, tint,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawTexture(GLTexture texture, float sourceX, float sourceY,
			float sourceWidth, float sourceHeight, float destinationX, float destinationY,
			float destinationWidth, float destinationHeight, Color tint, int pickId,
			GLMouseTarget mouseTarget) {
		Color actualTint = requireColor(tint);
		validateTextureDraw(texture, sourceX, sourceY, sourceWidth, sourceHeight,
				destinationX, destinationY, destinationWidth, destinationHeight);
		if (destinationWidth == 0 || destinationHeight == 0)
			return;

		float destinationX2 = destinationX + destinationWidth;
		float destinationY2 = destinationY + destinationHeight;
		GLTransform transform = renderer.currentTransform();
		float x1 = transform.transformX(destinationX, destinationY);
		float y1 = transform.transformY(destinationX, destinationY);
		float x2 = transform.transformX(destinationX2, destinationY);
		float y2 = transform.transformY(destinationX2, destinationY);
		float x3 = transform.transformX(destinationX2, destinationY2);
		float y3 = transform.transformY(destinationX2, destinationY2);
		float x4 = transform.transformX(destinationX, destinationY2);
		float y4 = transform.transformY(destinationX, destinationY2);
		requireFiniteCoordinates(x1, y1, x2, y2, x3, y3, x4, y4);

		float u0 = sourceX / texture.getWidth();
		float v0 = sourceY / texture.getHeight();
		float u1 = (sourceX + sourceWidth) / texture.getWidth();
		float v1 = (sourceY + sourceHeight) / texture.getHeight();
		submitTextured(texture,
				x1, y1, x2, y2, x3, y3, x4, y4,
				u0, v0, u1, v1,
				actualTint.getRed() / 255f, actualTint.getGreen() / 255f,
				actualTint.getBlue() / 255f, actualTint.getAlpha() / 255f,
				pickId, mouseTarget,
				Math.min(destinationX, destinationX2),
				Math.min(destinationY, destinationY2));
	}

	/** Draws MSDF-atlas text at a fractional baseline position using the current color. */
	public void drawText(GLFont font, String text, float x, float baselineY) {
		drawText(font, text, x, baselineY, color, pickId, mouseTarget);
	}

	public void drawText(GLFont font, String text, float x, float baselineY, int pickId) {
		drawText(font, text, x, baselineY, color, pickId);
	}

	public void drawText(GLFont font, String text, float x, float baselineY,
			GLMouseTarget mouseTarget) {
		drawText(font, text, x, baselineY, color,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	/** Draws MSDF-atlas text; {@code baselineY} follows Graphics2D drawString semantics. */
	public void drawText(GLFont font, String text, float x, float baselineY, Color color) {
		drawText(font, text, x, baselineY, color, pickId, mouseTarget);
	}

	public void drawText(GLFont font, String text, float x, float baselineY,
			Color color, int pickId) {
		drawText(font, text, x, baselineY, color, requirePickId(pickId), null);
	}

	public void drawText(GLFont font, String text, float x, float baselineY,
			Color color, GLMouseTarget mouseTarget) {
		drawText(font, text, x, baselineY, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void drawText(GLFont font, String text, float x, float baselineY,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		renderer.prepareFont(font);
		float[] vertices = font.createTextVertices(
				text, x, baselineY, requireColor(color));
		if (vertices.length == 0)
			return;
		renderer.currentTransform().transformVertices(
				vertices, TexturedVertexFormat.FLOATS_PER_VERTEX);
		submitText(font.atlasTexture(), font.msdfPixelRange(), vertices,
				pickId, mouseTarget, x, baselineY);
	}

	public void fillRect(float x, float y, float width, float height) {
		fillRect(x, y, width, height, color, pickId, mouseTarget);
	}

	public void fillRect(float x, float y, float width, float height, int pickId) {
		fillRect(x, y, width, height, color, pickId);
	}

	public void fillRect(float x, float y, float width, float height,
			GLMouseTarget mouseTarget) {
		fillRect(x, y, width, height, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void fillRect(float x, float y, float width, float height, Color color) {
		fillRect(x, y, width, height, color, pickId, mouseTarget);
	}

	public void fillRect(float x, float y, float width, float height,
			Color color, int pickId) {
		fillRect(x, y, width, height, color, requirePickId(pickId), null);
	}

	public void fillRect(float x, float y, float width, float height,
			Color color, GLMouseTarget mouseTarget) {
		fillRect(x, y, width, height, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillRect(float x, float y, float width, float height,
			Color color, int pickId, GLMouseTarget mouseTarget) {
		Color actualColor = requireColor(color);
		float right = x + width;
		float bottom = y + height;
		GLTransform transform = renderer.currentTransform();
		float x0 = transform.transformX(x, y);
		float y0 = transform.transformY(x, y);
		float x1 = transform.transformX(right, y);
		float y1 = transform.transformY(right, y);
		float x2 = transform.transformX(right, bottom);
		float y2 = transform.transformY(right, bottom);
		float x3 = transform.transformX(x, bottom);
		float y3 = transform.transformY(x, bottom);
		requireFiniteCoordinates(x0, y0, x1, y1, x2, y2, x3, y3);

		float red = actualColor.getRed() / 255f;
		float green = actualColor.getGreen() / 255f;
		float blue = actualColor.getBlue() / 255f;
		float alpha = actualColor.getAlpha() / 255f;
		float originX = Math.min(x, right);
		float originY = Math.min(y, bottom);
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addFilled(
					x0, y0, x1, y1, x2, y2,
					x0, y0, x2, y2, x3, y3,
					red, green, blue, alpha, pickId, mouseTarget, originX, originY);
		} else {
			recording.addFilledQuad(
					x0, y0, x1, y1, x2, y2, x3, y3,
					red, green, blue, alpha,
					pickId, mouseTarget, originX, originY);
		}
	}

	public void fillPolygon(float[] points) {
		fillPolygon(points, color, pickId, mouseTarget);
	}

	public void fillPolygon(float[] points, int pickId) {
		fillPolygon(points, color, pickId);
	}

	public void fillPolygon(float[] points, GLMouseTarget mouseTarget) {
		fillPolygon(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void fillPolygon(float[] points, Color color) {
		fillPolygon(points, color, pickId, mouseTarget);
	}

	public void fillPolygon(float[] points, Color color, int pickId) {
		fillPolygon(points, color, requirePickId(pickId), null);
	}

	public void fillPolygon(float[] points, Color color, GLMouseTarget mouseTarget) {
		fillPolygon(points, color, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillPolygon(float[] points, Color color, int pickId,
			GLMouseTarget mouseTarget) {
		float[] vertices = PolygonTessellator.tessellate(points, requireColor(color));
		emitFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	/** Fills a polygon using one interpolated color per x/y point pair. */
	public void fillPolygon(float[] points, Color[] colors) {
		fillPolygon(points, colors, pickId, mouseTarget);
	}

	public void fillPolygon(float[] points, Color[] colors, int pickId) {
		fillPolygon(points, colors, requirePickId(pickId), null);
	}

	public void fillPolygon(float[] points, Color[] colors, GLMouseTarget mouseTarget) {
		fillPolygon(points, colors, NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillPolygon(float[] points, Color[] colors, int pickId,
			GLMouseTarget mouseTarget) {
		float[] vertices = PolygonTessellator.tessellate(points, colors);
		emitFilledVertices(vertices, pickId, mouseTarget, points[0], points[1]);
	}

	public void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color, pickId, mouseTarget);
	}

	public void fillTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, int pickId) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color, pickId);
	}

	public void fillTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, GLMouseTarget mouseTarget) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	public void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color, pickId, mouseTarget);
	}

	public void fillTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, Color color, int pickId) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color, requirePickId(pickId), null);
	}

	public void fillTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, Color color, GLMouseTarget mouseTarget) {
		fillTriangle(x1, y1, x2, y2, x3, y3, color,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, Color color, int pickId, GLMouseTarget mouseTarget) {
		fillTriangle(x1, y1, color, x2, y2, color, x3, y3, color, pickId, mouseTarget);
	}

	public void fillTriangle(float x1, float y1, Color color1, float x2, float y2, Color color2,
			float x3, float y3, Color color3) {
		fillTriangle(x1, y1, color1, x2, y2, color2, x3, y3, color3, pickId, mouseTarget);
	}

	public void fillTriangle(float x1, float y1, Color color1,
			float x2, float y2, Color color2, float x3, float y3,
			Color color3, int pickId) {
		fillTriangle(x1, y1, color1, x2, y2, color2, x3, y3, color3,
				requirePickId(pickId), null);
	}

	public void fillTriangle(float x1, float y1, Color color1,
			float x2, float y2, Color color2, float x3, float y3,
			Color color3, GLMouseTarget mouseTarget) {
		fillTriangle(x1, y1, color1, x2, y2, color2, x3, y3, color3,
				NO_PICK_ID, requireMouseTarget(mouseTarget));
	}

	private void fillTriangle(float x1, float y1, Color color1,
			float x2, float y2, Color color2, float x3, float y3,
			Color color3, int pickId, GLMouseTarget mouseTarget) {
		Color actualColor1 = requireColor(color1);
		Color actualColor2 = requireColor(color2);
		Color actualColor3 = requireColor(color3);
		GLTransform transform = renderer.currentTransform();
		float transformedX1 = transform.transformX(x1, y1);
		float transformedY1 = transform.transformY(x1, y1);
		float transformedX2 = transform.transformX(x2, y2);
		float transformedY2 = transform.transformY(x2, y2);
		float transformedX3 = transform.transformX(x3, y3);
		float transformedY3 = transform.transformY(x3, y3);
		requireFiniteCoordinates(transformedX1, transformedY1,
				transformedX2, transformedY2, transformedX3, transformedY3);

		if (actualColor1 == actualColor2 && actualColor1 == actualColor3) {
			float red = actualColor1.getRed() / 255f;
			float green = actualColor1.getGreen() / 255f;
			float blue = actualColor1.getBlue() / 255f;
			float alpha = actualColor1.getAlpha() / 255f;
			submitFilled(transformedX1, transformedY1,
					transformedX2, transformedY2, transformedX3, transformedY3,
					red, green, blue, alpha,
					pickId, mouseTarget, x1, y1);
		} else {
			submitFilled(
					transformedX1, transformedY1,
					actualColor1.getRed() / 255f, actualColor1.getGreen() / 255f,
					actualColor1.getBlue() / 255f, actualColor1.getAlpha() / 255f,
					transformedX2, transformedY2,
					actualColor2.getRed() / 255f, actualColor2.getGreen() / 255f,
					actualColor2.getBlue() / 255f, actualColor2.getAlpha() / 255f,
					transformedX3, transformedY3,
					actualColor3.getRed() / 255f, actualColor3.getGreen() / 255f,
					actualColor3.getBlue() / 255f, actualColor3.getAlpha() / 255f,
					pickId, mouseTarget, x1, y1);
		}
	}

	public void flush() {
		submit(GLRenderer2D::flush);
	}

	private void emitFilledVertices(float[] vertices, int pickId,
			GLMouseTarget mouseTarget, float automaticOriginX, float automaticOriginY) {
		int vertexCount = vertices.length / FilledVertexFormat.FLOATS_PER_VERTEX;
		if (vertexCount == 0)
			return;
		renderer.currentTransform().transformVertices(
				vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		emitPreparedFilledVertices(vertices, pickId, mouseTarget,
				automaticOriginX, automaticOriginY);
	}

	private void fillCurvedShape(float[] points, Color color, int pickId,
			GLMouseTarget mouseTarget, float originX, float originY) {
		if (points.length == 0)
			return;
		// Ellipses and rounded rectangles are always convex. A direct triangle fan
		// avoids creating a GLU tessellator for every recorded widget, which makes
		// first-frame construction of hundreds of equally shaped controls much cheaper.
		float[] vertices = tessellateConvex(points, requireColor(color));
		renderer.currentTransform().transformVertices(
				vertices, FilledVertexFormat.FLOATS_PER_VERTEX);
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, originX, originY);
	}

	private static float[] tessellateConvex(float[] points, Color color) {
		int pointCount = points.length / 2;
		float centerX = 0;
		float centerY = 0;
		for (int point = 0; point < pointCount; point++) {
			centerX += points[point * 2];
			centerY += points[point * 2 + 1];
		}
		centerX /= pointCount;
		centerY /= pointCount;
		float red = color.getRed() / 255f;
		float green = color.getGreen() / 255f;
		float blue = color.getBlue() / 255f;
		float alpha = color.getAlpha() / 255f;
		float[] vertices = new float[pointCount * 3 * FilledVertexFormat.FLOATS_PER_VERTEX];
		int target = 0;
		for (int point = 0; point < pointCount; point++) {
			int next = (point + 1) % pointCount;
			target = putFilledVertex(vertices, target, centerX, centerY,
					red, green, blue, alpha);
			target = putFilledVertex(vertices, target,
					points[point * 2], points[point * 2 + 1],
					red, green, blue, alpha);
			target = putFilledVertex(vertices, target,
					points[next * 2], points[next * 2 + 1],
					red, green, blue, alpha);
		}
		return vertices;
	}

	private static int putFilledVertex(float[] vertices, int target,
			float x, float y, float red, float green, float blue, float alpha) {
		vertices[target++] = x;
		vertices[target++] = y;
		vertices[target++] = red;
		vertices[target++] = green;
		vertices[target++] = blue;
		vertices[target++] = alpha;
		return target;
	}

	private void drawCurvedShape(float[] points, Color color, int pickId,
			GLMouseTarget mouseTarget, float originX, float originY) {
		if (points.length == 0)
			return;
		GLStroke pathStroke = stroke;
		Color pathColor = requireColor(color);
		float[] vertices = StrokeGeometryCompiler.path(
				points, true, pathStroke, pathColor,
				renderer.currentTransform(), renderer.currentWorldToScreen(),
				renderer.currentScreenToWorld());
		if (recording != null
				&& pathStroke.getWidthSpace() == GLStroke.WidthSpace.SCREEN) {
			float[] recordedPoints = points.clone();
			if (recordDynamicFilled(replayRenderer ->
					StrokeGeometryCompiler.path(recordedPoints, true,
							pathStroke, pathColor,
							replayRenderer.currentTransform(),
							replayRenderer.currentWorldToScreen(),
							replayRenderer.currentScreenToWorld()),
					pickId, mouseTarget, originX, originY))
				return;
		}
		emitPreparedFilledVertices(vertices, pickId, mouseTarget, originX, originY);
	}

	private int cubicBezierSegments(float[] controlPoints) {
		GLTransform screenTransform = renderer.currentWorldToScreen().copy()
				.concatenate(renderer.currentTransform());
		float[] screen = controlPoints.clone();
		screenTransform.transform(screen, 0, 4);
		float firstSecondX = screen[0] - 2 * screen[2] + screen[4];
		float firstSecondY = screen[1] - 2 * screen[3] + screen[5];
		float secondSecondX = screen[2] - 2 * screen[4] + screen[6];
		float secondSecondY = screen[3] - 2 * screen[5] + screen[7];
		float maximumSecondDifference = Math.max(
				(float) Math.hypot(firstSecondX, firstSecondY),
				(float) Math.hypot(secondSecondX, secondSecondY));
		if (!(maximumSecondDifference > 0))
			return 1;
		int segments = (int) Math.ceil(Math.sqrt(
				3 * maximumSecondDifference / (8 * curveTolerance)));
		return Math.max(2, Math.min(2048, segments));
	}

	private float[] cubicBezierPoints(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1) {
		return cubicBezierPoints(x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1, 1);
	}

	private float[] cubicBezierPoints(float x0, float y0, float control1X,
			float control1Y, float control2X, float control2Y,
			float x1, float y1, int minimumSegments) {
		float[] controls = { x0, y0, control1X, control1Y, control2X,
				control2Y, x1, y1 };
		for (float value : controls)
			if (!Float.isFinite(value))
				throw new IllegalArgumentException(
						"Bezier coordinates must be finite");
		int segments = Math.max(minimumSegments, cubicBezierSegments(controls));
		float[] points = new float[(segments + 1) * 2];
		for (int segment = 0; segment <= segments; segment++) {
			float t = segment / (float) segments;
			float inverse = 1 - t;
			float inverse2 = inverse * inverse;
			float t2 = t * t;
			float first = inverse2 * inverse;
			float second = 3 * inverse2 * t;
			float third = 3 * inverse * t2;
			float fourth = t2 * t;
			int index = segment * 2;
			points[index] = first * x0 + second * control1X
					+ third * control2X + fourth * x1;
			points[index + 1] = first * y0 + second * control1Y
					+ third * control2Y + fourth * y1;
		}
		return points;
	}

	private int curveSegments(float localRadius) {
		if (!(localRadius > 0))
			return 8;
		float screenScale = renderer.currentWorldToScreen().copy()
				.concatenate(renderer.currentTransform()).getMaximumScale();
		float screenRadius = localRadius * screenScale;
		if (!(screenRadius > curveTolerance))
			return 8;
		double cosine = Math.max(-1, Math.min(1, 1 - curveTolerance / screenRadius));
		double halfAngle = Math.acos(cosine);
		if (!(halfAngle > 0))
			return 2048;
		return Math.max(8, Math.min(2048, (int) Math.ceil(Math.PI / halfAngle)));
	}

	private static void requireCircleRadius(float radius) {
		if (!Float.isFinite(radius) || radius < 0)
			throw new IllegalArgumentException(
					"Circle radius must be finite and non-negative");
	}

	private static float maximumRadius(GLCornerRadii radii) {
		if (radii == null)
			throw new IllegalArgumentException("Corner radii must not be null");
		return Math.max(Math.max(radii.topLeft(), radii.topRight()),
				Math.max(radii.bottomRight(), radii.bottomLeft()));
	}

	private void emitPreparedFilledVertices(float[] vertices, int pickId,
			GLMouseTarget mouseTarget, float automaticOriginX, float automaticOriginY) {
		int vertexCount = vertices.length / FilledVertexFormat.FLOATS_PER_VERTEX;
		if (vertexCount == 0)
			return;
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, automaticOriginX, automaticOriginY);
			automaticOriginX = resolvedMouseOriginX;
			automaticOriginY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addFilled(vertices, pickId, mouseTarget,
					automaticOriginX, automaticOriginY);
		} else {
			recording.addFilled(vertices, pickId, mouseTarget,
					automaticOriginX, automaticOriginY);
		}
	}

	private boolean recordDynamicFilled(
			GLRecording.FilledGeometryCompiler compiler, int pickId,
			GLMouseTarget mouseTarget, float automaticOriginX, float automaticOriginY) {
		if (recording == null)
			return false;
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, automaticOriginX, automaticOriginY);
			automaticOriginX = resolvedMouseOriginX;
			automaticOriginY = resolvedMouseOriginY;
		}
		recording.addDynamicFilled(compiler, pickId, mouseTarget,
				automaticOriginX, automaticOriginY);
		return true;
	}

	private void submitFilled(float x1, float y1, float x2, float y2,
			float x3, float y3, float red, float green, float blue, float alpha,
			int pickId, GLMouseTarget mouseTarget, float originX, float originY) {
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addFilled(x1, y1, x2, y2, x3, y3,
					red, green, blue, alpha, pickId, mouseTarget,
					originX, originY);
		} else {
			recording.addFilled(new float[] {
					x1, y1, red, green, blue, alpha,
					x2, y2, red, green, blue, alpha,
					x3, y3, red, green, blue, alpha
			}, pickId, mouseTarget, originX, originY);
		}
	}

	private void submitFilled(float x1, float y1,
			float red1, float green1, float blue1, float alpha1,
			float x2, float y2, float red2, float green2, float blue2, float alpha2,
			float x3, float y3, float red3, float green3, float blue3, float alpha3,
			int pickId, GLMouseTarget mouseTarget, float originX, float originY) {
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addFilled(
					x1, y1, red1, green1, blue1, alpha1,
					x2, y2, red2, green2, blue2, alpha2,
					x3, y3, red3, green3, blue3, alpha3,
					pickId, mouseTarget, originX, originY);
		} else {
			recording.addFilled(new float[] {
					x1, y1, red1, green1, blue1, alpha1,
					x2, y2, red2, green2, blue2, alpha2,
					x3, y3, red3, green3, blue3, alpha3
			}, pickId, mouseTarget, originX, originY);
		}
	}

	private void submitTextured(GLTexture texture,
			float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float u0, float v0, float u1, float v1,
			float red, float green, float blue, float alpha,
			int pickId, GLMouseTarget mouseTarget, float originX, float originY) {
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addTextured(texture,
					x1, y1, x2, y2, x3, y3, x4, y4,
					u0, v0, u1, v1, red, green, blue, alpha,
					pickId, mouseTarget, originX, originY);
		} else {
			recording.addTextured(texture,
					x1, y1, x2, y2, x3, y3, x4, y4,
					u0, v0, u1, v1, red, green, blue, alpha,
					pickId, mouseTarget, originX, originY);
		}
	}

	private void submitText(GLTexture atlas, float pixelRange, float[] vertices,
			int pickId, GLMouseTarget mouseTarget, float originX, float originY) {
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addText(atlas, pixelRange, vertices, pickId, mouseTarget,
					originX, originY);
		} else {
			recording.addText(atlas, pixelRange, vertices, pickId, mouseTarget,
					originX, originY);
		}
	}

	private void submitUI(GLTexture texture, float[] instances,
			int pickId, GLMouseTarget mouseTarget, float originX, float originY) {
		if (mouseTarget != null) {
			resolveMouseOrigin(mouseTarget, originX, originY);
			originX = resolvedMouseOriginX;
			originY = resolvedMouseOriginY;
		}
		if (recording == null) {
			renderer.addUI(texture, instances, pickId, mouseTarget, originX, originY);
		} else {
			recording.addUI(texture, instances, pickId, mouseTarget, originX, originY);
		}
	}

	private void submit(GLRecording.Command command) {
		if (recording == null)
			command.replay(renderer);
		else
			recording.add(command);
	}

	private void executeAndRecord(GLRecording.Command command) {
		command.replay(renderer);
		if (recording != null)
			recording.add(command);
	}

	void requireRecordingEnded() {
		if (recording != null)
			throw new IllegalStateException(
					"endRecording() must be called before ending the renderer frame");
	}

	private static void requireFiniteCoordinates(float x1, float y1,
			float x2, float y2, float x3, float y3) {
		if (!Float.isFinite(x1) || !Float.isFinite(y1)
				|| !Float.isFinite(x2) || !Float.isFinite(y2)
				|| !Float.isFinite(x3) || !Float.isFinite(y3))
			throw new IllegalArgumentException("Transformed coordinate is not finite");
	}

	private static void requireFiniteCoordinates(float x1, float y1,
			float x2, float y2, float x3, float y3, float x4, float y4) {
		if (!Float.isFinite(x1) || !Float.isFinite(y1)
				|| !Float.isFinite(x2) || !Float.isFinite(y2)
				|| !Float.isFinite(x3) || !Float.isFinite(y3)
				|| !Float.isFinite(x4) || !Float.isFinite(y4))
			throw new IllegalArgumentException("Transformed coordinate is not finite");
	}

	private static Color requireColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Drawing color must not be null");
		return color;
	}

	private static int requirePickId(int pickId) {
		if (pickId < 0)
			throw new IllegalArgumentException("Pick ID must not be negative");
		return pickId;
	}

	private static GLMouseTarget requireMouseTarget(GLMouseTarget mouseTarget) {
		if (mouseTarget == null)
			throw new IllegalArgumentException("Mouse target must not be null");
		return mouseTarget;
	}

	private static void validateLayerBounds(float x, float y,
			float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"Layer bounds must be finite and have a positive size");
	}

	private static int textureWidth(GLTexture texture) {
		if (texture == null)
			throw new IllegalArgumentException("Texture must not be null");
		return texture.getWidth();
	}

	private static int targetWidth(GLRenderTarget target) {
		if (target == null)
			throw new IllegalArgumentException("Render target must not be null");
		return target.getWidth();
	}

	private static int targetHeight(GLRenderTarget target) {
		if (target == null)
			throw new IllegalArgumentException("Render target must not be null");
		return target.getHeight();
	}

	private static GLImage requireImage(GLImage image) {
		if (image == null)
			throw new IllegalArgumentException("Image must not be null");
		return image;
	}

	private static int imageWidth(GLImage image) {
		return requireImage(image).getWidth();
	}

	private static int imageHeight(GLImage image) {
		return requireImage(image).getHeight();
	}

	private static int textureHeight(GLTexture texture) {
		if (texture == null)
			throw new IllegalArgumentException("Texture must not be null");
		return texture.getHeight();
	}

	private static void validateTextureDraw(GLTexture texture,
			float sourceX, float sourceY, float sourceWidth, float sourceHeight,
			float destinationX, float destinationY,
			float destinationWidth, float destinationHeight) {
		if (texture == null || texture.isDisposed())
			throw new IllegalArgumentException("Texture must be live");
		float[] values = { sourceX, sourceY, sourceWidth, sourceHeight,
				destinationX, destinationY, destinationWidth, destinationHeight };
		for (float value : values) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException(
						"Texture coordinates and dimensions must be finite");
		}
		if (sourceX < 0 || sourceY < 0 || sourceWidth <= 0 || sourceHeight <= 0
				|| sourceX + sourceWidth > texture.getWidth()
				|| sourceY + sourceHeight > texture.getHeight())
			throw new IllegalArgumentException(
					"Texture source region must lie inside the texture");
	}

	private ContextState captureContextState() {
		return new ContextState(color, stroke, filledShader, textureShader, textShader,
				uiShader,
				curveTolerance, pickId, mouseTarget,
				mouseOriginDefined, mouseOriginX, mouseOriginY,
				logicalWidth, logicalHeight);
	}

	private void restoreRecordingOuterState() {
		renderer.applyState(recordingOuterRendererState);
		ContextState state = recordingOuterContextState;
		restoreContextState(state);
		recordingOuterRendererState = null;
		recordingOuterContextState = null;
	}

	private void restoreContextState(ContextState state) {
		color = state.color;
		stroke = state.stroke;
		filledShader = state.filledShader;
		textureShader = state.textureShader;
		textShader = state.textShader;
		uiShader = state.uiShader;
		curveTolerance = state.curveTolerance;
		pickId = state.pickId;
		mouseTarget = state.mouseTarget;
		mouseOriginDefined = state.mouseOriginDefined;
		mouseOriginX = state.mouseOriginX;
		mouseOriginY = state.mouseOriginY;
		logicalWidth = state.logicalWidth;
		logicalHeight = state.logicalHeight;
	}

	private record ContextState(Color color, GLStroke stroke,
			ShaderProgram filledShader, ShaderProgram textureShader,
			ShaderProgram textShader, ShaderProgram uiShader, float curveTolerance,
			int pickId, GLMouseTarget mouseTarget,
			boolean mouseOriginDefined, float mouseOriginX, float mouseOriginY,
			float logicalWidth, float logicalHeight) {
	}
}
