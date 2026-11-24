package com.vingame.glswing.application;

import java.awt.Color;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.JFrame;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMapLoader;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoader;

/**
 * Standalone application launcher with declarative startup work, a standard
 * splash window, resource loading and first-frame handoff.
 */
public final class GLApplication {
	private static final String THEME_KEY = "glswing.application.theme";
	private static final String INPUT_KEY = "glswing.application.input-map";
	private static final String LOCALIZER_KEY = "glswing.application.localizer";

	private final Builder settings;

	/** Immutable framebuffer settings used when the selected host is created. */
	public record RenderingSettings(int antialiasingSamples, int swapInterval) {
		public static final RenderingSettings DEFAULT = new RenderingSettings(
				GLSwingCanvas.DEFAULT_ANTIALIASING_SAMPLES, 1);
		public RenderingSettings {
			if (antialiasingSamples < 0 || swapInterval < 0)
				throw new IllegalArgumentException(
						"Antialiasing samples and swap interval must not be negative");
		}
		public RenderingSettings withAntialiasingSamples(int samples) {
			return new RenderingSettings(samples, swapInterval);
		}
		public RenderingSettings withSwapInterval(int interval) {
			return new RenderingSettings(antialiasingSamples, interval);
		}
		public RenderingSettings withVSync(boolean enabled) {
			return withSwapInterval(enabled ? 1 : 0);
		}
	}

	private GLApplication(Builder settings) { this.settings = new Builder(settings); }

	public static Builder builder() { return new Builder(); }

	/** Starts asynchronously and returns a handle before startup is complete. */
	public GLApplicationHandle launch() {
		GLApplicationHandle handle = new GLApplicationHandle();
		Thread startup = new Thread(() -> run(handle), "glswing-application-startup");
		startup.setDaemon(false);
		startup.start();
		return handle;
	}

	private void run(GLApplicationHandle handle) {
		AtomicReference<GLSplashWindow> splashReference = new AtomicReference<>();
		AtomicReference<GLApplicationWindow> windowReference = new AtomicReference<>();
		AtomicReference<JFrame> frameReference = new AtomicReference<>();
		AtomicReference<GLApplicationContext> contextReference = new AtomicReference<>();
		try {
			onAwt(() -> {
				JFrame frame = new JFrame(settings.title);
				frameReference.set(frame);
				GLSplashWindow splash = new GLSplashWindow(settings.splash,
						settings.title, settings.splashViewFactory, frame);
				splashReference.set(splash);
				splash.show();
				return null;
			});
			// Use a separate AWT turn so the splash can be presented before JOGL host
			// creation performs any platform or graphics-driver initialization.
			onAwt(() -> {
				GLApplicationWindow window = GLApplicationWindow.create(
						frameReference.get(), settings.backend, settings.width, settings.height,
						settings.minimumWidth, settings.minimumHeight, settings.clearColor,
						settings.renderingSettings,
						() -> handle.mutableClosedFuture().complete(null));
				windowReference.set(window);
				handle.window(window.frame());
				handle.closeAction(() -> onAwtLater(() -> window.requestClose(
						() -> handle.mutableClosedFuture().complete(null))));
				return null;
			});

			GLStartupPlan.Builder plan = GLStartupPlan.builder();
			plan.provide(THEME_KEY, "Loading theme", 1, task -> loadTheme(task));
			plan.provide(INPUT_KEY, "Loading input map", 0.6,
					task -> loadInputMap(task));
			plan.provide(LOCALIZER_KEY, "Loading language resources", 0.6,
					task -> loadLocalizer());
			for (Consumer<GLStartupPlan.Builder> preparation : settings.preparations)
				preparation.accept(plan);
			plan.awt("Building interface", 2, task -> {
				GLApplicationWindow window = windowReference.get();
				GLTheme theme = task.require(THEME_KEY, GLTheme.class);
				GLInputMap inputMap = task.require(INPUT_KEY, GLInputMap.class);
				GLLocalizer localizer = task.require(LOCALIZER_KEY, GLLocalizer.class);
				window.apply(theme, inputMap, localizer);
				GLApplicationContext context = new GLApplicationContext(window.frame(),
						window.parent(), theme, inputMap, localizer);
				context.resultResolver(key -> task.require(key, Object.class));
				GLContainer root = settings.surface.create(context);
				window.root(root);
				window.beforeClose(() -> settings.closeHandler.accept(context));
				window.closeRequest(() -> settings.closeRequestHandler.requestClose(context));
				contextReference.set(context);
				return null;
			});
			plan.awt("Rendering first frame", 0.8, task -> {
				windowReference.get().renderFirstFrame();
				return null;
			});
			plan.background("Confirming first frame", 0.2, task -> {
				task.detail("Waiting for renderer");
				windowReference.get().firstFrame().get(
						settings.firstFrameTimeout.toMillis(), TimeUnit.MILLISECONDS);
				return null;
			});
			plan.awt("Showing application window", 0.2,
					task -> { windowReference.get().show(); return null; });

			GLSplashWindow splash = splashReference.get();
			GLStartupEngine engine = new GLStartupEngine(plan.build(), splash::update);
			Map<String, Object> results = engine.run(
					(operation, binder) -> windowReference.get().invokeGL(operation, binder));
			GLApplicationContext context = contextReference.get();
			context.resultsAvailable(results);
			handle.mutableReadyFuture().complete(context);
			splash.closeAfterMinimumDuration();
		} catch (Throwable failure) {
			handle.mutableReadyFuture().completeExceptionally(failure);
			if (settings.failureHandler != null) {
				try { settings.failureHandler.accept(failure); }
				catch (Throwable handlerFailure) { failure.addSuppressed(handlerFailure); }
			}
			GLSplashWindow splash = splashReference.get();
			GLApplicationWindow window = windowReference.get();
			JFrame frame = frameReference.get();
			Runnable close = () -> {
				if (window != null)
					window.close(() -> handle.mutableClosedFuture().complete(null));
				else {
					if (frame != null) frame.dispose();
					handle.mutableClosedFuture().complete(null);
				}
			};
			if (splash != null) splash.showFailure(failure, close);
			else {
				failure.printStackTrace();
				onAwtLater(close);
			}
		}
	}

