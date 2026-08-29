package com.vingame.glswing.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.MenuBar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.history.GLHistoryManager;
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
 * Optional standalone host combining a {@link JFrame}, direct GL canvas and
 * managed animator. Applications that need a custom Swing hierarchy may keep
 * using {@link GLSwingContainerCanvas} directly.
 * <p>
 * Ordinary Swing content APIs remain operational for compatibility, but are
 * deprecated on this class because heavyweight/lightweight overlap and the
 * managed full-canvas layout cannot be guaranteed. Prefer {@link #addGL} and
 * {@link #getGLCanvas()} for deliberate low-level customization.
 */
public class GLFrame extends JFrame implements GLParent, AutoCloseable {
	private static final long serialVersionUID = 1L;

	/** Immutable settings that must be known before JOGL creates the canvas. */
	public record RenderingSettings(int antialiasingSamples, int swapInterval,
			int targetFps) {
		public static final RenderingSettings DEFAULT = new RenderingSettings(
				GLSwingCanvas.DEFAULT_ANTIALIASING_SAMPLES, 1, 144);

		public RenderingSettings {
			if (antialiasingSamples < 0)
				throw new IllegalArgumentException(
						"Antialiasing sample count must not be negative");
			if (swapInterval < 0)
				throw new IllegalArgumentException(
						"Swap interval must not be negative");
			if (targetFps <= 0)
				throw new IllegalArgumentException("Target FPS must be positive");
		}

		public RenderingSettings withAntialiasingSamples(int samples) {
			return new RenderingSettings(samples, swapInterval, targetFps);
		}

		public RenderingSettings withSwapInterval(int interval) {
			return new RenderingSettings(antialiasingSamples, interval, targetFps);
		}

		public RenderingSettings withVSync(boolean enabled) {
			return withSwapInterval(enabled ? 1 : 0);
		}

		public RenderingSettings withTargetFps(int fps) {
			return new RenderingSettings(antialiasingSamples, swapInterval, fps);
		}
	}

	private final FrameCanvas glCanvas;
	private final FPSAnimator animator;
	private volatile RenderingSettings renderingSettings;
	private final AtomicBoolean closing = new AtomicBoolean();
	private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();
	private volatile Color clearColor = Color.BLACK;

	public GLFrame() {
		this("", RenderingSettings.DEFAULT);
	}

	public GLFrame(String title) {
		this(title, RenderingSettings.DEFAULT);
	}

	public GLFrame(String title, RenderingSettings settings) {
		super(title == null ? "" : title);
		if (settings == null)
			throw new IllegalArgumentException("Rendering settings must not be null");
		renderingSettings = settings;
		glCanvas = new FrameCanvas(settings.antialiasingSamples(),
				settings.swapInterval());
		animator = new FPSAnimator(glCanvas, settings.targetFps(), true);
		super.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		glCanvas.setBackground(clearColor);
		super.getContentPane().setBackground(clearColor);
		super.getContentPane().setLayout(new java.awt.BorderLayout());
		super.getContentPane().add(glCanvas, java.awt.BorderLayout.CENTER);
		super.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				close();
			}
		});
	}

	public final GLSwingContainerCanvas getGLCanvas() {
		return glCanvas;
	}

	public final FPSAnimator getAnimator() {
		return animator;
	}

	public final RenderingSettings getRenderingSettings() {
		return renderingSettings;
	}

	public final int getTargetFps() {
		return animator.getFPS();
	}

	public final void setTargetFps(int targetFps) {
		if (targetFps <= 0)
			throw new IllegalArgumentException("Target FPS must be positive");
		animator.setFPS(targetFps);
		renderingSettings = renderingSettings.withTargetFps(targetFps);
	}

	public final int getLastDrawCallCount() {
		return glCanvas.getLastDrawCallCount();
	}

	public final double getFramesPerSecond() {
		return glCanvas.getFramesPerSecond();
	}

	public final double getAverageFrameTimeMillis() {
		return glCanvas.getAverageFrameTimeMillis();
	}

	public final GLFrameProfile getFrameProfile() {
		return glCanvas.getFrameProfile();
	}

	public final int getRequestedAntialiasingSamples() {
		return glCanvas.getRequestedAntialiasingSamples();
	}

	public final int getActualAntialiasingSamples() {
		return glCanvas.getActualAntialiasingSamples();
	}

	public final int getRequestedSwapInterval() {
		return glCanvas.getSwapInterval();
	}

	public final int getActualSwapInterval() {
		return glCanvas.getActualSwapInterval();
	}

	public final float getRenderResolutionScale() {
		return glCanvas.getRenderResolutionScale();
	}

	public final void setRenderResolutionScale(float scale) {
		glCanvas.setRenderResolutionScale(scale);
	}

	public final int getSurfacePixelWidth() {
		return glCanvas.getSurfacePixelWidth();
	}

	public final int getSurfacePixelHeight() {
		return glCanvas.getSurfacePixelHeight();
	}

	public final int getRenderPixelWidth() {
		return glCanvas.getRenderPixelWidth();
	}

	public final int getRenderPixelHeight() {
		return glCanvas.getRenderPixelHeight();
	}

	public final Color getGLClearColor() {
		return clearColor;
	}

	public final void setGLClearColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Clear color must not be null");
		clearColor = color;
		glCanvas.setBackground(color);
		super.getContentPane().setBackground(color);
		repaintGL();
	}

	public final CompletableFuture<Void> closedFuture() {
		return closedFuture;
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			if (closing.get())
				throw new IllegalStateException("A closed GLFrame cannot be shown again");
			if (!GLSwingCanvas.hasRequiredAwtModuleAccess())
				throw new IllegalStateException(
						"GLFrame requires these VM arguments: "
								+ GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			super.setVisible(true);
			if (!animator.isStarted())
				animator.start();
		} else {
			if (animator.isStarted())
				animator.stop();
			super.setVisible(false);
		}
	}

	@Override
	public void close() {
		if (!closing.compareAndSet(false, true))
			return;
		Thread shutdown = new Thread(() -> {
			try {
				if (animator.isStarted())
					animator.stop();
				SwingUtilities.invokeLater(this::disposeNow);
			} catch (Throwable error) {
				closedFuture.completeExceptionally(error);
			}
		}, "glswing-frame-close");
		shutdown.start();
	}

	@Override
	public void dispose() {
		close();
	}

	private void disposeNow() {
		try {
			super.dispose();
			closedFuture.complete(null);
		} catch (Throwable error) {
			closedFuture.completeExceptionally(error);
		}
	}

	@Override public final void addGL(GLContainer child) { glCanvas.addGL(child); }
	@Override public final void addGL(GLContainer child, GLConstraints constraints) {
		glCanvas.addGL(child, constraints);
	}
	@Override public final boolean removeGL(GLContainer child) {
		return glCanvas.removeGL(child);
	}
	@Override public final void removeAllGL() { glCanvas.removeAllGL(); }
	@Override public final List<GLContainer> getGLChildren() {
		return glCanvas.getGLChildren();
	}
	@Override public final List<GLContainer> getGLChildrenInPaintOrder() {
		return glCanvas.getGLChildrenInPaintOrder();
	}
	@Override public final int getGLZOrder(GLContainer child) {
		return glCanvas.getGLZOrder(child);
	}
	@Override public final void setGLZOrder(GLContainer child, int zOrder) {
		glCanvas.setGLZOrder(child, zOrder);
	}
	@Override public final void setGLConstraints(GLContainer child,
			GLConstraints constraints) { glCanvas.setGLConstraints(child, constraints); }
	@Override public final GLConstraints getGLConstraints(GLContainer child) {
		return glCanvas.getGLConstraints(child);
	}
	@Override public final void setGLLayout(GLLayout layout) {
		glCanvas.setGLLayout(layout);
	}
	@Override public final GLLayout getGLLayout() { return glCanvas.getGLLayout(); }
	@Override public final void setGLPadding(GLInsets padding) {
		glCanvas.setGLPadding(padding);
	}
	public final void setGLPadding(float padding) { glCanvas.setGLPadding(padding); }
	@Override public final GLInsets getGLPadding() { return glCanvas.getGLPadding(); }
	@Override public final void invalidateGLLayout() { glCanvas.invalidateGLLayout(); }
	@Override public final boolean isGLLayoutDirty() { return glCanvas.isGLLayoutDirty(); }
	@Override public final void layoutGL() { glCanvas.layoutGL(); }
	@Override public final GLSize getPreferredGLSize() { return glCanvas.getPreferredGLSize(); }
	@Override public final void packGL() {
		glCanvas.packGL();
		pack();
	}
	@Override public final void setGLTheme(GLTheme theme) { glCanvas.setGLTheme(theme); }
	@Override public final GLTheme getGLTheme() { return glCanvas.getGLTheme(); }
	@Override public final void setGLLocalizer(GLLocalizer localizer) {
		glCanvas.setGLLocalizer(localizer);
	}
	@Override public final GLLocalizer getGLLocalizer() {
		return glCanvas.getGLLocalizer();
	}
	@Override public final GLFont getGLFont(GLFontSpec specification) {
		return glCanvas.getGLFont(specification);
	}
	@Override public final GLHistoryManager getGLHistoryManager() {
		return glCanvas.getGLHistoryManager();
	}
	public final void setGLHistoryManager(GLHistoryManager historyManager) {
		glCanvas.setGLHistoryManager(historyManager);
	}
	@Override public final void requestGLFocus(GLKeyTarget target) {
		glCanvas.requestGLFocus(target);
	}
	@Override public final void clearGLFocus(GLContainer subtree) {
		glCanvas.clearGLFocus(subtree);
	}
	@Override public final void repaintGL() { glCanvas.repaintGL(); }
	@Override public final void repaintGL(GLContainer source) {
		glCanvas.repaintGL(source);
	}
	@Override public final void repaintAndRebuildPicking(GLContainer source) {
		glCanvas.repaintAndRebuildPicking(source);
	}

	/** @deprecated Prefer {@link #addGL(GLContainer)} for managed content. */
	@Deprecated
	@Override public Component add(Component component) { return super.add(component); }
	/** @deprecated Prefer {@link #addGL(GLContainer)} for managed content. */
	@Deprecated
	@Override public Component add(String name, Component component) {
		return super.add(name, component);
	}
	/** @deprecated Prefer {@link #addGL(GLContainer)} for managed content. */
	@Deprecated
	@Override public Component add(Component component, int index) {
		return super.add(component, index);
	}
	/** @deprecated Prefer {@link #addGL(GLContainer)} for managed content. */
	@Deprecated
	@Override public void add(Component component, Object constraints) {
		super.add(component, constraints);
	}
	/** @deprecated Prefer {@link #addGL(GLContainer)} for managed content. */
	@Deprecated
	@Override public void add(Component component, Object constraints, int index) {
		super.add(component, constraints, index);
	}
	/** @deprecated Prefer {@link #removeGL(GLContainer)}. */
	@Deprecated
	@Override public void remove(Component component) { super.remove(component); }
	/** @deprecated Prefer {@link #removeGL(GLContainer)}. */
	@Deprecated
	@Override public void remove(int index) { super.remove(index); }
	/** @deprecated Prefer {@link #removeAllGL()}. */
	@Deprecated
	@Override public void removeAll() { super.removeAll(); }
	/** @deprecated The frame owns its full-canvas layout. */
	@Deprecated
	@Override public void setLayout(LayoutManager manager) { super.setLayout(manager); }
	/** @deprecated The frame owns its GL canvas content pane. */
	@Deprecated
	@Override public void setContentPane(Container contentPane) {
		super.setContentPane(contentPane);
	}
	/** @deprecated Use {@link #getGLCanvas()} for deliberate host customization. */
	@Deprecated
	@Override public Container getContentPane() { return super.getContentPane(); }
	/** @deprecated Swing glass panes cannot reliably overlap the heavyweight canvas. */
	@Deprecated
	@Override public void setGlassPane(Component glassPane) {
		super.setGlassPane(glassPane);
	}
	/** @deprecated Swing layered panes cannot reliably overlap the heavyweight canvas. */
	@Deprecated
	@Override public void setLayeredPane(JLayeredPane layeredPane) {
		super.setLayeredPane(layeredPane);
	}
	/** @deprecated Prefer the GPU-rendered {@code GLMenuBar}. */
	@Deprecated
	@Override public void setJMenuBar(JMenuBar menuBar) { super.setJMenuBar(menuBar); }
	/** @deprecated Prefer the GPU-rendered {@code GLMenuBar}. */
	@Deprecated
	@Override public synchronized void setMenuBar(MenuBar menuBar) {
		super.setMenuBar(menuBar);
	}

	private final class FrameCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;

		private FrameCanvas(int antialiasingSamples, int swapInterval) {
			super(antialiasingSamples, swapInterval);
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(clearColor);
		}
	}
}
