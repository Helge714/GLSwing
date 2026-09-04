package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.vingame.glswing.application.GLApplication;
import com.vingame.glswing.application.GLApplicationContext;
import com.vingame.glswing.application.GLApplicationHandle;
import com.vingame.glswing.application.GLBackend;
import com.vingame.glswing.application.GLSplashAlignment;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.document.GLDocument;
import com.vingame.glswing.document.GLDocumentCloseChoice;
import com.vingame.glswing.document.GLDocumentCodecRegistry;
import com.vingame.glswing.document.GLDocumentLifecycle;
import com.vingame.glswing.document.GLDocumentManager;
import com.vingame.glswing.document.GLPlainTextDocumentCodec;
import com.vingame.glswing.document.GLDocumentTabController;
import com.vingame.glswing.document.GLDocumentWorkspace;
import com.vingame.glswing.file.GLFileBrowser;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileDialogs;
import com.vingame.glswing.file.GLFileFilter;
import com.vingame.glswing.file.GLFileSelectionMode;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.GLNodePorts;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.lsp.GLLanguageServer;
import com.vingame.glswing.lsp.GLLanguageServerProfile;
import com.vingame.glswing.lsp.GLLanguageServerRegistry;
import com.vingame.glswing.rendering.GLColorInterpolation;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDrawCallProfile;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLAction;
import com.vingame.glswing.ui.GLActionMap;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLabelStyle;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLListSelectionModel;
import com.vingame.glswing.ui.GLDefaultTreeModel;
import com.vingame.glswing.ui.GLTree;
import com.vingame.glswing.ui.GLItemPresentation;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuBarItem;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLSliderGroup;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLTextArea;
import com.vingame.glswing.ui.GLTextStyle;
import com.vingame.glswing.ui.GLTextDocument;
import com.vingame.glswing.ui.GLCodeEditor;
import com.vingame.glswing.ui.GLCodeLanguages;
import com.vingame.glswing.ui.GLTextInputFormats;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeLoader;
import com.vingame.glswing.ui.GLThemeWatcher;
import com.vingame.glswing.ui.GLThemes;

/** Extensible fixed-layout showcase for every public GLSwing GUI control. */
public final class GuiShowcaseDemo {

	static final float DESIGN_WIDTH = 1180;
	static final float DESIGN_HEIGHT = 760;
	private static final Path THEME_RELATIVE_DIRECTORY = Path.of("resources",
			"com", "vingame", "glswing", "themes");
	private static List<String> lspCommand = List.of();

	private GuiShowcaseDemo() {
	}

	static Path showcaseThemeDirectory() {
		Path directory = Path.of("").toAbsolutePath().normalize();
		for (int depth = 0; directory != null && depth < 8;
				depth++, directory = directory.getParent()) {
			Path candidate = directory.resolve(THEME_RELATIVE_DIRECTORY);
			if (Files.isDirectory(candidate))
				return candidate;
		}
		return Path.of("").toAbsolutePath().normalize()
				.resolve(THEME_RELATIVE_DIRECTORY);
	}

	interface ShowcaseHost {
		GLParent parent();
		int viewWidth();
		int viewHeight();
		float zoom();
		void setZoom(float zoom);
		float getRenderResolutionScale();
		void setRenderResolutionScale(float scale);
		int getRenderPixelWidth();
		int getRenderPixelHeight();
		int getSurfacePixelWidth();
		int getSurfacePixelHeight();
		void report(String message);
		void setThemeCornerRadius(float radius);
		void setLanguage(Locale locale);
		void watchShowcaseTheme(String fileName, GLTheme fallback, String nameKey,
				boolean preserveExternalMetrics);
		void selectShowcaseColors(String fileName, String nameKey);
		void selectShowcaseMetrics(String fileName, String nameKey);
		GLHistoryManager historyManager();
		GLLocalizer localizer();
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GUI Showcase / GLJPanel");
		launchShowcase(args, GLBackend.GLJPANEL,
				"GLSwing – GUI Showcase");
	}

	static GLApplicationHandle launchShowcase(String[] args, GLBackend backend,
			String title) {
		lspCommand = languageServerCommand(args);
		AtomicReference<ShowcaseApplicationRoot> rootReference =
				new AtomicReference<>();
		return GLApplication.builder()
				.title(title)
				.backend(backend)
				.size(1260, 800)
				.minimumSize(760, 520)
				.clearColor(new Color(12, 17, 27))
				.theme(showcaseThemeDirectory().resolve("dark.gltheme"),
						GLThemes.DARK)
				.locale(Locale.GERMAN)
				.localizationBundle(
						"com.vingame.glswing.demo.languages.Showcase")
				.splash(splash -> splash
						.size(640, 380)
						.padding(32)
						.contentAlignment(GLSplashAlignment.CENTER_LEFT)
						.addTitle("GLSwing")
						.addGap(4)
						.addSubtitle("Preparing the GUI showcase")
						.addGap(22)
						.addTaskHistory(400, 7, 2, 0.2, true)
						.addProgressBar(false)
						.minimumDisplayMillis(500)
						.handoffDelayMillis(1000))
				.prepare(startup -> startup.background(
						"Discovering optional integrations", task -> {
							task.detail(lspCommand.isEmpty()
									? "Core language support"
									: "Language server enabled");
							return null;
						}))
				.surface(application -> {
					ShowcaseApplicationRoot root =
							new ShowcaseApplicationRoot(application, backend);
					rootReference.set(root);
					return root;
				})
				.onCloseRequest(application -> {
					ShowcaseApplicationRoot root = rootReference.get();
					return root == null
							? java.util.concurrent.CompletableFuture.completedFuture(true)
							: root.prepareClose();
				})
				.onClose(application -> {
					ShowcaseApplicationRoot root = rootReference.get();
					if (root != null) root.close();
				})
				.launch();
	}
	private static List<String> languageServerCommand(String[] args) {
		for (int index = 0; index < args.length; index++)
			if (args[index].equals("--lsp") && index + 1 < args.length)
				return List.copyOf(Arrays.asList(args).subList(index + 1, args.length));
		String executable = System.getProperty("glswing.demo.lsp.command", "").strip();
		return executable.isEmpty() ? List.of() : List.of(executable);
	}

