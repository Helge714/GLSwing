package com.vingame.glswing.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusListener;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.List;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;
import javax.swing.text.AttributeSet;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.history.GLHistoryManager;
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
public abstract class GLContainer implements GLParent, GLMouseTarget, Accessible {
	private Object connectedShapeOwner;
	private com.vingame.glswing.rendering.GLCornerRadii connectedShape;
	/** Layout-owned clip; custom controls should use the layout metric helpers below. */
	public final com.vingame.glswing.rendering.GLCornerRadii getConnectedShape() { return connectedShape; }
	public final void setConnectedShape(Object owner, com.vingame.glswing.rendering.GLCornerRadii shape) {
		java.util.Objects.requireNonNull(owner);
		if(shape==null && connectedShapeOwner!=owner) return;
		if(java.util.Objects.equals(shape,connectedShape) && owner==connectedShapeOwner) return;
		connectedShapeOwner=shape==null?null:owner; connectedShape=shape; invalidateRecording();
	}
	protected final float layoutCornerRadius(float radius) { return connectedShape==null?radius:0; }
	protected final float layoutBorderWidth(float width) { return connectedShape==null?width:0; }

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
	private volatile GLHistoryManager localHistoryManager;
	private volatile GLInputMap localInputMap;
	private volatile GLInsets visualOverflow = GLInsets.ZERO;
	private volatile float[] lastVisualBounds;
	private volatile float lastRenderedRootX;
	private volatile float lastRenderedRootY;
	private volatile float lastRenderedVisualWidth;
	private volatile float lastRenderedVisualHeight;
	private volatile float lastRenderedOverflowLeft;
	private volatile float lastRenderedOverflowTop;
	private transient AccessibleContext accessibleContext;
	private volatile String accessibleName;
	private volatile String accessibleDescription;
	private volatile AccessibleRole accessibleRole;
	private volatile boolean accessibilityEnabled = true;
	private volatile boolean accessibilityTransparent;

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
		boolean connectedClip = false;
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
			if(connectedShape!=null) {
				context.beginUIRoundedClip(0,0,width,height,connectedShape); connectedClip=true;
			}
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
			if(connectedClip) context.endClip();
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
		String oldAccessibleName = getAccessibleName();
		this.name = name;
		if (accessibleName == null)
			fireAccessibleProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
					oldAccessibleName, getAccessibleName());
		repaintGL();
	}

	public final void clearName() {
		setName(null);
	}

	public final boolean hasExplicitName() {
		return name != null;
	}

	/**
	 * Returns the semantic name announced by assistive technology. An explicit
	 * accessible name takes precedence over the normal component name.
	 */
	public final String getAccessibleName() {
		return accessibleName == null ? getName() : accessibleName;
	}

	public final void setAccessibleName(String name) {
		if (name != null && name.isBlank())
			throw new IllegalArgumentException("Accessible name must not be blank");
		if (java.util.Objects.equals(accessibleName, name)) return;
		String old = getAccessibleName();
		accessibleName = name;
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
				old, getAccessibleName());
	}

	public final String getAccessibleDescription() {
		return accessibleDescription;
	}

	public final void setAccessibleDescription(String description) {
		if (description != null && description.isBlank())
			throw new IllegalArgumentException(
					"Accessible description must not be blank");
		if (java.util.Objects.equals(accessibleDescription, description)) return;
		String old = accessibleDescription;
		accessibleDescription = description;
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY,
				old, description);
	}

	/** Overrides the control-specific Java accessibility role. */
	public final void setAccessibleRole(AccessibleRole role) {
		if (accessibleRole == role) return;
		AccessibleRole old = getAccessibleRole();
		accessibleRole = role;
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
				old, getAccessibleRole());
	}

	public final AccessibleRole getAccessibleRole() {
		return accessibleRole == null ? defaultAccessibleRole() : accessibleRole;
	}

	/** Removes purely decorative nodes, including their subtree, from the tree. */
	public final void setAccessibilityEnabled(boolean enabled) {
		if (accessibilityEnabled == enabled) return;
		accessibilityEnabled = enabled;
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_CHILD_PROPERTY,
				null, this);
	}

	public final boolean isAccessibilityEnabled() {
		return accessibilityEnabled;
	}

	/**
	 * Controls whether this node is omitted while its accessible descendants are
	 * promoted into the nearest semantic ancestor. This is intended for layout
	 * hosts, viewports, and other implementation-only wrappers.
	 */
	public final void setAccessibilityTransparent(boolean transparent) {
		if (accessibilityTransparent == transparent) return;
		accessibilityTransparent = transparent;
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_CHILD_PROPERTY,
				null, this);
	}

	public final boolean isAccessibilityTransparent() {
		return accessibilityTransparent;
	}

	/** Default role for custom containers; subclasses should refine it. */
	protected AccessibleRole defaultAccessibleRole() {
		return getGLChildren().isEmpty()
				? AccessibleRole.CANVAS : AccessibleRole.PANEL;
	}

	/** Adds control-specific states such as selected, editable or expanded. */
	protected void collectAccessibleStates(AccessibleStateSet states) {
	}

	/** Number of semantic actions offered by this node. */
	protected int accessibleActionCount() { return 0; }

	protected String accessibleActionDescription(int index) { return null; }

	protected boolean performAccessibleAction(int index) { return false; }

	/** Optional numeric value contract used by sliders, progress and spinners. */
	protected Number accessibleCurrentValue() { return null; }

	protected boolean setAccessibleCurrentValue(Number value) { return false; }

	protected Number accessibleMinimumValue() { return null; }

	protected Number accessibleMaximumValue() { return null; }

	/** Optional text exposed by text-bearing controls. */
	protected String accessibleTextContent() { return null; }

	protected int accessibleCaretPosition() { return -1; }

	protected int accessibleSelectionStart() { return -1; }

	protected int accessibleSelectionEnd() { return -1; }

	/** Announces a dynamic semantic state change to assistive technology. */
	protected final void fireAccessibleStateChange(AccessibleState state,
			boolean present) {
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
				present ? null : state, present ? state : null);
	}

	/** Announces a changed semantic value. */
	protected final void fireAccessibleValueChange(Object oldValue,
			Object newValue) {
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_VALUE_PROPERTY,
				oldValue, newValue);
	}

	/** Announces a changed automatically derived accessible name. */
	protected final void fireAccessibleNameChange(String oldName,
			String newName) {
		if (accessibleName == null)
			fireAccessibleProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
					oldName, newName);
	}

	/** Announces that a text control's content changed. */
	protected final void fireAccessibleTextChange(Object oldText,
			Object newText) {
		fireAccessibleProperty(AccessibleContext.ACCESSIBLE_TEXT_PROPERTY,
				oldText, newText);
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
	public final GLHistoryManager getGLHistoryManager() {
		GLHistoryManager manager = localHistoryManager;
		if (manager != null)
			return manager;
		GLParent parent = glParent;
		return parent == null ? null : parent.getGLHistoryManager();
	}

	/**
	 * Defines an independent undo/redo history for this complete subtree.
	 * Descendants inherit this manager unless they define a nearer scope.
	 */
	public final void setGLHistoryManager(GLHistoryManager manager) {
		if (manager == null)
			throw new IllegalArgumentException("History manager must not be null");
		localHistoryManager = manager;
	}

	/** Restores history inheritance from the nearest parent scope or host. */
	public final void clearGLHistoryManager() {
		localHistoryManager = null;
	}

	/** Whether this container starts an explicit history scope. */
	public final boolean hasExplicitGLHistoryManager() {
		return localHistoryManager != null;
	}

	@Override
	public final GLInputMap getGLInputMap() {
		GLInputMap inputMap = localInputMap;
		if (inputMap != null)
			return inputMap;
		GLParent parent = glParent;
		return parent == null ? GLInputMaps.DEFAULT : parent.getGLInputMap();
	}

	/** Defines the semantic input map for this complete subtree. */
	public final void setGLInputMap(GLInputMap inputMap) {
		if (inputMap == null)
			throw new IllegalArgumentException("Input map must not be null");
		if (localInputMap == inputMap) return;
		localInputMap = inputMap;
		notifyInputMapChanged();
	}

	/** Restores input-map inheritance from the nearest parent or host. */
	public final void clearGLInputMap() {
		if (localInputMap == null) return;
		localInputMap = null;
		notifyInputMapChanged();
	}

	public final boolean hasExplicitGLInputMap() {
		return localInputMap != null;
	}

	@Override
	public final void requestGLFocus(GLKeyTarget target) {
		GLParent parent = glParent;
		if (parent != null)
			parent.requestGLFocus(target);
	}

	@Override
	public final GLKeyTarget getGLFocusOwner() {
		GLParent parent = glParent;
		return parent == null ? null : parent.getGLFocusOwner();
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

	@Override
	public final void repaintInteractively(GLContainer source,
			Runnable settledCallback) {
		GLParent parent = glParent;
		if (parent != null)
			parent.repaintInteractively(source, settledCallback);
	}

	@Override
	public final boolean finishInteractiveRepaint() {
		GLParent parent = glParent;
		return parent != null && parent.finishInteractiveRepaint();
	}

	final void setGLParent(GLParent parent) {
		if (glParent != parent && connectedShapeOwner != null)
			setConnectedShape(connectedShapeOwner, null);
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

	/**
	 * Updates only the presentation position during an active captured-pointer
	 * gesture. The gesture owner must request color repainting and rebuild picking
	 * once the gesture ends. This keeps the captured target and its local origin
	 * stable while it moves.
	 *
	 * @return whether the position changed
	 */
	protected final boolean setLocationDuringPointerCapture(float x, float y) {
		requireFinite(x, y);
		if (this.x == x && this.y == y)
			return false;
		this.x = x;
		this.y = y;
		return true;
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
		fireAccessibleStateChange(AccessibleState.VISIBLE, visible);
		if (!visible)
			clearGLFocus(this);
		invalidateParentLayout();
	}

	protected void resized(float width, float height) {
	}

	protected void attached(GLParent parent) {
	}

	protected void detached(GLParent formerParent) {
	}

	@Override
	public final AccessibleContext getAccessibleContext() {
		if (accessibleContext == null)
			accessibleContext = new GLAccessibleContext();
		return accessibleContext;
	}

	private void fireAccessibleProperty(String property, Object oldValue,
			Object newValue) {
		AccessibleContext context = accessibleContext;
		if (context != null)
			context.firePropertyChange(property, oldValue, newValue);
	}

	final List<GLContainer> accessibleChildren() {
		return List.copyOf(accessibleChildrenInOrder());
	}

	/** Returns semantic children in assistive-technology navigation order. */
	protected List<GLContainer> accessibleChildrenInOrder() {
		List<GLContainer> result = new java.util.ArrayList<>();
		for (GLContainer child : getGLChildren())
			appendAccessibleChild(result, child);
		return result;
	}

	static List<GLContainer> accessibleChildrenOf(GLParent parent) {
		if (parent instanceof GLContainer container)
			return container.accessibleChildren();
		List<GLContainer> result = new java.util.ArrayList<>();
		if (parent != null)
			for (GLContainer child : parent.getGLChildren())
				appendAccessibleChild(result, child);
		return List.copyOf(result);
	}

	private static void appendAccessibleChild(List<GLContainer> result,
			GLContainer child) {
		if (!child.isAccessibilityEnabled() || !child.isVisible()) return;
		if (child.isAccessibilityTransparent())
			result.addAll(child.accessibleChildren());
		else result.add(child);
	}

	final Accessible accessibleAt(Point point) {
		if (point == null) return null;
		List<GLContainer> children = getGLChildrenInPaintOrder();
		for (int index = children.size() - 1; index >= 0; index--) {
			GLContainer child = children.get(index);
			if (!child.isAccessibilityEnabled() || !child.isVisible()
					|| point.x < child.getX() || point.y < child.getY()
					|| point.x >= child.getX() + child.getWidth()
					|| point.y >= child.getY() + child.getHeight()) continue;
			if (!child.isAccessibilityTransparent()) return child;
			Accessible descendant = child.accessibleAt(new Point(
					Math.round(point.x - child.getX()),
					Math.round(point.y - child.getY())));
			if (descendant != null) return descendant;
		}
		return null;
	}

	private Component accessibleHost() {
		for (GLParent parent = glParent; parent != null;) {
			if (parent instanceof Component component) return component;
			parent = parent instanceof GLContainer container
					? container.getGLParent() : null;
		}
		return null;
	}

	private Point rootLocation() {
		float rootX = x;
		float rootY = y;
		for (GLParent parent = glParent; parent instanceof GLContainer container;
				parent = container.getGLParent()) {
			rootX += container.getX();
			rootY += container.getY();
		}
		return new Point(Math.round(rootX), Math.round(rootY));
	}

	private Point semanticLocation() {
		float semanticX = x;
		float semanticY = y;
		GLParent parent = glParent;
		while (parent instanceof GLContainer container
				&& container.isAccessibilityTransparent()) {
			semanticX += container.getX();
			semanticY += container.getY();
			parent = container.getGLParent();
		}
		return new Point(Math.round(semanticX), Math.round(semanticY));
	}

	private final class GLAccessibleContext extends AccessibleContext
			implements AccessibleComponent, AccessibleAction, AccessibleValue,
				AccessibleText {
		@Override public String getAccessibleName() {
			return GLContainer.this.getAccessibleName();
		}
		@Override public void setAccessibleName(String name) {
			GLContainer.this.setAccessibleName(name);
		}
		@Override public String getAccessibleDescription() {
			return GLContainer.this.getAccessibleDescription();
		}
		@Override public void setAccessibleDescription(String description) {
			GLContainer.this.setAccessibleDescription(description);
		}
		@Override public AccessibleRole getAccessibleRole() {
			return GLContainer.this.getAccessibleRole();
		}
		@Override public AccessibleStateSet getAccessibleStateSet() {
			AccessibleStateSet states = new AccessibleStateSet();
			if (isEnabled()) states.add(AccessibleState.ENABLED);
			if (isVisible()) states.add(AccessibleState.VISIBLE);
			if (isShowing()) states.add(AccessibleState.SHOWING);
			if (GLContainer.this instanceof GLKeyTarget target
					&& target.isFocusTraversable())
				states.add(AccessibleState.FOCUSABLE);
			if (getGLFocusOwner() == GLContainer.this)
				states.add(AccessibleState.FOCUSED);
			collectAccessibleStates(states);
			return states;
		}
		@Override public Accessible getAccessibleParent() {
			GLParent parent = glParent;
			while (parent instanceof GLContainer container
					&& container.isAccessibilityTransparent())
				parent = container.getGLParent();
			return parent instanceof Accessible accessible ? accessible : null;
		}
		@Override public void setAccessibleParent(Accessible parent) {
			// Parentage follows the retained GL tree and cannot be reassigned here.
		}
		@Override public int getAccessibleIndexInParent() {
			if (glParent == null) return -1;
			GLParent semanticParent = glParent;
			while (semanticParent instanceof GLContainer container
					&& container.isAccessibilityTransparent())
				semanticParent = container.getGLParent();
			List<GLContainer> siblings = accessibleChildrenOf(semanticParent);
			return siblings.indexOf(GLContainer.this);
		}
		@Override public int getAccessibleChildrenCount() {
			return accessibleChildren().size();
		}
		@Override public Accessible getAccessibleChild(int index) {
			List<GLContainer> children = accessibleChildren();
			return index < 0 || index >= children.size() ? null : children.get(index);
		}
		@Override public Locale getLocale() {
			Component host = accessibleHost();
			return host == null ? Locale.getDefault() : host.getLocale();
		}
		@Override public AccessibleComponent getAccessibleComponent() { return this; }
		@Override public AccessibleAction getAccessibleAction() {
			return accessibleActionCount() == 0 ? null : this;
		}
		@Override public AccessibleValue getAccessibleValue() {
			return accessibleCurrentValue() == null ? null : this;
		}
		@Override public AccessibleText getAccessibleText() {
			return accessibleTextContent() == null ? null : this;
		}
		@Override public java.awt.Color getBackground() { return null; }
		@Override public void setBackground(java.awt.Color color) { }
		@Override public java.awt.Color getForeground() { return null; }
		@Override public void setForeground(java.awt.Color color) { }
		@Override public java.awt.Cursor getCursor() {
			Component host = accessibleHost();
			return host == null ? null : host.getCursor();
		}
		@Override public void setCursor(java.awt.Cursor cursor) {
			Component host = accessibleHost();
			if (host != null) host.setCursor(cursor);
		}
		@Override public java.awt.Font getFont() { return null; }
		@Override public void setFont(java.awt.Font font) { }
		@Override public java.awt.FontMetrics getFontMetrics(java.awt.Font font) {
			Component host = accessibleHost();
			return host == null || font == null ? null : host.getFontMetrics(font);
		}
		@Override public boolean isEnabled() {
			return !(GLContainer.this instanceof com.vingame.glswing.ui.GLControl control)
					|| control.isEnabled();
		}
		@Override public void setEnabled(boolean enabled) {
			if (GLContainer.this instanceof com.vingame.glswing.ui.GLControl control)
				control.setEnabled(enabled);
		}
		@Override public boolean isVisible() { return GLContainer.this.isVisible(); }
		@Override public void setVisible(boolean visible) {
			GLContainer.this.setVisible(visible);
		}
		@Override public boolean isShowing() {
			if (!isVisible()) return false;
			Component host = accessibleHost();
			return host != null && host.isShowing();
		}
		@Override public boolean contains(Point point) {
			return point != null && point.x >= 0 && point.y >= 0
					&& point.x < getWidth() && point.y < getHeight();
		}
		@Override public Point getLocationOnScreen() {
			Component host = accessibleHost();
			if (host == null || !host.isShowing()) return null;
			Point result = host.getLocationOnScreen();
			Point root = rootLocation();
			result.translate(root.x, root.y);
			return result;
		}
		@Override public Point getLocation() {
			return semanticLocation();
		}
		@Override public void setLocation(Point point) {
			if (point != null) GLContainer.this.setLocation(point.x, point.y);
		}
		@Override public Rectangle getBounds() {
			Point location = semanticLocation();
			return new Rectangle(location.x, location.y,
					Math.round(getWidth()), Math.round(getHeight()));
		}
		@Override public void setBounds(Rectangle bounds) {
			if (bounds != null) GLContainer.this.setBounds(bounds.x, bounds.y,
					bounds.width, bounds.height);
		}
		@Override public java.awt.Dimension getSize() {
			return new java.awt.Dimension(Math.round(getWidth()), Math.round(getHeight()));
		}
		@Override public void setSize(java.awt.Dimension size) {
			if (size != null) GLContainer.this.setSize(size.width, size.height);
		}
		@Override public Accessible getAccessibleAt(Point point) {
			return GLContainer.this.accessibleAt(point);
		}
		@Override public boolean isFocusTraversable() {
			return GLContainer.this instanceof GLKeyTarget target
					&& target.isFocusTraversable();
		}
		@Override public void requestFocus() {
			if (GLContainer.this instanceof GLKeyTarget target
					&& target.isFocusTraversable())
				requestGLFocus(target);
		}
		@Override public void addFocusListener(FocusListener listener) { }
		@Override public void removeFocusListener(FocusListener listener) { }
		@Override public int getAccessibleActionCount() {
			return accessibleActionCount();
		}
		@Override public String getAccessibleActionDescription(int index) {
			return accessibleActionDescription(index);
		}
		@Override public boolean doAccessibleAction(int index) {
			return performAccessibleAction(index);
		}
		@Override public Number getCurrentAccessibleValue() {
			return accessibleCurrentValue();
		}
		@Override public boolean setCurrentAccessibleValue(Number value) {
			return setAccessibleCurrentValue(value);
		}
		@Override public Number getMinimumAccessibleValue() {
			return accessibleMinimumValue();
		}
		@Override public Number getMaximumAccessibleValue() {
			return accessibleMaximumValue();
		}
		@Override public int getIndexAtPoint(Point point) { return -1; }
		@Override public Rectangle getCharacterBounds(int index) { return null; }
		@Override public int getCharCount() {
			String text = accessibleTextContent();
			return text == null ? 0 : text.length();
		}
		@Override public int getCaretPosition() { return accessibleCaretPosition(); }
		@Override public String getAtIndex(int part, int index) {
			return textSegment(part, index, 0);
		}
		@Override public String getAfterIndex(int part, int index) {
			return textSegment(part, index, 1);
		}
		@Override public String getBeforeIndex(int part, int index) {
			return textSegment(part, index, -1);
		}
		@Override public AttributeSet getCharacterAttribute(int index) { return null; }
		@Override public int getSelectionStart() { return accessibleSelectionStart(); }
		@Override public int getSelectionEnd() { return accessibleSelectionEnd(); }
		@Override public String getSelectedText() {
			String text = accessibleTextContent();
			int start = accessibleSelectionStart();
			int end = accessibleSelectionEnd();
			return text == null || start < 0 || end <= start || end > text.length()
					? null : text.substring(start, end);
		}

		private String textSegment(int part, int index, int direction) {
			String text = accessibleTextContent();
			if (text == null || text.isEmpty() || index < 0 || index >= text.length())
				return null;
			if (part == AccessibleText.CHARACTER) {
				int target = index + direction;
				return target < 0 || target >= text.length()
						? null : text.substring(target, target + 1);
			}
			BreakIterator iterator = part == AccessibleText.WORD
					? BreakIterator.getWordInstance(getLocale())
					: part == AccessibleText.SENTENCE
							? BreakIterator.getSentenceInstance(getLocale()) : null;
			if (iterator == null) return null;
			iterator.setText(text);
			int start = iterator.first();
			String previous = null;
			boolean containingSeen = false;
			for (int end = iterator.next(); end != BreakIterator.DONE;
					start = end, end = iterator.next()) {
				String segment = text.substring(start, end);
				if (index >= start && index < end) {
					if (direction < 0) return previous;
					if (direction == 0) return segment;
					containingSeen = true;
				} else if (containingSeen && direction > 0) return segment;
				previous = segment;
			}
			return null;
		}
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

	/** Called when the resolved inherited or local semantic input map changes. */
	protected void inputMapChanged() {
	}

	final void inheritedThemeChanged() {
		if (localTheme != null)
			return;
		notifyThemeChanged();
	}

	private void notifyThemeChanged() {
		// Theme values may affect custom rendering without changing a control's
		// typed style record (for example text-shadow softness). Every node must
		// therefore rebuild its recording, including currently hidden subtrees.
		invalidateRecording();
		themeChanged();
		for (GLContainer child : glChildren.snapshot())
			child.inheritedThemeChanged();
	}

	final void inheritedLocalizerChanged() {
		if (localLocalizer != null)
			return;
		notifyLocalizerChanged();
	}

	final void inheritedInputMapChanged() {
		if (localInputMap != null) return;
		notifyInputMapChanged();
	}

	private void notifyInputMapChanged() {
		inputMapChanged();
		for (GLContainer child : glChildren.snapshot())
			child.inheritedInputMapChanged();
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
