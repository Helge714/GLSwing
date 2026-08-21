package com.vingame.glswing.component;

import java.awt.Color;
import java.util.List;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.history.GLSaveManager;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLComposite;
import com.vingame.glswing.rendering.GLDropShadowEffect;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLLayerEffect;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;

/**
 * Lightweight retained render node with local bounds, cached geometry and an
 * ordered child list. Subclasses record their own content in local coordinates;
 * children are rendered independently and are never captured in the parent's
 * recording.
 */
public abstract class GLContainer implements GLParent, GLMouseTarget {

	private final GLChildList glChildren = new GLChildList(this);
	private final GLRecordingCache recordingCache = new GLRecordingCache();
	private final GLLayoutSupport layoutSupport = new GLLayoutSupport(this);
	private volatile GLParent glParent;
	private volatile String name;
	private volatile float x;
	private volatile float y;
	private volatile float width;
	private volatile float height;
	private volatile boolean visible = true;
	private volatile GLSize minimumGLSize = GLSize.ZERO;
	private volatile GLSize preferredGLSize;
	private volatile GLSize intrinsicPreferredGLSize;
	private volatile GLSize maximumGLSize = GLSize.INFINITE;
	private volatile GLTheme localTheme;
	private volatile GLLocalizer localLocalizer;
	private volatile GLInsets visualOverflow = GLInsets.ZERO;
	private volatile float[] lastVisualBounds;
	private volatile float lastRenderedRootX;
	private volatile float lastRenderedRootY;
	private volatile float lastRenderedVisualWidth;
	private volatile float lastRenderedVisualHeight;
	private volatile float lastRenderedOverflowLeft;
	private volatile float lastRenderedOverflowTop;

	/** Records this node's own visual content in a local coordinate system. */
	protected abstract void record(GLContext2D context);

	/**
	 * Draws this node's already prepared content. Called once per visible frame
	 * before the children; subclasses may override it for dynamic presentation.
	 */
	protected void render(GLContext2D context) {
		GLRecording recording = recordingCache.get();
		if (recording != null)
			context.draw(recording);
	}

	/**
	 * Renders this node's children in paint order. Subclasses may wrap the call,
	 * for example in a bounded layer used as a scrolling viewport.
	 */
	protected void renderChildren(GLContext2D context) {
		glChildren.render(context);
	}

	/**
	 * Applies presentation-only coordinates for this complete subtree. The hook
	 * runs after the component location was applied, but before bounds, culling,
	 * recording playback and child rendering are evaluated.
	 */
	protected void transformComponent(GLContext2D context) {
	}

	/**
	 * Renders this complete subtree. The method is final so coordinate, picking
	 * and recording lifetime rules cannot accidentally be bypassed.
	 */
	public final void renderComponent(GLContext2D context) {
		if (context == null)
			throw new IllegalArgumentException("Rendering context must not be null");
		if (!visible)
			return;

		GLTransform previousTransform = context.getTransform();
		GLMouseTarget previousTarget = context.getMouseTarget();
		int previousPickId = context.getPickId();
		Color previousColor = context.getColor();
		GLStroke previousStroke = context.getStroke();
		try {
			context.setTransform(previousTransform.copy().translate(x, y));
			transformComponent(context);
			if (isMouseTargetEnabled())
				context.setMouseTarget(this, 0, 0);
			else {
				context.clearMouseTarget();
				context.clearPickId();
			}
			layoutSupport.ensure(width, height);
			GLInsets overflow = visualOverflow;
			float localX = -overflow.left();
			float localY = -overflow.top();
			float visualWidth = width + overflow.horizontal();
			float visualHeight = height + overflow.vertical();
			lastVisualBounds = context.getScreenBounds(
					localX, localY, visualWidth, visualHeight);
			lastRenderedRootX = rootX();
			lastRenderedRootY = rootY();
			lastRenderedVisualWidth = visualWidth;
			lastRenderedVisualHeight = visualHeight;
			lastRenderedOverflowLeft = overflow.left();
			lastRenderedOverflowTop = overflow.top();
			if (context.intersectsRedrawBounds(
					localX, localY, visualWidth, visualHeight)) {
				ensureRecording(context);
				render(context);
			}
			renderChildren(context);
		} finally {
			context.setTransform(previousTransform);
			context.setColor(previousColor);
			context.setStroke(previousStroke);
			if (previousTarget != null)
				context.setMouseTarget(previousTarget, 0, 0);
			else if (previousPickId != GLContext2D.NO_PICK_ID)
				context.setPickId(previousPickId);
			else {
				context.clearMouseTarget();
				context.clearPickId();
			}
		}
	}

