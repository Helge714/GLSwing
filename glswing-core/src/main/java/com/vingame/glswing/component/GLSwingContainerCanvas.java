package com.vingame.glswing.component;

import java.awt.Dimension;
import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;

/**
 * Direct-presenting {@link GLSwingCanvas} that owns the same retained
 * {@link GLContainer} hierarchy used by {@link GLSwingPanel}. It is useful for
 * measuring or displaying large control trees without GLJPanel's offscreen
 * Swing presentation step.
 */
public abstract class GLSwingContainerCanvas extends GLSwingCanvas
		implements GLParent {

	private static final long serialVersionUID = 1L;

	private final GLChildList glChildren = new GLChildList(this);
	private final GLLayoutSupport layoutSupport = new GLLayoutSupport(this);
	private final Map<GLFontSpec, GLFont> themedFonts = new LinkedHashMap<>();
	private final GLContainerInputDispatcher inputDispatcher;
	private final GLDamageTracker damageTracker = new GLDamageTracker();
    private final java.util.concurrent.atomic.AtomicBoolean displayQueued = new java.util.concurrent.atomic.AtomicBoolean();
	private final GLInteractiveRepaintController interactiveRepaint =
			new GLInteractiveRepaintController(() -> {
				damageTracker.repaintAll(true);
				requestDisplay();
			});
	private volatile GLTheme theme = GLTheme.DEFAULT;
	private volatile GLLocalizer localizer = GLLocalizer.DEFAULT;
	private volatile GLHistoryManager historyManager = new GLHistoryManager();
	private volatile GLInputMap inputMap = GLInputMaps.DEFAULT;
	private transient GLKeyTarget focusedTarget;

	protected GLSwingContainerCanvas() {
		super();
		inputDispatcher = new GLContainerInputDispatcher(this);
	}

	protected GLSwingContainerCanvas(int antialiasingSamples, int swapInterval) {
		super(antialiasingSamples, swapInterval);
		inputDispatcher = new GLContainerInputDispatcher(this);
	}

	@Override
	public AccessibleContext getAccessibleContext() {
		if (accessibleContext == null)
			accessibleContext = new AccessibleGLSwingContainerCanvas();
		return accessibleContext;
	}

	protected class AccessibleGLSwingContainerCanvas extends AccessibleAWTCanvas {
		private static final long serialVersionUID = 1L;
		@Override public AccessibleRole getAccessibleRole() {
			return AccessibleRole.CANVAS;
		}
		@Override public int getAccessibleChildrenCount() {
			return accessibleRootChildren().size();
		}
		@Override public Accessible getAccessibleChild(int index) {
			List<GLContainer> children = accessibleRootChildren();
			return index < 0 || index >= children.size() ? null : children.get(index);
		}
		@Override public Accessible getAccessibleAt(Point point) {
			if (point == null) return null;
			List<GLContainer> children = glChildren.paintOrderSnapshot();
			for (int index = children.size() - 1; index >= 0; index--) {
				GLContainer child = children.get(index);
				if (!child.isAccessibilityEnabled() || !child.isVisible()) continue;
				if (point.x >= child.getX() && point.y >= child.getY()
						&& point.x < child.getX() + child.getWidth()
						&& point.y < child.getY() + child.getHeight()) {
					if (!child.isAccessibilityTransparent()) return child;
					Accessible descendant = child.accessibleAt(new Point(
							Math.round(point.x - child.getX()),
							Math.round(point.y - child.getY())));
					if (descendant != null) return descendant;
				}
			}
			return null;
		}
		private List<GLContainer> accessibleRootChildren() {
			return GLContainer.accessibleChildrenOf(GLSwingContainerCanvas.this);
		}
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

	@Override
	public final void removeAllGL() {
		glChildren.removeAll();
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
	public final void setGLConstraints(GLContainer child,
			GLConstraints constraints) {
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
		layoutSupport.ensure(getWidth(), getHeight());
	}

	@Override
	public final GLSize getPreferredGLSize() {
		if (!glChildren.isEmpty())
			return layoutSupport.measure();
		Dimension preferred = getPreferredSize();
		return new GLSize(Math.max(0, preferred.width),
				Math.max(0, preferred.height));
	}

	@Override
	public final void packGL() {
		GLSize preferred = getPreferredGLSize();
		setPreferredSize(new Dimension((int) Math.ceil(preferred.width()),
				(int) Math.ceil(preferred.height())));
		invalidate();
		if (getWidth() > 0 && getHeight() > 0)
			layoutGL();
	}

	@Override
	public final void setGLTheme(GLTheme theme) {
		if (theme == null)
			throw new IllegalArgumentException("GL theme must not be null");
		if (this.theme == theme)
			return;
		this.theme = theme;
		for (GLContainer child : glChildren.snapshot())
			child.inheritedThemeChanged();
		repaintGL();
	}

	@Override
	public final GLTheme getGLTheme() {
		return theme;
	}

	@Override
	public final void setGLLocalizer(GLLocalizer localizer) {
		if (localizer == null)
			throw new IllegalArgumentException("GL localizer must not be null");
		if (this.localizer == localizer)
			return;
		this.localizer = localizer;
		for (GLContainer child : glChildren.snapshot())
			child.inheritedLocalizerChanged();
		repaintGL();
	}

	@Override
	public final GLLocalizer getGLLocalizer() {
		return localizer;
	}

	@Override
	public final synchronized GLFont getGLFont(GLFontSpec specification) {
		if (specification == null)
			throw new IllegalArgumentException(
					"Font specification must not be null");
		return themedFonts.computeIfAbsent(specification,
				GLFontSpec::createFont);
	}

	@Override
	public final GLHistoryManager getGLHistoryManager() {
		return historyManager;
	}

	public final void setGLHistoryManager(GLHistoryManager historyManager) {
		if (historyManager == null)
			throw new IllegalArgumentException("History manager must not be null");
		this.historyManager = historyManager;
	}

	@Override
	public final GLInputMap getGLInputMap() { return inputMap; }

	public final void setGLInputMap(GLInputMap inputMap) {
		if (inputMap == null)
			throw new IllegalArgumentException("Input map must not be null");
		if (this.inputMap == inputMap) return;
		this.inputMap = inputMap;
		for (GLContainer child : glChildren.snapshot())
			child.inheritedInputMapChanged();
	}

	@Override
	public final void requestGLFocus(GLKeyTarget target) {
		if (target != null) {
			if (!(target instanceof GLContainer container)
					|| !belongsToCanvas(container))
				throw new IllegalArgumentException(
						"Focused GL target must belong to this canvas");
			if (!target.isFocusTraversable())
				target = null;
		}
		if (focusedTarget == target)
			return;
		GLKeyTarget previous = focusedTarget;
		focusedTarget = target;
		if (previous != null)
			previous.focusLost();
		if (target != null)
			target.focusGained();
		repaintColorOnly();
	}

	@Override
	public final GLKeyTarget getGLFocusOwner() { return focusedTarget; }

	@Override
	public final void clearGLFocus(GLContainer subtree) {
		if (subtree == null)
			return;
		GLKeyTarget current = focusedTarget;
		if (current instanceof GLContainer container
				&& isInside(container, subtree))
			requestGLFocus(null);
	}

    /** Coalesce explicit GL redraws on the EDT, including modal AWT event loops.
     * Heavyweight Canvas repaint events alone need not reach a modal-blocked owner.
     * Animated canvases keep their existing frame scheduler.
     */
    private void requestDisplay() {
        super.repaint();
        // AWT can invoke repaint while this object's superclass is being constructed.
        if(displayQueued==null || !displayQueued.compareAndSet(false,true))return;
        java.awt.EventQueue.invokeLater(()->{
            displayQueued.set(false);
            var animator=getAnimator();
            if(isShowing() && isRealized() && (animator==null || !animator.isAnimating()))display();
        });
    }

	@Override
	public final void repaintGL() {
		interactiveRepaint.cancel();
		damageTracker.repaintAll(true);
		requestDisplay();
	}

	@Override
	public final void repaintGL(GLContainer source) {
		damageTracker.repaint(source, false);
		requestDisplay();
	}

	@Override
	public final void repaintAndRebuildPicking(GLContainer source) {
		interactiveRepaint.cancel();
		damageTracker.repaint(source, true);
		requestDisplay();
	}

	@Override
	public final void repaintInteractively(GLContainer source,
			Runnable settledCallback) {
		damageTracker.repaint(source, false);
		requestDisplay();
		int delay = (int) Math.round(inputMap.getValue(
				GLInputActions.RENDER_INTERACTION_SETTLE_MILLIS, 75));
		interactiveRepaint.request(source, delay, settledCallback);
	}

	@Override
	public final boolean finishInteractiveRepaint() {
		return interactiveRepaint.settle();
	}

	@Override
	public void repaint() {
		if (interactiveRepaint != null)
			interactiveRepaint.cancel();
		if (damageTracker != null)
			damageTracker.repaintAll(true);
		requestDisplay();
	}

	final void repaintColorOnly() {
		damageTracker.repaintAll(false);
		requestDisplay();
	}

	@Override
	protected final void asynchronousResourceUpdated() {
		repaintColorOnly();
	}

	final boolean isPickingDirty() {
		return damageTracker.isPickingDirty();
	}

	@Override
	protected final void pickSnapshotsUpdated() {
		inputDispatcher.pickSnapshotsUpdated();
	}

	@Override
	protected final boolean isSceneCachingEnabled() {
		return true;
	}

	@Override
	protected final SceneUpdate consumeSceneUpdate(float logicalWidth,
			float logicalHeight) {
		GLDamageTracker.Damage damage = damageTracker.consume(
				logicalWidth, logicalHeight);
		return new SceneUpdate(damage.redraw(), damage.full(),
				damage.rebuildPicking(), damage.bounds());
	}

	@Override
	protected final void sceneInvalidated() {
		damageTracker.repaintAll(true);
	}

	@Override
	protected final void renderResolutionChanged() {
		// The color viewport changes, but logical geometry and the one-pixel-per-
		// logical-pixel picking surface remain valid. Keeping the pick snapshot
		// current also prevents mouse release from forcing a reentrant display().
		repaintColorOnly();
	}

	@Override
	protected final void initialize(GL3 gl) {
		initializeContainer(gl);
	}

	@Override
	protected final void render(GLContext2D context) {
		layoutSupport.ensure(getWidth(), getHeight());
		renderContainer(context);
		glChildren.render(context);
	}

	@Override
	protected final void resized(GL3 gl, int surfaceWidth, int surfaceHeight) {
		layoutSupport.invalidateLocal();
		resizedContainer(gl, surfaceWidth, surfaceHeight);
	}

	@Override
	protected final void disposeResources(GL3 gl) {
		interactiveRepaint.cancel();
		inputDispatcher.reset();
		disposeContainerResources(gl);
		requestGLFocus(null);
		synchronized (this) {
			for (GLFont font : themedFonts.values())
				font.dispose(gl);
			themedFonts.clear();
		}
	}

	protected void initializeContainer(GL3 gl) {
	}

	/** Draws canvas-owned content immediately before the retained children. */
	protected void renderContainer(GLContext2D context) {
	}

	protected void resizedContainer(GL3 gl,
			int surfaceWidth, int surfaceHeight) {
	}

	protected void disposeContainerResources(GL3 gl) {
	}

	final GLKeyTarget focusedGLTarget() {
		return focusedTarget;
	}

	final boolean isAttachedTarget(GLMouseTarget target) {
		return !(target instanceof GLContainer container)
				|| belongsToCanvas(container);
	}

	final boolean belongsToCanvas(GLContainer container) {
		GLParent parent = container.getGLParent();
		while (parent instanceof GLContainer ancestor)
			parent = ancestor.getGLParent();
		return parent == this;
	}

	private static boolean isInside(GLContainer container,
			GLContainer subtree) {
		for (GLContainer current = container; current != null;) {
			if (current == subtree)
				return true;
			GLParent parent = current.getGLParent();
			current = parent instanceof GLContainer ancestor ? ancestor : null;
		}
		return false;
	}
}
