package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeLoader;
import com.vingame.glswing.ui.GLThemeWatcher;
import com.vingame.glswing.ui.GLThemes;

/** Direct-GLCanvas host for exactly the same scene as {@link GuiShowcaseDemo}. */
public final class CanvasGuiShowcaseDemo {
	private static final int TARGET_FPS = 144;

	private CanvasGuiShowcaseDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GUI Showcase / GLCanvas");
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("CanvasGuiShowcaseDemo benoetigt diese VM-Argumente:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			return;
		}
		SwingUtilities.invokeLater(CanvasGuiShowcaseDemo::showWindow);
	}

	private static void showWindow() {
		ShowcaseCanvas canvas = new ShowcaseCanvas();
		canvas.setPreferredSize(new Dimension(1260, 800));
		JLabel status = GuiShowcaseDemo.createStatusLabel(
				"Canvas-Showcase startet ...");
		FPSAnimator animator = new FPSAnimator(canvas, TARGET_FPS, true);
		GLDemoErrorReporter.attach(animator);
		Timer statistics = new Timer(250,
				event -> status.setText(canvas.performanceText()));

		AtomicBoolean closing = new AtomicBoolean();
		JFrame frame = new JFrame("GLSwingNext - GUI Showcase (Direct Canvas)");
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (!closing.compareAndSet(false, true))
					return;
				canvas.closeThemeWatcher();
				statistics.stop();
				Thread shutdown = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "GL-canvas-showcase-stop");
				shutdown.setDaemon(true);
				shutdown.start();
			}
		});
		frame.pack();
		frame.setMinimumSize(new Dimension(760, 520));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		statistics.start();
		animator.start();
	}

	static final class ShowcaseCanvas extends GLSwingContainerCanvas
			implements GuiShowcaseDemo.ShowcaseHost {
		private static final long serialVersionUID = 1L;
		private static final float MIN_ZOOM = 0.45f;
		private static final float MAX_ZOOM = 2.5f;
		private static final String SHOWCASE_BUNDLE =
				"com.vingame.glswing.demo.languages.Showcase";
		private static final Path THEME_DIRECTORY =
				GuiShowcaseDemo.showcaseThemeDirectory();

		private final GuiShowcaseDemo.ShowcaseSurface surface =
				new GuiShowcaseDemo.ShowcaseSurface(this);
		private final GuiShowcaseDemo.ZoomLayer zoomLayer =
				new GuiShowcaseDemo.ZoomLayer(this, surface);
		private volatile float zoom = 1;
		private volatile String lastAction =
				"Strg + Mausrad: Zoom | Strg + 0: 100 %";
		private GLThemeWatcher themeWatcher;
		private String colorsFile = "showcase-dark-colors.gltheme";
		private String colorsNameKey = "showcase.theme.colors.dark";
		private String metricsFile = "showcase-default-metrics.gltheme";
		private String metricsNameKey = "showcase.theme.metrics.default";

		ShowcaseCanvas() {
			this(DEFAULT_ANTIALIASING_SAMPLES);
		}

		ShowcaseCanvas(int antialiasingSamples) {
			super(antialiasingSamples, 0);
			surface.setBounds(0, 0, GuiShowcaseDemo.DESIGN_WIDTH,
					GuiShowcaseDemo.DESIGN_HEIGHT);
			zoomLayer.setBounds(0, 0, GuiShowcaseDemo.DESIGN_WIDTH,
					GuiShowcaseDemo.DESIGN_HEIGHT);
			zoomLayer.addGL(surface);
			addGL(zoomLayer);
			addGL(surface.menuBar());
			watchShowcaseTheme("dark.gltheme", GLThemes.DARK,
					"showcase.theme.dark", false);
			setLanguage(Locale.GERMAN);
			addMouseWheelListener(event -> {
				if (!event.isControlDown())
					return;
				event.consume();
				double factor = Math.pow(1.15, -event.getPreciseWheelRotation());
				setZoomFromWheel((float) (zoom * factor));
			});
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent event) {
					if (event.getKeyCode() == KeyEvent.VK_0
							&& (event.getModifiersEx()
									& InputEvent.CTRL_DOWN_MASK) != 0)
						setZoom(1);
				}
			});
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(12, 17, 27));
			float menuHeight = surface.menuBar().getPreferredGLSize().height();
			surface.menuBar().setBounds(0, 0, getWidth(), menuHeight);
		}

		@Override
		public GLParent parent() {
			return this;
		}

		@Override
		public int viewWidth() {
			return getWidth();
		}

		@Override
		public int viewHeight() {
			return getHeight();
		}

		@Override
		public float zoom() {
			return zoom;
		}

		@Override
		public void setZoom(float zoom) {
			applyZoom(zoom, true);
		}

		private void setZoomFromWheel(float zoom) {
			applyZoom(zoom, false);
		}

		private void applyZoom(float zoom, boolean rebuildPicking) {
			float resolved = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
			if (Float.compare(this.zoom, resolved) == 0)
				return;
			surface.closeTransientPopups();
			this.zoom = resolved;
			lastAction = String.format("Zoom auf %.0f %% gesetzt", resolved * 100);
			if (rebuildPicking) {
				repaintAndRebuildPicking(zoomLayer);
			} else {
				repaintInteractively(zoomLayer);
			}
		}

		@Override
		public void report(String message) {
			lastAction = message;
		}

		@Override
		public GLHistoryManager historyManager() {
			return getGLHistoryManager();
		}

		@Override
		public void setThemeCornerRadius(float radius) {
			setGLTheme(getGLTheme().withCornerRadius(radius));
			lastAction = String.format("Theme-Rundung auf %.1f px gesetzt", radius);
		}

		@Override
		public void setLanguage(Locale locale) {
			GLLocalizer localizer = GLLocalizer.forLocale(locale)
					.withBundle(SHOWCASE_BUNDLE);
			setGLLocalizer(localizer);
			surface.updateLocalizedMenuText();
			lastAction = localizer.text("showcase.status.language",
					locale.getDisplayLanguage(locale));
		}

		@Override
		public void watchShowcaseTheme(String fileName, GLTheme fallback,
				String nameKey, boolean preserveExternalMetrics) {
			closeThemeWatcher();
			Path file = THEME_DIRECTORY.resolve(fileName);
			if (!Files.isRegularFile(file)) {
				applyShowcaseTheme(fallback, nameKey, false,
						preserveExternalMetrics);
				if (preserveExternalMetrics)
					lastAction = "Externe Theme-Datei nicht gefunden: " + file;
				return;
			}
			try {
				themeWatcher = GLThemeWatcher.watch(file,
						theme -> applyShowcaseTheme(theme, nameKey, true,
								preserveExternalMetrics),
						error -> lastAction = "Theme-Fehler: " + error.getMessage());
			} catch (GLThemeLoadException error) {
				applyShowcaseTheme(fallback, nameKey, false,
						preserveExternalMetrics);
				lastAction = "Theme-Watcher nicht aktiv: " + error.getMessage();
			}
		}

		@Override
		public void selectShowcaseColors(String fileName, String nameKey) {
			colorsFile = fileName;
			colorsNameKey = nameKey;
			watchThemeFragments();
		}

		@Override
		public void selectShowcaseMetrics(String fileName, String nameKey) {
			metricsFile = fileName;
			metricsNameKey = nameKey;
			watchThemeFragments();
		}

		private void watchThemeFragments() {
			closeThemeWatcher();
			Path watchedFile = THEME_DIRECTORY.resolve(metricsFile);
			try {
				themeWatcher = GLThemeWatcher.watch(watchedFile,
						unused -> applyThemeFragments(true),
						error -> lastAction = "Theme-Fehler: " + error.getMessage());
			} catch (GLThemeLoadException error) {
				lastAction = "Theme-Watcher nicht aktiv: " + error.getMessage();
			}
		}

		private void applyThemeFragments(boolean hotReloaded) {
			try {
				GLTheme theme = GLThemeLoader.loadBundle(GLThemes.DEFAULT,
						THEME_DIRECTORY.resolve(colorsFile),
						THEME_DIRECTORY.resolve(metricsFile));
				setGLTheme(theme);
				surface.syncThemeRadius(theme.buttonStyle().cornerRadius());
				lastAction = getGLLocalizer().text(
						"showcase.status.themeCombination",
						getGLLocalizer().text(colorsNameKey),
						getGLLocalizer().text(metricsNameKey))
						+ (hotReloaded ? " | Hot-Reload" : "");
			} catch (GLThemeLoadException error) {
				lastAction = "Theme-Fehler: " + error.getMessage();
			}
		}

		@Override
		public GLLocalizer localizer() {
			return getGLLocalizer();
		}

		private void applyShowcaseTheme(GLTheme theme, String nameKey,
				boolean hotReloaded, boolean preserveExternalMetrics) {
			GLTheme applied = preserveExternalMetrics ? theme
					: theme.withCornerRadius(surface.themeRadius());
			setGLTheme(applied);
			if (preserveExternalMetrics)
				surface.syncThemeRadius(applied.buttonStyle().cornerRadius());
			lastAction = getGLLocalizer().text("showcase.status.theme",
					getGLLocalizer().text(nameKey))
					+ (hotReloaded ? " | Hot-Reload" : "");
		}

		void closeThemeWatcher() {
			finishInteractiveRepaint();
			if (themeWatcher == null)
				return;
			themeWatcher.close();
			themeWatcher = null;
		}

		private String performanceText() {
			return GuiShowcaseDemo.formatPerformance("GLCanvas", lastAction, zoom,
					getFramesPerSecond(), getFrameProfile(), getLastDrawCallProfile(),
					getActualAntialiasingSamples(), getActualSwapInterval(),
					getRenderResolutionScale(), getRenderPixelWidth(),
					getRenderPixelHeight(), getSurfacePixelWidth(),
					getSurfacePixelHeight());
		}
	}
}