	private void ensureRecording(GLContext2D context) {
		if (recordingCache.isDirty())
			recordingCache.ensure(context, this::record);
	}

	/** Marks only this node's cached visual geometry for lazy reconstruction. */
	public final void invalidateRecording() {
		recordingCache.invalidate();
		repaintGL();
	}

	public final boolean isRecordingDirty() {
		return recordingCache.isDirty();
	}

	/** Gives subclasses read-only access to their cached recording. */
	protected final GLRecording getRecording() {
		return recordingCache.get();
	}

	@Override
	public final void addGL(GLContainer child) {
		glChildren.add(child);
	}

	@Override
	public final void addGL(GLContainer child, GLConstraints constraints) {
		glChildren.add(child, constraints);
	}

	@Override
	public final boolean removeGL(GLContainer child) {
		return glChildren.remove(child);
	}

	public final void removeFromGLParent() {
		GLParent currentParent = glParent;
		if (currentParent != null)
			currentParent.removeGL(this);
	}

	@Override
	public final void removeAllGL() {
		glChildren.removeAll();
	}

	public final GLParent getGLParent() {
		return glParent;
	}

	/**
	 * Returns the explicit component name, or a control-specific default such as
	 * its visible text. Unnamed custom containers fall back to their class name.
	 */
	public final String getName() {
		String resolved = name;
		if (resolved == null || resolved.isBlank())
			resolved = defaultName();
		return resolved == null || resolved.isBlank()
				? getClass().getSimpleName() : resolved;
	}

	/** Sets a stable semantic name; {@code null} restores the automatic name. */
	public final void setName(String name) {
		if (name != null && name.isBlank())
			throw new IllegalArgumentException(
					"Component name must not be blank");
		if (java.util.Objects.equals(this.name, name))
			return;
		this.name = name;
		repaintGL();
	}

	public final void clearName() {
		setName(null);
	}

	public final boolean hasExplicitName() {
		return name != null;
	}

	/** Supplies the automatic name used until {@link #setName(String)} is called. */
	protected String defaultName() {
		return getClass().getSimpleName();
	}

	/** Moves this node above all of its siblings. */
	public final void bringToFront() {
		requireGLParent().bringGLToFront(this);
	}

	/** Moves this node below all of its siblings. */
	public final void sendToBack() {
		requireGLParent().sendGLToBack(this);
	}

	/** Moves this node one paint position toward the front. */
	public final void moveForward() {
		requireGLParent().moveGLForward(this);
	}

	/** Moves this node one paint position toward the back. */
	public final void moveBackward() {
		requireGLParent().moveGLBackward(this);
	}

	public final int getZOrder() {
		return requireGLParent().getGLZOrder(this);
	}

	public final void setZOrder(int zOrder) {
		requireGLParent().setGLZOrder(this, zOrder);
	}

	@Override
	public final List<GLContainer> getGLChildren() {
		return glChildren.snapshot();
	}

	@Override
	public final List<GLContainer> getGLChildrenInPaintOrder() {
		return glChildren.paintOrderSnapshot();
	}

	@Override
	public final int getGLZOrder(GLContainer child) {
		return glChildren.getZOrder(child);
	}

	@Override
	public final void setGLZOrder(GLContainer child, int zOrder) {
		glChildren.setZOrder(child, zOrder);
	}

	@Override
	public final void setGLConstraints(GLContainer child, GLConstraints constraints) {
		glChildren.setConstraints(child, constraints);
	}

	@Override
	public final GLConstraints getGLConstraints(GLContainer child) {
		return glChildren.getConstraints(child);
	}

