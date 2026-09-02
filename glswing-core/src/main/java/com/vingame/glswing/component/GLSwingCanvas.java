package com.vingame.glswing.component;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDrawCallProfile;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;
import com.vingame.glswing.rendering.internal.SceneFramebuffer;

/**
 * High-throughput heavyweight AWT backend for {@link GLContext2D} rendering.
 * <p>
 * Unlike {@link GLSwingPanel}, this canvas presents its OpenGL framebuffer
 * directly and therefore avoids a full GPU-to-CPU image readback per frame.
 * It can be embedded in Swing containers, but heavyweight/lightweight overlap
 * restrictions of {@link GLCanvas} still apply.
 * <p>
 * On modular Java runtimes, JOGL's Windows AWT backend requires these VM opens:
 * {@code --add-opens=java.base/java.lang=ALL-UNNAMED},
 * {@code --add-opens=java.desktop/sun.awt=ALL-UNNAMED} and
 * {@code --add-opens=java.desktop/sun.java2d=ALL-UNNAMED}.
 */
public abstract class GLSwingCanvas extends GLCanvas implements GLEventListener {

	private static final long serialVersionUID = 1L;
	public static final int DEFAULT_ANTIALIASING_SAMPLES = 4;
	public static final String AWT_NO_ERASE_BACKGROUND_PROPERTY =
			"sun.awt.noerasebackground";
	public static final String REQUIRED_WINDOWS_VM_ARGUMENTS =
			"--add-opens=java.base/java.lang=ALL-UNNAMED "
			+ "--add-opens=java.desktop/sun.awt=ALL-UNNAMED "
			+ "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED";

	private final int requestedAntialiasingSamples;
	private final int swapInterval;
	private volatile int actualAntialiasingSamples;
	private volatile int actualSwapInterval;
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
	private long previousFrameEndNanos;
	private final CompletableFuture<Void> firstFrameFuture = new CompletableFuture<>();
	private final AtomicReference<CompletableFuture<Void>> nextFrameFuture =
			new AtomicReference<>();

	/**
	 * Returns whether JOGL may inspect the internal AWT graphics configuration
	 * required by heavyweight canvases on modular Java runtimes.
	 */
	public static boolean hasRequiredAwtModuleAccess() {
		Module jogamp = GLCanvas.class.getModule();
		Module javaBase = Object.class.getModule();
		Module javaDesktop = java.awt.Component.class.getModule();
		return javaBase.isOpen("java.lang", jogamp)
				&& javaDesktop.isOpen("sun.awt", jogamp)
				&& javaDesktop.isOpen("sun.java2d", jogamp);
	}

	protected GLSwingCanvas() {
		this(DEFAULT_ANTIALIASING_SAMPLES, 1);
	}

	protected GLSwingCanvas(int antialiasingSamples) {
		this(antialiasingSamples, 1);
	}

	/**
	 * @param antialiasingSamples requested framebuffer sample count, or zero
	 * @param swapInterval zero for an uncapped benchmark, one for vertical sync
	 */
	protected GLSwingCanvas(int antialiasingSamples, int swapInterval) {
		super(createCapabilities(antialiasingSamples));
		if (swapInterval < 0)
			throw new IllegalArgumentException("Swap interval must not be negative");
		requestedAntialiasingSamples = antialiasingSamples;
		this.swapInterval = swapInterval;
		setBackground(Color.BLACK);
		addGLEventListener(this);
	}

	private static GLCapabilities createCapabilities(int antialiasingSamples) {
		configureAwtBackgroundErase();
		if (antialiasingSamples < 0)
			throw new IllegalArgumentException("Antialiasing sample count must not be negative");
		GLCapabilities capabilities = new GLCapabilities(GLProfile.get(GLProfile.GL3));
		capabilities.setSampleBuffers(antialiasingSamples > 0);
		if (antialiasingSamples > 0)
			capabilities.setNumSamples(antialiasingSamples);
		return capabilities;
	}

	/**
	 * Prevents AWT from clearing heavyweight canvases before JOGL presents the
	 * resized frame. An explicitly supplied VM property remains authoritative.
	 */
	private static void configureAwtBackgroundErase() {
		try {
			if (System.getProperty(AWT_NO_ERASE_BACKGROUND_PROPERTY) == null)
				System.setProperty(AWT_NO_ERASE_BACKGROUND_PROPERTY, "true");
		} catch (SecurityException ignored) {
			// Restricted runtimes may deny global properties; JOGL's own per-canvas
			// background-erase suppression remains the fallback in that case.
		}
	}

	public final int getLastDrawCallCount() {
		return lastDrawCallCount;
	}

	public final GLDrawCallProfile getLastDrawCallProfile() {
		return lastDrawCallProfile;
	}