	private static void showWindow() {
		ShowcasePanel panel = new ShowcasePanel();
		panel.setPreferredSize(new Dimension(1260, 800));
		JLabel status = createStatusLabel("GUI-Showcase startet ...");
		Timer statistics = new Timer(1000,
				event -> status.setText(panel.performanceText()));

		JFrame frame = new JFrame("GLSwingNext - GUI Showcase");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			private boolean closePending;
			@Override
			public void windowClosing(WindowEvent event) {
				if (closePending) return;
				closePending = true;
				panel.prepareClose().whenComplete((approved, failure) ->
						SwingUtilities.invokeLater(() -> {
							if (failure != null || !Boolean.TRUE.equals(approved)) {
								closePending = false;
								if (failure != null)
									panel.report("Close failed: " + failure.getMessage());
								return;
							}
							statistics.stop();
							panel.closeThemeWatcher();
							panel.closeLanguageServer();
							panel.destroy();
							frame.dispose();
							System.exit(0);
						}));
			}
		});
		frame.pack();
		frame.setMinimumSize(new Dimension(760, 520));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		statistics.start();
		panel.repaintGL();
	}

	static final class ShowcasePanel extends GLSwingPanel
			implements ShowcaseHost {
		private static final long serialVersionUID = 1L;
		private static final float MIN_ZOOM = 0.45f;
		private static final float MAX_ZOOM = 2.5f;
		private static final String SHOWCASE_BUNDLE =
			"com.vingame.glswing.demo.languages.Showcase";
		private static final Path THEME_DIRECTORY = showcaseThemeDirectory();

		private final ShowcaseSurface surface = new ShowcaseSurface(this);
		private final ZoomLayer zoomLayer = new ZoomLayer(this, surface);
		private volatile float zoom = 1;
		private volatile String lastAction =
				"Strg + Mausrad: Zoom | Strg + 0: 100 %";
		private GLThemeWatcher themeWatcher;
		private String colorsFile = "showcase-dark-colors.gltheme";
		private String colorsNameKey = "showcase.theme.colors.dark";
		private String metricsFile = "showcase-default-metrics.gltheme";
		private String metricsNameKey = "showcase.theme.metrics.default";

		ShowcasePanel() {
			this(DEFAULT_ANTIALIASING_SAMPLES);
		}

		ShowcasePanel(int antialiasingSamples) {
			super(antialiasingSamples);
			surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
			zoomLayer.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
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
				double factor = Math.pow(1.15,
						-event.getPreciseWheelRotation());
				setZoomFromWheel((float) (zoom * factor));
			});
			getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
					KeyStroke.getKeyStroke(KeyEvent.VK_0,
							InputEvent.CTRL_DOWN_MASK), "resetZoom");
			getActionMap().put("resetZoom", new AbstractAction() {
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(ActionEvent event) {
					setZoom(1);
				}
			});
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(12, 17, 27));
		}

		@Override
		protected void render(GLContext2D context) {
			super.render(context);
			float menuHeight = surface.menuBar().getPreferredGLSize().height();
			surface.menuBar().setBounds(0, 0, getWidth(), menuHeight);
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

		void closeThemeWatcher() {
			finishInteractiveRepaint();
			if (themeWatcher == null)
				return;
			themeWatcher.close();
			themeWatcher = null;
		}
		void closeLanguageServer() { surface.closeLanguageServer(); }
		java.util.concurrent.CompletionStage<Boolean> prepareClose() {
			return surface.prepareClose();
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
		public GLLocalizer localizer() {
			return getGLLocalizer();
		}

		private String performanceText() {
			return formatPerformance("GLJPanel / Event-driven", lastAction, zoom,
					getFramesPerSecond(), getFrameProfile(), getLastDrawCallProfile(),
					getActualAntialiasingSamples(), -1,
					getRenderResolutionScale(), getRenderPixelWidth(),
					getRenderPixelHeight(), getSurfacePixelWidth(),
					getSurfacePixelHeight());
		}

	}

	/** Eager showcase root used by both managed application backends. */
	static final class ShowcaseApplicationRoot extends GLContainer
			implements ShowcaseHost, AutoCloseable {
		private static final float MIN_ZOOM = 0.45f;
		private static final float MAX_ZOOM = 2.5f;
		private static final String SHOWCASE_BUNDLE =
				"com.vingame.glswing.demo.languages.Showcase";
		private static final Path THEME_DIRECTORY = showcaseThemeDirectory();

		private final GLApplicationContext application;
		private final GLBackend backend;
		private final ShowcaseSurface surface;
		private final ZoomLayer zoomLayer;
		private final JLabel status;
		private final Timer statistics;
		private volatile float zoom = 1;
		private volatile String lastAction =
				"Strg + Mausrad: Zoom | Strg + 0: 100 %";
		private GLThemeWatcher themeWatcher;
		private String colorsFile = "showcase-dark-colors.gltheme";
		private String colorsNameKey = "showcase.theme.colors.dark";
		private String metricsFile = "showcase-default-metrics.gltheme";
		private String metricsNameKey = "showcase.theme.metrics.default";

		ShowcaseApplicationRoot(GLApplicationContext application,
				GLBackend backend) {
			this.application = application;
			this.backend = backend;
			setGLTheme(application.theme());
			setGLLocalizer(application.localizer());
			surface = new ShowcaseSurface(this);
			zoomLayer = new ZoomLayer(this, surface);
			surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
			zoomLayer.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
			zoomLayer.addGL(surface);
			addGL(zoomLayer);
			addGL(surface.menuBar());

			status = createStatusLabel("GUI showcase is starting ...");
			application.window().getContentPane().add(status, BorderLayout.SOUTH);
			application.window().pack();
			application.window().setLocationRelativeTo(null);
			statistics = new Timer(1000,
					event -> status.setText(performanceText()));
			statistics.start();

			installZoomInput(renderComponent());
			surface.updateLocalizedMenuText();
			watchShowcaseTheme("dark.gltheme", application.theme(),
					"showcase.theme.dark", false);
		}

		private Component renderComponent() {
			return (Component) application.parent();
		}

		private void installZoomInput(Component component) {
			component.addMouseWheelListener(event -> {
				if (!event.isControlDown()) return;
				event.consume();
				double factor = Math.pow(1.15,
						-event.getPreciseWheelRotation());
				setZoomFromWheel((float) (zoom * factor));
			});
			if (component instanceof JComponent swingComponent) {
				swingComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
						KeyStroke.getKeyStroke(KeyEvent.VK_0,
								InputEvent.CTRL_DOWN_MASK), "resetZoom");
				swingComponent.getActionMap().put("resetZoom", new AbstractAction() {
					private static final long serialVersionUID = 1L;
					@Override public void actionPerformed(ActionEvent event) { setZoom(1); }
				});
			} else {
				component.addKeyListener(new java.awt.event.KeyAdapter() {
					@Override public void keyPressed(KeyEvent event) {
						if (event.getKeyCode() == KeyEvent.VK_0
								&& (event.getModifiersEx()
										& InputEvent.CTRL_DOWN_MASK) != 0)
							setZoom(1);
					}
				});
			}
		}

		@Override protected void record(GLContext2D context) { }

		@Override protected void render(GLContext2D context) {
			super.render(context);
			float menuHeight = surface.menuBar().getPreferredGLSize().height();
			surface.menuBar().setBounds(0, 0, getWidth(), menuHeight);
		}

		@Override public GLParent parent() { return application.parent(); }
		@Override public int viewWidth() { return (int) getWidth(); }
		@Override public int viewHeight() { return (int) getHeight(); }
		@Override public float zoom() { return zoom; }
		@Override public float getRenderResolutionScale() {
			GLParent parent = application.parent();
			return parent instanceof GLSwingContainerCanvas canvas
					? canvas.getRenderResolutionScale()
					: ((GLSwingPanel) parent).getRenderResolutionScale();
		}
		@Override public void setRenderResolutionScale(float scale) {
			GLParent parent = application.parent();
			if (parent instanceof GLSwingContainerCanvas canvas)
				canvas.setRenderResolutionScale(scale);
			else ((GLSwingPanel) parent).setRenderResolutionScale(scale);
		}
		@Override public int getRenderPixelWidth() {
			GLParent parent = application.parent();
			return parent instanceof GLSwingContainerCanvas canvas
					? canvas.getRenderPixelWidth()
					: ((GLSwingPanel) parent).getRenderPixelWidth();
		}
		@Override public int getRenderPixelHeight() {
			GLParent parent = application.parent();
			return parent instanceof GLSwingContainerCanvas canvas
					? canvas.getRenderPixelHeight()
					: ((GLSwingPanel) parent).getRenderPixelHeight();
		}
		@Override public int getSurfacePixelWidth() {
			GLParent parent = application.parent();
			return parent instanceof GLSwingContainerCanvas canvas
					? canvas.getSurfacePixelWidth()
					: ((GLSwingPanel) parent).getSurfacePixelWidth();
		}
		@Override public int getSurfacePixelHeight() {
			GLParent parent = application.parent();
			return parent instanceof GLSwingContainerCanvas canvas
					? canvas.getSurfacePixelHeight()
					: ((GLSwingPanel) parent).getSurfacePixelHeight();
		}

		@Override public void setZoom(float zoom) { applyZoom(zoom, true); }

		private void setZoomFromWheel(float zoom) { applyZoom(zoom, false); }

		private void applyZoom(float zoom, boolean rebuildPicking) {
			float resolved = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
			if (Float.compare(this.zoom, resolved) == 0) return;
			surface.closeTransientPopups();
			this.zoom = resolved;
			lastAction = String.format("Zoom auf %.0f %% gesetzt", resolved * 100);
			if (rebuildPicking) repaintAndRebuildPicking(zoomLayer);
			else repaintInteractively(zoomLayer);
		}

		@Override public void report(String message) { lastAction = message; }

		@Override public GLHistoryManager historyManager() {
			return application.parent().getGLHistoryManager();
		}

		@Override public void setThemeCornerRadius(float radius) {
			applyRootTheme(getGLTheme().withCornerRadius(radius));
			lastAction = String.format("Theme-Rundung auf %.1f px gesetzt", radius);
		}

		@Override public void setLanguage(Locale locale) {
			GLLocalizer localizer = GLLocalizer.forLocale(locale)
					.withBundle(SHOWCASE_BUNDLE);
			setGLLocalizer(localizer);
			application.parent().setGLLocalizer(localizer);
			surface.updateLocalizedMenuText();
			lastAction = localizer.text("showcase.status.language",
					locale.getDisplayLanguage(locale));
		}

		private void applyRootTheme(GLTheme theme) {
			setGLTheme(theme);
			application.parent().setGLTheme(theme);
		}

		private void applyShowcaseTheme(GLTheme theme, String nameKey,
				boolean hotReloaded, boolean preserveExternalMetrics) {
			GLTheme applied = preserveExternalMetrics ? theme
					: theme.withCornerRadius(surface.themeRadius());
			applyRootTheme(applied);
			if (preserveExternalMetrics)
				surface.syncThemeRadius(applied.buttonStyle().cornerRadius());
			lastAction = localizer().text("showcase.status.theme",
					localizer().text(nameKey))
					+ (hotReloaded ? " | Hot-Reload" : "");
		}

		@Override public void watchShowcaseTheme(String fileName, GLTheme fallback,
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

		@Override public void selectShowcaseColors(String fileName, String nameKey) {
			colorsFile = fileName;
			colorsNameKey = nameKey;
			watchThemeFragments();
		}

		@Override public void selectShowcaseMetrics(String fileName, String nameKey) {
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
				applyRootTheme(theme);
				surface.syncThemeRadius(theme.buttonStyle().cornerRadius());
				lastAction = localizer().text("showcase.status.themeCombination",
						localizer().text(colorsNameKey),
						localizer().text(metricsNameKey))
						+ (hotReloaded ? " | Hot-Reload" : "");
			} catch (GLThemeLoadException error) {
				lastAction = "Theme-Fehler: " + error.getMessage();
			}
		}

		@Override public GLLocalizer localizer() { return getGLLocalizer(); }

		private void closeThemeWatcher() {
			finishInteractiveRepaint();
			if (themeWatcher == null) return;
			themeWatcher.close();
			themeWatcher = null;
		}

		private String performanceText() {
			GLParent parent = application.parent();
			if (parent instanceof GLSwingContainerCanvas canvas)
				return formatPerformance("GLCanvas / Event-driven", lastAction,
						zoom, canvas.getFramesPerSecond(), canvas.getFrameProfile(),
						canvas.getLastDrawCallProfile(),
						canvas.getActualAntialiasingSamples(),
						canvas.getActualSwapInterval(),
						canvas.getRenderResolutionScale(),
						canvas.getRenderPixelWidth(), canvas.getRenderPixelHeight(),
						canvas.getSurfacePixelWidth(), canvas.getSurfacePixelHeight());
			GLSwingPanel panel = (GLSwingPanel) parent;
			return formatPerformance("GLJPanel / Event-driven", lastAction, zoom,
					panel.getFramesPerSecond(), panel.getFrameProfile(),
					panel.getLastDrawCallProfile(), panel.getActualAntialiasingSamples(),
					-1, panel.getRenderResolutionScale(), panel.getRenderPixelWidth(),
					panel.getRenderPixelHeight(), panel.getSurfacePixelWidth(),
					panel.getSurfacePixelHeight());
		}

		@Override public void close() {
			statistics.stop();
			closeThemeWatcher();
			surface.closeLanguageServer();
		}

		java.util.concurrent.CompletionStage<Boolean> prepareClose() {
			return surface.prepareClose();
		}
	}

	/** Keeps only the showcase content in the zoomable coordinate system. */
	static final class ZoomLayer extends GLContainer {
		private final ShowcaseHost host;
		private final ShowcaseSurface surface;

		ZoomLayer(ShowcaseHost host, ShowcaseSurface surface) {
			this.host = host;
			this.surface = surface;
		}

		@Override
		protected void record(GLContext2D context) {
		}

		@Override
		protected void transformComponent(GLContext2D context) {
			float menuHeight = surface.menuBar().getHeight();
			float contentHeight = Math.max(0, host.viewHeight() - menuHeight);
			context.resetTransform();
			context.translate(host.viewWidth() * 0.5f,
					menuHeight + contentHeight * 0.5f);
			context.scale(host.zoom());
			context.translate(-DESIGN_WIDTH * 0.5f,
					-DESIGN_HEIGHT * 0.5f);
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	static final class ShowcaseSurface extends GLContainer implements GLKeyTarget {
		private static final GLIcon ACTION_ICON = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/test_icon.svg");
		private static final GLIcon LINK_ICON = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/link_icon.svg");
		private static final Color SURFACE = new Color(24, 31, 45);
		private static final Color CARD = new Color(34, 43, 60);
		private static final Color CARD_BORDER = new Color(66, 81, 108);
		private static final GLLabelStyle TITLE_STYLE =
				GLLabelStyle.DEFAULT
						.withFont(new GLFontSpec("SansSerif", java.awt.Font.BOLD, 25))
						.withTextColor(new Color(242, 247, 255));
		private static final GLLabelStyle SUBTITLE_STYLE =
				GLLabelStyle.DEFAULT
						.withFont(new GLFontSpec("SansSerif", java.awt.Font.PLAIN, 13))
						.withTextColor(new Color(157, 174, 199));
		private static final GLLabelStyle SECTION_STYLE =
				GLLabelStyle.DEFAULT
						.withFont(new GLFontSpec("SansSerif", java.awt.Font.BOLD, 17))
						.withTextColor(new Color(226, 237, 252));
		private static final GLLabelStyle CAPTION_STYLE =
				GLLabelStyle.DEFAULT
						.withFont(new GLFontSpec("SansSerif", java.awt.Font.PLAIN, 11))
						.withTextColor(new Color(145, 163, 189));

		private final ShowcaseHost host;
		private final GLMenuBar menuBar = new GLMenuBar();
		private final PassiveLayer headerLayer = new PassiveLayer();
		private final PassiveLayer controlsPage = new PassiveLayer();
		private final PassiveLayer codePage = new PassiveLayer();
		private final PassiveLayer containersPage = new PassiveLayer();
		private final PassiveLayer filesPage = new PassiveLayer();
		private final PassiveLayer nodeEditorPage = new PassiveLayer();
		private final GLTabbedPane workspaceTabs = new GLTabbedPane();
		private final GLPopupMenu fileMenu = new GLPopupMenu();
		private final GLPopupMenu editMenu = new GLPopupMenu();
		private final GLPopupMenu viewMenu = new GLPopupMenu();
		private final GLPopupMenu helpMenu = new GLPopupMenu();
		private final GLPopupMenu languageMenu = new GLPopupMenu();
		private final GLPopupMenu themeMenu = new GLPopupMenu();
		private final GLPopupMenu standalonePopup = new GLPopupMenu();
		private final GLComboBox<String> normalCombo =
				new GLComboBox<>(List.of("Rot", "Gruen", "Blau", "Orange"));
		private final GLSlider fixedSlider = GLSlider.fixed("Deckkraft", 0, 100, 35);
		private final GLSlider freeSlider = GLSlider.free("Offset", 12);
		private final GLSlider themeRadiusSlider =
				GLSlider.fixed("Rundung", 0, 16, 4);
		private final GLSlider renderResolutionSlider =
				GLSlider.fixed("Aufloesung", 25, 100, 100);
		private GLMenuBarItem languageBarItem;
		private GLMenuBarItem themeBarItem;
		private GLMenuItem englishItem;
		private GLMenuItem germanItem;
		private GLMenuItem colorsMenuItem;
		private GLMenuItem metricsMenuItem;
		private GLMenuItem darkColorsItem;
		private GLMenuItem lightColorsItem;
		private GLMenuItem showcaseColorsItem;
		private GLMenuItem defaultMetricsItem;
		private GLMenuItem extremeMetricsItem;
		private final GLActionMap applicationActions = new GLActionMap();
		private GLAction undoAction;
		private GLAction redoAction;
		private GLAction saveAction;
		private GLAction saveAsAction;
		private GLAction saveAllAction;
		private GLAction closeDocumentAction;
		private boolean syncingThemeRadius;
		private GLCodeEditor codeEditor;
		private final GLTabbedPane codeTabs = new GLTabbedPane();
		private final GLDocumentWorkspace documentWorkspace;
		private final GLDocumentTabController codeDocuments;
		private final GLDocumentCodecRegistry documentCodecs = new GLDocumentCodecRegistry();
		private final GLPlainTextDocumentCodec sourceDocumentCodec =
				new GLPlainTextDocumentCodec("glswing.source-text", "java", "gltheme",
						"glinput", "properties", "txt", "md");
		private final GLDocumentManager documentManager;
		private final GLDocumentLifecycle<GLTextDocument> documentLifecycle;
		private final Map<URI, GLCodeEditor> editorsByUri = new HashMap<>();
		private GLLabel lspStatus;
		private GLButton installLspButton;
		private GLButton selectLspButton;
		private final GLLanguageServerRegistry languageServerRegistry =
				GLLanguageServerRegistry.standard();
		private final GLLanguageServerProfile languageServerProfile = languageServerRegistry
				.forLanguage("java").orElseThrow();
		private volatile GLLanguageServer languageServer;
		private volatile boolean languageServerClosing;
		private final GLFileDialogs fileDialogs = GLFileDialogs.auto();
		private final GLFileDialogs nativeFileDialogs = GLFileDialogs.nativeDialogs();
		private final GLFileDialogs themedFileDialogs = GLFileDialogs.themed();

		ShowcaseSurface(ShowcaseHost host) {
			this.host = host;
			documentWorkspace = new GLDocumentWorkspace(host.historyManager());
			codeDocuments = new GLDocumentTabController(codeTabs, documentWorkspace);
			documentCodecs.register(sourceDocumentCodec);
			documentManager = new GLDocumentManager(documentCodecs);
			documentLifecycle = new GLDocumentLifecycle<>(documentManager,
					sourceDocumentCodec, codeDocuments, fileDialogs, host.parent(),
					() -> new GLTextDocument("public final class Untitled {\n\n}\n"),
					this::createJavaDocumentView);
			documentLifecycle.setCloseRequestHandler(this::confirmDocumentClose);
			configureDocumentDialogText();
			headerLayer.setBounds(0, 0, DESIGN_WIDTH, 105);
			addGL(headerLayer);
			buildMenuBar();
			buildHeaderLayer();
			buildBasicsLayer();
			buildValueLayer();
			buildSelectionLayer();
			buildContainersPage();
			buildFilesPage();
			buildNodeEditorPage();
			buildCodePage();
			workspaceTabs.addTab("Controls", controlsPage);
			workspaceTabs.addTab("Menus & Containers", containersPage);
			workspaceTabs.addTab("Files", filesPage);
			workspaceTabs.addTab("Node Editor", nodeEditorPage);
			workspaceTabs.addTab("Code Editor", codePage);
			workspaceTabs.addSelectionListener((source, oldIndex, newIndex) -> {
				closeTransientPopups();
				codeDocuments.setActive(source.getComponentAt(newIndex) == codePage);
				host.report("Showcase: " + source.getTitleAt(newIndex));
			});
			workspaceTabs.setBounds(12, 105, DESIGN_WIDTH - 24,
					DESIGN_HEIGHT - 111);
			addGL(workspaceTabs);
			codeDocuments.setActive(workspaceTabs.getSelectedComponent() == codePage);
			documentWorkspace.addListener(workspace -> updateHistoryActions());
			documentLifecycle.addListener(lifecycle -> updateDocumentActions());
			updateHistoryActions();
			updateDocumentActions();
		}

		private GLContainer createJavaDocumentView(GLDocument<GLTextDocument> document) {
			GLCodeEditor editor = document.location() == null
					? new GLCodeEditor(document.content())
					: new GLCodeEditor(document.content(), document.location());
			if (editor.getLanguage() == GLCodeLanguages.JAVA) configureCodeEditor(editor);
			else editor.setNavigationHandler(this::openDefinition);
			if (document.location() != null) editorsByUri.put(document.location(), editor);
			return editor;
		}

		private void buildCodePage() {
			lspStatus = new GLLabel("Core diagnostics active · Looking for Java LSP …");
			lspStatus.setBounds(18, 14, 690, 30);
			codePage.addGL(lspStatus);
			installLspButton = new GLButton("Install Java LSP");
			installLspButton.setBounds(720, 14, 190, 30);
			installLspButton.addActionListener(event -> installLanguageServer());
			codePage.addGL(installLspButton);
			selectLspButton = new GLButton("Select installation …");
			selectLspButton.setBounds(920, 14, 208, 30);
			selectLspButton.addActionListener(event -> selectLanguageServer());
			codePage.addGL(selectLspButton);
			Path initialPath = showcaseWorkspace().resolve("glswing-examples/src/main/java/"
					+ "com/vingame/glswing/demo/HelloGLSwing.java");
			String initialSource;
			try { initialSource = Files.readString(initialPath); }
			catch (java.io.IOException failure) {
				throw new IllegalStateException("Cannot load showcase source", failure);
			}
			codeEditor = new GLCodeEditor(initialSource);
			configureCodeEditor(codeEditor);
			URI initialUri = initialPath.toUri();
			addCodeDocumentTab("HelloGLSwing.java", initialUri, codeEditor);
			editorsByUri.put(initialUri, codeEditor);
			addThemeEditor(showcaseThemeDirectory().resolve("showcase-bundle.gltheme"));
			addConfigurationEditor(showcaseWorkspace().resolve(
					"glswing-core/src/main/resources/com/vingame/glswing/input/default.glinput"));
			addConfigurationEditor(showcaseWorkspace().resolve(
					"glswing-core/src/main/resources/com/vingame/glswing/languages/Messages_de.properties"));
			codeTabs.addSelectionListener((source, oldIndex, newIndex) ->
					updateCodeTabStatus(newIndex));
			codeTabs.setBounds(18, 50, 1110, 513);
			codeTabs.setTabsClosable(true);
			codeTabs.setClosedTabHistoryEnabled(true);
			codePage.addGL(codeTabs);
			if (!lspCommand.isEmpty()) startLanguageServer(lspCommand);
			else languageServerProfile.findInstallation().ifPresentOrElse(installation -> {
				try { startLanguageServer(languageServerProfile.command(
						installation, showcaseWorkspace())); }
				catch (java.io.IOException failure) { showLanguageServerFailure(failure); }
			}, () -> {
				lspStatus.setText("Core diagnostics active · Java LSP is not installed");
				showLanguageServerActions(true);
			});
		}

		private void updateCodeTabStatus(int index) {
			if (index < 0 || index >= codeTabs.getTabCount()
					|| !(codeTabs.getComponentAt(index) instanceof GLCodeEditor editor)) return;
			if (editor.getLanguage() == GLCodeLanguages.GLTHEME)
				lspStatus.setText("GLTheme · Built-in schema intelligence active");
			else if (editor.getLanguage() == GLCodeLanguages.GLINPUT)
				lspStatus.setText("GLInput · Built-in binding intelligence active");
			else if (editor.getLanguage() == GLCodeLanguages.LOCALIZATION)
				lspStatus.setText("Localization · Root-bundle validation active");
			else if (languageServer != null)
				lspStatus.setText("Java LSP · Connected");
			else lspStatus.setText("Core diagnostics active · Java LSP is not connected");
		}

		private void addThemeEditor(Path path) {
			addConfigurationEditor(path);
		}

		private void addConfigurationEditor(Path path) {
			try {
				URI uri = path.toAbsolutePath().normalize().toUri();
				GLCodeEditor editor = new GLCodeEditor(Files.readString(path), uri);
				editor.setNavigationHandler(this::openDefinition);
				addCodeDocumentTab(path.getFileName().toString(), uri, editor);
				editorsByUri.put(uri, editor);
			} catch (java.io.IOException failure) {
				lspStatus.setText("Configuration · Cannot load showcase file: "
						+ failure.getMessage());
			}
		}

		private void configureCodeEditor(GLCodeEditor editor) {
			editor.setLanguage(GLCodeLanguages.JAVA);
			editor.setNavigationHandler(this::openDefinition);
			editor.setNavigationStatusListener(status -> {
				switch (status.state()) {
				case REQUESTING -> lspStatus.setText("Java LSP · Looking up definition …");
				case AVAILABLE -> lspStatus.setText("Java LSP · " + status.targetCount()
						+ (status.targetCount() == 1 ? " definition found" : " definitions found"));
				case EMPTY -> lspStatus.setText("Java LSP · No definition at this position");
				case FAILED -> lspStatus.setText("Java LSP · Definition lookup failed: "
						+ status.failure().getMessage());
				}
			});
			editor.setCompletionStatusListener(status -> {
				switch (status.state()) {
				case REQUESTING -> lspStatus.setText("Java LSP · Requesting completion …");
				case AVAILABLE -> lspStatus.setText("Java LSP · " + status.proposalCount()
						+ " completion proposals");
				case EMPTY -> lspStatus.setText("Java LSP · No completion proposals at the caret");
				case FAILED -> lspStatus.setText("Java LSP · Completion failed: "
						+ status.failure().getMessage());
				}
			});
			editor.setSemanticStatusListener(status -> {
				switch (status.state()) {
				case AVAILABLE -> lspStatus.setText("Java LSP · Semantic highlighting active · "
						+ status.tokenCount() + " classifications");
				case EMPTY -> lspStatus.setText(
						"Java LSP · Lexer highlighting active · no semantic classifications");
				case FAILED -> lspStatus.setText(
						"Java LSP · Semantic highlighting unavailable: "
								+ status.failure().getMessage());
				case REQUESTING -> lspStatus.setText(
						"Java LSP · Loading semantic classifications …");
				}
			});
		}

		private void openDefinition(com.vingame.glswing.ui.GLCodeLocation location) {
			GLCodeEditor existing = editorsByUri.get(location.uri());
			int existingTab = existing == null ? -1 : codeTabs.indexOfComponent(existing);
			if (existing != null && existingTab < 0) {
				if (codeTabs.reopenTab(existing))
					existingTab = codeTabs.indexOfComponent(existing);
				else {
					editorsByUri.remove(location.uri());
					existing = null;
				}
			}
			if (existing != null) {
				codeTabs.setSelectedIndex(existingTab);
				moveTo(existing, location.line(), location.character());
				lspStatus.setText(languageLabel(existing) + " · Definition is in "
						+ tabName(location.uri()));
				return;
			}
			com.vingame.glswing.ui.GLCodeLanguage targetLanguage =
					GLCodeLanguages.forUri(location.uri()).orElse(null);
			if (targetLanguage == GLCodeLanguages.GLTHEME
					|| targetLanguage == GLCodeLanguages.GLINPUT
					|| targetLanguage == GLCodeLanguages.LOCALIZATION) {
				openLocalConfigurationSource(location);
				return;
			}
			openProfileSource(location);
		}

		private void openLocalConfigurationSource(
				com.vingame.glswing.ui.GLCodeLocation location) {
			try {
				Path path = Path.of(location.uri());
				GLCodeEditor editor = new GLCodeEditor(Files.readString(path), location.uri());
				editor.setNavigationHandler(this::openDefinition);
				int tab = addCodeDocumentTab(tabName(location.uri()), location.uri(), editor);
				editorsByUri.put(location.uri(), editor);
				codeTabs.setSelectedIndex(tab);
				moveTo(editor, location.line(), location.character());
				lspStatus.setText(languageLabel(editor) + " · Definition is in "
						+ tabName(location.uri()));
			} catch (RuntimeException | java.io.IOException failure) {
				lspStatus.setText("Configuration · Cannot load source: "
						+ failure.getMessage());
			}
		}

		private static String languageLabel(GLCodeEditor editor) {
			if (editor.getLanguage() == GLCodeLanguages.GLTHEME) return "GLTheme";
			if (editor.getLanguage() == GLCodeLanguages.GLINPUT) return "GLInput";
			if (editor.getLanguage() == GLCodeLanguages.LOCALIZATION)
				return "Localization";
			return "Java LSP";
		}

		private void openProfileSource(com.vingame.glswing.ui.GLCodeLocation location) {
			GLLanguageServer current = languageServer;
			if (current == null) {
				lspStatus.setText("Java LSP · Language server is not connected");
				return;
			}
			lspStatus.setText("Java LSP · Loading source …");
			languageServerProfile.loadSource(current, location.uri()).whenComplete((source, failure) ->
					openSourceViewer(location, source, failure));
		}

		private void openSourceViewer(com.vingame.glswing.ui.GLCodeLocation location,
				String source, Throwable failure) {
			if (failure != null || source == null || source.isBlank()) {
				SwingUtilities.invokeLater(() -> lspStatus.setText("Java LSP · Cannot load source"
						+ (failure == null ? "" : ": " + failure.getMessage())));
				return;
			}
			java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				GLCodeEditor editor = new GLCodeEditor(source, false);
				editor.setEditable(false);
				editor.setNavigationHandler(this::openDefinition);
				return editor;
			}).whenComplete((editor, constructionFailure) ->
					SwingUtilities.invokeLater(() -> {
						if (constructionFailure != null) {
							lspStatus.setText("Java LSP · Cannot create source viewer: "
									+ constructionFailure.getMessage());
							return;
						}
						GLLanguageServer current = languageServer;
						if (current != null)
							editor.setDiagnosticProvider(
									(document, listener) -> current.view(document, listener),
									location.uri());
						int tab = addCodeDocumentTab(
								tabName(location.uri()), location.uri(), editor);
						editorsByUri.put(location.uri(), editor);
						codeTabs.setSelectedIndex(tab);
						moveTo(editor, location.line(), location.character());
						lspStatus.setText("Java LSP · Source opened");
					}));
		}

		private static String tabName(URI uri) {
			String value = uri.getPath();
			if (value == null || value.isBlank()) value = uri.getSchemeSpecificPart();
			int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
			String name = slash >= 0 ? value.substring(slash + 1) : value;
			int query = name.indexOf('?');
			if (query >= 0) name = name.substring(0, query);
			return name.isBlank() ? "Library source" : name;
		}

		private int addCodeDocumentTab(String title, URI uri, GLCodeEditor editor) {
			GLDocument<GLTextDocument> document = new GLDocument<>(
					editor.getDocument(), title, uri, new GLHistoryManager());
			document.setReadOnly(!editor.isEditable());
			documentManager.manage(document, sourceDocumentCodec);
			return codeDocuments.addDocument(document, editor);
		}

		private static void moveTo(GLCodeEditor editor, int line, int character) {
			if (line < 0 || line >= editor.getDocument().getLineCount()) return;
			int start = editor.getDocument().getLineStart(line);
			int end = editor.getDocument().getLineEnd(line);
			editor.setCaretPosition(Math.min(end, start + Math.max(0, character)));
		}

		private void startLanguageServer(List<String> command) {
			if (!SwingUtilities.isEventDispatchThread()) {
				SwingUtilities.invokeLater(() -> startLanguageServer(command));
				return;
			}
			showLanguageServerActions(false);
			lspStatus.setText("Core diagnostics active · Starting Java LSP …");
			Thread starter = new Thread(() -> {
				try {
					Path workspace = showcaseWorkspace();
					GLLanguageServer started = new GLLanguageServer(command, workspace,
							languageServerProfile.initializationOptions());
					if (languageServerClosing) { started.close(); return; }
					languageServer = started;
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						Path document = workspace.resolve("glswing-examples/src/main/java/"
								+ "com/vingame/glswing/demo/HelloGLSwing.java");
						lspStatus.setText("Core diagnostics active · LSP connected: "
								+ Path.of(command.get(0)).getFileName());
						codeEditor.setDiagnosticProvider(started, document.toUri());
						host.report("Language server connected");
					});
				} catch (RuntimeException | java.io.IOException failure) { showLanguageServerFailure(failure); }
			}, "glswing-showcase-lsp");
			starter.setDaemon(true);
			starter.start();
		}

		private void installLanguageServer() {
			showLanguageServerActions(false);
			Thread installer = new Thread(() -> {
				try {
					Path installation = languageServerProfile.install(message ->
							SwingUtilities.invokeLater(() -> lspStatus.setText(message)));
					startLanguageServer(languageServerProfile.command(
							installation, showcaseWorkspace()));
				} catch (RuntimeException | java.io.IOException | InterruptedException failure) {
					if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
					showLanguageServerFailure(failure);
				}
			}, "glswing-showcase-lsp-install");
			installer.setDaemon(true);
			installer.start();
		}

		private void selectLanguageServer() {
			fileDialogs.show(GLFileDialogRequest.open()
					.title("Select extracted Eclipse JDT Language Server")
					.selectionMode(GLFileSelectionMode.DIRECTORIES)
					.build(), host.parent()).whenComplete((result, failure) -> {
				if (failure != null) {
					showLanguageServerFailure(failure);
					return;
				}
				if (!result.approved()) return;
				try {
					Path installation = result.selectedPath();
					languageServerProfile.selectInstallation(installation);
					startLanguageServer(languageServerProfile.command(
							installation, showcaseWorkspace()));
				} catch (RuntimeException | java.io.IOException error) {
					showLanguageServerFailure(error);
				}
			});
		}

		private void showLanguageServerFailure(Throwable failure) {
			SwingUtilities.invokeLater(() -> {
				lspStatus.setText("Core diagnostics active · LSP failed: " + failure.getMessage());
				showLanguageServerActions(true);
			});
		}

		private void showLanguageServerActions(boolean visible) {
			installLspButton.setVisible(visible
					&& languageServerProfile.supportsManagedInstallation());
			selectLspButton.setVisible(visible
					&& languageServerProfile.supportsInstallationSelection());
		}

		private static Path showcaseWorkspace() {
			Path directory = Path.of("").toAbsolutePath().normalize();
			for (int depth = 0; directory != null && depth < 8;
					depth++, directory = directory.getParent()) {
				if (Files.isRegularFile(directory.resolve("pom.xml"))
						&& Files.isRegularFile(directory.resolve("glswing-examples/pom.xml")))
					return directory;
			}
			return Path.of("").toAbsolutePath().normalize();
		}

		void closeLanguageServer() {
			languageServerClosing = true;
			documentLifecycle.close();
			codeDocuments.close();
			documentWorkspace.close();
			documentManager.close();
			fileDialogs.close();
			nativeFileDialogs.close();
			themedFileDialogs.close();
			for (GLCodeEditor editor : editorsByUri.values())
				editor.clearDiagnosticProvider();
			GLLanguageServer current = languageServer;
			if (current == null) return;
			languageServer = null;
			Thread closer = new Thread(current::close, "glswing-showcase-lsp-close");
			closer.setDaemon(true);
			closer.start();
		}

		java.util.concurrent.CompletionStage<Boolean> prepareClose() {
			return documentLifecycle.prepareCloseAll();
		}

		private void buildHeaderLayer() {
			addLabel(headerLayer, "GLSwingNext GUI Showcase", TITLE_STYLE,
					28, 43, 500, 34);
			addLabel(headerLayer,
					"Feste 1180 x 760 Designflaeche - alle Zustandswechsel sind interaktiv",
					SUBTITLE_STYLE, 30, 75, 490, 22);
			GLButton resetZoom = new GLButton("Zoom 100 %");
			resetZoom.addActionListener(event -> host.setZoom(1));
			addControl(headerLayer, resetZoom, 1015, 52, 130, 26);
			themeRadiusSlider.setStep(1);
			themeRadiusSlider.setDefaultValue(4);
			themeRadiusSlider.setValueFormatter(
					value -> String.format("%.1f px", value));
			themeRadiusSlider.addChangeListener(event -> {
				if (!syncingThemeRadius)
					host.setThemeCornerRadius((float) themeRadiusSlider.getValue());
			});
			addControl(headerLayer, themeRadiusSlider, 765, 52, 225, 26);
			renderResolutionSlider.setStep(5);
			renderResolutionSlider.setDefaultValue(100);
			renderResolutionSlider.setValueFormatter(
					value -> String.format("%.0f %%", value));
			renderResolutionSlider.addChangeListener(event ->
					host.setRenderResolutionScale(
							(float) renderResolutionSlider.getValue() / 100f));
			addControl(headerLayer, renderResolutionSlider, 535, 52, 215, 26);
		}

		private void buildMenuBar() {
			GLAction createAction = GLAction.builder(GLInputActions.DOCUMENT_NEW)
					.textKey("showcase.action.document.new")
					.shortDescriptionKey("showcase.action.document.new.description")
					.icon(ACTION_ICON)
					.onAction(() -> {
						documentLifecycle.newDocument();
						showCodeWorkspace();
						host.report("New Java document created");
					})
					.build();
			GLAction openAction = GLAction.builder(GLInputActions.DOCUMENT_OPEN)
					.textKey("showcase.action.document.open")
					.shortDescriptionKey("showcase.action.document.open.description")
					.icon(LINK_ICON)
					.onAction(event -> documentLifecycle.open().whenComplete((document, failure) ->
							SwingUtilities.invokeLater(() -> {
								if (failure != null) host.report("Open failed: " + failure.getMessage());
								else if (document != null) { showCodeWorkspace(); host.report("Opened " + document.title()); }
							})))
					.build();
			saveAction = GLAction.builder(GLInputActions.DOCUMENT_SAVE)
					.textKey("showcase.action.document.save")
					.shortDescriptionKey("showcase.action.document.save.description")
					.enabledWhen(documentLifecycle::canSave)
					.onAction(event -> saveActiveDocument(false))
					.build();
			saveAsAction = GLAction.builder(GLInputActions.DOCUMENT_SAVE_AS)
					.textKey("showcase.action.document.saveAs")
					.shortDescriptionKey("showcase.action.document.saveAs.description")
					.enabledWhen(documentLifecycle::canSaveAs)
					.onAction(event -> saveActiveDocument(true))
					.build();
			saveAllAction = GLAction.builder(GLInputActions.DOCUMENT_SAVE_ALL)
					.textKey("showcase.action.document.saveAll")
					.shortDescriptionKey("showcase.action.document.saveAll.description")
					.enabledWhen(documentLifecycle::canSaveAll)
					.onAction(event -> documentLifecycle.saveAll()
							.whenComplete((paths, failure) ->
									reportDocumentOperation("Save all", failure)))
					.build();
			closeDocumentAction = GLAction.builder(GLInputActions.DOCUMENT_CLOSE)
					.textKey("showcase.action.document.close")
					.shortDescriptionKey("showcase.action.document.close.description")
					.enabledWhen(documentLifecycle::canClose)
					.onAction(event -> documentLifecycle.closeActiveDocument())
					.build();
			applicationActions.add(createAction).add(openAction).add(saveAction)
					.add(saveAsAction).add(saveAllAction).add(closeDocumentAction);
			fileMenu.addItem(createAction);
			fileMenu.addItem(openAction);
			fileMenu.addItem(saveAction);
			fileMenu.addItem(saveAsAction);
			fileMenu.addItem(saveAllAction);
			fileMenu.addItem(closeDocumentAction);
			fileMenu.addSeparator();
			GLPopupMenu openDialogs = new GLPopupMenu();
			openDialogs.addItem("Systemdialog ...",
					event -> showOpenDialog(nativeFileDialogs, "Native"));
			openDialogs.addItem("GLSwing-Dialog ...",
					event -> showOpenDialog(themedFileDialogs, "Themed"));
			GLMenuItem open = fileMenu.addSubMenu("Dateidialoge testen", openDialogs);
			open.setIcon(LINK_ICON);
			GLPopupMenu saveDialogs = new GLPopupMenu();
			saveDialogs.addItem("Systemdialog ...",
					event -> showSaveDialog(nativeFileDialogs, "Native"));
			saveDialogs.addItem("GLSwing-Dialog ...",
					event -> showSaveDialog(themedFileDialogs, "Themed"));
			GLMenuItem saveAs = fileMenu.addSubMenu("Speicherdialoge testen", saveDialogs);
			saveAs.setIcon(ACTION_ICON);
			fileMenu.addSeparator();
			GLPopupMenu export = new GLPopupMenu();
			export.addItem("PNG",
					event -> host.report("Export > PNG"));
			export.addItem("SVG",
					event -> host.report("Export > SVG"));
			GLMenuItem exportItem = fileMenu.addSubMenu("Exportieren", export);
			exportItem.setIcon(LINK_ICON);
			GLMenuItem unavailable = fileMenu.addItem("Nicht verfuegbar");
			unavailable.setEnabled(false);

			undoAction = GLAction.builder(GLInputActions.HISTORY_UNDO)
					.textKey("showcase.action.history.undo")
					.enabledWhen(documentWorkspace::canUndo)
					.onAction(() -> documentWorkspace.undo()).build();
			redoAction = GLAction.builder(GLInputActions.HISTORY_REDO)
					.textKey("showcase.action.history.redo")
					.enabledWhen(documentWorkspace::canRedo)
					.onAction(() -> documentWorkspace.redo()).build();
			applicationActions.add(undoAction).add(redoAction);
			editMenu.addItem(undoAction);
			editMenu.addItem(redoAction);

			viewMenu.addItem("Zoom 100 %",
					event -> host.setZoom(1));
			viewMenu.addItem("Zoom 150 %",
					event -> host.setZoom(1.5f));
			viewMenu.addItem("Zoom 75 %",
					event -> host.setZoom(0.75f));
			helpMenu.addItem("Ueber GLSwingNext",
					event -> host.report("GPU-gerenderte Lightweight-GUI"));

			menuBar.addMenu("Datei", fileMenu);
			menuBar.addMenu("Bearbeiten", editMenu);
			menuBar.addMenu("Ansicht", viewMenu);
			menuBar.addMenu("Hilfe", helpMenu);
			englishItem = languageMenu.addItem("English",
					event -> host.setLanguage(Locale.ENGLISH));
			germanItem = languageMenu.addItem("Deutsch",
					event -> host.setLanguage(Locale.GERMAN));
			languageBarItem = menuBar.addMenu("Sprache", languageMenu);
			languageBarItem.setIcon(ACTION_ICON);
			GLPopupMenu colorsMenu = new GLPopupMenu();
			darkColorsItem = colorsMenu.addItem("Dunkel",
					event -> host.selectShowcaseColors(
						"showcase-dark-colors.gltheme",
						"showcase.theme.colors.dark"));
			lightColorsItem = colorsMenu.addItem("Hell",
					event -> host.selectShowcaseColors(
						"showcase-light-colors.gltheme",
						"showcase.theme.colors.light"));
			showcaseColorsItem = colorsMenu.addItem("Orange + VT323",
					event -> host.selectShowcaseColors("showcase-colors.gltheme",
						"showcase.theme.colors.showcase"));
			colorsMenuItem = themeMenu.addSubMenu("Farben", colorsMenu);
			GLPopupMenu metricsMenu = new GLPopupMenu();
			defaultMetricsItem = metricsMenu.addItem("Standard",
					event -> host.selectShowcaseMetrics(
						"showcase-default-metrics.gltheme",
						"showcase.theme.metrics.default"));
			extremeMetricsItem = metricsMenu.addItem("Extrem",
					event -> host.selectShowcaseMetrics("showcase-metrics.gltheme",
						"showcase.theme.metrics.extreme"));
			metricsMenuItem = themeMenu.addSubMenu("Metriken", metricsMenu);
			themeBarItem = menuBar.addMenu("Theme", themeMenu);
			themeBarItem.setIcon(LINK_ICON);
			GLPopupMenu disabledMenu = new GLPopupMenu();
			var disabledItem = menuBar.addMenu("Deaktiviert", disabledMenu);
			disabledItem.setEnabled(false);
		}

		private void showCodeWorkspace() {
			int index = workspaceTabs.indexOfComponent(codePage);
			if (index >= 0) workspaceTabs.setSelectedIndex(index);
		}

		private java.util.concurrent.CompletionStage<Path> saveActiveDocument(boolean saveAs) {
			return (saveAs ? documentLifecycle.saveAs() : documentLifecycle.save())
					.whenComplete((path, failure) -> reportDocumentOperation("Save", failure));
		}

		@Override public void keyPressed(GLKeyEvent event) {
			applicationActions.handleKeyPressed(event, getGLInputMap(), this);
		}

		@Override public boolean isFocusTraversable() { return false; }

		private void reportDocumentOperation(String operation, Throwable failure) {
			SwingUtilities.invokeLater(() -> host.report(failure == null
					? operation + " completed" : operation + " failed: " + failure.getMessage()));
		}

		private java.util.concurrent.CompletionStage<GLDocumentCloseChoice>
				confirmDocumentClose(GLDocument<?> document) {
			GLLocalizer text = host.localizer();
			java.util.concurrent.CompletableFuture<GLDocumentCloseChoice> result =
					new java.util.concurrent.CompletableFuture<>();
			AtomicReference<GLDialog> dialogReference = new AtomicReference<>();
			GLContainer content = new GLContainer() {
				private final GLLabel message = new GLLabel(text.text(
						"showcase.document.close.message", document.title()));
				private final GLButton save = new GLButton(text.text(
						"showcase.document.close.save"));
				private final GLButton discard = new GLButton(text.text(
						"showcase.document.close.discard"));
				private final GLButton cancel = new GLButton(text.text(
						"showcase.document.close.cancel"));
				{
					addGL(message); addGL(save); addGL(discard); addGL(cancel);
					save.addActionListener(event -> finish(GLDocumentCloseChoice.SAVE));
					discard.addActionListener(event -> finish(GLDocumentCloseChoice.DISCARD));
					cancel.addActionListener(event -> finish(GLDocumentCloseChoice.CANCEL));
				}
				private void finish(GLDocumentCloseChoice choice) {
					if (result.complete(choice)) dialogReference.get().close();
				}
				@Override protected void record(GLContext2D context) { }
				@Override protected void resized(float width, float height) {
					message.setBounds(22, 24, Math.max(1, width - 44), 32);
					cancel.setBounds(width - 104, height - 48, 82, 30);
					discard.setBounds(width - 206, height - 48, 92, 30);
					save.setBounds(width - 298, height - 48, 82, 30);
				}
			};
			GLDialog dialog = new GLDialog(text.text("showcase.document.close.title"), content);
			dialog.setSize(470, 150); dialogReference.set(dialog);
			dialog.closedFuture().whenComplete((unused, failure) ->
					result.complete(GLDocumentCloseChoice.CANCEL));
			try { dialog.show(host.parent()); }
			catch (RuntimeException failure) { result.completeExceptionally(failure); }
			return result;
		}

		private void showOpenDialog(GLFileDialogs dialogs, String backendName) {
			dialogs.show(GLFileDialogRequest.open().title("Open source file")
					.multiple(true)
					.filter("Java source", "java")
					.filter("All text files", "txt", "md")
					.filter("All files")
					.build(), host.parent()).whenComplete((result, failure) ->
					SwingUtilities.invokeLater(() -> host.report(backendName + ": "
							+ (failure != null ? "file dialog failed: " + failure.getMessage()
							: result.approved() ? "selected " + result.selectedPaths()
									: "file dialog cancelled"))));
		}

		private void showSaveDialog(GLFileDialogs dialogs, String backendName) {
			dialogs.show(GLFileDialogRequest.save().title("Save example")
					.suggestedFileName("example.java")
					.filter("Java source", "java")
					.filter("All files")
					.build(), host.parent()).whenComplete((result, failure) ->
					SwingUtilities.invokeLater(() -> host.report(backendName + ": "
							+ (failure != null ? "file dialog failed: " + failure.getMessage()
							: result.approved() ? "save target " + result.selectedPath()
									: "file dialog cancelled"))));
		}

		private void updateHistoryActions() {
			if (undoAction == null || redoAction == null)
				return;
			GLLocalizer text = host.localizer();
			String undo = text.text("showcase.action.history.undo");
			String redo = text.text("showcase.action.history.redo");
			undoAction.setText(documentWorkspace.canUndo()
					? undo + ": " + documentWorkspace.undoName() : undo);
			redoAction.setText(documentWorkspace.canRedo()
					? redo + ": " + documentWorkspace.redoName() : redo);
			undoAction.refreshEnabled();
			redoAction.refreshEnabled();
		}

		private void updateDocumentActions() {
			applicationActions.refreshEnabled();
		}

		void updateLocalizedMenuText() {
			GLLocalizer text = host.localizer();
			configureDocumentDialogText();
			languageBarItem.setText(text.text("showcase.menu.language"));
			themeBarItem.setText(text.text("showcase.menu.theme"));
			englishItem.setText(text.text("showcase.language.english"));
			germanItem.setText(text.text("showcase.language.german"));
			colorsMenuItem.setText(text.text("showcase.theme.colors"));
			metricsMenuItem.setText(text.text("showcase.theme.metrics"));
			darkColorsItem.setText(text.text("showcase.theme.colors.dark"));
			lightColorsItem.setText(text.text("showcase.theme.colors.light"));
			showcaseColorsItem.setText(
					text.text("showcase.theme.colors.showcase"));
			defaultMetricsItem.setText(
					text.text("showcase.theme.metrics.default"));
			extremeMetricsItem.setText(
					text.text("showcase.theme.metrics.extreme"));
			updateHistoryActions();
		}

		private void configureDocumentDialogText() {
			GLLocalizer text = host.localizer();
			documentLifecycle.setDialogText(
					text.text("showcase.document.open.title"),
					text.text("showcase.document.save.title"),
					text.text("showcase.document.format"),
					text.text("showcase.document.allFiles"));
		}

		void syncThemeRadius(float radius) {
			syncingThemeRadius = true;
			try {
				themeRadiusSlider.setValue(radius);
			} finally {
				syncingThemeRadius = false;
			}
		}

		private void buildBasicsLayer() {
			ControlLayer section = controlLayer("Buttons & Checkboxen",
					24, 0, 350, 590, controlsPage);
			addLabel(section, "Normales Label", GLLabelStyle.DEFAULT,
					18, 51, 250, 20);
			addLabel(section, "Sekundaerer Beschreibungstext", SUBTITLE_STYLE,
					18, 78, 290, 20);

			caption(section, "ENABLED / DISABLED", 18, 112);
			GLButton enabled = new GLButton("Aktiver Button");
			enabled.addActionListener(event -> host.report("Aktiver Button geklickt"));
			addControl(section, enabled, 18, 134, 145, 27);
			GLButton disabled = new GLButton("Deaktiviert");
			disabled.setEnabled(false);
			addControl(section, disabled, 178, 134, 145, 27);

			caption(section, "FOCUSABLE / TEXT-ELLIPSIS", 18, 174);
			GLButton focusable = new GLButton("Fokus per Klick und Tastatur");
			focusable.setFocusable(true);
			focusable.addActionListener(event -> host.report("Focusable Button aktiviert"));
			addControl(section, focusable, 18, 196, 305, 27);
			GLButton longText = new GLButton(
					"Dieser Buttontext ist absichtlich deutlich zu lang");
			addControl(section, longText, 18, 235, 210, 27);

			caption(section, "CHECKBOX-ZUSTAENDE", 18, 279);
			GLCheckBox unchecked = new GLCheckBox("Nicht ausgewaehlt");
			unchecked.addActionListener(event -> host.report(
					"Checkbox: " + unchecked.isSelected()));
			addControl(section, unchecked, 18, 302, 200, 22);
			GLCheckBox checked = new GLCheckBox("Ausgewaehlt + focusable", true);
			checked.setFocusable(true);
			addControl(section, checked, 18, 334, 230, 22);
			GLCheckBox disabledOff = new GLCheckBox("Deaktiviert, leer");
			disabledOff.setEnabled(false);
			addControl(section, disabledOff, 18, 366, 200, 22);
			GLCheckBox disabledOn = new GLCheckBox("Deaktiviert, gesetzt", true);
			disabledOn.setEnabled(false);
			addControl(section, disabledOn, 18, 398, 220, 22);

			caption(section, "Hover und Pressed: direkt mit der Maus testen",
					18, 449);
			GLButton action = new GLButton("ActionListener testen");
			action.setActionCommand("showcase.action");
			action.addActionListener(event -> host.report(
					"ActionCommand: " + event.getActionCommand()));
			addControl(section, action, 18, 474, 210, 27);
		}

		private void buildValueLayer() {
			ControlLayer section = controlLayer("Slider & Gruppen",
					410, 0, 350, 590, controlsPage);
			caption(section, "FIXED: Bereich 0 ... 100, Default 50", 18, 50);
			fixedSlider.setDefaultValue(50);
			fixedSlider.setStep(5);
			addControl(section, fixedSlider, 18, 72, 324, 26);

			caption(section, "FIXED DISABLED", 18, 108);
			GLSlider fixedDisabled = GLSlider.fixed("Fortschritt", 0, 1, 0.65);
			fixedDisabled.setEnabled(false);
			addControl(section, fixedDisabled, 18, 130, 324, 26);

			caption(section, "FREE: Shift fein, Strg grob, Rechtsklick-Menue", 18, 166);
			freeSlider.setDefaultValue(0);
			freeSlider.setDragSensitivity(0.1);
			freeSlider.setPreciseDragFactor(0.1);
			freeSlider.setCoarseDragFactor(10);
			freeSlider.getContextMenu().addSeparator();
			freeSlider.getContextMenu().addItem("Wert auf 42 setzen",
					event -> freeSlider.setValue(42));
			addControl(section, freeSlider, 18, 188, 324, 26);

			caption(section, "FREE DISABLED", 18, 224);
			GLSlider freeDisabled = GLSlider.free("Offset", -8);
			freeDisabled.setEnabled(false);
			addControl(section, freeDisabled, 18, 246, 324, 26);

			caption(section, "GRUPPE: vertikal ueberstreichen", 18, 310);
			GLSlider red = GLSlider.fixed("Rot", 0, 1, 128);
			GLSlider green = GLSlider.fixed("Gruen", 0, 1, 96);
			GLSlider blue = GLSlider.fixed("Blau", 0, 1, 160);
			red.setDragSensitivity(1);
			green.setDragSensitivity(1);
			blue.setDragSensitivity(1);
			GLSliderGroup colorGroup = new GLSliderGroup(red, green, blue);
			addControl(section, colorGroup, 18, 334, 314, 72);
			caption(section, "Aenderungen unterstuetzen Undo / Redo", 18, 438);
		}

		private void buildSelectionLayer() {
			ControlLayer section = controlLayer("Text & Auswahl",
					796, 0, 350, 590, controlsPage);
			caption(section, "TEXTEDITOR", 18, 50);
			GLTextEditor normal = new GLTextEditor("Editierbarer Text");
			normal.addActionListener(event -> host.report(
					"Text uebernommen: " + normal.getText()));
			addControl(section, normal, 18, 72, 314, 26);

			caption(section, "ZAHLEN / LETZTEN WERT WIEDERHERSTELLEN", 18, 116);
			GLTextEditor numeric = new GLTextEditor("12.5");
			numeric.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
			numeric.setInputVerifier(ShowcaseSurface::isFiniteNumber);
			numeric.setValidationFailureMode(
					GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
			addControl(section, numeric, 18, 138, 314, 26);

			caption(section, "INVALID / DISABLED", 18, 182);
			GLTextEditor invalid = new GLTextEditor("Ungueltig");
			invalid.setInvalid(true);
			addControl(section, invalid, 18, 204, 190, 26);
			GLTextEditor editorDisabled = new GLTextEditor("Deaktiviert");
			editorDisabled.setEnabled(false);
			addControl(section, editorDisabled, 218, 204, 114, 26);

			caption(section, "COMBOBOX MIT ICONS", 18, 262);
			normalCombo.setPopupTitle("Farbe waehlen");
			normalCombo.setItemPresentationProvider(value ->
					new GLItemPresentation(value,
							value.equals("Blau") || value.equals("Gruen")
									? LINK_ICON : ACTION_ICON));
			normalCombo.getPopupMenu().setTitleIcon(ACTION_ICON);
			normalCombo.addActionListener(event -> host.report(
					"Farbe: " + normalCombo.getSelectedItem()));
			addControl(section, normalCombo, 18, 284, 314, 26);

			caption(section, "DISABLED", 18, 328);
			GLComboBox<String> disabled = new GLComboBox<>(
					List.of("Nicht", "Verfuegbar"));
			disabled.setEnabled(false);
			addControl(section, disabled, 18, 350, 314, 26);

			caption(section, "MEHRZEILIG / SOFT-WRAP / LOKALES UNDO", 18, 394);
			GLTextArea textArea = new GLTextArea(
					"GLTextArea bearbeitet mehrere Zeilen.\n"
					+ "Enter erzeugt Umbrueche; Tab fuegt Einrueckungen ein.\n"
					+ "Fett und kursiv gelten fuer die aktuelle Auswahl.\n"
					+ "Ein Rechtsklick oeffnet Ausrichtung und Formatierung.\n"
					+ "Der Scrollbalken erscheint automatisch bei Ueberlauf.\n"
					+ "Mausrad, Auswahl sowie Undo und Redo bleiben im Editor.");
			textArea.setLineWrap(true);
			textArea.setTabSize(4);
			String sample = textArea.getText();
			int bold = sample.indexOf("Fett");
			textArea.getDocument().setStyle(bold, bold + 4,
					GLTextStyle.PLAIN.withBold(true));
			int italic = sample.indexOf("kursiv");
			textArea.getDocument().setStyle(italic, italic + 6,
					GLTextStyle.PLAIN.withItalic(true));
			addControl(section, textArea, 18, 416, 314, 142);
		}

		private void buildContainersPage() {
			ControlLayer overlays = controlLayer("Menues, Popups & Dialoge",
					100, 28, 450, 500, containersPage);
			buildStandalonePopup();
			GLButton popup = new GLButton("Kontextmenue oeffnen");
			popup.addActionListener(event -> openPopup(popup));
			addControl(overlays, popup, 28, 72, 250, 30);
			GLButton modeless = new GLButton("Modelosen Dialog oeffnen");
			modeless.addActionListener(event -> openShowcaseDialog(
					host.parent(), GLDialog.Modality.MODELESS, 1));
			addControl(overlays, modeless, 28, 132, 250, 30);
			GLButton modal = new GLButton("Owner-modalen Dialog oeffnen");
			modal.addActionListener(event -> openShowcaseDialog(
					host.parent(), GLDialog.Modality.OWNER_MODAL, 1));
			addControl(overlays, modal, 28, 192, 280, 30);
			addLabel(overlays,
					"Die globale Menueleiste demonstriert Icons, Accelerators, Mnemonics, Untermenues sowie Undo und Redo.",
					SUBTITLE_STYLE, 28, 260, 380, 58);

			ControlLayer scrolling = controlLayer("Listen & ScrollPane",
					610, 28, 450, 500, containersPage);
			GLList<String> list = new GLList<>(java.util.stream.IntStream
					.rangeClosed(1, 1_000)
					.mapToObj(index -> "Virtualisierter Eintrag " + index)
					.toList());
			list.getSelectionModel().setSelectionMode(
					GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
			list.addActivationListener((source, item, index) ->
					host.report("Liste aktiviert: " + item));
			GLScrollPane listScroll = new GLScrollPane(list);
			listScroll.setHorizontalScrollBarPolicy(
					GLScrollPane.ScrollBarPolicy.NEVER);
			listScroll.setScrollUnit(list.getRowHeight());
			addControl(scrolling, listScroll, 24, 48, 402, 220);
			caption(scrolling,
					"1.000 Eintraege · Mehrfachauswahl mit Strg/Shift · Doppelklick aktiviert",
					24, 276);

			GLDefaultTreeModel<String> documents = new GLDefaultTreeModel<>();
			documents.addRoot("GLSwingNext");
			documents.addChild("GLSwingNext", "glswing-core");
			documents.addChild("GLSwingNext", "glswing-examples");
			documents.addChild("GLSwingNext", "docs");
			documents.addChild("glswing-core", "src/main/java");
			documents.addChild("glswing-core", "src/main/resources");
			documents.addChild("glswing-examples", "GuiShowcaseDemo.java");
			documents.addChild("docs", "README.md");
			documents.addChild("docs", "ui-framework.md");
			GLTree<String> tree = new GLTree<>(documents);
			tree.getSelectionModel().setSelectionMode(
					GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
			tree.addActivationListener((source, item) -> {
				host.report("Baum aktiviert: " + item);
				source.startEditing(item);
			});
			tree.setCellEditor(new GLTree.CellEditor<>() {
				@Override public GLContainer createEditor(GLTree<String> source,
						String value, GLList.EditController controller) {
					GLTextEditor editor = new GLTextEditor(value);
					editor.addActionListener(event -> controller.commit());
					editor.addCancelListener(event -> controller.cancel());
					return editor;
				}
				@Override public void layoutEditor(GLTree<String> source, String value,
						GLContainer editor, float x, float width, float height) {
					editor.setBounds(x, 2, Math.max(1, width - x - 6), height - 4);
				}
				@Override public boolean commitEditor(GLTree<String> source,
						String value, GLContainer component) {
					String replacement = ((GLTextEditor) component).getText().strip();
					if (replacement.isEmpty() || documents.contains(replacement)) return false;
					documents.replaceNode(value, replacement);
					return true;
				}
			});
			tree.expandAll();
			addControl(scrolling, tree, 24, 310, 402, 142);
			caption(scrolling,
					"Pfeile links/rechts klappen · Doppelklick auf Blaetter benennt um",
					24, 458);
		}

		private void buildFilesPage() {
			GLFileBrowser browser = new GLFileBrowser(showcaseWorkspace());
			browser.setMultipleSelection(true);
			browser.setBounds(18, 54, 1110, 510);
			browser.addDirectoryListener((source, directory) ->
					host.report("Ordner: " + directory));
			browser.addActivationListener((source, file) ->
					host.report("Datei aktiviert: " + file));

			GLLabel searchLabel = new GLLabel("Search");
			searchLabel.setBounds(18, 18, 56, 24);
			filesPage.addGL(searchLabel);
			GLTextEditor search = new GLTextEditor();
			search.setBounds(78, 14, 280, 30);
			search.addTextChangeListener(event -> {
				browser.setSearchQuery(search.getText());
				browser.refresh();
			});
			filesPage.addGL(search);

			GLComboBox<GLFileFilter> filter = new GLComboBox<>(List.of(
					new GLFileFilter("All files"),
					new GLFileFilter("Java source", "java"),
					new GLFileFilter("Images", "png", "jpg", "jpeg", "webp")));
			filter.setItemTextProvider(GLFileFilter::description);
			GLLabel filterLabel = new GLLabel("Filter");
			filterLabel.setBounds(380, 18, 48, 24);
			filesPage.addGL(filterLabel);
			filter.setBounds(432, 14, 220, 30);
			filter.addActionListener(event -> {
				browser.setFileFilter(filter.getSelectedItem());
				browser.refresh();
			});
			filesPage.addGL(filter);

			GLCheckBox hidden = new GLCheckBox("Show hidden files");
			hidden.setBounds(680, 18, 190, 24);
			hidden.addActionListener(event -> {
				browser.setShowHidden(hidden.isSelected());
				browser.refresh();
			});
			filesPage.addGL(hidden);
			filesPage.addGL(browser);
		}

		private void buildNodeEditorPage() {
			GLNodeEditor editor = new GLNodeEditor();
			editor.setBounds(8, 8, 1124, 590);
			nodeEditorPage.addGL(editor);

			GLNode texture = new GLNode("Texture");
			texture.setResizeMode(GLNodeResizeMode.BOTH);
			texture.addInput("scale", GLSlider.fixed("Scale", 0.1, 8, 1));
			texture.addOutput("color", new GLLabel("Mapped Color"));
			editor.addNode(texture, 80, 80);

			GLNode material = new GLNode("Material");
			material.setResizeMode(GLNodeResizeMode.HORIZONTAL);
			material.addInput("roughness",
					GLSlider.fixed("Roughness", 0, 1, 0.42));
			GLComboBox<String> blend = new GLComboBox<>(
					List.of("Linear", "Multiply", "Screen"));
			GLNodePorts base = material.addInputOutput("base", blend, "result");
			base.input().setContentHiddenWhenConnected(true);
			editor.addNode(material, 390, 115);

			GLNode output = new GLNode("Output");
			output.setResizeMode(GLNodeResizeMode.NONE);
			output.addInput("surface", new GLLabel("Surface"));
			output.addInput("alpha", GLSlider.fixed("Alpha", 0, 1, 1));
			editor.addNode(output, 780, 270);

			editor.connect(texture.findPort("color"), material.findPort("base"));
			GLNodePort surface = output.findPort("surface");
			editor.connect(material.findPort("result"), surface);
			editor.setConnectionColorInterpolator(GLColorInterpolation.OKLAB);
		}

		private void openShowcaseDialog(GLParent owner,
				GLDialog.Modality modality, int level) {
			AtomicReference<GLDialog> self = new AtomicReference<>();
			GLContainer content = dialogContent(level, self);
			String title = level == 1 ? "GLSwingNext Dialog"
					: "Verschachtelter Dialog - Ebene " + level;
			GLDialog dialog = new GLDialog(title, content);
			dialog.setSize(520, 300);
			dialog.setModality(modality);
			self.set(dialog);
			dialog.closedFuture().whenComplete((unused, error) -> {
				if (error != null) {
					error.printStackTrace();
					host.report("Dialogfehler: " + error.getMessage());
				} else {
					host.report("Dialog Ebene " + level + " geschlossen");
				}
			});
			try {
				dialog.show(owner);
				host.report((modality == GLDialog.Modality.MODELESS
						? "Modeloser" : "Owner-modaler")
						+ " Dialog wird geoeffnet");
			} catch (RuntimeException error) {
				host.report("Dialog konnte nicht geoeffnet werden: "
						+ error.getMessage());
				error.printStackTrace();
			}
		}

		private GLContainer dialogContent(int level,
				AtomicReference<GLDialog> self) {
			return new GLContainer() {
				{
					GLLabel title = new GLLabel(level == 1
							? "Eigenstaendiger GLSwing-Dialog"
							: "Verschachtelte Dialogebene " + level);
					title.setBounds(28, 28, 430, 28);
					addGL(title);

					GLLabel description = new GLLabel(
							"Verschiebbar, resizable und an sein Besitzerfenster gekoppelt.");
					description.setBounds(28, 69, 460, 24);
					addGL(description);

					GLButton nested = new GLButton("Unterdialog oeffnen");
					nested.setBounds(28, 226, 190, 28);
					nested.addActionListener(event -> openShowcaseDialog(this,
							GLDialog.Modality.OWNER_MODAL, level + 1));
					addGL(nested);

					GLButton close = new GLButton("Schliessen");
					close.setBounds(368, 226, 120, 28);
					close.addActionListener(event -> {
						GLDialog dialog = self.get();
						if (dialog != null)
							dialog.close();
					});
					addGL(close);
				}

				@Override
				protected void record(GLContext2D context) {
				}
			};
		}

		private void buildStandalonePopup() {
			standalonePopup.setTitle("Werkzeuge");
			standalonePopup.setTitleIcon(ACTION_ICON);
			standalonePopup.addItem("Schnellaktion",
					event -> host.report("Popup-Schnellaktion"));
			standalonePopup.addSeparator();
			GLPopupMenu modes = new GLPopupMenu();
			modes.addItem("Einfach",
					event -> host.report("Modus: Einfach"));
			modes.addItem("Erweitert",
					event -> host.report("Modus: Erweitert"));
			standalonePopup.addSubMenu("Modus", modes);
			for (int index = 1; index <= 22; index++) {
				int number = index;
				standalonePopup.addItem("Scrollbarer Eintrag " + index,
						event -> host.report("Popup-Eintrag " + number));
			}
			GLMenuItem disabled = standalonePopup.addItem("Deaktivierter Eintrag");
			disabled.setEnabled(false);
		}

		private void openPopup(GLButton anchor) {
			float[] bounds = anchor.getLastRenderedScreenBounds();
			if (bounds != null)
				standalonePopup.show(host.parent(), bounds[0], bounds[1] + bounds[3]);
		}

		void closeTransientPopups() {
			menuBar.closeMenu();
			standalonePopup.close();
			normalCombo.getPopupMenu().close();
			fixedSlider.getContextMenu().close();
			freeSlider.getContextMenu().close();
		}

		GLMenuBar menuBar() {
			return menuBar;
		}

		float themeRadius() {
			return (float) themeRadiusSlider.getValue();
		}

		private ControlLayer controlLayer(String title, float x, float y,
				float width, float height, PassiveLayer page) {
			ControlLayer layer = new ControlLayer(title);
			layer.setBounds(x, y, width, height);
			page.addGL(layer);
			return layer;
		}

		private static void caption(GLContainer parent, String text,
				float x, float y) {
			addLabel(parent, text, CAPTION_STYLE, x, y, 330, 18);
		}

		private static void addLabel(GLContainer parent, String text,
				GLLabelStyle style, float x, float y, float width, float height) {
			GLLabel label = new ThemedLabel(text, style,
					style == SUBTITLE_STYLE || style == CAPTION_STYLE);
			addControl(parent, label, x, y, width, height);
		}

		private static void addControl(GLContainer parent, GLContainer control,
				float x, float y, float width, float height) {
			control.setBounds(x, y, width, height);
			parent.addGL(control);
		}

		private static boolean isFiniteNumber(String text) {
			try {
				return Double.isFinite(Double.parseDouble(text.replace(',', '.')));
			} catch (NumberFormatException error) {
				return false;
			}
		}

		@Override
		protected void record(GLContext2D context) {
			Color surface = getGLTheme().values().getColor("palette.surface", SURFACE);
			Color border = getGLTheme().values().getColor("palette.border",
					new Color(75, 91, 119));
			context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
					18, 1.5f, surface, border,
					GLStroke.WidthSpace.SCREEN);
		}

		/** A visual and interaction layer for one related family of controls. */
		private static final class ControlLayer extends GLContainer {
			private ControlLayer(String title) {
				addLabel(this, title, SECTION_STYLE, 18, 15, 320, 25);
			}

			@Override
			protected void record(GLContext2D context) {
				Color background = getGLTheme().values().getColor("state.normal",
						CARD);
				Color border = getGLTheme().values().getColor("palette.border",
						CARD_BORDER);
				context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
						14, 1, background, border,
						GLStroke.WidthSpace.SCREEN);
			}
		}

		/** Non-rendering grouping node used to keep the scene graph structured. */
		private static final class PassiveLayer extends GLContainer {
			@Override
			protected void record(GLContext2D context) {
			}

			@Override
			protected boolean isMouseTargetEnabled() {
				return false;
			}
		}

		/** Preserves typographic hierarchy while inheriting theme text colors. */
		private static final class ThemedLabel extends GLLabel {
			private final GLLabelStyle template;
			private final boolean secondary;

			private ThemedLabel(String text, GLLabelStyle template,
					boolean secondary) {
				super(text);
				this.template = template;
				this.secondary = secondary;
				applyThemeColor();
			}

			@Override
			protected void themeChanged() {
				applyThemeColor();
			}

			private void applyThemeColor() {
				String key = secondary ? "palette.textSecondary" : "palette.text";
				Color fallback = template.textColor();
				setGLStyle(template.withTextColor(
						getGLTheme().values().getColor(key, fallback)));
			}
		}

	}

	static String formatPerformance(String backend, String action, float zoom,
			double fps, GLFrameProfile profile, GLDrawCallProfile drawCalls, int samples,
			int swapInterval, float renderScale, int renderWidth, int renderHeight,
			int surfaceWidth, int surfaceHeight) {
		String swap = swapInterval < 0 ? "" : " | Swap " + swapInterval;
		double framePeriod = fps > 0 ? 1000.0 / fps : 0;
		double scheduleWait = Math.max(0,
				framePeriod - profile.endToEndMillis());
		return String.format("<html><div style='font-family:monospace'>"
				+ "%s | %s | Zoom %.0f %% | Render %.0f %% (%dx%d -> %dx%d) | %.0f FPS | Periode %.2f ms | Call E2E %.2f ms | Wait %.2f ms<br>"
				+ "Callback %.2f | Backend-Rest %.2f ms &nbsp; "
				+ "CPU: House %.3f | Scene %.3f | Present %.3f ms &nbsp; "
				+ "GPU: Scene %.3f | Present %.3f ms | %d Drawcalls "
				+ "(UI %d, Filled %d, Texture %d, Text %d, Sprite %d, Layer %d, Target %d, Other %d) | MSAA %dx%s"
				+ "<br>%s"
				+ "</div></html>", backend, action, zoom * 100,
				renderScale * 100, renderWidth, renderHeight,
				surfaceWidth, surfaceHeight, fps,
				framePeriod, profile.endToEndMillis(), scheduleWait,
				profile.callbackCpuMillis(), profile.backendRemainderMillis(),
				profile.housekeepingCpuMillis(),
				profile.sceneCpuMillis(), profile.presentCpuMillis(),
				profile.sceneGpuMillis(), profile.presentGpuMillis(), drawCalls.total(),
				drawCalls.ui(), drawCalls.filled(), drawCalls.texture(), drawCalls.text(),
				drawCalls.sprite(), drawCalls.layers(), drawCalls.renderTargets(),
				drawCalls.other(),
				samples, swap, formatDrawCallEvents(drawCalls));
	}

	private static String formatDrawCallEvents(GLDrawCallProfile profile) {
		if (profile.events().isEmpty())
			return "Flushes: none";
		StringBuilder result = new StringBuilder("Flushes ")
				.append(profile.events().size()).append(": ");
		for (int index = 0; index < profile.events().size(); index++) {
			if (index > 0)
				result.append(index % 4 == 0 ? "<br>" : " &rarr; ");
			var event = profile.events().get(index);
			result.append(event.pipeline()).append('[').append(event.calls())
					.append("]: ").append(event.reason());
		}
		return result.toString();
	}

	static JLabel createStatusLabel(String text) {
		JLabel status = new JLabel(text, SwingConstants.LEFT);
		status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		Dimension fixedSize = new Dimension(1, 132);
		status.setPreferredSize(fixedSize);
		status.setMinimumSize(fixedSize);
		status.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedSize.height));
		return status;
	}
}
