package com.vingame.glswing.component;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.AWTException;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLEditingHistoryTarget;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLMouseWheelCaptureTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.input.GLImmediateHoverRegistry;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerControllers;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDrawCallProfile;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.GLRecording;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.internal.SceneFramebuffer;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLMenuMnemonics;

/**
 * Base class for event-driven OpenGL rendering inside Swing.
 * <p>
 * Subclasses render through the Graphics2D-style {@link GLContext2D}. Optional
 * custom GPU resources can be managed through the lifecycle hooks. The panel
 * does not clear the visible framebuffer automatically; call
 * {@link GLContext2D#clear()} or {@link GLContext2D#clear(java.awt.Color)} from
 * {@link #render(GLContext2D)} when a fresh frame is desired.
 */
public abstract class GLSwingPanel extends GLJPanel
		implements GLEventListener, GLParent {

	private static final long serialVersionUID = 1L;
	public static final int DEFAULT_ANTIALIASING_SAMPLES = 4;

	private final int requestedAntialiasingSamples;
	private final GLChildList glChildren = new GLChildList(this);
	private final GLRecordingCache recordingCache = new GLRecordingCache();
	private final GLLayoutSupport layoutSupport = new GLLayoutSupport(this);
	private final GLDamageTracker damageTracker = new GLDamageTracker();
	private final GLInteractiveRepaintController interactiveRepaint =
			new GLInteractiveRepaintController(() -> {
				damageTracker.repaintAll(true);
				super.repaint();
			});
	private final Map<GLFontSpec, GLFont> themedFonts = new LinkedHashMap<>();
	private volatile GLTheme theme = GLTheme.DEFAULT;
	private volatile GLLocalizer localizer = GLLocalizer.DEFAULT;
	private volatile GLHistoryManager historyManager = new GLHistoryManager();
	private volatile GLInputMap inputMap = GLInputMaps.DEFAULT;
	private volatile int actualAntialiasingSamples;
	private volatile int lastSurfaceWidth;
	private volatile int lastSurfaceHeight;
	private volatile int lastRenderWidth;
	private volatile int lastRenderHeight;
	private volatile float renderResolutionScale = 1;
	private transient GLRenderer2D renderer;
	private transient SceneFramebuffer sceneFramebuffer;
	private volatile int lastDrawCallCount;
	private volatile GLDrawCallProfile lastDrawCallProfile = GLDrawCallProfile.EMPTY;
	private volatile double framesPerSecond;
	private volatile double averageFrameTimeMillis;
	private volatile GLFrameProfile frameProfile = GLFrameProfile.EMPTY;
	private transient GLGpuTimerQueries gpuTimers;
	private long statisticsHousekeepingNanos;
	private long statisticsSceneNanos;
	private long statisticsPresentNanos;
	private long endToEndWindowStartNanos;
	private long endToEndAccumulatedNanos;
	private int endToEndCount;
	private long statisticsWindowStartNanos;
	private long statisticsRenderNanos;
	private int statisticsFrameCount;
	private transient GLPickResult hoverPick = GLPickResult.none();
	private transient GLPickResult capturedPick = GLPickResult.none();
	private transient GLPickResult clickCandidate = GLPickResult.none();
	private transient MouseEvent pendingHoverEvent;
	private transient boolean pendingHoverMoved;
	private transient boolean hoverReadScheduled;
	private transient long hoverReadGeneration;
	private transient volatile MouseEvent postWheelHoverEvent;
	private transient volatile long postWheelHoverGeneration;
	private transient int pointerTasksPending;
	private final transient ArrayDeque<QueuedPointerTask> pointerTaskQueue =
			new ArrayDeque<>();
	private transient volatile boolean pointerTaskBatchScheduled;
	private transient int pressPicksPending;
	private transient MouseEvent pendingDragEvent;
	private transient GLKeyTarget focusedTarget;
	private int lastDragX;
	private int lastDragY;
	private transient boolean pointerWarpRequested;
	private transient float pointerWarpX;
	private transient float pointerWarpY;
	private transient boolean pointerWarpEventPending;
	private transient int expectedPointerWarpX;
	private transient int expectedPointerWarpY;
	private transient Robot pointerRobot;
	private final CompletableFuture<Void> firstFrameFuture = new CompletableFuture<>();
	private final AtomicReference<CompletableFuture<Void>> nextFrameFuture =
			new AtomicReference<>();
	private final transient GLPointerController pointerController =
			GLPointerControllers.forAwt(this);

	protected GLSwingPanel() {
		this(DEFAULT_ANTIALIASING_SAMPLES);
	}

	protected GLSwingPanel(int antialiasingSamples) {
		super(createCapabilities(antialiasingSamples));
		requestedAntialiasingSamples = antialiasingSamples;
		// JOGL's GLSL flip currently produces a black image with this GL3 panel.
		// Read the native GL orientation directly and flip it as part of Swing's
		// final drawImage operation instead of copying every row into a second CPU
		// buffer inside GLJPanel.
		setUseGLSLVerticalFlip(false);
		setSkipGLOrientationVerticalFlip(true);
		addGLEventListener(this);
		installMouseDispatch();
		installKeyboardDispatch();
	}

	@Override
	protected final void paintComponent(Graphics graphics) {
		long startNanos = System.nanoTime();
		try {
			if (!(graphics instanceof Graphics2D)) {
				super.paintComponent(graphics);
				return;
			}
			Graphics2D presentation = (Graphics2D) graphics.create();
			try {
				presentation.translate(0, getHeight());
				presentation.scale(1, -1);
				super.paintComponent(presentation);
			} finally {
				presentation.dispose();
			}
		} finally {
			updateEndToEndStatistics(startNanos, System.nanoTime());
		}
	}

	private static GLCapabilities createCapabilities(int antialiasingSamples) {
		if (antialiasingSamples < 0)
			throw new IllegalArgumentException("Antialiasing sample count must not be negative");
		GLCapabilities capabilities = new GLCapabilities(GLProfile.get(GLProfile.GL3));
		capabilities.setAlphaBits(8);
		capabilities.setSampleBuffers(antialiasingSamples > 0);
		if (antialiasingSamples > 0)
			capabilities.setNumSamples(antialiasingSamples);
		return capabilities;
	}

	public final int getLastDrawCallCount() {
		return lastDrawCallCount;
	}

	public final GLDrawCallProfile getLastDrawCallProfile() {
		return lastDrawCallProfile;
	}

	/** Recent rate at which {@link #display(GLAutoDrawable)} completed. */
	public final double getFramesPerSecond() {
		return framesPerSecond;
	}

	/** Recent average CPU time spent submitting one complete frame. */
	public final double getAverageFrameTimeMillis() {
		return averageFrameTimeMillis;
	}

	public final GLFrameProfile getFrameProfile() {
		return frameProfile;
	}

	/** Completes after the first frame has been rendered and presented. */
	public final CompletableFuture<Void> firstFrameFuture() {
		return firstFrameFuture;
	}

	/** Returns a future completed by the next successfully presented frame. */
	public final CompletableFuture<Void> nextFrameFuture() {
		CompletableFuture<Void> created = new CompletableFuture<>();
		if (nextFrameFuture.compareAndSet(null, created)) return created;
		return nextFrameFuture.get();
	}

	public final int getRequestedAntialiasingSamples() {
		return requestedAntialiasingSamples;
	}

	/** Returns the sample count supplied by the active OpenGL framebuffer. */
	public final int getActualAntialiasingSamples() {
		return actualAntialiasingSamples;
	}

	public final int getSurfacePixelWidth() {
		return lastSurfaceWidth;
	}

	public final int getSurfacePixelHeight() {
		return lastSurfaceHeight;
	}

	/** Scale of the retained color scene relative to the physical drawable. */
	public final float getRenderResolutionScale() {
		return renderResolutionScale;
	}

	public final int getRenderPixelWidth() {
		return lastRenderWidth;
	}

	public final int getRenderPixelHeight() {
		return lastRenderHeight;
	}

	/**
	 * Sets the color-scene resolution. Logical layout and the picking buffer are
	 * deliberately unaffected. GLJPanel's final native-size Swing readback is a
	 * JOGL implementation detail and cannot be reduced through this setting.
	 */
	public final void setRenderResolutionScale(float scale) {
		if (!Float.isFinite(scale) || scale < 0.25f || scale > 1)
			throw new IllegalArgumentException(
					"Render resolution scale must be between 0.25 and 1");
		if (Float.compare(renderResolutionScale, scale) == 0)
			return;
		renderResolutionScale = scale;
		damageTracker.repaintAll(false);
		super.repaint();
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
		layoutSupport.ensure(getWidth(), getHeight());
	}

	@Override
	public final GLSize getPreferredGLSize() {
		if (!glChildren.isEmpty())
			return layoutSupport.measure();
		Dimension preferred = getPreferredSize();
		return new GLSize(Math.max(0, preferred.width), Math.max(0, preferred.height));
	}

	@Override
	public final void packGL() {
		GLSize preferred = getPreferredGLSize();
		setPreferredSize(new Dimension(
				(int) Math.ceil(preferred.width()),
				(int) Math.ceil(preferred.height())));
		revalidate();
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
		invalidateRecording();
		for (GLContainer child : glChildren.snapshot())
			child.inheritedThemeChanged();
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
			throw new IllegalArgumentException("Font specification must not be null");
		return themedFonts.computeIfAbsent(specification, GLFontSpec::createFont);
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
					|| !belongsToPanel(container))
				throw new IllegalArgumentException(
						"Focused GL target must belong to this panel");
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
		if (current instanceof GLContainer container && isInside(container, subtree))
			requestGLFocus(null);
	}

	@Override
	public final void repaintGL() {
		interactiveRepaint.cancel();
		damageTracker.repaintAll(true);
		super.repaint();
	}

	@Override
	public final void repaintGL(GLContainer source) {
		damageTracker.repaint(source, false);
		super.repaint();
	}

	@Override
	public final void repaintAndRebuildPicking(GLContainer source) {
		interactiveRepaint.cancel();
		damageTracker.repaint(source, true);
		super.repaint();
	}

	@Override
	public final void repaintInteractively(GLContainer source,
			Runnable settledCallback) {
		damageTracker.repaint(source, false);
		super.repaint();
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
		super.repaint();
	}

	private void repaintColorOnly() {
		damageTracker.repaintAll(false);
		super.repaint();
	}

	/** Marks the panel's own cached content for lazy reconstruction. */
	public final void invalidateRecording() {
		recordingCache.invalidate();
		repaint();
	}

	public final boolean isRecordingDirty() {
		return recordingCache.isDirty();
	}

	/** Gives subclasses read-only access to their cached panel recording. */
	protected final GLRecording getPanelRecording() {
		return recordingCache.get();
	}

	/**
	 * Returns the topmost pick ID at a logical Swing pixel from the most recently
	 * completed CPU snapshot without entering or synchronizing with the GL thread.
	 */
	public final int pick(int x, int y) {
		return pickResult(x, y).getId();
	}

	/**
	 * Resolves a logical Swing pixel into its integer ID, direct mouse target and
	 * coordinates in the local system active when the hit geometry was drawn.
	 */
	public final GLPickResult pickResult(int x, int y) {
		GLRenderer2D activeRenderer = renderer;
		if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()
				|| activeRenderer == null)
			return GLPickResult.none();
		GLPickResult result = activeRenderer.readPickSnapshot(
				x, y, getWidth(), getHeight());
		return result == null ? GLPickResult.none() : result;
	}

	private void installMouseDispatch() {
		MouseAdapter dispatcher = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent event) {
				queueHoverRead(event, false);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				cancelHoverRead();
				if (!hasPressedMouseButton(event))
					cancelPointerCapture(event);
				leaveHover(event);
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				if (consumePointerWarpEvent(event))
					return;
				queueHoverRead(event, true);
			}

			@Override
			public void mousePressed(MouseEvent event) {
				finishInteractiveRepaint();
				pointerWarpEventPending = false;
				cancelHoverRead();
				clickCandidate = GLPickResult.none();
				requestFocusInWindow();
				pressPicksPending++;
				readPointerPick(event, true, result -> {
					pressPicksPending--;
					dispatchMousePressed(event, result);
					if (pressPicksPending == 0) {
						MouseEvent drag = pendingDragEvent;
						pendingDragEvent = null;
						if (drag != null)
							dispatchMouseDragged(drag);
					}
				});
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (consumePointerWarpEvent(event))
					return;
				if (pressPicksPending > 0)
					pendingDragEvent = event;
				else
					dispatchMouseDragged(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				cancelHoverRead();
				readPointerPick(event, true,
						releaseHit -> dispatchMouseReleased(event, releaseHit));
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				cancelHoverRead();
				queuePointerBarrier(() -> dispatchMouseClicked(event));
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				cancelHoverRead();
				readPointerPick(event, false, result -> {
					if (result.hasTarget()) {
						dispatchMouseWheel(event, result);
						repaintColorOnly();
					}
					SwingUtilities.invokeLater(() -> requestPostWheelHover(event));
				});
			}
		};
		addMouseListener(dispatcher);
		addMouseMotionListener(dispatcher);
		addMouseWheelListener(dispatcher);
	}

	private static boolean hasPressedMouseButton(MouseEvent event) {
		int buttons = MouseEvent.BUTTON1_DOWN_MASK
				| MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK;
		return (event.getModifiersEx() & buttons) != 0;
	}

	private void dispatchMouseWheel(MouseWheelEvent event, GLPickResult result) {
		GLMouseTarget capture = wheelCaptureTarget(result.getTarget());
		if (capture != null && capture != result.getTarget()) {
			GLMouseWheelEvent captured = new GLMouseWheelEvent(event,
					result.retarget(capture));
			capture.mouseWheelMoved(captured);
			if (captured.isConsumed())
				return;
		}
		GLMouseWheelEvent translated = new GLMouseWheelEvent(event, result);
		GLMouseTarget target = result.getTarget();
		while (target != null) {
			target.mouseWheelMoved(translated);
			if (translated.isConsumed() || !(target instanceof GLContainer child))
				return;
			GLParent parent = child.getGLParent();
			target = parent instanceof GLMouseTarget mouseTarget
					? mouseTarget : null;
		}
	}

	private static GLMouseTarget wheelCaptureTarget(GLMouseTarget target) {
		for (GLMouseTarget current = target; current instanceof GLContainer child;) {
			if (current instanceof GLMouseWheelCaptureTarget)
				return current;
			GLParent parent = child.getGLParent();
			current = parent instanceof GLMouseTarget mouseTarget
					? mouseTarget : null;
		}
		return target instanceof GLMouseWheelCaptureTarget ? target : null;
	}

	private void dispatchMousePressed(MouseEvent event, GLPickResult result) {
		GLMouseTarget hitTarget = result.getTarget();
		if (!preservesKeyboardFocus(hitTarget))
			requestGLFocus(hitTarget instanceof GLKeyTarget keyTarget
					&& keyTarget.isFocusTraversable() ? keyTarget : null);
		updateHover(event, result, false);
		lastDragX = event.getX();
		lastDragY = event.getY();
		capturedPick = result.hasTarget() ? dispatchPress(event, result)
				: GLPickResult.none();
		if (capturedPick.hasTarget())
			repaintColorOnly();
	}

	private static boolean preservesKeyboardFocus(GLMouseTarget target) {
		for (GLMouseTarget current = target; current != null;) {
			if (current.preservesKeyboardFocusOnPress()) return true;
			if (!(current instanceof GLContainer child)) return false;
			GLParent parent = child.getGLParent();
			current = parent instanceof GLMouseTarget mouseTarget ? mouseTarget : null;
		}
		return false;
	}

	private GLPickResult dispatchPress(MouseEvent event, GLPickResult initial) {
		GLPickResult current = initial;
		GLMouseTarget target = current.getTarget();
		GLMouseEvent translated = mouseEvent(event, current, 0, 0);
		target.mousePressed(translated);
		if (event.getButton() != MouseEvent.BUTTON2)
			return current;
		while (!translated.isConsumed() && target instanceof GLContainer child) {
			GLParent parent = child.getGLParent();
			if (!(parent instanceof GLMouseTarget parentTarget))
				break;
			target = parentTarget;
			current = current.retarget(target);
			translated = mouseEvent(event, current, 0, 0);
			target.mousePressed(translated);
		}
		return current;
	}

	private void dispatchMouseDragged(MouseEvent event) {
		if (!capturedPick.hasTarget())
			return;
		if (!isAttachedTarget(capturedPick.getTarget())) {
			capturedPick = GLPickResult.none();
			return;
		}
		GLPickResult current = capturedPick.at(event.getX(), event.getY());
		pointerWarpRequested = false;
		current.getTarget().mouseDragged(mouseEvent(event, current,
				event.getX() - lastDragX, event.getY() - lastDragY));
		if (pointerWarpRequested) {
			float x = Math.max(0, Math.min(getWidth() - 1, pointerWarpX));
			float y = Math.max(0, Math.min(getHeight() - 1, pointerWarpY));
			capturedPick = capturedPick.at(x, y);
			lastDragX = Math.round(x);
			lastDragY = Math.round(y);
			warpPointer(x, y);
		} else {
			capturedPick = current;
			lastDragX = event.getX();
			lastDragY = event.getY();
		}
		repaintColorOnly();
	}

	private void requestPointerWarp(float x, float y) {
		pointerWarpRequested = true;
		pointerWarpX = x;
		pointerWarpY = y;
	}

	private boolean consumePointerWarpEvent(MouseEvent event) {
		if (!pointerWarpEventPending)
			return false;
		pointerWarpEventPending = false;
		if (Math.abs(event.getX() - expectedPointerWarpX) > 2
				|| Math.abs(event.getY() - expectedPointerWarpY) > 2)
			return false;
		lastDragX = event.getX();
		lastDragY = event.getY();
		if (capturedPick.hasTarget())
			capturedPick = capturedPick.at(event.getX(), event.getY());
		return true;
	}

	private void warpPointer(float x, float y) {
		if (!isShowing())
			return;
		try {
			Point origin = getLocationOnScreen();
			Robot robot = pointerRobot;
			if (robot == null) {
				robot = getGraphicsConfiguration() == null
						? new Robot() : new Robot(getGraphicsConfiguration().getDevice());
				pointerRobot = robot;
			}
			expectedPointerWarpX = Math.round(x);
			expectedPointerWarpY = Math.round(y);
			pointerWarpEventPending = true;
			robot.mouseMove(origin.x + expectedPointerWarpX,
					origin.y + expectedPointerWarpY);
		} catch (AWTException | IllegalComponentStateException | SecurityException ignored) {
			pointerWarpEventPending = false;
			// Pointer confinement is optional on restricted/headless platforms.
		}
	}

	private void dispatchMouseReleased(MouseEvent event, GLPickResult releaseHit) {
		if (!capturedPick.hasTarget()) {
			updateHover(event, releaseHit, false);
			return;
		}
		GLPickResult capturedAtRelease = capturedPick.at(event.getX(), event.getY());
		if (isAttachedTarget(capturedAtRelease.getTarget())) {
			capturedAtRelease.getTarget().mouseReleased(
					mouseEvent(event, capturedAtRelease, 0, 0));
			clickCandidate = releaseHit.getTarget() == capturedAtRelease.getTarget()
					? releaseHit : GLPickResult.none();
		} else
			clickCandidate = GLPickResult.none();
		capturedPick = GLPickResult.none();
		updateHover(event, releaseHit, false);
		repaintColorOnly();
	}

	private void cancelPointerCapture(MouseEvent event) {
		if (!capturedPick.hasTarget())
			return;
		GLPickResult captured = capturedPick.at(event.getX(), event.getY());
		capturedPick = GLPickResult.none();
		clickCandidate = GLPickResult.none();
		pointerWarpRequested = false;
		pointerWarpEventPending = false;
		if (isAttachedTarget(captured.getTarget()))
			captured.getTarget().mouseCaptureLost(
					mouseEvent(event, captured, 0, 0));
		repaintColorOnly();
	}

	private void dispatchMouseClicked(MouseEvent event) {
		if (clickCandidate.hasTarget()
				&& isAttachedTarget(clickCandidate.getTarget())) {
			GLPickResult current = clickCandidate.at(event.getX(), event.getY());
			current.getTarget().mouseClicked(mouseEvent(event, current, 0, 0));
		}
		clickCandidate = GLPickResult.none();
		repaintColorOnly();
	}

	private void readPointerPick(MouseEvent event, boolean requireCurrent,
			Consumer<GLPickResult> completion) {
		pointerTasksPending++;
		GLPickResult immediate = GLImmediateHoverRegistry.resolve(
				this, event.getX(), event.getY());
		if (immediate != null) {
			completePointerTask(completion, immediate);
			return;
		}
		GLRenderer2D currentRenderer = renderer;
		boolean snapshotCurrent = currentRenderer != null
				&& !damageTracker.isPickingDirty()
				&& currentRenderer.isLatestPickSnapshotReady();
		if (requireCurrent && !snapshotCurrent) {
			queuePointerPick(event, completion);
			super.repaint();
			return;
		}
		GLPickResult result = currentRenderer == null ? null
				: currentRenderer.readPickSnapshot(event.getX(), event.getY(),
						getWidth(), getHeight());
		completePointerTask(completion,
				result == null ? GLPickResult.none() : result);
		if (result == null)
			repaintColorOnly();
	}

	private void queuePointerPick(MouseEvent event,
			Consumer<GLPickResult> completion) {
		int x = event.getX();
		int y = event.getY();
		int width = getWidth();
		int height = getHeight();
		QueuedPointerTask task = new QueuedPointerTask(() -> {
			GLRenderer2D activeRenderer = renderer;
			GLPickResult result = activeRenderer == null ? null
					: activeRenderer.readPickSnapshot(x, y, width, height);
			completePointerTask(completion,
					result == null ? GLPickResult.none() : result);
		}, false);
		synchronized (pointerTaskQueue) {
			pointerTaskQueue.addLast(task);
		}
	}

	private void completePointerTask(Consumer<GLPickResult> completion,
			GLPickResult result) {
		try {
			GLMouseTarget target = result.getTarget();
			completion.accept(target instanceof GLContainer container
					&& !belongsToPanel(container) ? GLPickResult.none() : result);
		} finally {
			pointerTasksPending--;
		}
	}

	/** Enqueues a no-read marker so click delivery follows its release read. */
	private void queuePointerBarrier(Runnable completion) {
		synchronized (pointerTaskQueue) {
			if (pointerTasksPending == 0 && pointerTaskQueue.isEmpty()
					&& !pointerTaskBatchScheduled) {
				completion.run();
				return;
			}
			pointerTaskQueue.addLast(new QueuedPointerTask(completion, true));
		}
		super.repaint();
	}

	/**
	 * Publishes one complete press/release/click group after a current asynchronous
	 * pick snapshot exists. No OpenGL call is made from the AWT input callback.
	 */
	private void publishPointerTasksIfReady() {
		GLRenderer2D activeRenderer = renderer;
		if (pointerTaskBatchScheduled || activeRenderer == null
				|| damageTracker.isPickingDirty()
				|| !activeRenderer.isLatestPickSnapshotReady())
			return;
		List<QueuedPointerTask> batch = new ArrayList<>();
		synchronized (pointerTaskQueue) {
			if (pointerTaskBatchScheduled || pointerTaskQueue.isEmpty())
				return;
			while (!pointerTaskQueue.isEmpty()) {
				QueuedPointerTask task = pointerTaskQueue.removeFirst();
				batch.add(task);
				if (task.barrier())
					break;
			}
			pointerTaskBatchScheduled = true;
		}
		SwingUtilities.invokeLater(() -> {
			boolean more;
			try {
				for (QueuedPointerTask task : batch)
					task.action().run();
			} finally {
				synchronized (pointerTaskQueue) {
					pointerTaskBatchScheduled = false;
					more = !pointerTaskQueue.isEmpty();
				}
				if (more)
					super.repaint();
			}
		});
	}

	/**
	 * Resolves high-frequency motion events from the latest completed CPU image.
	 * No GL command or GPU synchronization occurs on the Swing event thread.
	 */
	private void queueHoverRead(MouseEvent event, boolean sendMoved) {
		postWheelHoverEvent = null;
		postWheelHoverGeneration++;
		GLPickResult immediate = GLImmediateHoverRegistry.resolve(
				this, event.getX(), event.getY());
		if (immediate != null) {
			// An older queued GPU result must not overwrite the current immediate
			// menu hover after it eventually returns to the event thread.
			hoverReadGeneration++;
			pendingHoverEvent = null;
			pendingHoverMoved = false;
			updateHover(event, immediate, sendMoved);
			return;
		}
		pendingHoverEvent = event;
		pendingHoverMoved = sendMoved;
		if (!hoverReadScheduled)
			startHoverRead();
	}

	private void startHoverRead() {
		MouseEvent event = pendingHoverEvent;
		if (event == null) {
			hoverReadScheduled = false;
			return;
		}
		boolean sendMoved = pendingHoverMoved;
		GLRenderer2D activeRenderer = renderer;
		if (activeRenderer == null) {
			hoverReadScheduled = false;
			// A newly shown GLJPanel can receive its first mouse event before its
			// renderer exists. Keep the latest event pending; the first completed
			// frame publishes it through publishPendingHoverIfReady(). Retrying here
			// would recurse synchronously without changing either condition.
			super.repaint();
			return;
		}
		if (event.getX() < 0 || event.getY() < 0
				|| event.getX() >= getWidth() || event.getY() >= getHeight()) {
			pendingHoverEvent = null;
			pendingHoverMoved = false;
			hoverReadScheduled = false;
			updateHover(event, GLPickResult.none(), sendMoved);
			return;
		}
		if (damageTracker.isPickingDirty()
				|| !activeRenderer.isLatestPickSnapshotReady()) {
			hoverReadScheduled = false;
			super.repaint();
			return;
		}
		pendingHoverEvent = null;
		pendingHoverMoved = false;

		hoverReadScheduled = true;
		long generation = hoverReadGeneration;
		GLPickResult result = activeRenderer.readPickSnapshot(
				event.getX(), event.getY(), getWidth(), getHeight());
		completeHoverRead(event, result, sendMoved, generation);
		if (result == null)
			repaintColorOnly();
	}

	private void completeHoverRead(MouseEvent event, GLPickResult result,
			boolean sendMoved, long generation) {
		if (result != null && generation == hoverReadGeneration && isDisplayable()) {
			GLMouseTarget target = result.getTarget();
			if (!(target instanceof GLContainer container) || belongsToPanel(container))
				updateHover(event, result, sendMoved);
			else
				updateHover(event, GLPickResult.none(), sendMoved);
		}
		hoverReadScheduled = false;
		if (pendingHoverEvent != null)
			startHoverRead();
	}

	private void cancelHoverRead() {
		hoverReadGeneration++;
		pendingHoverEvent = null;
		pendingHoverMoved = false;
		postWheelHoverEvent = null;
		postWheelHoverGeneration++;
	}

	private void requestPostWheelHover(MouseEvent event) {
		postWheelHoverGeneration++;
		postWheelHoverEvent = event;
		super.repaint();
	}

	private void publishPostWheelHoverIfReady() {
		MouseEvent event = postWheelHoverEvent;
		GLRenderer2D activeRenderer = renderer;
		if (event == null || activeRenderer == null)
			return;
		if (damageTracker.isPickingDirty()
				|| !activeRenderer.isLatestPickSnapshotReady()) {
			SwingUtilities.invokeLater(this::repaintForSnapshotPoll);
			return;
		}
		long generation = postWheelHoverGeneration;
		GLPickResult result = activeRenderer.readPickSnapshot(
				event.getX(), event.getY(), getWidth(), getHeight());
		if (result == null)
			return;
		postWheelHoverEvent = null;
		SwingUtilities.invokeLater(() -> {
			if (generation != postWheelHoverGeneration || !isDisplayable())
				return;
			GLMouseTarget target = result.getTarget();
			updateHover(event, target instanceof GLContainer container
					&& !belongsToPanel(container) ? GLPickResult.none() : result, false);
		});
	}

	private void publishPendingHoverIfReady() {
		GLRenderer2D activeRenderer = renderer;
		if (pendingHoverEvent == null || hoverReadScheduled || activeRenderer == null)
			return;
		if (damageTracker.isPickingDirty()
				|| !activeRenderer.isLatestPickSnapshotReady()) {
			SwingUtilities.invokeLater(this::repaintForSnapshotPoll);
			return;
		}
		hoverReadScheduled = true;
		SwingUtilities.invokeLater(() -> {
			hoverReadScheduled = false;
			if (pendingHoverEvent != null && isDisplayable()) startHoverRead();
		});
	}

	private void repaintForSnapshotPoll() {
		if (postWheelHoverEvent != null && isDisplayable())
			super.repaint();
	}

	private void installKeyboardDispatch() {
		setFocusable(true);
		setFocusTraversalKeysEnabled(false);
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				GLInputMap resolvedInputMap = focusedTarget instanceof GLContainer container
						? container.getGLInputMap() : inputMap;
				if (GLMenuMnemonics.handleKeyPressed(GLSwingPanel.this, event,
						resolvedInputMap)) {
					repaintColorOnly();
					return;
				}
				if (focusedTarget instanceof GLEditingHistoryTarget editor
						&& editor.handleEditingHistoryKeyPressed(event,
								resolvedInputMap)) {
					repaintColorOnly();
					return;
				}
				GLHistoryManager history = focusedTarget instanceof GLContainer container
						? container.getGLHistoryManager() : historyManager;
				if (history != null && history.handleKeyPressed(event, resolvedInputMap)) {
					repaintColorOnly();
					return;
				}
				if (resolvedInputMap.matches(GLInputActions.FOCUS_NEXT, event)
						|| resolvedInputMap.matches(GLInputActions.FOCUS_PREVIOUS, event)) {
					focusNext(resolvedInputMap.matches(
							GLInputActions.FOCUS_PREVIOUS, event));
					event.consume();
					return;
				}
				dispatchKey(event, KeyDispatch.PRESSED);
			}

			@Override
			public void keyReleased(KeyEvent event) {
				dispatchKey(event, KeyDispatch.RELEASED);
			}

			@Override
			public void keyTyped(KeyEvent event) {
				dispatchKey(event, KeyDispatch.TYPED);
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent event) {
				requestGLFocus(null);
			}
		});
	}

	private void dispatchKey(KeyEvent event, KeyDispatch dispatch) {
		GLKeyTarget target = focusedTarget;
		if (target == null)
			return;
		if (!(target instanceof GLContainer container)
				|| !belongsToPanel(container) || !target.isFocusTraversable()) {
			requestGLFocus(null);
			return;
		}
		com.vingame.glswing.input.GLKeyEvent glEvent =
				new com.vingame.glswing.input.GLKeyEvent(event);
		switch (dispatch) {
		case PRESSED -> dispatchKeyPressed(target, glEvent);
		case RELEASED -> target.keyReleased(glEvent);
		case TYPED -> target.keyTyped(glEvent);
		}
		repaintColorOnly();
	}

	private static void dispatchKeyPressed(GLKeyTarget target,
			com.vingame.glswing.input.GLKeyEvent event) {
		target.keyPressed(event);
		if (event.isConsumed() || !(target instanceof GLContainer container))
			return;
		for (GLParent parent = container.getGLParent();
				parent instanceof GLContainer ancestor;
				parent = ancestor.getGLParent()) {
			if (ancestor instanceof GLKeyTarget keyTarget)
				keyTarget.keyPressed(event);
			if (event.isConsumed())
				return;
		}
	}

	private void focusNext(boolean backwards) {
		List<GLKeyTarget> targets = new ArrayList<>();
		collectFocusable(this, targets);
		if (targets.isEmpty()) {
			requestGLFocus(null);
			return;
		}
		int current = targets.indexOf(focusedTarget);
		int next = backwards
				? (current <= 0 ? targets.size() - 1 : current - 1)
				: (current < 0 || current + 1 == targets.size() ? 0 : current + 1);
		requestGLFocus(targets.get(next));
	}

	private static void collectFocusable(GLParent parent, List<GLKeyTarget> result) {
		for (GLContainer child : parent.getGLChildren()) {
			if (!child.isVisible())
				continue;
			if (child instanceof GLKeyTarget target && target.isFocusTraversable())
				result.add(target);
			collectFocusable(child, result);
		}
	}

	private boolean belongsToPanel(GLContainer container) {
		GLParent parent = container.getGLParent();
		while (parent instanceof GLContainer ancestor)
			parent = ancestor.getGLParent();
		return parent == this;
	}

	private boolean isAttachedTarget(GLMouseTarget target) {
		return !(target instanceof GLContainer container)
				|| belongsToPanel(container);
	}

	private static boolean isInside(GLContainer container, GLContainer subtree) {
		for (GLContainer current = container; current != null;) {
			if (current == subtree)
				return true;
			GLParent parent = current.getGLParent();
			current = parent instanceof GLContainer ancestor ? ancestor : null;
		}
		return false;
	}

	private enum KeyDispatch {
		PRESSED,
		RELEASED,
		TYPED
	}

	private record QueuedPointerTask(Runnable action, boolean barrier) {
	}

	private void updateHover(MouseEvent event, GLPickResult next, boolean sendMoved) {
		GLMouseTarget previousTarget = hoverPick.getTarget();
		GLMouseTarget nextTarget = next.getTarget();
		if (previousTarget != nextTarget) {
			if (previousTarget != null)
				previousTarget.mouseExited(mouseEvent(event,
						hoverPick.at(event.getX(), event.getY()), 0, 0));
			hoverPick = next;
			if (nextTarget != null)
				nextTarget.mouseEntered(mouseEvent(event, next, 0, 0));
		} else {
			hoverPick = next;
		}
		if (sendMoved && nextTarget != null)
			nextTarget.mouseMoved(mouseEvent(event, next, 0, 0));
	}

	private void leaveHover(MouseEvent event) {
		if (hoverPick.hasTarget()) {
			hoverPick.getTarget().mouseExited(mouseEvent(event,
					hoverPick.at(event.getX(), event.getY()), 0, 0));
			hoverPick = GLPickResult.none();
		}
	}

	private GLMouseEvent mouseEvent(MouseEvent event, GLPickResult pick,
			float deltaX, float deltaY) {
		return new GLMouseEvent(event, pick, deltaX, deltaY,
				this::requestPointerWarp, pointerController);
	}

	@Override
	public final void init(GLAutoDrawable drawable) {
		GL3 gl = drawable.getGL().getGL3();
		int[] samples = new int[1];
		gl.glGetIntegerv(GL3.GL_SAMPLES, samples, 0);
		actualAntialiasingSamples = samples[0];
		renderer = GLRenderer2D.create(gl);
		renderer.setAsynchronousResourceRepaint(this::repaintColorOnly);
		sceneFramebuffer = SceneFramebuffer.create(gl);
		gpuTimers = new GLGpuTimerQueries();
		gpuTimers.initialize(gl);
		resetFrameStatistics();
		initialize(gl);
	}

	@Override
	public final void display(GLAutoDrawable drawable) {
		long frameStartNanos = System.nanoTime();
		GL3 gl = drawable.getGL().getGL3();
		gpuTimers.poll(gl);
		renderer.pollPickSnapshots(gl);
		publishPointerTasksIfReady();
		publishPendingHoverIfReady();
		publishPostWheelHoverIfReady();
		// GLJPanel owns an offscreen drawable whose resize can trail the Swing
		// component resize. Never inherit a viewport from the preceding frame:
		// it may still have the old aspect ratio and would stretch the freshly
		// calculated logical projection, including text and rounded corners.
		int surfaceWidth = Math.max(1, drawable.getSurfaceWidth());
		int surfaceHeight = Math.max(1, drawable.getSurfaceHeight());
		lastSurfaceWidth = surfaceWidth;
		lastSurfaceHeight = surfaceHeight;
		float resolutionScale = renderResolutionScale;
		int renderWidth = Math.max(1, Math.round(surfaceWidth * resolutionScale));
		int renderHeight = Math.max(1, Math.round(surfaceHeight * resolutionScale));
		lastRenderWidth = renderWidth;
		lastRenderHeight = renderHeight;
		gl.glViewport(0, 0, surfaceWidth, surfaceHeight);
		float logicalWidth = Math.max(1, getWidth());
		float logicalHeight = Math.max(1, getHeight());
		SceneFramebuffer scene = sceneFramebuffer;
		GLDamageTracker.Damage damage = damageTracker.consume(
				logicalWidth, logicalHeight);
		long housekeepingEndNanos = System.nanoTime();
		boolean frameBegan = false;
		long sceneNanos = 0;
		long presentNanos = 0;
		try {
			if (damage.redraw()) {
				long sceneStartNanos = System.nanoTime();
				gpuTimers.begin(gl, GLGpuTimerQueries.SCENE);
				scene.beginUpdate(gl, renderWidth, renderHeight,
						actualAntialiasingSamples, logicalWidth, logicalHeight,
						damage.full() ? null : damage.bounds());
				GLContext2D context = renderer.beginFrame(gl,
						logicalWidth, logicalHeight,
						Projection2D.TOP_LEFT_PIXELS.create(logicalWidth, logicalHeight),
						damage.rebuildPicking());
				frameBegan = true;
				context.setOutputMask(true, damage.rebuildPicking());
				renderer.setRedrawBounds(damage.full() ? null : damage.bounds());
				layoutSupport.ensure(logicalWidth, logicalHeight);
				if (recordingCache.isDirty())
					recordingCache.ensure(context, this::record);
				render(context);
				glChildren.render(context);
				renderer.endFrame();
				frameBegan = false;
				lastDrawCallCount = renderer.drawCallCount();
				lastDrawCallProfile = renderer.drawCallProfile();
				scene.endUpdate(gl);
				gpuTimers.end(gl);
				sceneNanos = System.nanoTime() - sceneStartNanos;
			} else {
				lastDrawCallCount = 0;
				lastDrawCallProfile = GLDrawCallProfile.EMPTY;
			}
			long presentStartNanos = System.nanoTime();
			gpuTimers.begin(gl, GLGpuTimerQueries.PRESENT);
			scene.present(gl, surfaceWidth, surfaceHeight);
			gpuTimers.end(gl);
			presentNanos = System.nanoTime() - presentStartNanos;
			firstFrameFuture.complete(null);
			CompletableFuture<Void> waiter = nextFrameFuture.getAndSet(null);
			if (waiter != null) waiter.complete(null);
		} finally {
			try {
				try {
					if (frameBegan)
						renderer.endFrame();
				} finally {
					if (scene.isUpdating())
						scene.endUpdate(gl);
				}
			} finally {
				// Keep timer-query state recoverable when user rendering throws.
				gpuTimers.end(gl);
			}
			updateFrameStatistics(frameStartNanos, System.nanoTime(),
					housekeepingEndNanos - frameStartNanos, sceneNanos, presentNanos);
		}
	}

	@Override
	public final void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL3 gl = drawable.getGL().getGL3();
		int surfaceWidth = Math.max(1, drawable.getSurfaceWidth());
		int surfaceHeight = Math.max(1, drawable.getSurfaceHeight());
		gl.glViewport(0, 0, surfaceWidth, surfaceHeight);
		layoutSupport.invalidateLocal();
		recordingCache.invalidate();
		if (sceneFramebuffer != null)
			sceneFramebuffer.invalidate();
		damageTracker.repaintAll(true);
		resized(gl, surfaceWidth, surfaceHeight);
	}

	@Override
	public final void dispose(GLAutoDrawable drawable) {
		interactiveRepaint.cancel();
		GL3 gl = drawable.getGL().getGL3();
		disposeResources(gl);
		synchronized (this) {
			for (GLFont font : themedFonts.values())
				font.dispose(gl);
			themedFonts.clear();
		}
		recordingCache.clear();
		if (sceneFramebuffer != null) {
			sceneFramebuffer.dispose(gl);
			sceneFramebuffer = null;
		}
		if (renderer != null) {
			renderer.dispose(gl);
			renderer = null;
		}
		if (gpuTimers != null) {
			gpuTimers.dispose(gl);
			gpuTimers = null;
		}
		hoverPick = GLPickResult.none();
		capturedPick = GLPickResult.none();
		clickCandidate = GLPickResult.none();
		hoverReadGeneration++;
		pendingHoverEvent = null;
		if (focusedTarget != null)
			focusedTarget.focusLost();
		focusedTarget = null;
		resetFrameStatistics();
	}

	private void updateFrameStatistics(long frameStartNanos, long frameEndNanos,
			long housekeepingNanos, long sceneNanos, long presentNanos) {
		if (statisticsWindowStartNanos == 0)
			statisticsWindowStartNanos = frameStartNanos;
		statisticsFrameCount++;
		statisticsRenderNanos += frameEndNanos - frameStartNanos;
		statisticsHousekeepingNanos += housekeepingNanos;
		statisticsSceneNanos += sceneNanos;
		statisticsPresentNanos += presentNanos;
		long elapsed = frameEndNanos - statisticsWindowStartNanos;
		if (elapsed < 500_000_000L)
			return;
		framesPerSecond = statisticsFrameCount * 1_000_000_000.0 / elapsed;
		averageFrameTimeMillis = statisticsRenderNanos
				/ (statisticsFrameCount * 1_000_000.0);
		frameProfile = new GLFrameProfile(frameProfile.endToEndMillis(),
				averageFrameTimeMillis,
				statisticsHousekeepingNanos / (statisticsFrameCount * 1_000_000.0),
				statisticsSceneNanos / (statisticsFrameCount * 1_000_000.0),
				statisticsPresentNanos / (statisticsFrameCount * 1_000_000.0),
				gpuTimers.averageMillis(GLGpuTimerQueries.SCENE),
				gpuTimers.averageMillis(GLGpuTimerQueries.PRESENT));
		statisticsWindowStartNanos = frameEndNanos;
		statisticsRenderNanos = 0;
		statisticsHousekeepingNanos = 0;
		statisticsSceneNanos = 0;
		statisticsPresentNanos = 0;
		statisticsFrameCount = 0;
	}

	private synchronized void updateEndToEndStatistics(long start, long end) {
		if (endToEndWindowStartNanos == 0)
			endToEndWindowStartNanos = start;
		endToEndAccumulatedNanos += end - start;
		endToEndCount++;
		if (end - endToEndWindowStartNanos < 500_000_000L)
			return;
		double average = endToEndAccumulatedNanos
				/ (endToEndCount * 1_000_000.0);
		GLFrameProfile current = frameProfile;
		frameProfile = new GLFrameProfile(average, current.callbackCpuMillis(),
				current.housekeepingCpuMillis(), current.sceneCpuMillis(),
				current.presentCpuMillis(), current.sceneGpuMillis(),
				current.presentGpuMillis());
		endToEndWindowStartNanos = end;
		endToEndAccumulatedNanos = 0;
		endToEndCount = 0;
	}

	private void resetFrameStatistics() {
		framesPerSecond = 0;
		averageFrameTimeMillis = 0;
		statisticsWindowStartNanos = 0;
		statisticsRenderNanos = 0;
		statisticsFrameCount = 0;
		statisticsHousekeepingNanos = 0;
		statisticsSceneNanos = 0;
		statisticsPresentNanos = 0;
		endToEndWindowStartNanos = 0;
		endToEndAccumulatedNanos = 0;
		endToEndCount = 0;
		frameProfile = GLFrameProfile.EMPTY;
	}

	protected void initialize(GL3 gl) {
	}

	/** Records the panel's own cached content. The default implementation is empty. */
	protected void record(GLContext2D context) {
	}

	/**
	 * Draws the panel's own cached content before its GL children. Override and call
	 * {@code super.render(context)} to add dynamic drawing around the recording.
	 */
	protected void render(GLContext2D context) {
		GLRecording recording = recordingCache.get();
		if (recording != null)
			context.draw(recording);
	}

	protected void resized(GL3 gl, int surfaceWidth, int surfaceHeight) {
	}

	protected void disposeResources(GL3 gl) {
	}
}