	public final double getFramesPerSecond() {
		return framesPerSecond;
	}

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

	@Override
	public void display() {
		long startNanos = System.nanoTime();
		super.display();
		updateEndToEndStatistics(startNanos, System.nanoTime());
	}

	public final int getRequestedAntialiasingSamples() {
		return requestedAntialiasingSamples;
	}

	public final int getActualAntialiasingSamples() {
		return actualAntialiasingSamples;
	}

	public final int getSurfacePixelWidth() {
		return lastSurfaceWidth;
	}

	public final int getSurfacePixelHeight() {
		return lastSurfaceHeight;
	}

	public final float getRenderResolutionScale() {
		return renderResolutionScale;
	}

	public final int getRenderPixelWidth() {
		return lastRenderWidth;
	}

	public final int getRenderPixelHeight() {
		return lastRenderHeight;
	}

	/** Sets the retained color-scene resolution without changing layout or picking. */
	public final void setRenderResolutionScale(float scale) {
		if (!Float.isFinite(scale) || scale < 0.25f || scale > 1)
			throw new IllegalArgumentException(
					"Render resolution scale must be between 0.25 and 1");
		if (Float.compare(renderResolutionScale, scale) == 0)
			return;
		renderResolutionScale = scale;
		renderResolutionChanged();
	}

	public final int getSwapInterval() {
		return swapInterval;
	}

	/** Returns the interval currently reported by the active OpenGL context. */
	public final int getActualSwapInterval() {
		return actualSwapInterval;
	}

	public final int pick(int x, int y) {
		return pickResult(x, y).getId();
	}

	/** Instantly resolves one logical pixel from the latest completed CPU snapshot. */
	public final GLPickResult pickResult(int x, int y) {
		GLPickResult result = latestPickResult(x, y);
		return result == null ? GLPickResult.none() : result;
	}

	/** Null means that no dimension-compatible snapshot is ready yet. */
	final GLPickResult latestPickResult(int x, int y) {
		GLRenderer2D activeRenderer = renderer;
		return activeRenderer == null ? null
				: activeRenderer.readPickSnapshot(x, y, getWidth(), getHeight());
	}

	final boolean isLatestPickSnapshotReady() {
		GLRenderer2D activeRenderer = renderer;
		return activeRenderer != null && activeRenderer.isLatestPickSnapshotReady();
	}

	@Override
	public final void init(GLAutoDrawable drawable) {
		GL3 gl = drawable.getGL().getGL3();
		applySwapInterval(gl);
		int[] samples = new int[1];
		gl.glGetIntegerv(GL3.GL_SAMPLES, samples, 0);
		actualAntialiasingSamples = samples[0];
		renderer = GLRenderer2D.create(gl);
		renderer.setAsynchronousResourceRepaint(this::asynchronousResourceUpdated);
		gpuTimers = new GLGpuTimerQueries();
		gpuTimers.initialize(gl);
		if (isSceneCachingEnabled())
			sceneFramebuffer = SceneFramebuffer.create(gl);
		resetFrameStatistics();
		initialize(gl);
	}

	/** Called from resource workers; AWT repaint is safe to request off the EDT. */
	protected void asynchronousResourceUpdated() {
		repaint();
	}