	private GLTheme loadTheme(GLStartupTaskContext task) {
		if (settings.themePath == null) return settings.theme;
		return GLThemeLoader.loadOrDefault(settings.themePath, settings.theme,
				error -> task.detail("Using fallback: " + error.getMessage()));
	}

	private GLInputMap loadInputMap(GLStartupTaskContext task) {
		if (settings.inputMapPath == null) return settings.inputMap;
		return GLInputMapLoader.loadOrDefault(settings.inputMapPath,
				settings.inputMap,
				error -> task.detail("Using fallback: " + error.getMessage()));
	}

	private GLLocalizer loadLocalizer() {
		GLLocalizer localizer = GLLocalizer.forLocale(settings.locale);
		for (String bundle : settings.localizationBundles)
			localizer = localizer.withBundle(bundle);
		return localizer;
	}

	private static <T> T onAwt(java.util.concurrent.Callable<T> operation)
			throws Exception {
		if (EventQueue.isDispatchThread()) return operation.call();
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try {
			EventQueue.invokeAndWait(() -> {
				try { result.set(operation.call()); }
				catch (Throwable error) { failure.set(error); }
			});
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		} catch (InvocationTargetException error) {
			throw new IllegalStateException("Cannot create application window",
					error.getCause());
		}
		if (failure.get() instanceof Exception error) throw error;
		if (failure.get() instanceof Error error) throw error;
		return result.get();
	}

	private static void onAwtLater(Runnable operation) {
		if (EventQueue.isDispatchThread()) operation.run();
		else EventQueue.invokeLater(operation);
	}

	/** Mutable configuration used to create and launch an application. */
	public static final class Builder {
		private String title = "GLSwing Application";
		private GLBackend backend = GLBackend.GLJPANEL;
		private int width = 1100;
		private int height = 720;
		private int minimumWidth = 640;
		private int minimumHeight = 420;
		private Color clearColor; // null follows application.clearColor from the active theme
		private RenderingSettings renderingSettings = RenderingSettings.DEFAULT;
		private GLSplashConfiguration splash = GLSplashConfiguration.builder().build();
		private GLSplashViewFactory splashViewFactory;
		private GLTheme theme = GLTheme.DEFAULT;
		private Path themePath;
		private GLInputMap inputMap = GLInputMaps.DEFAULT;
		private Path inputMapPath;
		private Locale locale = Locale.getDefault();
		private final java.util.List<String> localizationBundles =
				new java.util.ArrayList<>();
		private final java.util.List<Consumer<GLStartupPlan.Builder>> preparations =
				new java.util.ArrayList<>();
		private GLApplicationSurfaceFactory surface;
		private Consumer<Throwable> failureHandler = Throwable::printStackTrace;
		private Consumer<GLApplicationContext> closeHandler = context -> { };
		private GLApplicationCloseRequestHandler closeRequestHandler = context ->
				java.util.concurrent.CompletableFuture.completedFuture(true);
		private Duration firstFrameTimeout = Duration.ofSeconds(30);

		public Builder() { }

		private Builder(Builder source) {
			title = source.title;
			backend = source.backend;
			width = source.width;
			height = source.height;
			minimumWidth = source.minimumWidth;
			minimumHeight = source.minimumHeight;
			clearColor = source.clearColor;
			renderingSettings = source.renderingSettings;
			splash = source.splash;
			splashViewFactory = source.splashViewFactory;
			theme = source.theme;
			themePath = source.themePath;
			inputMap = source.inputMap;
			inputMapPath = source.inputMapPath;
			locale = source.locale;
			localizationBundles.addAll(source.localizationBundles);
			preparations.addAll(source.preparations);
			surface = source.surface;
			failureHandler = source.failureHandler;
			closeHandler = source.closeHandler;
			closeRequestHandler = source.closeRequestHandler;
			firstFrameTimeout = source.firstFrameTimeout;
		}