	@Override
	public final void setGLLayout(GLLayout layout) {
		layoutSupport.setLayout(layout);
	}

	@Override
	public final GLLayout getGLLayout() {
		return layoutSupport.getLayout();
	}

	@Override
	public final void setGLPadding(GLInsets padding) {
		layoutSupport.setPadding(padding);
	}

	public final void setGLPadding(float padding) {
		setGLPadding(GLInsets.all(padding));
	}

	@Override
	public final GLInsets getGLPadding() {
		return layoutSupport.getPadding();
	}

	@Override
	public final void invalidateGLLayout() {
		layoutSupport.invalidate();
	}

	@Override
	public final boolean isGLLayoutDirty() {
		return layoutSupport.isDirty();
	}

	@Override
	public final void layoutGL() {
		layoutSupport.ensure(width, height);
	}

	public final void setMinimumGLSize(float width, float height) {
		setMinimumGLSize(new GLSize(width, height));
	}

	public final void setMinimumGLSize(GLSize size) {
		if (size == null)
			throw new IllegalArgumentException("Minimum GL size must not be null");
		requireFiniteSize(size, "Minimum GL size");
		if (size.width() > maximumGLSize.width()
				|| size.height() > maximumGLSize.height())
			throw new IllegalArgumentException("Minimum GL size exceeds maximum GL size");
		minimumGLSize = size;
		invalidateParentLayout();
	}

	public final GLSize getMinimumGLSize() {
		return minimumGLSize;
	}

	public final void setPreferredGLSize(float width, float height) {
		setPreferredGLSize(new GLSize(width, height));
	}

	public final void setPreferredGLSize(GLSize size) {
		if (size == null)
			throw new IllegalArgumentException("Preferred GL size must not be null");
		requireFiniteSize(size, "Preferred GL size");
		preferredGLSize = size;
		invalidateParentLayout();
	}

	public final void clearPreferredGLSize() {
		if (preferredGLSize == null)
			return;
		preferredGLSize = null;
		invalidateParentLayout();
	}

	/** Updates a subclass-computed preferred size without replacing a user override. */
	protected final void setIntrinsicPreferredGLSize(GLSize size) {
		if (size == null)
			throw new IllegalArgumentException("Intrinsic preferred GL size must not be null");
		requireFiniteSize(size, "Intrinsic preferred GL size");
		if (size.equals(intrinsicPreferredGLSize))
			return;
		intrinsicPreferredGLSize = size;
		invalidateParentLayout();
	}

	@Override
	public final GLSize getPreferredGLSize() {
		GLSize preferred = preferredGLSize;
		if (preferred == null)
			preferred = intrinsicPreferredGLSize;
		if (preferred == null) {
			preferred = glChildren.isEmpty()
					? new GLSize(width, height)
					: layoutSupport.measure();
		}
		return preferred.clamp(minimumGLSize, maximumGLSize);
	}

	public final void setMaximumGLSize(float width, float height) {
		setMaximumGLSize(new GLSize(width, height));
	}

	public final void setMaximumGLSize(GLSize size) {
		if (size == null)
			throw new IllegalArgumentException("Maximum GL size must not be null");
		if (size.width() < minimumGLSize.width()
				|| size.height() < minimumGLSize.height())
			throw new IllegalArgumentException("Maximum GL size is below minimum GL size");
		maximumGLSize = size;
		invalidateParentLayout();
	}

	public final GLSize getMaximumGLSize() {
		return maximumGLSize;
	}

	@Override
	public final void packGL() {
		GLSize preferred = getPreferredGLSize();
		setSize(preferred.width(), preferred.height());
		layoutGL();
	}