	@Override
	public final void display(GLAutoDrawable drawable) {
		long frameStartNanos = System.nanoTime();
		GL3 gl = drawable.getGL().getGL3();
		gpuTimers.poll(gl);
		renderer.pollPickSnapshots(gl);
		pickSnapshotsUpdated();
		if (previousFrameEndNanos != 0
				&& frameStartNanos - previousFrameEndNanos > 250_000_000L) {
			// A minimized/occluded Windows canvas may receive a renewed WGL surface.
			// Do not include the pause in FPS and restore its requested presentation mode.
			resetFrameStatistics();
			applySwapInterval(gl);
		}
		float logicalWidth = Math.max(1, getWidth());
		float logicalHeight = Math.max(1, getHeight());
		lastSurfaceWidth = Math.max(1, drawable.getSurfaceWidth());
		lastSurfaceHeight = Math.max(1, drawable.getSurfaceHeight());
		float resolutionScale = renderResolutionScale;
		int renderWidth = Math.max(1,
				Math.round(lastSurfaceWidth * resolutionScale));
		int renderHeight = Math.max(1,
				Math.round(lastSurfaceHeight * resolutionScale));
		lastRenderWidth = renderWidth;
		lastRenderHeight = renderHeight;
		SceneFramebuffer scene = sceneFramebuffer;
		SceneUpdate update = scene == null
				? SceneUpdate.FULL : consumeSceneUpdate(logicalWidth, logicalHeight);
		long housekeepingEndNanos = System.nanoTime();
		boolean frameBegan = false;
		long sceneNanos = 0;
		long presentNanos = 0;
		try {
			long sceneStartNanos = 0;
			if (scene != null && update.redraw()) {
				sceneStartNanos = System.nanoTime();
				gpuTimers.begin(gl, GLGpuTimerQueries.SCENE);
				gl.glViewport(0, 0, renderWidth, renderHeight);
				scene.beginUpdate(gl, renderWidth, renderHeight,
						actualAntialiasingSamples, logicalWidth, logicalHeight,
						update.full() ? null : update.bounds());
			}
			if (scene == null || update.redraw()) {
				GLContext2D context = renderer.beginFrame(gl, logicalWidth, logicalHeight,
						Projection2D.TOP_LEFT_PIXELS.create(logicalWidth, logicalHeight),
						update.rebuildPicking());
				frameBegan = true;
				if (scene != null) {
					context.setOutputMask(true, update.rebuildPicking());
					renderer.setRedrawBounds(update.full() ? null : update.bounds());
				}
				render(context);
				renderer.endFrame();
				frameBegan = false;
				lastDrawCallCount = renderer.drawCallCount();
				lastDrawCallProfile = renderer.drawCallProfile();
				if (scene != null) {
					scene.endUpdate(gl);
					sceneUpdateCompleted();
					gpuTimers.end(gl);
					sceneNanos = System.nanoTime() - sceneStartNanos;
				}
			} else {
				lastDrawCallCount = 0;
				lastDrawCallProfile = GLDrawCallProfile.EMPTY;
			}
			if (scene != null) {
				long presentStartNanos = System.nanoTime();
				gpuTimers.begin(gl, GLGpuTimerQueries.PRESENT);
				scene.present(gl, lastSurfaceWidth, lastSurfaceHeight);
				gpuTimers.end(gl);
				presentNanos = System.nanoTime() - presentStartNanos;
			}
			firstFrameFuture.complete(null);
			CompletableFuture<Void> waiter = nextFrameFuture.getAndSet(null);
			if (waiter != null) waiter.complete(null);
		} finally {
			try {
				try {
					if (frameBegan)
						renderer.endFrame();
				} finally {
					if (scene != null && scene.isUpdating())
						scene.endUpdate(gl);
				}
			} finally {
				// A render callback may throw while a scene or presentation query is
				// active. Always close it so a later repaint can start a clean frame.
				gpuTimers.end(gl);
			}
			long frameEndNanos = System.nanoTime();
			updateFrameStatistics(frameStartNanos, frameEndNanos,
					housekeepingEndNanos - frameStartNanos, sceneNanos, presentNanos);
			previousFrameEndNanos = frameEndNanos;
		}
	}

	@Override
	public final void reshape(GLAutoDrawable drawable,
			int x, int y, int width, int height) {
		GL3 gl = drawable.getGL().getGL3();
		gl.glViewport(0, 0, width, height);
		resized(gl, width, height);
		if (sceneFramebuffer != null) {
			sceneFramebuffer.invalidate();
			sceneInvalidated();
		}
	}

	@Override
	public final void dispose(GLAutoDrawable drawable) {
		GL3 gl = drawable.getGL().getGL3();
		disposeResources(gl);
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
		resetFrameStatistics();
		previousFrameEndNanos = 0;
	}

	private void applySwapInterval(GL3 gl) {
		gl.setSwapInterval(swapInterval);
		actualSwapInterval = gl.getContext().getSwapInterval();
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
				statisticsSceneNanos == 0 ? 0
						: gpuTimers.averageMillis(GLGpuTimerQueries.SCENE),
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

	/** Enables persistent scene caching for retained subclasses. */
	protected boolean isSceneCachingEnabled() {
		return false;
	}

	protected SceneUpdate consumeSceneUpdate(float logicalWidth,
			float logicalHeight) {
		return SceneUpdate.FULL;
	}

	protected void sceneUpdateCompleted() {
	}

	protected void sceneInvalidated() {
	}

	/** Called after a color-only render-resolution change. */
	protected void renderResolutionChanged() {
		repaint();
	}

	protected abstract void render(GLContext2D context);

	/** Called after asynchronous picking transfers have been polled. */
	protected void pickSnapshotsUpdated() {
	}

	protected void resized(GL3 gl, int surfaceWidth, int surfaceHeight) {
	}

	protected void disposeResources(GL3 gl) {
	}

	protected record SceneUpdate(boolean redraw, boolean full,
			boolean rebuildPicking, float[] bounds) {
		protected static final SceneUpdate FULL =
				new SceneUpdate(true, true, true, null);
	}
}