		public Builder title(String title) {
			if (title == null) throw new IllegalArgumentException("Title must not be null");
			this.title = title;
			return this;
		}
		public Builder backend(GLBackend backend) {
			if (backend == null) throw new IllegalArgumentException("Backend is required");
			this.backend = backend;
			return this;
		}
		public Builder size(int width, int height) {
			if (width <= 0 || height <= 0)
				throw new IllegalArgumentException("Application size must be positive");
			this.width = width;
			this.height = height;
			return this;
		}
		public Builder minimumSize(int width, int height) {
			if (width <= 0 || height <= 0)
				throw new IllegalArgumentException("Minimum size must be positive");
			minimumWidth = width;
			minimumHeight = height;
			return this;
		}
		public Builder clearColor(Color color) {
			if (color == null) throw new IllegalArgumentException("Clear color is required");
			clearColor = color;
			return this;
		}
		public Builder renderingSettings(RenderingSettings settings) {
			if (settings == null)
				throw new IllegalArgumentException("Rendering settings are required");
			renderingSettings = settings;
			return this;
		}
		public Builder splash(Consumer<GLSplashConfiguration.Builder> configuration) {
			if (configuration == null)
				throw new IllegalArgumentException("Splash configuration is required");
			GLSplashConfiguration.Builder builder = GLSplashConfiguration.builder();
			configuration.accept(builder);
			splash = builder.build();
			return this;
		}
		public Builder splash(GLSplashConfiguration configuration) {
			if (configuration == null)
				throw new IllegalArgumentException("Splash configuration is required");
			splash = configuration;
			return this;
		}
		/**
		 * Replaces the standard splash layout with a custom Swing composition.
		 * The factory runs on the AWT event-dispatch thread.
		 */
		public Builder splashView(GLSplashViewFactory factory) {
			if (factory == null)
				throw new IllegalArgumentException("Splash view factory is required");
			splashViewFactory = factory;
			return this;
		}
		public Builder theme(Path path) {
			if (path == null) throw new IllegalArgumentException("Theme path is required");
			themePath = path;
			return this;
		}
		public Builder theme(GLTheme fallback) {
			if (fallback == null) throw new IllegalArgumentException("Theme is required");
			theme = fallback;
			themePath = null;
			return this;
		}
		public Builder theme(Path path, GLTheme fallback) {
			if (path == null || fallback == null)
				throw new IllegalArgumentException(
						"Theme path and fallback are required");
			theme = fallback;
			themePath = path;
			return this;
		}
		public Builder inputMap(Path path) {
			if (path == null)
				throw new IllegalArgumentException("Input-map path is required");
			inputMapPath = path;
			return this;
		}
		public Builder inputMap(GLInputMap fallback) {
			if (fallback == null)
				throw new IllegalArgumentException("Input map is required");
			inputMap = fallback;
			inputMapPath = null;
			return this;
		}
		public Builder inputMap(Path path, GLInputMap fallback) {
			if (path == null || fallback == null)
				throw new IllegalArgumentException(
						"Input-map path and fallback are required");
			inputMap = fallback;
			inputMapPath = path;
			return this;
		}
		public Builder locale(Locale locale) {
			if (locale == null) throw new IllegalArgumentException("Locale is required");
			this.locale = locale;
			return this;
		}
		public Builder localizationBundle(String baseName) {
			if (baseName == null || baseName.isBlank())
				throw new IllegalArgumentException("Bundle base name must not be blank");
			localizationBundles.add(baseName);
			return this;
		}
		public Builder prepare(Consumer<GLStartupPlan.Builder> preparation) {
			if (preparation == null)
				throw new IllegalArgumentException("Startup preparation is required");
			preparations.add(preparation);
			return this;
		}
		public Builder surface(GLApplicationSurfaceFactory surface) {
			this.surface = surface;
			return this;
		}
		public Builder onFailure(Consumer<Throwable> handler) {
			if (handler == null)
				throw new IllegalArgumentException("Failure handler is required");
			failureHandler = handler;
			return this;
		}
		/** Runs on AWT immediately before the rendering host is disposed. */
		public Builder onClose(Consumer<GLApplicationContext> handler) {
			if (handler == null)
				throw new IllegalArgumentException("Close handler is required");
			closeHandler = handler;
			return this;
		}
		/**
		 * Installs an asynchronous close guard. Returning {@code false}, completing
		 * exceptionally, or returning {@code null} keeps the application open.
		 */
		public Builder onCloseRequest(GLApplicationCloseRequestHandler handler) {
			if (handler == null)
				throw new IllegalArgumentException("Close request handler is required");
			closeRequestHandler = handler;
			return this;
		}
		public Builder firstFrameTimeout(Duration timeout) {
			if (timeout == null || timeout.isZero() || timeout.isNegative())
				throw new IllegalArgumentException("First-frame timeout must be positive");
			firstFrameTimeout = timeout;
			return this;
		}
		public GLApplication build() {
			if (surface == null)
				throw new IllegalStateException("An application surface is required");
			return new GLApplication(this);
		}
		public GLApplicationHandle launch() { return build().launch(); }
	}
}