	@Override
	public final void setGLTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("GL theme must not be null");
		if (localTheme == theme)
			return;
		localTheme = theme;
		notifyThemeChanged();
	}

	public final void clearGLTheme() {
		if (localTheme == null)
			return;
		localTheme = null;
		notifyThemeChanged();
	}

	@Override
	public final GLTheme getGLTheme() {
		GLTheme theme = localTheme;
		if (theme != null)
			return theme;
		GLParent parent = glParent;
		return parent == null ? GLTheme.DEFAULT : parent.getGLTheme();
	}

	@Override
	public final void setGLLocalizer(GLLocalizer localizer) {
		if (localizer == null)
			throw new IllegalArgumentException("GL localizer must not be null");
		if (localLocalizer == localizer)
			return;
		localLocalizer = localizer;
		notifyLocalizerChanged();
	}

	public final void clearGLLocalizer() {
		if (localLocalizer == null)
			return;
		localLocalizer = null;
		notifyLocalizerChanged();
	}

	@Override
	public final GLLocalizer getGLLocalizer() {
		GLLocalizer localizer = localLocalizer;
		if (localizer != null)
			return localizer;
		GLParent parent = glParent;
		return parent == null ? GLLocalizer.DEFAULT : parent.getGLLocalizer();
	}

	@Override
	public final GLFont getGLFont(GLFontSpec specification) {
		if (specification == null)
			throw new IllegalArgumentException("Font specification must not be null");
		GLParent parent = glParent;
		return parent == null ? specification.measurementFont()
				: parent.getGLFont(specification);
	}

	@Override
	public final GLSaveManager getGLSaveManager() {
		GLParent parent = glParent;
		return parent == null ? null : parent.getGLSaveManager();
	}

	@Override
	public final void requestGLFocus(GLKeyTarget target) {
		GLParent parent = glParent;
		if (parent != null)
			parent.requestGLFocus(target);
	}

	@Override
	public final void clearGLFocus(GLContainer subtree) {
		GLParent parent = glParent;
		if (parent != null)
			parent.clearGLFocus(subtree);
	}

	@Override
	public final void repaintGL() {
		GLParent parent = glParent;
		if (parent != null)
			parent.repaintGL(this);
	}

	@Override
	public final void repaintGL(GLContainer source) {
		GLParent parent = glParent;
		if (parent != null)
			parent.repaintGL(source);
	}

	@Override
	public final void repaintAndRebuildPicking(GLContainer source) {
		GLParent parent = glParent;
		if (parent != null)
			parent.repaintAndRebuildPicking(source);
	}

	final void setGLParent(GLParent parent) {
		glParent = parent;
	}

	public final void setBounds(float x, float y, float width, float height) {
		requireFinite(x, y, width, height);
		if (width < 0 || height < 0)
			throw new IllegalArgumentException("Width and height must not be negative");
		boolean moved = this.x != x || this.y != y;
		boolean resized = this.width != width || this.height != height;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		if (resized) {
			layoutSupport.invalidateLocal();
			invalidateRecording();
			resized(width, height);
		}
		if (moved || resized) {
			GLParent parent = glParent;
			if (parent != null)
				parent.repaintAndRebuildPicking(this);
		}
	}

	public final void setLocation(float x, float y) {
		setBounds(x, y, width, height);
	}

	public final void setSize(float width, float height) {
		setBounds(x, y, width, height);
	}

	public final float getX() {
		return x;
	}

	public final float getY() {
		return y;
	}

	public final float getWidth() {
		return width;
	}

	public final float getHeight() {
		return height;
	}

	/**
	 * Declares pixels drawn outside this container's bounds. Damage tracking and
	 * CPU culling include this area, making shadows, blur and outlines safe.
	 */
	public final void setVisualOverflow(GLInsets overflow) {
		if (overflow == null)
			throw new IllegalArgumentException("Visual overflow must not be null");
		if (visualOverflow.equals(overflow))
			return;
		visualOverflow = overflow;
		repaintGL();
	}

	public final void setVisualOverflow(float pixels) {
		setVisualOverflow(GLInsets.all(pixels));
	}

	/** Derives conservative, direction-aware overflow from a layer composite. */
	public final void setVisualOverflow(GLComposite composite) {
		if (composite == null)
			throw new IllegalArgumentException("Layer composite must not be null");
		float top = 0;
		float left = 0;
		float bottom = 0;
		float right = 0;
		for (GLLayerEffect effect : composite.getEffects()) {
			if (effect instanceof GLDropShadowEffect shadow) {
				float radius = shadow.getRadius();
				left += radius + Math.max(0, -shadow.getOffsetX());
				right += radius + Math.max(0, shadow.getOffsetX());
				top += radius + Math.max(0, -shadow.getOffsetY());
				bottom += radius + Math.max(0, shadow.getOffsetY());
			} else {
				float padding = effect.getPadding();
				top += padding;
				left += padding;
				bottom += padding;
				right += padding;
			}
		}
		setVisualOverflow(new GLInsets(top, left, bottom, right));
	}

	public final GLInsets getVisualOverflow() {
		return visualOverflow;
	}

	public final boolean isVisible() {
		return visible;
	}

	public final void setVisible(boolean visible) {
		if (this.visible == visible)
			return;
		this.visible = visible;
		invalidateParentLayout();
	}

	protected void resized(float width, float height) {
	}

	protected void attached(GLParent parent) {
	}

	protected void detached(GLParent formerParent) {
	}

	/** Controls whether this container's recorded geometry participates in picking. */
	protected boolean isMouseTargetEnabled() {
		return true;
	}

	/** Called when the resolved inherited or local theme changes. */
	protected void themeChanged() {
	}

	/** Called when the resolved inherited or local language changes. */
	protected void localizerChanged() {
	}

	final void inheritedThemeChanged() {
		if (localTheme != null)
			return;
		notifyThemeChanged();
	}

	private void notifyThemeChanged() {
		themeChanged();
		for (GLContainer child : glChildren.snapshot())
			child.inheritedThemeChanged();
	}

	final void inheritedLocalizerChanged() {
		if (localLocalizer != null)
			return;
		notifyLocalizerChanged();
	}

	private void notifyLocalizerChanged() {
		localizerChanged();
		for (GLContainer child : glChildren.snapshot())
			child.inheritedLocalizerChanged();
	}

	private void invalidateParentLayout() {
		GLParent parent = glParent;
		if (parent != null)
			parent.invalidateGLLayout();
		else
			repaintGL();
	}

	private static void requireFinite(float... values) {
		for (float value : values)
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Container bounds must be finite");
	}

	private static void requireFiniteSize(GLSize size, String description) {
		if (!Float.isFinite(size.width()) || !Float.isFinite(size.height()))
			throw new IllegalArgumentException(description + " must be finite");
	}

	final float[] lastScreenVisualBounds() {
		float[] bounds = lastVisualBounds;
		return bounds == null ? null : bounds.clone();
	}

	/**
	 * Returns the visual screen bounds produced by the most recent render pass,
	 * or {@code null} before this container was rendered for the first time.
	 */
	public final float[] getLastRenderedScreenBounds() {
		return lastScreenVisualBounds();
	}

	final float[] predictedScreenVisualBounds() {
		float[] previous = lastVisualBounds;
		if (previous == null)
			return null;
		GLInsets overflow = visualOverflow;
		float oldLogicalWidth = Math.max(0.0001f, lastRenderedVisualWidth);
		float oldLogicalHeight = Math.max(0.0001f, lastRenderedVisualHeight);
		float scaleX = previous[2] / oldLogicalWidth;
		float scaleY = previous[3] / oldLogicalHeight;
		float currentRootX = rootX();
		float currentRootY = rootY();
		return new float[] {
				previous[0] + (currentRootX - lastRenderedRootX
						+ lastRenderedOverflowLeft - overflow.left()) * scaleX,
				previous[1] + (currentRootY - lastRenderedRootY
						+ lastRenderedOverflowTop - overflow.top()) * scaleY,
				(width + overflow.horizontal()) * scaleX,
				(height + overflow.vertical()) * scaleY
		};
	}

	private float rootX() {
		float result = x;
		for (GLParent parent = glParent; parent instanceof GLContainer container;
				parent = container.getGLParent())
			result += container.x;
		return result;
	}

	private float rootY() {
		float result = y;
		for (GLParent parent = glParent; parent instanceof GLContainer container;
				parent = container.getGLParent())
			result += container.y;
		return result;
	}

	private GLParent requireGLParent() {
		GLParent parent = glParent;
		if (parent == null)
			throw new IllegalStateException(
					"A detached GL container has no sibling Z-order");
		return parent;
	}
}
