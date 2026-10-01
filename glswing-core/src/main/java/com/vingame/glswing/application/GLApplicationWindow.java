package com.vingame.glswing.application;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.BorderLayout;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JFrame;

import com.jogamp.opengl.GL3;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;

final class GLApplicationWindow {
	private final JFrame frame;
	private final GLParent parent;
	private final Component renderComponent;
	private CompletableFuture<Void> presentationFrame;
	private final Runnable destroy;
	private final GLInvoker glInvoker;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicBoolean closeRequestPending = new AtomicBoolean();
	private Runnable beforeClose = () -> { };
	private Supplier<java.util.concurrent.CompletionStage<Boolean>> closeRequest =
			() -> CompletableFuture.completedFuture(true);
	private GLContainer root;
	private Point revealLocation;
	private boolean primedVisible;
	private boolean transparentPriming;

	private GLApplicationWindow(JFrame frame, GLParent parent,
			Component renderComponent,
			Runnable destroy, GLInvoker glInvoker, Runnable onClosed) {
		this.frame = frame;
		this.parent = parent;
		this.renderComponent = renderComponent;
		this.destroy = destroy;
		this.glInvoker = glInvoker;
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent event) { requestClose(onClosed); }
		});
		renderComponent.addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent event) { resizeRoot(); }
		});
	}

	static GLApplicationWindow create(JFrame frame, GLBackend backend, int width,
			int height, int minimumWidth, int minimumHeight, Color clearColor,
			GLApplication.RenderingSettings renderingSettings, Runnable onClosed) {
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Application windows must be created on AWT");
		if (frame == null) throw new IllegalArgumentException("Application frame required");
		if (backend == GLBackend.CANVAS) {
			if (!GLSwingCanvas.hasRequiredAwtModuleAccess())
				throw new IllegalStateException(
						"The Canvas backend requires these VM arguments: "
								+ GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			ApplicationCanvas canvas = new ApplicationCanvas(clearColor,
					renderingSettings.antialiasingSamples(),
					renderingSettings.swapInterval());
			canvas.setPreferredSize(new Dimension(width, height));
			frame.setLayout(new BorderLayout());
			frame.add(canvas, BorderLayout.CENTER);
			frame.pack();
			frame.setMinimumSize(new Dimension(minimumWidth, minimumHeight));
			frame.setLocationRelativeTo(null);
			return new GLApplicationWindow(frame, canvas, canvas,
					canvas::destroy,
					(operation, binder) -> {
						AtomicReference<Object> result = new AtomicReference<>();
						AtomicReference<Throwable> failure = new AtomicReference<>();
						canvas.invoke(true, drawable -> {
							try {
								GL3 gl = drawable.getGL().getGL3();
								binder.accept(gl);
								result.set(operation.call());
							} catch (Throwable error) { failure.set(error); }
							return true;
						});
						rethrow(failure.get());
						return result.get();
					}, onClosed);
		}

		ApplicationPanel panel = new ApplicationPanel(clearColor);
		panel.setPreferredSize(new Dimension(width, height));
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.setMinimumSize(new Dimension(minimumWidth, minimumHeight));
		frame.setLocationRelativeTo(null);
		return new GLApplicationWindow(frame, panel, panel,
				panel::destroy,
				(operation, binder) -> {
					AtomicReference<Object> result = new AtomicReference<>();
					AtomicReference<Throwable> failure = new AtomicReference<>();
					panel.invoke(true, drawable -> {
						try {
							GL3 gl = drawable.getGL().getGL3();
							binder.accept(gl);
							result.set(operation.call());
						} catch (Throwable error) { failure.set(error); }
						return true;
					});
					rethrow(failure.get());
					return result.get();
				}, onClosed);
	}

	JFrame frame() { return frame; }
	GLParent parent() { return parent; }
	CompletableFuture<Void> firstFrame() {
		if (presentationFrame == null)
			throw new IllegalStateException("The application surface is not attached");
		return presentationFrame;
	}

	void apply(GLTheme theme, GLInputMap inputMap, GLLocalizer localizer) {
		parent.setGLTheme(theme);
		parent.setGLLocalizer(localizer);
		if (parent instanceof ApplicationPanel panel) panel.setGLInputMap(inputMap);
		else if (parent instanceof ApplicationCanvas canvas)
			canvas.setGLInputMap(inputMap);
	}

	void root(GLContainer root) {
		if (root == null) throw new IllegalArgumentException("Root surface must not be null");
		this.root = root;
		parent.addGL(root);
		resizeRoot();
		if (renderComponent instanceof GLSwingPanel panel)
			presentationFrame = panel.nextFrameFuture();
		else presentationFrame = ((GLSwingCanvas) renderComponent).nextFrameFuture();
	}

	void show() {
		if (primedVisible) {
			if (transparentPriming) frame.setOpacity(1);
			else if (revealLocation != null) frame.setLocation(revealLocation);
			frame.setAutoRequestFocus(true);
			frame.toFront();
			frame.requestFocus();
			return;
		}
		frame.setVisible(true);
	}

	void beforeClose(Runnable operation) {
		beforeClose = operation == null ? () -> { } : operation;
	}

	void closeRequest(Supplier<java.util.concurrent.CompletionStage<Boolean>> operation) {
		closeRequest = operation == null
				? () -> CompletableFuture.completedFuture(true) : operation;
	}

	void requestClose(Runnable onClosed) {
		if (closed.get() || !closeRequestPending.compareAndSet(false, true)) return;
		java.util.concurrent.CompletionStage<Boolean> decision;
		try { decision = closeRequest.get(); }
		catch (Throwable failure) {
			closeRequestPending.set(false);
			failure.printStackTrace();
			return;
		}
		if (decision == null) decision = CompletableFuture.completedFuture(false);
		decision.whenComplete((approved, failure) -> EventQueue.invokeLater(() -> {
			closeRequestPending.set(false);
			if (failure != null) {
				failure.printStackTrace();
				return;
			}
			if (Boolean.TRUE.equals(approved)) close(onClosed);
		}));
	}

	void renderFirstFrame() {
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("The first frame must be rendered on AWT");
		if (!frame.isDisplayable()) frame.pack();
		primeInvisibleWindow();
		if (renderComponent instanceof GLSwingPanel panel) panel.display();
		else ((GLSwingCanvas) renderComponent).display();
		if (!firstFrame().isDone() && transparentPriming) {
			// Some compositors suppress drawing for a fully transparent window.
			// Keep it user-invisible by moving it beyond the virtual desktop instead.
			frame.setOpacity(1);
			transparentPriming = false;
			frame.setLocation(offscreenLocation());
			if (renderComponent instanceof GLSwingPanel panel) panel.display();
			else ((GLSwingCanvas) renderComponent).display();
		}
	}

	private void primeInvisibleWindow() {
		if (primedVisible) return;
		revealLocation = frame.getLocation();
		frame.setAutoRequestFocus(false);
		try {
			boolean supported = frame.getGraphicsConfiguration().getDevice()
					.isWindowTranslucencySupported(WindowTranslucency.TRANSLUCENT);
			if (supported && renderComponent instanceof GLSwingPanel) {
				frame.setOpacity(0);
				transparentPriming = true;
			} else frame.setLocation(offscreenLocation());
		} catch (RuntimeException unsupported) {
			transparentPriming = false;
			frame.setLocation(offscreenLocation());
		}
		frame.setVisible(true);
		primedVisible = true;
	}

	private Point offscreenLocation() {
		Rectangle desktop = new Rectangle();
		for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getScreenDevices())
			for (var configuration : device.getConfigurations())
				desktop = desktop.union(configuration.getBounds());
		return new Point(desktop.x + desktop.width + 64, desktop.y);
	}

	Object invokeGL(Callable<Object> operation, Consumer<GL3> binder)
			throws Exception {
		if (!frame.isDisplayable())
			throw new IllegalStateException(
					"The application host has not been realized");
		return glInvoker.invoke(operation, binder);
	}

	void close(Runnable onClosed) {
		if (!closed.compareAndSet(false, true)) return;
		try { beforeClose.run(); }
		finally {
			try { destroy.run(); }
			finally {
				frame.dispose();
				onClosed.run();
			}
		}
	}

	private void resizeRoot() {
		GLContainer current = root;
		if (current != null)
			current.setBounds(0, 0, Math.max(0, renderComponent.getWidth()),
					Math.max(0, renderComponent.getHeight()));
	}

	private static void rethrow(Throwable failure) throws Exception {
		if (failure instanceof Exception error) throw error;
		if (failure instanceof Error error) throw error;
	}

	@FunctionalInterface
	private interface GLInvoker {
		Object invoke(Callable<Object> operation, Consumer<GL3> binder)
				throws Exception;
	}

	static Color resolveClearColor(Color override, GLTheme theme) {
        return override!=null?override:theme.values().getColor("application.clearColor");
    }

	private static final class ApplicationPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final Color clearColor;
		ApplicationPanel(Color clearColor) {
			this.clearColor = clearColor;
			setBackground(clearColor != null ? clearColor : com.vingame.glswing.ui.GLTheme.DEFAULT.values().getColor("application.clearColor"));
		}
		@Override protected void record(GLContext2D context) {
			context.clear(resolveClearColor(clearColor,getGLTheme()));
		}
	}

	private static final class ApplicationCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final Color clearColor;
		ApplicationCanvas(Color clearColor, int antialiasingSamples,
				int swapInterval) {
			super(antialiasingSamples, swapInterval);
			this.clearColor = clearColor;
			setBackground(clearColor != null ? clearColor : com.vingame.glswing.ui.GLTheme.DEFAULT.values().getColor("application.clearColor"));
		}
		@Override protected void renderContainer(GLContext2D context) {
			context.clear(resolveClearColor(clearColor,getGLTheme()));
		}
	}
}
