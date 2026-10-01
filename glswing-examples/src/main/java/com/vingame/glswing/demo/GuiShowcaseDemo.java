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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
import com.vingame.glswing.document.GLDocumentAutosaveConfiguration;
import com.vingame.glswing.document.GLDocumentAutosaveCoordinator;
import com.vingame.glswing.document.GLDocumentAutosaveEvent;
import com.vingame.glswing.document.GLDocumentCloseChoice;
import com.vingame.glswing.document.GLDocumentConflictChoice;
import com.vingame.glswing.document.GLDocumentConflictRequest;
import com.vingame.glswing.document.GLDocumentCodecRegistry;
import com.vingame.glswing.document.GLDocumentFileState;
import com.vingame.glswing.document.GLDocumentFileWatcher;
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
import com.vingame.glswing.node.GLNodePalette;
import com.vingame.glswing.node.GLNodePropertyInspector;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodeEditorListener;
import com.vingame.glswing.node.GLNodeEditingActions;
import com.vingame.glswing.node.GLNodePorts;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.node.GLNodeWorkspace;
import com.vingame.glswing.node.binding.GLNodeEditorBinding;
import com.vingame.glswing.node.binding.GLNodeGraphEditingSession;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.document.GLNodeGraphDocument;
import com.vingame.glswing.node.composite.GLCompositeExpansionLimits;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.composite.GLCompositeInterfaceEditor;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.composite.GLCompositeNodeViewFactory;
import com.vingame.glswing.node.composite.GLNodeInputEditorDescriptor;
import com.vingame.glswing.node.composite.GLNodeInputEditorRegistry;
import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.composite.GLCompositePortMapping;
import com.vingame.glswing.node.execution.GLExecutionPlan;
import com.vingame.glswing.node.execution.GLExecutionOperation;
import com.vingame.glswing.node.execution.GLExecutionPlanCompiler;
import com.vingame.glswing.node.execution.GLExecutionPlanController;
import com.vingame.glswing.node.execution.GLExecutionPlanOptions;
import com.vingame.glswing.node.execution.GLExecutionPlanSwapResult;
import com.vingame.glswing.node.execution.GLNodeImplementationDescriptor;
import com.vingame.glswing.node.execution.GLNodeImplementationKind;
import com.vingame.glswing.node.execution.GLNodeImplementationRegistry;
import com.vingame.glswing.node.execution.java.GLJavaExecutablePlan;
import com.vingame.glswing.node.execution.java.GLJavaExecutionBackend;
import com.vingame.glswing.node.execution.java.GLJavaExecutionException;
import com.vingame.glswing.node.execution.java.GLJavaExecutionResult;
import com.vingame.glswing.node.execution.java.GLJavaExecutionTrace;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodePropertySchemaRegistry;
import com.vingame.glswing.node.model.GLNumberNodeProperty;
import com.vingame.glswing.node.model.GLEnumNodeProperty;
import com.vingame.glswing.node.model.GLBooleanNodeProperty;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValueTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeValueTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.node.model.GLPortTiming;
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
import com.vingame.glswing.ui.GLDialogButtonBar;
import com.vingame.glswing.ui.GLDocumentDialogs;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLabelStyle;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLListSelectionModel;
import com.vingame.glswing.ui.GLDefaultTreeModel;
import com.vingame.glswing.ui.GLDefaultListModel;
import com.vingame.glswing.ui.GLTree;
import com.vingame.glswing.ui.GLCodeEditor;
import com.vingame.glswing.ui.GLCodeLanguages;
import com.vingame.glswing.ui.GLCodeWorkspace;
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
import com.vingame.glswing.ui.GLTextInputFormats;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeLoader;
import com.vingame.glswing.ui.GLThemeWatcher;
import com.vingame.glswing.ui.GLThemes;

/** Extensible fixed-layout showcase for every public GLSwing GUI control. */
public final class GuiShowcaseDemo {

	static final float DESIGN_WIDTH = 1180;
	static final float DESIGN_HEIGHT = 790;
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
		GLApplicationHandle handle = GLApplication.builder()
				.title(title)
				.backend(backend)
				.size(1260, 920)
				.minimumSize(760, 520)
				.clearColor(new Color(12, 17, 27))
				.theme(showcaseThemeDirectory().resolve("dark.gltheme"),
						GLThemes.BLUE)
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
		handle.closedFuture().thenRun(() -> System.exit(0));
		return handle;
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
		panel.setPreferredSize(new Dimension(1260, 920));
		JLabel status = createStatusLabel("GUI-Showcase startet ...");
		Timer statistics = new Timer(1000,
				event -> status.setText(panel.performanceText()));

		JFrame frame = new JFrame("GLSwing - GUI Showcase");
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
			watchShowcaseTheme("dark.gltheme", GLThemes.BLUE,
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
		private GLMenuItem defaultColorsItem;
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
		private final GLCodeWorkspace codeWorkspace;
		private final GLTabbedPane codeTabs;
		private final GLDocumentWorkspace documentWorkspace;
		private final GLDocumentTabController codeDocuments;
		private final GLDocumentCodecRegistry documentCodecs = new GLDocumentCodecRegistry();
		private final GLPlainTextDocumentCodec sourceDocumentCodec =
				new GLPlainTextDocumentCodec("glswing.source-text", "java", "gltheme",
						"glinput", "properties", "txt", "md");
		private final GLDocumentManager documentManager;
		private final GLDocumentLifecycle<GLTextDocument> documentLifecycle;
		private final GLDocumentAutosaveCoordinator documentAutosave;
		private final GLDocumentFileWatcher documentFileWatcher;
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
		private ExecutorService nodeExecutionExecutor;
		private GLExecutionPlanController<GLJavaExecutablePlan> nodePlanController;
		private GLNodeGraph showcaseNodeGraph;
		private GLNodeTypeRegistry showcaseNodeTypes;
		private GLNodeEditor showcaseNodeEditor;
		private GLNodeEditorBinding showcaseNodeBinding;
		private final GLNodeCatalog showcaseNodeCatalog = new GLNodeCatalog();
		private final GLNodePalette nodeCreationPalette =
				new GLNodePalette(showcaseNodeCatalog);
		private GLList<GLNodeCatalogEntry> nodePaletteList;
		private GLTextEditor nodePaletteSearch;
		private GLCheckBox nodePaletteCompatible;
		private GLLabel nodePaletteStatus;
		private PendingNodeConnection pendingNodeConnection;
		private Float requestedNodeX;
		private Float requestedNodeY;
		private GLPopupMenu nodeCreationMenu;
		private int nodePaletteInsertCount;
		private int extractedCompositeCount;
		private GLCompositeGraphRegistry showcaseCompositeRegistry;
		private GLNodeInputEditorRegistry showcaseInputEditors;
		private GLComboBox<GLGraphCyclePolicy> showcaseCyclePolicy;
		private GLLabel nodeExecutionStatus;
		private GLLabel nodeExecutionStats;
		private GLLabel nodeExecutionOutput;
		private GLTextArea nodeExecutionDetails;
		private GLTree<PlanTreeItem> nodeExecutionTree;
		private final GLDefaultTreeModel<PlanTreeItem> nodeExecutionTreeModel =
				new GLDefaultTreeModel<>();
		private GLExecutionOperation nodeInspectedOperation;
		private boolean syncingInspectorSelection;
		private final Map<GLNodeId, PlanTreeItem> nodeTreeItemsByVisibleId =
				new HashMap<>();
		private final Map<String, GLContainer> openCompositeTabs = new HashMap<>();
		private final Map<String, GLNodeEditorBinding> openCompositeBindings =
				new HashMap<>();
		private final Map<String, GLNodeWorkspace> openCompositeWorkspaces =
				new HashMap<>();
		private GLButton nodeRunButton;
		private GLButton nodeBreakpointButton;
		private GLNodePropertyInspector nodePropertyInspector;
		private final Set<GLNodeId> nodeBreakpoints = new LinkedHashSet<>();
		private volatile GLJavaExecutionResult nodeLastExecution;
		private volatile GLNodeId nodePausedAtBreakpoint;
		private final AtomicReference<GLNodeValue> observedNodeOutput =
				new AtomicReference<>();
		private final GLNodeId showcaseTintNodeId = GLNodeId.random();
		private final GLNodeId showcaseMaterialLeafNodeId = GLNodeId.random();
		private final GLNodeConnectionId showcaseCompositeConnectionId =
				GLNodeConnectionId.random();

		private enum PlanTreeKind { PLAN, COMPOSITE, OPERATION }
		private record PlanTreeItem(String key, String label, PlanTreeKind kind,
				List<GLCompositeOrigin.Instance> compositePath,
				GLExecutionOperation operation,
				GLNodeId visibleNodeId) {
			PlanTreeItem {
				compositePath = compositePath == null ? List.of() : List.copyOf(compositePath);
			}
			@Override public String toString() { return label; }
		}
		private record PendingNodeConnection(GLNodeEndpoint endpoint,
				GLNodePort.Direction direction, float x, float y) { }

		ShowcaseSurface(ShowcaseHost host) {
			this.host = host;
			documentWorkspace = new GLDocumentWorkspace(host.historyManager());
			codeWorkspace = new GLCodeWorkspace(documentWorkspace);
			codeTabs = codeWorkspace.tabs();
			codeDocuments = codeWorkspace.documentTabs();
			documentCodecs.register(sourceDocumentCodec);
			documentManager = new GLDocumentManager(documentCodecs);
			codeWorkspace.setDocumentResolver(location -> {
				URI uri = location.uri();
				if (!"file".equalsIgnoreCase(uri.getScheme()))
					return java.util.concurrent.CompletableFuture.failedFuture(
							new java.io.IOException("Cannot edit non-file source: " + uri));
				Path path;
				try { path = Path.of(uri).toAbsolutePath().normalize(); }
				catch (RuntimeException failure) {
					return java.util.concurrent.CompletableFuture.failedFuture(failure);
				}
				if (!path.startsWith(showcaseWorkspace()))
					return java.util.concurrent.CompletableFuture.failedFuture(
							new java.io.IOException("Source is outside the showcase workspace: " + path));
				return documentManager.open(path, GLTextDocument.class);
			});
			codeWorkspace.setEditorConfigurer(editor -> {
				if (editor.getLanguage() == GLCodeLanguages.JAVA)
					configureCodeEditor(editor);
				if (editor.getDocumentUri() != null)
					editorsByUri.put(editor.getDocumentUri(), editor);
			});
			codeWorkspace.setNavigationFailureHandler(failure ->
					SwingUtilities.invokeLater(() -> lspStatus.setText(
							"Java LSP · Cannot open workspace edit target: "
									+ failure.getMessage())));
			documentAutosave = new GLDocumentAutosaveCoordinator(documentManager,
					showcaseApplicationData().resolve("recovery"),
					loadAutosaveConfiguration());
			try { documentFileWatcher = new GLDocumentFileWatcher(); }
			catch (java.io.IOException failure) {
				throw new IllegalStateException("Document file monitoring is unavailable", failure);
			}
			documentLifecycle = new GLDocumentLifecycle<>(documentManager,
					sourceDocumentCodec, codeDocuments, fileDialogs, host.parent(),
					() -> new GLTextDocument("public final class Untitled {\n\n}\n"),
					this::createJavaDocumentView);
			documentLifecycle.setCloseRequestHandler(this::confirmDocumentClose);
			documentLifecycle.setFailureHandler(
					GLDocumentDialogs.failureHandler(host.parent()));
			documentLifecycle.setMigrationRequestHandler(
					GLDocumentDialogs.migrationHandler(host.parent()));
			documentLifecycle.setAutosaveCoordinator(documentAutosave);
			documentLifecycle.setFileWatcher(documentFileWatcher);
			documentLifecycle.setConflictRequestHandler(this::confirmSaveConflict);
			documentLifecycle.setAutoReloadCleanDocuments(true);
			documentLifecycle.setReloadFailureHandler(failure -> SwingUtilities.invokeLater(() ->
					host.report(host.localizer().text("showcase.document.reload.failed",
							failure.getMessage()))));
			documentFileWatcher.addListener(event -> {
				if (event.state() == GLDocumentFileState.DELETED
						|| event.state() == GLDocumentFileState.UNAVAILABLE)
					SwingUtilities.invokeLater(() -> host.report(host.localizer().text(
							"showcase.document.external." + event.state().name().toLowerCase(),
							event.document().title())));
			});
			documentAutosave.addListener(this::reportAutosaveEvent);
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
			discoverRecoveries();
		}

		private GLContainer createJavaDocumentView(GLDocument<GLTextDocument> document) {
			GLCodeEditor editor = document.location() == null
					? new GLCodeEditor(document.content())
					: new GLCodeEditor(document.content(), document.location());
			editor.setEditable(!document.isReadOnly());
			if (editor.getLanguage() == GLCodeLanguages.JAVA) configureCodeEditor(editor);
			else editor.setNavigationHandler(this::openDefinition);
			if (document.location() != null) {
				GLCodeEditor previous = editorsByUri.put(document.location(), editor);
				if (previous == codeEditor) codeEditor = editor;
			}
			codeWorkspace.configureEditor(editor);
			editor.setNavigationHandler(this::openDefinition);
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
			URI initialUri = initialPath.toUri();
			codeEditor = new GLCodeEditor(initialSource, initialUri);
			configureCodeEditor(codeEditor);
			addCodeDocumentTab("HelloGLSwing.java", initialUri, codeEditor);
			editorsByUri.put(initialUri, codeEditor);
			addThemeEditor(showcaseThemeDirectory().resolve("showcase-bundle.gltheme"));
			addConfigurationEditor(showcaseWorkspace().resolve(
					"glswing-core/src/main/resources/com/vingame/glswing/input/default.glinput"));
			addConfigurationEditor(showcaseWorkspace().resolve(
					"glswing-core/src/main/resources/com/vingame/glswing/languages/Messages_de.properties"));
			codeTabs.setSelectedIndex(0);
			codeTabs.addSelectionListener((source, oldIndex, newIndex) ->
					updateCodeTabStatus(newIndex));
			codeWorkspace.setBounds(18, 50, 1110, 513);
			codePage.addGL(codeWorkspace);
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
			GLLanguageServer current = languageServer;
			if (current != null && editor.isEditable()
					&& editor.getDocumentUri() != null
					&& editor.getDiagnosticProvider() != current)
				editor.setDiagnosticProvider(current, editor.getDocumentUri());
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
			editor.setOperationStatusListener(status -> {
				String operation = switch (status.operation()) {
				case REFERENCES -> "References";
				case RENAME -> "Rename";
				case CODE_ACTIONS -> "Code actions";
				};
				switch (status.state()) {
				case REQUESTING -> lspStatus.setText("Java LSP · " + operation + " …");
				case AVAILABLE -> lspStatus.setText("Java LSP · " + operation + " · "
						+ status.resultCount() + " result"
						+ (status.resultCount() == 1 ? "" : "s"));
				case EMPTY -> lspStatus.setText("Java LSP · " + operation + " · no result");
				case APPLIED -> lspStatus.setText("Java LSP · " + operation + " applied");
				case FAILED -> lspStatus.setText("Java LSP · " + operation + " failed: "
						+ status.failure().getMessage());
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
				boolean localFile = "file".equalsIgnoreCase(location.uri().getScheme());
				String uriPath = location.uri().getPath();
				boolean bundled = uriPath != null && uriPath.replace('\\', '/')
						.contains("/com/vingame/glswing/themes/");
				String source;
				if (localFile) source = Files.readString(Path.of(location.uri()));
				else if ("jar".equalsIgnoreCase(location.uri().getScheme()))
					try (var stream = location.uri().toURL().openStream()) {
						source = new String(stream.readAllBytes(),
								java.nio.charset.StandardCharsets.UTF_8);
					}
				else throw new java.io.IOException(
						"Unsupported configuration source URI: " + location.uri());
				GLCodeEditor editor = new GLCodeEditor(source, location.uri());
				if (bundled || !localFile) editor.setEditable(false);
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
			codeWorkspace.addDocument(document, editor);
			editor.setNavigationHandler(this::openDefinition);
			return codeTabs.indexOfComponent(editor);
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
						lspStatus.setText("Core diagnostics active · LSP connected: "
								+ Path.of(command.get(0)).getFileName());
						editorsByUri.forEach((uri, editor) -> {
							if (editor.isEditable()
									&& editor.getLanguage() == GLCodeLanguages.JAVA)
								editor.setDiagnosticProvider(started, uri);
						});
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

		private static Path showcaseApplicationData() {
			String home = System.getProperty("user.home", "").strip();
			Path base = home.isEmpty() ? showcaseWorkspace() : Path.of(home);
			return base.resolve(".glswing").resolve("showcase")
					.toAbsolutePath().normalize();
		}

		private GLDocumentAutosaveConfiguration loadAutosaveConfiguration() {
			Path preferences = showcaseApplicationData().resolve("document.properties");
			try {
				if (Files.isRegularFile(preferences))
					return GLDocumentAutosaveConfiguration.load(preferences);
				GLDocumentAutosaveConfiguration.DEFAULT.save(preferences);
				return GLDocumentAutosaveConfiguration.DEFAULT;
			} catch (java.io.IOException | IllegalArgumentException failure) {
				host.report("Autosave preferences: " + failure.getMessage());
				return GLDocumentAutosaveConfiguration.DEFAULT;
			}
		}

		void closeLanguageServer() {
			languageServerClosing = true;
			openCompositeBindings.values().forEach(GLNodeEditorBinding::close);
			openCompositeBindings.clear();
			openCompositeWorkspaces.values().forEach(GLNodeWorkspace::close);
			openCompositeWorkspaces.clear();
			if (nodePlanController != null) nodePlanController.close();
			if (nodeExecutionExecutor != null) nodeExecutionExecutor.shutdownNow();
			documentLifecycle.close();
			documentAutosave.close();
			documentFileWatcher.close();
			codeWorkspace.close();
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
			current.close();
		}

		java.util.concurrent.CompletionStage<Boolean> prepareClose() {
			return documentLifecycle.prepareCloseAll();
		}

		private void buildHeaderLayer() {
			addLabel(headerLayer, "GLSwing GUI Showcase", TITLE_STYLE,
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
			helpMenu.addItem("About GLSwing",
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
			defaultColorsItem = colorsMenu.addItem("Standard",
					event -> host.selectShowcaseColors(
						"showcase-default-colors.gltheme",
						"showcase.theme.colors.default"));
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

		private void reportAutosaveEvent(GLDocumentAutosaveEvent event) {
			if (event.state() == GLDocumentAutosaveEvent.State.SAVED)
				host.report(host.localizer().text("showcase.autosave.saved",
						event.document().title()));
			else if (event.state() == GLDocumentAutosaveEvent.State.FAILED)
				host.report(host.localizer().text("showcase.autosave.failed",
						event.failure().getMessage()));
		}

		private void discoverRecoveries() {
			documentAutosave.scan().whenComplete((recoveries, failure) ->
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						if (failure != null) {
							host.report(host.localizer().text("showcase.autosave.failed",
									failure.getMessage()));
							return;
						}
						showRecovery(recoveries, 0);
					}));
		}

		private void showRecovery(
				List<com.vingame.glswing.document.GLDocumentRecovery> recoveries,
				int index) {
			if (languageServerClosing || index >= recoveries.size()) return;
			if (!((Component) host.parent()).isShowing()) {
				Timer retry = new Timer(50,
						event -> showRecovery(recoveries, index));
				retry.setRepeats(false);
				retry.start();
				return;
			}
			var recovery = recoveries.get(index);
			GLLocalizer text = host.localizer();
			AtomicReference<GLDialog> dialogReference = new AtomicReference<>();
			GLContainer content = new GLContainer() {
				private final GLLabel message = new GLLabel(text.text(
						"showcase.autosave.recovery.message", recovery.title(),
						java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
								.format(recovery.savedAt().atZone(
										java.time.ZoneId.systemDefault()))));
				private final GLButton restore = new GLButton(text.text(
						"showcase.autosave.recovery.restore"));
				private final GLButton discard = new GLButton(text.text(
						"showcase.autosave.recovery.discard"));
				private final GLButton keep = new GLButton(text.text(
						"showcase.autosave.recovery.keep"));
				private final GLDialogButtonBar actions = new GLDialogButtonBar()
						.addButton(restore).addButton(discard).addButton(keep);
				{
					addGL(message); addGL(actions);
					restore.addActionListener(event -> restoreRecovery());
					discard.addActionListener(event -> discardRecovery());
					keep.addActionListener(event -> finishAndContinue());
				}
				private void restoreRecovery() {
					dialogReference.get().close();
					documentAutosave.recover(recovery, GLTextDocument.class)
							.whenComplete((document, failure) ->
									SwingUtilities.invokeLater(() -> {
										if (failure == null) {
											documentLifecycle.addDocument(document);
											showCodeWorkspace();
											host.report(text.text(
													"showcase.autosave.recovered",
													document.title()));
										} else host.report(text.text(
												"showcase.autosave.failed",
												failure.getMessage()));
										showRecovery(recoveries, index + 1);
									}));
				}
				private void discardRecovery() {
					dialogReference.get().close();
					documentAutosave.discard(recovery).whenComplete((deleted, failure) ->
							SwingUtilities.invokeLater(() -> {
								if (failure != null) host.report(text.text(
										"showcase.autosave.failed", failure.getMessage()));
								showRecovery(recoveries, index + 1);
							}));
				}
				private void finishAndContinue() {
					dialogReference.get().close();
					showRecovery(recoveries, index + 1);
				}
				@Override protected void record(GLContext2D context) { }
				@Override protected void resized(float width, float height) {
					message.setBounds(22, 22, Math.max(1, width - 44), 42);
					actions.setBounds(22, height - 48, Math.max(1, width - 44), 30);
				}
			};
			GLDialog dialog = new GLDialog(text.text(
					"showcase.autosave.recovery.title"), content);
			dialog.setSize(560, 160);
			dialogReference.set(dialog);
			try { dialog.show(host.parent()); }
			catch (RuntimeException failure) {
				host.report(text.text("showcase.autosave.failed", failure.getMessage()));
			}
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
				private final GLDialogButtonBar actions = new GLDialogButtonBar()
						.addButton(save).addButton(discard).addButton(cancel);
				{
					addGL(message); addGL(actions);
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
					actions.setBounds(22, height - 48, Math.max(1, width - 44), 30);
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

		private java.util.concurrent.CompletionStage<GLDocumentConflictChoice>
				confirmSaveConflict(GLDocumentConflictRequest request) {
			GLLocalizer text = host.localizer();
			java.util.concurrent.CompletableFuture<GLDocumentConflictChoice> result =
					new java.util.concurrent.CompletableFuture<>();
			AtomicReference<GLDialog> dialogReference = new AtomicReference<>();
			String stateKey = request.fileState() == GLDocumentFileState.DELETED
					? "deleted" : request.fileState() == GLDocumentFileState.UNAVAILABLE
							? "unavailable" : "modified";
			GLContainer content = new GLContainer() {
				private final GLLabel message = new GLLabel(text.text(
						"showcase.document.conflict." + stateKey, request.document().title()));
				private final GLButton reload = new GLButton(text.text(
						"showcase.document.conflict.reload"));
				private final GLButton overwrite = new GLButton(text.text(
						"showcase.document.conflict.overwrite"));
				private final GLButton saveAs = new GLButton(text.text(
						"showcase.document.conflict.saveAs"));
				private final GLButton cancel = new GLButton(text.text(
						"showcase.document.close.cancel"));
				private final GLDialogButtonBar actions = new GLDialogButtonBar()
						.addButton(reload).addButton(overwrite).addButton(saveAs).addButton(cancel);
				{
					addGL(message); addGL(actions);
					reload.setEnabled(request.fileState() == GLDocumentFileState.MODIFIED);
					reload.addActionListener(event -> finish(GLDocumentConflictChoice.RELOAD));
					overwrite.addActionListener(event -> finish(GLDocumentConflictChoice.OVERWRITE));
					saveAs.addActionListener(event -> finish(GLDocumentConflictChoice.SAVE_AS));
					cancel.addActionListener(event -> finish(GLDocumentConflictChoice.CANCEL));
				}
				private void finish(GLDocumentConflictChoice choice) {
					if (result.complete(choice)) dialogReference.get().close();
				}
				@Override protected void record(GLContext2D context) { }
				@Override protected void resized(float width, float height) {
					message.setBounds(22, 20, Math.max(1, width - 44), 48);
					actions.setBounds(22, height - 48, Math.max(1, width - 44), 30);
				}
			};
			GLDialog dialog = new GLDialog(text.text("showcase.document.conflict.title"), content);
			dialog.setSize(650, 165); dialogReference.set(dialog);
			dialog.closedFuture().whenComplete((unused, failure) ->
					result.complete(GLDocumentConflictChoice.CANCEL));
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
			defaultColorsItem.setText(
					text.text("showcase.theme.colors.default"));
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
			caption(section, "Farbwahl / RGB, HSV, HSL, OKLab, OKLCH", 18, 516);
			var colorChooser = new com.vingame.glswing.ui.GLColorChooser(new Color(84,120,181));
			addControl(section, colorChooser, 18, 541, 120, 30);
		}

		private void buildValueLayer() {
			ControlLayer section = controlLayer("Slider & Gruppen",
					410, 0, 350, 590, controlsPage);
			caption(section, "FIXED: Bereich 0 ... 100, Default 50", 18, 50);
			fixedSlider.setDefaultValue(50);
			fixedSlider.setStep(5);
			addControl(section, fixedSlider, 18, 72, 324, 26);

			caption(section, "FIXED + BUTTONS: ganzzahlige Schritte", 18, 108);
			GLSlider fixedButtons = GLSlider.fixed("Iterations", 0, 10, 4,
					GLSlider.Presentation.BUTTONS);
			fixedButtons.setIntegerValuesOnly(true);
			addControl(section, fixedButtons, 18, 130, 324, 26);

			caption(section, "FREE: Shift fein, Strg grob, Rechtsklick-Menue", 18, 166);
			freeSlider.setDefaultValue(0);
			freeSlider.setDragSensitivity(0.1);
			freeSlider.setPreciseDragFactor(0.1);
			freeSlider.setCoarseDragFactor(10);
			freeSlider.getContextMenu().addSeparator();
			freeSlider.getContextMenu().addItem("Wert auf 42 setzen",
					event -> freeSlider.setValue(42));
			addControl(section, freeSlider, 18, 188, 324, 26);

			caption(section, "FREE + BAR: Basis 0 ... 10, erweitert sich", 18, 224);
			GLSlider freeBar = GLSlider.free("Dynamischer Wert", 0, 10, 5);
			addControl(section, freeBar, 18, 246, 324, 26);

			caption(section, "GRUPPE: vertikal ueberstreichen", 18, 310);
			GLSlider red = GLSlider.fixed("Rot", 0, 255, 128);
			GLSlider green = GLSlider.fixed("Gruen", 0, 255, 96);
			GLSlider blue = GLSlider.fixed("Blau", 0, 255, 160);
			red.setIntegerValuesOnly(true);
			green.setIntegerValuesOnly(true);
			blue.setIntegerValuesOnly(true);
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
			documents.addRoot("GLSwing");
			documents.addChild("GLSwing", "glswing-core");
			documents.addChild("GLSwing", "glswing-examples");
			documents.addChild("GLSwing", "docs");
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
			search.setCommitOnFocusLost(false);
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
			GLLabel policyLabel = new GLLabel("Cycle policy");
			policyLabel.setBounds(8, 12, 88, 28);
			nodeEditorPage.addGL(policyLabel);
			GLComboBox<GLGraphCyclePolicy> policy = new GLComboBox<>(
					List.of(GLGraphCyclePolicy.values()));
			showcaseCyclePolicy = policy;
			policy.setItemTextProvider(value -> switch (value) {
				case ACYCLIC -> "Acyclic";
				case DELAYED_FEEDBACK -> "Delayed feedback";
				case PREVIOUS_STEP -> "Previous step";
			});
			policy.setSelectedItem(GLGraphCyclePolicy.DELAYED_FEEDBACK);
			policy.setBounds(100, 10, 180, 30);
			nodeEditorPage.addGL(policy);

			GLCheckBox delayedOutput = new GLCheckBox(
					"Material output uses previous-step timing");
			delayedOutput.setSelected(true);
			delayedOutput.setBounds(300, 12, 330, 26);
			nodeEditorPage.addGL(delayedOutput);
			GLLabel validationStatus = new GLLabel("Not validated");
			validationStatus.setBounds(650, 12, 474, 26);
			nodeEditorPage.addGL(validationStatus);

			GLNodeEditor editor = new GLNodeEditor();
			showcaseNodeEditor = editor;
			editor.setBounds(236, 48, 548, 550);
			editor.setConnectionColorInterpolator(GLColorInterpolation.OKLAB);
			nodeEditorPage.addGL(editor);
			GLNodeId textureId = GLNodeId.random();
			GLNodeId materialId = GLNodeId.random();
			GLNodeId outputId = GLNodeId.random();
			GLNodeConnectionId textureToMaterialId = GLNodeConnectionId.random();
			GLNodeConnectionId materialToOutputId = GLNodeConnectionId.random();
			GLNodeConnectionId feedbackId = GLNodeConnectionId.random();
			GLNodeGraph graph = new GLNodeGraph();
			showcaseNodeGraph = graph;
			graph.edit("Create showcase feedback graph", edit -> {
				edit.addNode(new GLGraphNode(textureId, "showcase.texture",
						Map.of("gain", GLNodeValue.of(1.0))));
				edit.addNode(new GLGraphNode(materialId, "showcase.material-stack",
						Map.of("blend", GLNodeValue.of("Normal"),
								"enabled", GLNodeValue.of(true))));
				edit.addNode(new GLGraphNode(outputId, "showcase.output"));
				edit.addConnection(new GLGraphConnection(textureToMaterialId,
						new GLNodeEndpoint(textureId, "color"),
						new GLNodeEndpoint(materialId, "base")));
				edit.addConnection(new GLGraphConnection(materialToOutputId,
						new GLNodeEndpoint(materialId, "result"),
						new GLNodeEndpoint(outputId, "surface")));
				edit.addConnection(new GLGraphConnection(feedbackId,
						new GLNodeEndpoint(materialId, "result"),
						new GLNodeEndpoint(textureId, "feedback")));
			});

			GLNodeTypeRegistry types = new GLNodeTypeRegistry();
			showcaseNodeTypes = types;
			registerShowcaseNodeTypes(types, GLPortTiming.PREVIOUS_STEP);
			showcaseCompositeRegistry = createShowcaseCompositeRegistry(
					GLPortTiming.PREVIOUS_STEP);
			showcaseInputEditors = createShowcaseInputEditors();
			configureNodeExecutionBackend();
			GLNodeEditorViewState viewState = new GLNodeEditorViewState(Map.of(
					textureId, new GLNodeViewState(45, 80),
					materialId, new GLNodeViewState(290, 115),
					outputId, new GLNodeViewState(570, 270)));
			GLNodeEditorBinding binding = new GLNodeEditorBinding(graph, types,
					editor, new GLCompositeNodeViewFactory(showcaseCompositeRegistry,
						showcaseInputEditors, (node, type) -> createShowcaseNodeView(type),
						id -> showcaseNodeCatalog.find(id)
								.map(GLNodeCatalogEntry::displayName).orElse(id)),
					policy.getSelectedItem(), viewState);
			showcaseNodeBinding = binding;
			buildNodeExecutionInspector();
			GLNodeGraphEditingSession editing = new GLNodeGraphEditingSession(binding,
					host.historyManager());
			editor.setEditingActions(new GLNodeEditingActions() {
				@Override public boolean canCopy() {
					return !editor.getSelectedNodes().isEmpty();
				}
				@Override public boolean canCut() { return canCopy(); }
				@Override public boolean canPaste() { return editing.hasClipboardContent(); }
				@Override public boolean canDuplicate() { return canCopy(); }
				@Override public boolean canExtractComposite() { return canCopy(); }
				@Override public void copy() { editing.copySelection(); }
				@Override public void cut() { editing.cutSelection(); }
				@Override public void paste(float x, float y) { editing.paste(x, y); }
				@Override public void duplicate() { editing.duplicateSelection(28, 28); }
				@Override public com.vingame.glswing.node.GLNodePlacement beginPaste(
						float x, float y) { return editing.beginPaste(x, y); }
				@Override public com.vingame.glswing.node.GLNodePlacement beginDuplicate(
						float x, float y) { return editing.beginDuplicate(x, y); }
				@Override public com.vingame.glswing.node.GLNodePlacement beginMoveSelection(
						float x, float y) { return editing.beginMoveSelection(x, y); }
				@Override public void delete() { editing.removeSelection(); }
				@Override public void toggleHidden() { editing.toggleHiddenSelection(); }
				@Override public void toggleMuted() { editing.toggleMutedSelection(); }
				@Override public void requestNodeCreation(float x, float y,
						float screenX, float screenY) {
					showNodeCreationMenu(x, y, screenX, screenY);
				}
				@Override public void openSelectedComposite() {
					GLNodeId id = selectedShowcaseNodeId();
					GLGraphNode node = id == null ? null
							: showcaseNodeGraph.snapshot().findNode(id).orElse(null);
					if (node == null) return;
					showcaseCompositeRegistry.findComposite(node.typeId()).ifPresent(descriptor ->
							openCompositeAsset(id, node.typeId(), descriptor.assetId()));
				}
				@Override public boolean canOpenSelectedComposite() {
					GLNodeId id = selectedShowcaseNodeId();
					return id != null && showcaseNodeGraph.snapshot().findNode(id)
							.flatMap(node -> showcaseCompositeRegistry.findComposite(
									node.typeId())).isPresent();
				}
				@Override public void extractComposite() {
					int number = ++extractedCompositeCount;
					String typeId = "showcase.extracted." + number;
					try {
						editing.extractSelection(typeId + ".asset", typeId,
								"Extracted Composite " + number, "Composites",
								showcaseCompositeRegistry, showcaseNodeCatalog);
						refreshNodePalette();
						nodePaletteStatus.setText("Selection extracted as a reusable composite");
					} catch (RuntimeException failure) {
						nodePaletteStatus.setText("Cannot extract: " + failure.getMessage());
					}
				}
			});
			editor.setEmptyConnectionDropListener((source, port, x, y, screenX, screenY) -> {
				GLNodeId nodeId = binding.findNodeId(port.getNode()).orElse(null);
				if (nodeId == null) return;
				pendingNodeConnection = new PendingNodeConnection(
						new GLNodeEndpoint(nodeId, port.getId()), port.getDirection(), x, y);
				nodePaletteCompatible.setSelected(true);
				nodePaletteStatus.setText("Choose a compatible node to complete the connection");
				refreshNodePalette();
				nodePaletteSearch.requestGLFocus(nodePaletteSearch);
			});
			editor.setNodeCreationRequestListener((source, x, y, screenX, screenY) ->
					showNodeCreationMenu(x, y, screenX, screenY));
			configureShowcaseNodeCatalog(types);
			buildNodePalette();
			editor.addNodeEditorListener(new GLNodeEditorListener() {
				@Override public void selectionChanged(GLNodeEditor source) {
					if (!syncingInspectorSelection) {
						nodeInspectedOperation = null;
						PlanTreeItem item = nodeTreeItemsByVisibleId.get(
								selectedShowcaseNodeId());
						if (item != null && nodeExecutionTree != null) {
							syncingInspectorSelection = true;
							try { nodeExecutionTree.setSelectedValue(item); }
							finally { syncingInspectorSelection = false; }
						}
					}
					refreshNodeInspectorDetails();
					if (nodePropertyInspector != null) nodePropertyInspector.refresh();
					if (nodePaletteCompatible != null && nodePaletteCompatible.isSelected())
						refreshNodePalette();
				}
			});
			binding.addValidationListener((source, result) -> {
				showNodeValidation(validationStatus, result);
				compileShowcaseNodePlan(false);
			});
			policy.addActionListener(event ->
					binding.setCyclePolicy(policy.getSelectedItem()));
			delayedOutput.addActionListener(event -> {
				GLPortTiming timing = delayedOutput.isSelected()
						? GLPortTiming.PREVIOUS_STEP : GLPortTiming.CURRENT;
				registerShowcaseNodeTypes(types, timing);
				updateShowcaseCompositeTiming(timing);
				binding.refresh();
			});
			showNodeValidation(validationStatus, binding.validationResult());
			compileShowcaseNodePlan(false);
		}

		private void buildNodeExecutionInspector() {
			GLTabbedPane inspectorTabs = new GLTabbedPane();
			inspectorTabs.setBounds(796, 48, 336, 590);
			nodeEditorPage.addGL(inspectorTabs);
			ControlLayer inspector = new ControlLayer("Execution Inspector");
			inspectorTabs.addTab("Execution", inspector);
			nodeExecutionStatus = new GLLabel("Waiting for graph");
			nodeExecutionStatus.setBounds(16, 47, 304, 22);
			inspector.addGL(nodeExecutionStatus);
			nodeExecutionStats = new GLLabel("No active plan");
			nodeExecutionStats.setBounds(16, 71, 304, 22);
			inspector.addGL(nodeExecutionStats);
			nodeExecutionOutput = new GLLabel("Last output: -");
			nodeExecutionOutput.setBounds(16, 95, 304, 22);
			inspector.addGL(nodeExecutionOutput);
			nodeExecutionTree = new GLTree<>(nodeExecutionTreeModel);
			nodeExecutionTree.setRenameContextMenuEnabled(false);
			nodeExecutionTree.setRowHeight(23);
			nodeExecutionTree.setItemTextProvider(PlanTreeItem::label);
			nodeExecutionTree.setBounds(16, 124, 304, 150);
			nodeExecutionTree.getSelectionModel().addSelectionListener(
					(source, oldSelection, newSelection) -> inspectPlanTreeSelection());
			nodeExecutionTree.addActivationListener((source, item) -> {
				if (item.kind() == PlanTreeKind.COMPOSITE) openCompositeGraphTab(item);
			});
			inspector.addGL(nodeExecutionTree);

			nodeExecutionDetails = new GLTextArea("Compile the graph to inspect its plan.");
			nodeExecutionDetails.setEditable(false);
			nodeExecutionDetails.setLineWrap(false);
			nodeExecutionDetails.setBounds(16, 280, 304, 170);
			inspector.addGL(nodeExecutionDetails);

			GLButton compile = new GLButton("Compile");
			compile.setBounds(16, 462, 90, 30);
			compile.addActionListener(event -> compileShowcaseNodePlan(false));
			inspector.addGL(compile);
			nodeRunButton = new GLButton("Run step");
			nodeRunButton.setEnabled(false);
			nodeRunButton.setBounds(112, 462, 96, 30);
			nodeRunButton.addActionListener(event -> runShowcaseNodeStep());
			inspector.addGL(nodeRunButton);
			GLButton reject = new GLButton("Reject update");
			reject.setBounds(214, 462, 106, 30);
			reject.addActionListener(event -> compileShowcaseNodePlan(true));
			inspector.addGL(reject);

			GLButton stepOperation = new GLButton("Step op");
			stepOperation.setBounds(16, 504, 90, 30);
			stepOperation.addActionListener(event -> advanceShowcaseNodeOperation(false));
			inspector.addGL(stepOperation);
			GLButton continueExecution = new GLButton("Continue");
			continueExecution.setBounds(112, 504, 96, 30);
			continueExecution.addActionListener(event -> advanceShowcaseNodeOperation(true));
			inspector.addGL(continueExecution);
			nodeBreakpointButton = new GLButton("Breakpoint");
			nodeBreakpointButton.setBounds(214, 504, 106, 30);
			nodeBreakpointButton.addActionListener(event -> toggleNodeBreakpoint());
			inspector.addGL(nodeBreakpointButton);
			buildNodePropertyInspector(inspectorTabs);
		}

		private void configureShowcaseNodeCatalog(GLNodeTypeRegistry types) {
			if (!showcaseNodeCatalog.entries().isEmpty()) return;
			showcaseNodeCatalog.register(new GLNodeCatalogEntry(
					types.find("showcase.texture").orElseThrow(), "Texture", "Sources",
					Map.of("gain", GLNodeValue.of(1.0)), "image", "color"));
			showcaseNodeCatalog.register(new GLNodeCatalogEntry(
					types.find("showcase.material-stack").orElseThrow(), "Material Stack",
					"Composites", Map.of("blend", GLNodeValue.of("Normal"),
							"enabled", GLNodeValue.of(true)), "shade", "tint"));
			showcaseNodeCatalog.register(new GLNodeCatalogEntry(
					types.find("showcase.output").orElseThrow(), "Surface Output", "Outputs",
					Map.of(), "result", "display"));
		}

		private void buildNodePalette() {
			ControlLayer palette = new ControlLayer("Node Palette");
			palette.setBounds(8, 48, 216, 550);
			nodeEditorPage.addGL(palette);
			nodeCreationPalette.setBounds(2, 34, 212, 448);
			nodeCreationPalette.setActivationListener(this::insertPaletteNode);
			palette.addGL(nodeCreationPalette);
			nodePaletteSearch = nodeCreationPalette.searchEditor();
			nodePaletteList = nodeCreationPalette.nodeList();
			nodePaletteCompatible = nodeCreationPalette.compatibleOnlyControl();
			GLButton add = new GLButton("Add node");
			add.setBounds(12, 494, 92, 30);
			add.addActionListener(event -> insertPaletteNode(nodePaletteList.getSelectedValue()));
			palette.addGL(add);
			GLButton favorite = new GLButton("Favorite");
			favorite.setBounds(110, 494, 94, 30);
			favorite.addActionListener(event -> nodeCreationPalette.toggleSelectedFavorite());
			palette.addGL(favorite);
			nodePaletteStatus = nodeCreationPalette.statusLabel();
			nodePaletteStatus.setText("Double-click a node to add it");
			refreshNodePalette();
		}

		private void refreshNodePalette() {
			if (nodePaletteList == null) return;
			GLNodePortDescriptor compatible = null;
			if (nodePaletteCompatible != null && nodePaletteCompatible.isSelected()) {
				GLNodeEndpoint endpoint = pendingNodeConnection == null ? null
						: pendingNodeConnection.endpoint();
				GLNodeId selected = endpoint == null ? selectedShowcaseNodeId()
						: endpoint.nodeId();
				if (selected != null) compatible = showcaseNodeGraph.snapshot().findNode(selected)
						.flatMap(node -> showcaseNodeTypes.find(node.typeId()))
						.flatMap(type -> endpoint == null
								? type.ports().stream().filter(port -> port.direction()
										== com.vingame.glswing.node.model.GLPortDirection.OUTPUT)
										.findFirst()
								: type.findPort(endpoint.portId())).orElse(null);
			}
			nodeCreationPalette.setCompatiblePort(compatible);
			nodeCreationPalette.refresh();
		}

		private void insertPaletteNode(GLNodeCatalogEntry entry) {
			if (entry == null || showcaseNodeBinding == null) return;
			PendingNodeConnection pending = pendingNodeConnection;
			int offset = nodePaletteInsertCount++ % 8;
			float x = pending != null ? pending.x() : requestedNodeX != null
					? requestedNodeX : 55 + offset * 28;
			float y = pending != null ? pending.y() : requestedNodeY != null
					? requestedNodeY : 350 + offset * 18;
			GLNodeId added = showcaseNodeBinding.addNode(entry.type().id(),
					entry.defaultProperties(), x, y);
			if (pending != null) {
				var desiredDirection = pending.direction() == GLNodePort.Direction.OUTPUT
						? com.vingame.glswing.node.model.GLPortDirection.INPUT
						: com.vingame.glswing.node.model.GLPortDirection.OUTPUT;
				GLNodePortDescriptor sourcePort = showcaseNodeGraph.snapshot()
						.findNode(pending.endpoint().nodeId())
						.flatMap(node -> showcaseNodeTypes.find(node.typeId()))
						.flatMap(type -> type.findPort(pending.endpoint().portId()))
						.orElseThrow();
				entry.type().ports().stream().filter(port -> port.direction() == desiredDirection
						&& port.valueTypeId().equals(sourcePort.valueTypeId()))
						.findFirst().ifPresent(port -> showcaseNodeGraph.edit(
								"Complete connection", edit -> edit.addConnection(
										new GLGraphConnection(GLNodeConnectionId.random(),
												pending.direction() == GLNodePort.Direction.OUTPUT
														? pending.endpoint()
														: new GLNodeEndpoint(added, port.id()),
												pending.direction() == GLNodePort.Direction.OUTPUT
														? new GLNodeEndpoint(added, port.id())
														: pending.endpoint()))));
				pendingNodeConnection = null;
				nodePaletteStatus.setText("Connection completed");
				nodePaletteCompatible.setSelected(false);
			}
			showcaseNodeCatalog.markUsed(entry.type().id());
			requestedNodeX = requestedNodeY = null;
			refreshNodePalette();
		}

		private void showNodeCreationMenu(float x, float y, float screenX, float screenY) {
			requestedNodeX = x;
			requestedNodeY = y;
			showNodeCreationMenu(screenX, screenY, entry -> true,
					this::insertPaletteNode);
		}

		private void showNodeCreationMenu(float screenX, float screenY,
				Predicate<GLNodeCatalogEntry> allowed,
				Consumer<GLNodeCatalogEntry> insertion) {
			if (nodeCreationMenu != null && nodeCreationMenu.isShowing())
				nodeCreationMenu.close();
			nodeCreationMenu = nodeCreationPalette.showPopup(host.parent(),
					screenX, screenY, allowed, null, insertion);
		}

		private void buildNodePropertyInspector(GLTabbedPane inspectorTabs) {
			ControlLayer properties = new ControlLayer("Property Inspector");
			inspectorTabs.addTab("Properties", properties);
			GLNodePropertySchemaRegistry schemas = new GLNodePropertySchemaRegistry();
			schemas.register("showcase.texture", List.of(
					new GLNumberNodeProperty("gain", "Gain", 0, 2, .01, false)));
			schemas.register("showcase.material-stack", List.of(
					new GLEnumNodeProperty("blend", "Blend mode",
							List.of("Normal", "Multiply", "Screen")),
					new GLBooleanNodeProperty("enabled", "Enabled")));
			nodePropertyInspector = new GLNodePropertyInspector(showcaseNodeBinding,
					schemas);
			GLScrollPane scroll = new GLScrollPane(nodePropertyInspector);
			scroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
			scroll.setVerticalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.AS_NEEDED);
			scroll.setContentTracksViewportWidth(true);
			scroll.setBounds(8, 40, 320, 510);
			properties.addGL(scroll);
		}

		private void configureNodeExecutionBackend() {
			nodeExecutionExecutor = Executors.newSingleThreadExecutor(operation -> {
				Thread thread = new Thread(operation, "glswing-showcase-node-execution");
				thread.setDaemon(true);
				return thread;
			});
			GLJavaExecutionBackend backend = new GLJavaExecutionBackend();
			backend.register("showcase.texture", (context, inputs, properties) -> {
				GLNodeValue feedback = inputs.getOrDefault("feedback", List.of(
						GLNodeValue.NullValue.INSTANCE)).get(0);
				String value = feedback == GLNodeValue.NullValue.INSTANCE
						? "Texture" : "Texture + feedback";
				return Map.of("color", GLNodeValue.of(value));
			});
			backend.register("showcase.material", (context, inputs, properties) ->
					Map.of("result", GLNodeValue.of("Shaded "
							+ inputs.get("base").get(0).toJavaValue())));
			backend.register("showcase.tint", (context, inputs, properties) ->
					Map.of("result", GLNodeValue.of("Tinted "
							+ inputs.get("base").get(0).toJavaValue())));
			backend.register("showcase.output", (context, inputs, properties) -> {
				observedNodeOutput.set(inputs.get("surface").get(0));
				return Map.of();
			});
			nodePlanController = new GLExecutionPlanController<>(backend,
					nodeExecutionExecutor, ignored -> { });
		}

		private static GLNodeImplementationRegistry showcaseImplementations(
				boolean incompatible) {
			GLNodeImplementationRegistry implementations =
					new GLNodeImplementationRegistry();
			implementations.register(new GLNodeImplementationDescriptor(
					"showcase.texture", "showcase.texture",
					GLNodeImplementationKind.STATEFUL, false, 2, 2));
			implementations.register(new GLNodeImplementationDescriptor(
					"showcase.tint", "showcase.tint", GLNodeImplementationKind.PURE,
					false, 2, 1));
			implementations.register(new GLNodeImplementationDescriptor(
					"showcase.material", incompatible ? "showcase.unavailable"
							: "showcase.material", GLNodeImplementationKind.PURE,
					false, 4, 3));
			implementations.register(new GLNodeImplementationDescriptor(
					"showcase.output", "showcase.output",
					GLNodeImplementationKind.EFFECT, true, 1, 0));
			return implementations;
		}

		private void compileShowcaseNodePlan(boolean incompatible) {
			if (showcaseNodeGraph == null || showcaseNodeTypes == null
					|| nodePlanController == null) return;
			GLNodeGraphAsset asset = new GLNodeGraphAsset("showcase.execution", 1,
					GLNodeGraphAssetKind.PROJECT, showcaseNodeGraph.snapshot(),
					new GLNodeEditorViewState(Map.of()));
			var compiled = GLExecutionPlanCompiler.compile(asset,
					showcaseCompositeRegistry, GLCompositeExpansionLimits.DEFAULT,
					showcaseNodeTypes, showcaseImplementations(incompatible),
					showcaseCyclePolicy.getSelectedItem(),
					new GLExecutionPlanOptions("frame"));
			if (!compiled.isValid()) {
				compiled.diagnostics().stream().flatMap(problem ->
						problem.nodeIds().stream()).findFirst().ifPresent(this::focusShowcaseNode);
				nodeExecutionStatus.setText("Compilation rejected");
				nodeExecutionStats.setText(compiled.diagnostics().size() + " problem(s)");
				nodeExecutionDetails.setText(compiled.diagnostics().stream()
						.map(problem -> problem.code() + "\n  " + problem.message())
						.reduce((left, right) -> left + "\n\n" + right)
						.orElse("Unknown compilation failure"));
				nodeRunButton.setEnabled(nodePlanController.currentPlan().isPresent());
				return;
			}
			GLExecutionPlan candidate = compiled.plan().orElseThrow();
			String retainedHash = nodePlanController.currentPlan()
					.map(GLExecutionPlan::hash).orElse(null);
			nodeExecutionStatus.setText(incompatible
					? "Testing incompatible update ..." : "Preparing plan ...");
			nodePlanController.prepareAndSwap(candidate).whenComplete((result, failure) ->
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						if (failure != null) {
							nodeExecutionStatus.setText("Preparation failed");
							nodeExecutionDetails.setText(failure.toString());
						} else if (result.status()
								== GLExecutionPlanSwapResult.Status.ACTIVATED) {
							nodeLastExecution = null;
							nodePausedAtBreakpoint = null;
							nodeExecutionStatus.setText("Active · "
									+ shortHash(candidate.hash()));
							nodeExecutionStats.setText(planStatistics(candidate));
							rebuildNodeExecutionTree(candidate);
							refreshNodeInspectorDetails();
						} else {
							String active = nodePlanController.currentPlan()
									.map(plan -> shortHash(plan.hash())).orElse("none");
							nodeExecutionStatus.setText("Update "
									+ result.status().name().toLowerCase(Locale.ROOT)
									+ " · active " + active + " retained");
							if (!result.diagnostics().isEmpty())
								nodeExecutionDetails.setText(result.diagnostics().stream()
										.map(problem -> problem.code() + "\n  "
												+ problem.message())
										.reduce((left, right) -> left + "\n\n" + right)
										.orElse("Update rejected"));
							if (retainedHash != null && nodePlanController.currentPlan()
									.map(GLExecutionPlan::hash).filter(retainedHash::equals).isEmpty())
								nodeExecutionStatus.setText("Retention check failed");
						}
						nodeRunButton.setEnabled(nodePlanController.currentPlan().isPresent());
					}));
		}

		private void runShowcaseNodeStep() {
			if (nodePlanController == null) return;
			var lease = nodePlanController.acquire();
			if (lease.isEmpty()) {
				nodeExecutionStatus.setText("No active plan");
				return;
			}
			nodeRunButton.setEnabled(false);
			nodeExecutionExecutor.execute(() -> {
				try (var active = lease.get()) {
					var result = active.prepared().evaluate();
					nodeLastExecution = result;
					nodePausedAtBreakpoint = null;
					GLNodeValue output = observedNodeOutput.get();
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						nodeExecutionStatus.setText("Executed step " + result.step()
								+ " · " + shortHash(active.plan().hash()));
						nodeExecutionOutput.setText("Last output: "
								+ (output == null ? "-" : output.toJavaValue()));
						refreshNodeInspectorDetails();
						nodeRunButton.setEnabled(true);
					});
				} catch (GLJavaExecutionException failure) {
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						focusShowcaseNode(visibleOperationNodeId(failure.operation()));
						nodeExecutionStatus.setText("Execution failed");
						nodeExecutionDetails.setText(formatBackendFailure(failure));
						nodeRunButton.setEnabled(true);
					});
				}
			});
		}

		private void advanceShowcaseNodeOperation(boolean continueToBreakpoint) {
			if (nodePlanController == null || nodeExecutionExecutor == null) return;
			var lease = nodePlanController.acquire();
			if (lease.isEmpty()) {
				nodeExecutionStatus.setText("No active plan");
				return;
			}
			Set<GLNodeId> breakpoints = Set.copyOf(nodeBreakpoints);
			nodeRunButton.setEnabled(false);
				nodeExecutionExecutor.execute(() -> {
				try (var active = lease.get()) {
					GLJavaExecutablePlan executable = active.prepared();
					GLNodeId resumedBreakpoint = nodePausedAtBreakpoint;
					GLJavaExecutionTrace lastTrace = null;
					GLJavaExecutionResult completed = null;
					GLExecutionOperation paused = null;
					do {
						var next = executable.nextOperation();
						GLNodeId breakpointNode = next.map(
								ShowcaseSurface::visibleOperationNodeId).orElse(null);
						if (continueToBreakpoint && next.isPresent()
								&& breakpoints.contains(breakpointNode)
								&& !breakpointNode.equals(resumedBreakpoint)) {
							paused = next.get();
							nodePausedAtBreakpoint = breakpointNode;
							break;
						}
						var progress = executable.executeNext();
						lastTrace = progress.trace().orElse(lastTrace);
						completed = progress.completedStep().orElse(null);
					} while (continueToBreakpoint && completed == null);
					if (paused == null)
						nodePausedAtBreakpoint = completed == null ? resumedBreakpoint : null;
					GLJavaExecutionTrace displayedTrace = lastTrace;
					GLJavaExecutionResult displayedResult = completed;
					GLExecutionOperation displayedPause = paused;
					if (completed != null) nodeLastExecution = completed;
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						if (displayedPause != null) {
							nodeExecutionStatus.setText("Paused before operation "
									+ (displayedPause.index() + 1));
							focusShowcaseNode(visibleOperationNodeId(displayedPause));
						} else if (displayedResult != null) {
							nodeExecutionStatus.setText("Completed step "
									+ displayedResult.step());
							GLNodeValue output = observedNodeOutput.get();
							nodeExecutionOutput.setText("Last output: "
									+ (output == null ? "-" : output.toJavaValue()));
						} else if (displayedTrace != null) {
							nodeExecutionStatus.setText("Executed operation "
									+ (displayedTrace.operation().index() + 1));
							focusShowcaseNode(visibleOperationNodeId(
									displayedTrace.operation()));
						}
						refreshNodeInspectorDetails(displayedTrace);
						nodeRunButton.setEnabled(true);
					});
				} catch (GLJavaExecutionException failure) {
					SwingUtilities.invokeLater(() -> {
						if (languageServerClosing) return;
						focusShowcaseNode(visibleOperationNodeId(failure.operation()));
						nodeExecutionStatus.setText("Execution failed");
						nodeExecutionDetails.setText(formatBackendFailure(failure));
						nodeRunButton.setEnabled(true);
					});
				}
			});
		}

		private void toggleNodeBreakpoint() {
			GLNodeId selected = selectedShowcaseNodeId();
			if (selected == null) {
				nodeExecutionStatus.setText("Select a node first");
				return;
			}
			if (!nodeBreakpoints.add(selected)) nodeBreakpoints.remove(selected);
			refreshNodeInspectorDetails();
		}

		private GLNodeId selectedShowcaseNodeId() {
			if (showcaseNodeEditor == null || showcaseNodeBinding == null
					|| showcaseNodeEditor.getActiveNode() == null) return null;
			return showcaseNodeBinding.findNodeId(showcaseNodeEditor.getActiveNode())
					.orElse(null);
		}

		private void focusShowcaseNode(GLNodeId id) {
			if (showcaseNodeEditor == null || showcaseNodeBinding == null || id == null)
				return;
			showcaseNodeBinding.findNodeView(id).ifPresent(node ->
					showcaseNodeEditor.setSelectedNodes(List.of(node)));
		}

		private static GLNodeId visibleOperationNodeId(GLExecutionOperation operation) {
			return operation.origin().filter(origin -> !origin.instances().isEmpty())
					.map(origin -> origin.instances().get(0).nodeId())
					.orElse(operation.nodeId());
		}

		private void rebuildNodeExecutionTree(GLExecutionPlan plan) {
			nodeExecutionTreeModel.clear();
			nodeTreeItemsByVisibleId.clear();
			PlanTreeItem root = new PlanTreeItem("plan", "Execution plan · "
					+ shortHash(plan.hash()), PlanTreeKind.PLAN, List.of(), null, null);
			nodeExecutionTreeModel.addRoot(root);
			Map<String, PlanTreeItem> composites = new HashMap<>();
			for (GLExecutionOperation operation : plan.operations()) {
				PlanTreeItem parent = root;
				List<GLCompositeOrigin.Instance> path = operation.origin()
						.map(GLCompositeOrigin::instances).orElse(List.of());
				for (int depth = 0; depth < path.size(); depth++) {
					List<GLCompositeOrigin.Instance> prefix = List.copyOf(
							path.subList(0, depth + 1));
					String key = "composite/" + prefix.stream()
							.map(instance -> instance.nodeId().toString())
							.reduce((left, right) -> left + "/" + right).orElse("");
					PlanTreeItem composite = composites.get(key);
					if (composite == null) {
						GLCompositeOrigin.Instance instance = prefix.get(prefix.size() - 1);
						composite = new PlanTreeItem(key, instance.nodeTypeId(),
								PlanTreeKind.COMPOSITE, prefix, null,
								prefix.get(0).nodeId());
						composites.put(key, composite);
						nodeExecutionTreeModel.addChild(parent, composite);
						nodeTreeItemsByVisibleId.putIfAbsent(prefix.get(0).nodeId(), composite);
					}
					parent = composite;
				}
				GLNodeId visible = path.isEmpty() ? operation.nodeId() : path.get(0).nodeId();
				PlanTreeItem leaf = new PlanTreeItem("operation/" + operation.index(),
						(operation.index() + 1) + " · " + operation.operationTypeId(),
						PlanTreeKind.OPERATION, path, operation, visible);
				nodeExecutionTreeModel.addChild(parent, leaf);
				if (path.isEmpty()) nodeTreeItemsByVisibleId.put(operation.nodeId(), leaf);
			}
			nodeExecutionTree.expandAll();
		}

		private void inspectPlanTreeSelection() {
			if (syncingInspectorSelection || nodeExecutionTree == null) return;
			PlanTreeItem item = nodeExecutionTree.getSelectedValue();
			if (item == null) return;
			nodeInspectedOperation = item.operation();
			if (item.visibleNodeId() != null) {
				syncingInspectorSelection = true;
				try { focusShowcaseNode(item.visibleNodeId()); }
				finally { syncingInspectorSelection = false; }
			}
			GLExecutionPlan plan = nodePlanController == null ? null
					: nodePlanController.currentPlan().orElse(null);
			if (plan == null) return;
			if (item.kind() == PlanTreeKind.COMPOSITE) {
				nodeExecutionDetails.setText(describeComposite(plan, item));
				return;
			}
			refreshNodeInspectorDetails();
		}

		private void openCompositeGraphTab(PlanTreeItem item) {
			if (item.compositePath().isEmpty() || showcaseCompositeRegistry == null) return;
			GLCompositeOrigin.Instance instance = item.compositePath()
					.get(item.compositePath().size() - 1);
			String assetId = instance.assetId();
			openCompositeAsset(instance.nodeId(), instance.nodeTypeId(), assetId);
		}

		private void openCompositeAsset(GLNodeId instanceId, String nodeTypeId,
				String assetId) {
			openCompositeAsset(instanceId, nodeTypeId, assetId, showcaseNodeEditor);
		}

		private void openCompositeAsset(GLNodeId instanceId, String nodeTypeId,
				String assetId, GLNodeEditor returnEditor) {
			GLContainer existing = openCompositeTabs.get(assetId);
			if (existing != null) {
				existing.bringToFront();
				return;
			}
			GLNodeGraphAsset asset = showcaseCompositeRegistry.findAsset(assetId)
					.orElse(null);
			if (asset == null || asset.compositeDescriptor() == null) return;
			GLContainer page = new GLContainer() {
				@Override protected void resized(float width, float height) {
					GLNodeWorkspace workspace = openCompositeWorkspaces.get(assetId);
					if (workspace != null) workspace.setBounds(0, 0, width, height);
				}
				@Override protected void record(GLContext2D context) {
					context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
							getGLTheme().values().getColor("palette.surface", SURFACE));
				}
			};
			page.setBounds(0, 0, nodeEditorPage.getWidth(), nodeEditorPage.getHeight());
			openCompositeTabs.put(assetId, page);
			GLNodeValueTypeRegistry valueTypes = new GLNodeValueTypeRegistry();
			valueTypes.register(new GLNodeValueTypeDescriptor("number", "Number"));
			valueTypes.register(new GLNodeValueTypeDescriptor("color", "Color"));
			valueTypes.register(new GLNodeValueTypeDescriptor("audio", "Audio"));
			GLNodeWorkspace workspace = new GLNodeWorkspace(showcaseNodeTypes,
					showcaseCompositeRegistry, showcaseNodeCatalog,
					(node, type) -> createShowcaseNodeView(type),
					showcaseCyclePolicy.getSelectedItem(), host.historyManager(),
					valueTypes, showcaseInputEditors);
			workspace.setBounds(0, 0, page.getWidth(), page.getHeight());
			workspace.editor().setConnectionColorInterpolator(GLColorInterpolation.OKLAB);
			workspace.setCompositeExtractionSupplier(() -> {
				int number = ++extractedCompositeCount;
				String type = "showcase.extracted." + number;
				return new GLNodeWorkspace.CompositeExtraction(type + ".asset", type,
						"Extracted Composite " + number, "Composites");
			});
			workspace.setChangeListener(() -> {
				refreshNodePalette();
				if (showcaseNodeBinding != null) showcaseNodeBinding.refresh();
				compileShowcaseNodePlan(false);
			});
			workspace.setAdditionalDependentGraphsSupplier(() ->
					List.of(showcaseNodeGraph));
			openCompositeWorkspaces.put(assetId, workspace);
			page.addGL(workspace);
			workspace.openDocument(nodeTypeId, new GLNodeGraphDocument(asset));
			GLButton close = new GLButton("×");
			close.setBounds(page.getWidth() - 38, 6, 30, 28);
			page.addGL(close);
			close.bringToFront();
			Runnable closeComposite = () -> {
				workspace.close();
				openCompositeWorkspaces.remove(assetId);
				openCompositeTabs.remove(assetId);
				nodeEditorPage.removeGL(page);
				GLNodeEditor focusTarget = returnEditor == null
						? showcaseNodeEditor : returnEditor;
				focusTarget.requestGLFocus(focusTarget);
			};
			workspace.setRootNavigationAction(closeComposite);
			close.addActionListener(event -> closeComposite.run());
			nodeEditorPage.addGL(page);
			page.bringToFront();
			workspace.editor().requestGLFocus(workspace.editor());
		}

		@SuppressWarnings("unused")
		private void openCompositeAssetLegacy(GLNodeId instanceId, String nodeTypeId,
				String assetId, GLNodeEditor returnEditor) {
			GLContainer existing = openCompositeTabs.get(assetId);
			if (existing != null) {
				existing.bringToFront();
				return;
			}
			GLNodeGraphAsset asset = showcaseCompositeRegistry.findAsset(assetId)
					.orElse(null);
			if (asset == null) return;
			GLContainer page = new GLContainer() {
				@Override protected void record(GLContext2D context) {
					context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
							getGLTheme().values().getColor("palette.surface", SURFACE));
				}
			};
			page.setBounds(0, 0, nodeEditorPage.getWidth(), nodeEditorPage.getHeight());
			openCompositeTabs.put(assetId, page);
			GLButton rootCrumb = new GLButton("Node Editor");
			rootCrumb.setBounds(10, 10, 112, 30);
			page.addGL(rootCrumb);
			GLLabel separator = new GLLabel(">");
			separator.setBounds(128, 10, 18, 30);
			page.addGL(separator);
			GLLabel current = new GLLabel(nodeTypeId + "  /  " + assetId);
			current.setBounds(150, 10, 760, 30);
			page.addGL(current);
			GLButton close = new GLButton("Close");
			close.setBounds(1050, 10, 96, 30);
			page.addGL(close);
			AtomicReference<Runnable> closeAction = new AtomicReference<>();
			GLNodeEditor nestedEditor = new GLNodeEditor() {
				@Override public void keyPressed(GLKeyEvent event) {
					super.keyPressed(event);
					if (!event.isConsumed()
							&& getGLInputMap().matches(GLInputActions.CANCEL, event)) {
						Runnable action = closeAction.get();
						if (action != null) action.run();
						event.consume();
					}
				}
			};
			nestedEditor.setBounds(10, 50, 816, 560);
			nestedEditor.setConnectionColorInterpolator(GLColorInterpolation.OKLAB);
			page.addGL(nestedEditor);
			GLNodeGraph nestedGraph = new GLNodeGraph(asset.graph());
			GLNodeEditorBinding nestedBinding = new GLNodeEditorBinding(nestedGraph,
					showcaseNodeTypes, nestedEditor,
					(node, type) -> createShowcaseNodeView(type),
					showcaseCyclePolicy.getSelectedItem(), asset.viewState());
			openCompositeBindings.put(assetId, nestedBinding);
			GLCompositeInterfaceEditor interfaceEditor =
					new GLCompositeInterfaceEditor(showcaseCompositeRegistry,
							showcaseNodeTypes, host.historyManager());
			interfaceEditor.setBounds(836, 50, 310, 560);
			interfaceEditor.setAssetId(assetId);
			page.addGL(interfaceEditor);
			GLNodeGraphEditingSession nestedEditing = new GLNodeGraphEditingSession(
					nestedBinding, host.historyManager());
			nestedEditor.setEditingActions(new GLNodeEditingActions() {
				private boolean hasModelSelection() {
					return nestedEditor.getSelectedNodes().stream()
							.anyMatch(node -> nestedBinding.findNodeId(node).isPresent());
				}
				@Override public boolean canCopy() {
					return hasModelSelection();
				}
				@Override public boolean canCut() { return canCopy(); }
				@Override public boolean canPaste() {
					return nestedEditing.hasClipboardContent();
				}
				@Override public boolean canDuplicate() { return canCopy(); }
				@Override public boolean canExtractComposite() { return canCopy(); }
				@Override public boolean canDelete() { return hasModelSelection(); }
				@Override public void copy() { nestedEditing.copySelection(); }
				@Override public void cut() { nestedEditing.cutSelection(); }
				@Override public void paste(float x, float y) {
					nestedEditing.paste(x, y);
				}
				@Override public void duplicate() {
					nestedEditing.duplicateSelection(28, 28);
				}
				@Override public com.vingame.glswing.node.GLNodePlacement beginPaste(
						float x, float y) { return nestedEditing.beginPaste(x, y); }
				@Override public com.vingame.glswing.node.GLNodePlacement beginDuplicate(
						float x, float y) { return nestedEditing.beginDuplicate(x, y); }
				@Override public com.vingame.glswing.node.GLNodePlacement beginMoveSelection(
						float x, float y) { return nestedEditing.beginMoveSelection(x, y); }
				@Override public void delete() { nestedEditing.removeSelection(); }
				@Override public void requestNodeCreation(float x, float y,
						float screenX, float screenY) {
					showNodeCreationMenu(screenX, screenY,
							entry -> !entry.type().id().equals(nodeTypeId), entry -> {
								nestedBinding.addNode(entry.type().id(),
										entry.defaultProperties(), x, y);
								showcaseNodeCatalog.markUsed(entry.type().id());
							});
				}
				@Override public boolean canOpenSelectedComposite() {
					GLNode selected = nestedEditor.getActiveNode();
					return selected != null && nestedBinding.findNodeId(selected)
							.flatMap(id -> nestedGraph.snapshot().findNode(id))
							.flatMap(node -> showcaseCompositeRegistry.findComposite(
									node.typeId())).isPresent();
				}
				@Override public void openSelectedComposite() {
					GLNode selected = nestedEditor.getActiveNode();
					GLNodeId id = selected == null ? null
							: nestedBinding.findNodeId(selected).orElse(null);
					GLGraphNode node = id == null ? null
							: nestedGraph.snapshot().findNode(id).orElse(null);
					if (node == null) return;
					showcaseCompositeRegistry.findComposite(node.typeId())
							.ifPresent(descriptor -> openCompositeAsset(id,
									node.typeId(), descriptor.assetId(), nestedEditor));
				}
				@Override public void extractComposite() {
					int number = ++extractedCompositeCount;
					String extractedType = "showcase.extracted." + number;
					nestedEditing.extractSelection(extractedType + ".asset",
							extractedType, "Extracted Composite " + number,
							"Composites", showcaseCompositeRegistry,
							showcaseNodeCatalog);
					refreshNodePalette();
				}
			});
			nestedEditor.setNodeCreationRequestListener((source, x, y, screenX, screenY) ->
					showNodeCreationMenu(screenX, screenY,
							entry -> !entry.type().id().equals(nodeTypeId), entry -> {
								nestedBinding.addNode(entry.type().id(),
										entry.defaultProperties(), x, y);
								showcaseNodeCatalog.markUsed(entry.type().id());
							}));
			addCompositeBoundaryNodes(nestedEditor, nestedBinding,
					asset.compositeDescriptor());
			interfaceEditor.setChangeListener(() -> {
				GLCompositeNodeDescriptor descriptor = showcaseCompositeRegistry
						.findAsset(assetId).map(GLNodeGraphAsset::compositeDescriptor)
						.orElse(null);
				if (descriptor == null) return;
				for (GLNode node : List.copyOf(nestedEditor.getNodes()))
					if (("Group Input".equals(node.getTitle())
							|| "Group Output".equals(node.getTitle()))
							&& !node.isTitleEditable()) nestedEditor.removeNode(node);
				addCompositeBoundaryNodes(nestedEditor, nestedBinding, descriptor);
				compileShowcaseNodePlan(false);
			});
			nestedGraph.addListener((source, change) -> {
				GLNodeGraphAsset currentAsset = showcaseCompositeRegistry.findAsset(assetId)
						.orElse(asset);
				showcaseCompositeRegistry.replaceAsset(new GLNodeGraphAsset(assetId,
						currentAsset.version(), currentAsset.kind(), change.after(),
						nestedBinding.captureViewState(), currentAsset.metadata(),
						currentAsset.compositeDescriptor()));
				compileShowcaseNodePlan(false);
			});
			Runnable closeComposite = () -> {
				GLNodeGraphAsset currentAsset = showcaseCompositeRegistry.findAsset(assetId)
						.orElse(asset);
				showcaseCompositeRegistry.replaceAsset(new GLNodeGraphAsset(assetId,
						currentAsset.version(), currentAsset.kind(), nestedGraph.snapshot(),
						nestedBinding.captureViewState(), currentAsset.metadata(),
						currentAsset.compositeDescriptor()));
				nestedBinding.close();
				openCompositeBindings.remove(assetId);
				openCompositeTabs.remove(assetId);
				nodeEditorPage.removeGL(page);
				GLNodeEditor focusTarget = returnEditor == null
						? showcaseNodeEditor : returnEditor;
				focusTarget.requestGLFocus(focusTarget);
			};
			closeAction.set(closeComposite);
			close.addActionListener(event -> closeComposite.run());
			rootCrumb.addActionListener(event -> closeComposite.run());
			nodeEditorPage.addGL(page);
			page.bringToFront();
			nestedEditor.requestGLFocus(nestedEditor);
		}

		private void addCompositeBoundaryNodes(GLNodeEditor editor,
				GLNodeEditorBinding binding, GLCompositeNodeDescriptor descriptor) {
			if (descriptor == null) return;
			addCompositeBoundaryNode(editor, binding, descriptor,
					GLPortDirection.INPUT, 24, 90);
			addCompositeBoundaryNode(editor, binding, descriptor,
					GLPortDirection.OUTPUT,
					Math.max(300, editor.getWidth() - 220), 90);
		}

		private void addCompositeBoundaryNode(GLNodeEditor editor,
				GLNodeEditorBinding binding, GLCompositeNodeDescriptor descriptor,
				GLPortDirection direction, float x, float y) {
			if (descriptor == null || hasCompositeBoundaryNode(editor, direction)) return;
			GLNode boundary = new GLNode(direction == GLPortDirection.INPUT
					? "Group Input" : "Group Output");
			boundary.setTitleEditable(false);
			boundary.setResizeMode(GLNodeResizeMode.NONE);
			List<GLNodePort[]> connections = new java.util.ArrayList<>();
			for (GLCompositePortMapping mapping : descriptor.portMappings()) {
				GLNodePortDescriptor port = mapping.publicPort();
				if (port.direction() != direction) continue;
				if (!mapping.isBound()) continue;
				GLNode internal = binding.findNodeView(
						mapping.internalEndpoint().nodeId()).orElse(null);
				if (internal == null) continue;
				GLNodePort internalPort = internal.findPort(
						mapping.internalEndpoint().portId());
				if (internalPort == null) continue;
				if (direction == GLPortDirection.INPUT) {
					GLNodePort output = boundary.addOutput(port.id(), new GLLabel(port.id()));
					connections.add(new GLNodePort[] { output, internalPort });
				} else {
					GLNodePort input = boundary.addInput(port.id(), new GLLabel(port.id()));
					connections.add(new GLNodePort[] { internalPort, input });
				}
			}
			if (connections.isEmpty()) return;
			editor.addNode(boundary, x, y);
			for (GLNodePort[] connection : connections)
				if (editor.canConnect(connection[0], connection[1]))
					editor.connect(connection[0], connection[1]);
			editor.setSelectedNode(boundary);
		}

		private static boolean hasCompositeBoundaryNode(GLNodeEditor editor,
				GLPortDirection direction) {
			String title = direction == GLPortDirection.INPUT
					? "Group Input" : "Group Output";
			return editor.getNodes().stream().anyMatch(node -> title.equals(node.getTitle())
					&& !node.isTitleEditable());
		}

		private void refreshNodeInspectorDetails() {
			refreshNodeInspectorDetails(null);
		}

		private void refreshNodeInspectorDetails(GLJavaExecutionTrace currentTrace) {
			if (nodePlanController == null || nodeExecutionDetails == null) return;
			GLExecutionPlan plan = nodePlanController.currentPlan().orElse(null);
			if (plan == null) return;
			GLNodeId selected = selectedShowcaseNodeId();
			if (nodeBreakpointButton != null) nodeBreakpointButton.setText(selected != null
					&& nodeBreakpoints.contains(selected) ? "Remove break" : "Add break");
			if (nodeInspectedOperation != null) {
				GLJavaExecutionTrace trace = traceFor(nodeInspectedOperation, currentTrace);
				nodeExecutionDetails.setText(describeOperation(plan,
						nodeInspectedOperation, trace, nodeLastExecution));
				return;
			}
			if (selected == null) {
				nodeExecutionDetails.setText(describePlan(plan));
				return;
			}
			GLExecutionOperation operation = plan.operationsByNodeId().get(selected);
			if (operation == null) {
				PlanTreeItem composite = nodeTreeItemsByVisibleId.get(selected);
				nodeExecutionDetails.setText(composite != null
						&& composite.kind() == PlanTreeKind.COMPOSITE
								? describeComposite(plan, composite)
								: "SELECTED NODE\n" + selected
										+ "\n\nThe node was folded or removed from this plan.");
				return;
			}
			GLJavaExecutionTrace trace = traceFor(operation, currentTrace);
			nodeExecutionDetails.setText(describeOperation(plan, operation, trace,
					nodeLastExecution));
		}

		private GLJavaExecutionTrace traceFor(GLExecutionOperation operation,
				GLJavaExecutionTrace currentTrace) {
			if (currentTrace != null && currentTrace.operation().nodeId()
					.equals(operation.nodeId())) return currentTrace;
			return nodeLastExecution == null ? null : nodeLastExecution.traces().stream()
					.filter(value -> value.operation().nodeId().equals(operation.nodeId()))
					.findFirst().orElse(null);
		}

		private static String planStatistics(GLExecutionPlan plan) {
			var statistics = plan.statistics();
			return statistics.scheduledOperations() + " ops · "
					+ statistics.temporarySlots() + " temp · "
					+ statistics.stateSlots() + " state · "
					+ statistics.maximumLatency() + " " + plan.latencyUnitId();
		}

		private static String describePlan(GLExecutionPlan plan) {
			StringBuilder text = new StringBuilder("PLAN ")
					.append(shortHash(plan.hash())).append('\n')
					.append("Source nodes: ").append(plan.statistics().sourceNodes())
					.append("\nRetained: ").append(plan.statistics().retainedNodes())
					.append("\nFolded: ").append(plan.statistics().foldedOperations())
					.append("\nRemoved: ").append(plan.statistics().sourceNodes()
							- plan.statistics().retainedNodes())
					.append("\nCost: ").append(plan.statistics().estimatedOperationCost())
					.append("\n\nSCHEDULE\n");
			for (var operation : plan.operations()) {
				text.append(operation.index() + 1).append("  ")
						.append(operation.operationTypeId()).append("\n   node ")
						.append(operation.nodeId().toString(), 0, 8)
						.append(" · latency ").append(operation.outputLatency())
						.append(' ').append(plan.latencyUnitId()).append('\n');
				for (var input : operation.inputs())
					if (input.compensationLatency() > 0)
						text.append("   +").append(input.compensationLatency())
								.append(" align ").append(input.portId()).append('\n');
				operation.origin().ifPresent(origin -> text.append("   origin ")
						.append(origin.sourceAssetId()).append('\n'));
			}
			return text.toString().stripTrailing();
		}

		private String describeComposite(GLExecutionPlan plan, PlanTreeItem item) {
			List<GLExecutionOperation> operations = plan.operations().stream()
					.filter(operation -> operation.origin().map(origin ->
							origin.instances().size() >= item.compositePath().size()
									&& origin.instances().subList(0,
											item.compositePath().size())
										.equals(item.compositePath())).orElse(false))
					.toList();
			long estimatedCost = operations.stream()
					.mapToLong(GLExecutionOperation::estimatedCost).sum();
			long measuredNanos = nodeLastExecution == null ? 0
					: nodeLastExecution.traces().stream()
							.filter(trace -> operations.contains(trace.operation()))
							.mapToLong(GLJavaExecutionTrace::durationNanos).sum();
			long stateSlots = plan.values().stream().filter(value ->
					value.storage() == com.vingame.glswing.node.execution.GLExecutionStorage.STATE
							&& operations.stream().anyMatch(operation ->
									operation.nodeId().equals(value.producerNodeId())))
					.count();
			StringBuilder text = new StringBuilder("COMPOSITE\n")
					.append(item.label()).append("\npath ")
					.append(item.compositePath().stream()
							.map(instance -> instance.nodeTypeId() + ":"
									+ instance.nodeId().toString().substring(0, 8))
							.reduce((left, right) -> left + " > " + right).orElse("root"))
					.append("\n\ninternal operations ").append(operations.size())
					.append("\nestimated cost ").append(estimatedCost)
					.append("\nstate slots ").append(stateSlots);
			if (nodeLastExecution != null)
				text.append("\nmeasured total ").append(formatDuration(measuredNanos));
			text.append("\n\nSCHEDULE\n");
			for (GLExecutionOperation operation : operations)
				text.append(operation.index() + 1).append(" · ")
						.append(operation.operationTypeId()).append("\n");
			return text.toString().stripTrailing();
		}

		private static String describeOperation(GLExecutionPlan plan,
				GLExecutionOperation operation, GLJavaExecutionTrace trace,
				GLJavaExecutionResult result) {
			StringBuilder text = new StringBuilder("SELECTED OPERATION ")
					.append(operation.index() + 1).append('\n')
					.append(operation.nodeTypeId()).append("\nnode ")
					.append(operation.nodeId()).append("\nkind ")
					.append(operation.kind()).append("\nlatency ")
					.append(operation.processingLatency()).append(" -> ")
					.append(operation.outputLatency()).append(' ')
					.append(plan.latencyUnitId()).append("\nestimated cost ")
					.append(operation.estimatedCost());
			if (trace != null) text.append("\nmeasured ")
					.append(formatDuration(trace.durationNanos()));
			text.append("\n\nINPUTS\n");
			if (operation.inputs().isEmpty()) text.append("-\n");
			for (var input : operation.inputs()) {
				text.append(input.portId()).append(" [")
						.append(input.timing().name().toLowerCase(Locale.ROOT))
						.append("] = ");
				List<GLNodeValue> values = trace == null ? null
						: trace.inputs().get(input.portId());
				text.append(values == null ? "not evaluated" : formatNodeValues(values))
						.append('\n');
			}
			text.append("\nOUTPUTS\n");
			if (operation.outputs().isEmpty()) text.append("-");
			for (var output : operation.outputs().entrySet()) {
				GLNodeValue value = trace == null ? null : trace.outputs().get(output.getKey());
				if (value == null && result != null)
					value = result.find(output.getValue()).orElse(null);
				var storage = plan.values().stream()
						.filter(candidate -> candidate.ref().equals(output.getValue()))
						.findFirst().orElse(null);
				text.append(output.getKey()).append(" = ")
						.append(value == null ? "not evaluated" : formatNodeValue(value));
				if (storage != null) text.append("\n  ")
						.append(storage.storage().name().toLowerCase(Locale.ROOT))
						.append(" slot ").append(storage.storageSlot());
				text.append('\n');
			}
			operation.origin().ifPresent(origin -> text.append("\nORIGIN\n")
					.append(origin.sourceAssetId()).append('\n')
					.append(origin.instances().isEmpty() ? "root node"
							: origin.instances().stream().map(instance -> instance.assetId()
									+ ":" + instance.nodeId().toString().substring(0, 8))
									.reduce((left, right) -> left + " > " + right).orElse("")));
			return text.toString().stripTrailing();
		}

		private static String formatNodeValues(List<GLNodeValue> values) {
			return values.stream().map(ShowcaseSurface::formatNodeValue)
					.reduce((left, right) -> left + ", " + right).orElse("-");
		}

		private static String formatNodeValue(GLNodeValue value) {
			Object result = value.toJavaValue();
			return result == null ? "null" : String.valueOf(result);
		}

		private static String formatDuration(long nanos) {
			if (nanos < 1_000) return nanos + " ns";
			if (nanos < 1_000_000) return String.format(Locale.ROOT, "%.2f us",
					nanos / 1_000.0);
			return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
		}

		private static String formatBackendFailure(GLJavaExecutionException failure) {
			var diagnostic = failure.diagnostic();
			return "BACKEND ERROR\n" + diagnostic.code() + "\nnode "
					+ failure.operation().nodeId() + "\noperation "
					+ (failure.operation().index() + 1) + "\n\n"
					+ diagnostic.message()
					+ diagnostic.technicalDetails().map(value -> "\n\n" + value)
							.orElse("");
		}

		private static String shortHash(String hash) {
			return hash.substring(0, Math.min(8, hash.length()));
		}

		private static void registerShowcaseNodeTypes(GLNodeTypeRegistry types,
				GLPortTiming materialTiming) {
			types.unregister("showcase.texture");
			types.unregister("showcase.tint");
			types.unregister("showcase.material");
			types.unregister("showcase.material-stack");
			types.unregister("showcase.output");
			types.register(new GLNodeTypeDescriptor("showcase.texture",
					GLNodePortDescriptor.input("feedback", "color", false, 1),
					GLNodePortDescriptor.output("color", "color")));
			types.register(new GLNodeTypeDescriptor("showcase.material",
					GLNodePortDescriptor.input("base", "color", true, 1),
					GLNodePortDescriptor.output("result", "color", materialTiming,
							Integer.MAX_VALUE)));
			types.register(new GLNodeTypeDescriptor("showcase.tint",
					GLNodePortDescriptor.input("base", "color", true, 1),
					GLNodePortDescriptor.output("result", "color")));
			types.register(new GLNodeTypeDescriptor("showcase.material-stack",
					GLNodePortDescriptor.input("base", "color", true, 1),
					GLNodePortDescriptor.output("result", "color", materialTiming,
							Integer.MAX_VALUE)));
			types.register(new GLNodeTypeDescriptor("showcase.output",
					GLNodePortDescriptor.input("surface", "color", true, 1)));
		}

		private GLNodeInputEditorRegistry createShowcaseInputEditors() {
			GLNodeInputEditorRegistry editors = new GLNodeInputEditorRegistry();
			editors.register(new GLNodeInputEditorDescriptor(
					"showcase.color-picker", "Color picker", Set.of("color"), Map.of()),
					context -> {
						com.vingame.glswing.ui.GLColorChooser chooser =
								new com.vingame.glswing.ui.GLColorChooser(new Color(84, 120, 181));
						chooser.setToolTipText(context.port().id());
						return chooser;
					});
			return editors;
		}

		private GLCompositeGraphRegistry createShowcaseCompositeRegistry(
				GLPortTiming materialTiming) {
			GLNodeGraphAsset previous = showcaseCompositeRegistry == null ? null
					: showcaseCompositeRegistry.findAsset("showcase.material-stack.asset")
							.orElse(null);
			GLCompositeNodeDescriptor descriptor = new GLCompositeNodeDescriptor(
					"showcase.material-stack", "showcase.material-stack.asset", 1,
					List.of(new GLCompositePortMapping(
							GLNodePortDescriptor.input("base", "color", true, 1),
							new GLNodeEndpoint(showcaseTintNodeId, "base")),
							new GLCompositePortMapping(GLNodePortDescriptor.output(
									"result", "color", materialTiming, Integer.MAX_VALUE),
									new GLNodeEndpoint(showcaseMaterialLeafNodeId, "result"))));
			var graph = previous == null
					? new com.vingame.glswing.node.model.GLNodeGraphSnapshot(
							com.vingame.glswing.node.model.GLNodeGraphId.random(), 0,
							List.of(new GLGraphNode(showcaseTintNodeId, "showcase.tint"),
									new GLGraphNode(showcaseMaterialLeafNodeId,
											"showcase.material")),
							List.of(new GLGraphConnection(showcaseCompositeConnectionId,
									new GLNodeEndpoint(showcaseTintNodeId, "result"),
									new GLNodeEndpoint(showcaseMaterialLeafNodeId, "base"))))
					: previous.graph();
			GLNodeEditorViewState viewState = previous == null
					? new GLNodeEditorViewState(Map.of(
							showcaseTintNodeId, new GLNodeViewState(180, 170),
							showcaseMaterialLeafNodeId, new GLNodeViewState(570, 170)))
					: previous.viewState();
			GLNodeGraphAsset asset = new GLNodeGraphAsset(
					"showcase.material-stack.asset", 1,
					GLNodeGraphAssetKind.COMPOSITE,
					graph, viewState, Map.of(), descriptor);
			GLCompositeGraphRegistry registry = new GLCompositeGraphRegistry();
			registry.registerAsset(asset);
			if (showcaseCompositeRegistry != null)
				for (GLNodeGraphAsset existing : showcaseCompositeRegistry.assets())
					if (!existing.id().equals(asset.id())) registry.registerAsset(existing);
			return registry;
		}

		private void updateShowcaseCompositeTiming(GLPortTiming timing) {
			if (showcaseCompositeRegistry == null) return;
			GLNodeGraphAsset asset = showcaseCompositeRegistry
					.findAsset("showcase.material-stack.asset").orElse(null);
			if (asset == null || asset.compositeDescriptor() == null) return;
			GLCompositeNodeDescriptor old = asset.compositeDescriptor();
			List<GLCompositePortMapping> ports = old.portMappings().stream().map(mapping -> {
				GLNodePortDescriptor port = mapping.publicPort();
				if (port.direction() != GLPortDirection.OUTPUT) return mapping;
				GLNodePortDescriptor updated = new GLNodePortDescriptor(port.id(),
						port.direction(), port.valueTypeId(), timing, port.required(),
						port.maximumConnections());
				return new GLCompositePortMapping(updated, mapping.internalEndpoint(),
						mapping.presentation());
			}).toList();
			GLCompositeNodeDescriptor descriptor = new GLCompositeNodeDescriptor(
					old.nodeTypeId(), old.assetId(), old.minimumAssetVersion(), ports,
					old.properties(), old.propertyMappings());
			showcaseCompositeRegistry.replaceAsset(new GLNodeGraphAsset(asset.id(),
					asset.version(), asset.kind(), asset.graph(), asset.viewState(),
					asset.metadata(), descriptor));
			showcaseNodeTypes.replace(descriptor.nodeType());
		}

		private static GLNode createShowcaseNodeView(GLNodeTypeDescriptor type) {
			if (type.id().startsWith("showcase.extracted.")) {
				GLNode node = new GLNode("Extracted Composite");
				node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
				for (GLNodePortDescriptor port : type.ports())
					if (port.direction() == com.vingame.glswing.node.model.GLPortDirection.INPUT)
						node.addInput(port.id(), new GLLabel(port.id()));
					else node.addOutput(port.id(), new GLLabel(port.id()));
				return node;
			}
			return switch (type.id()) {
				case "showcase.texture" -> {
					GLNode node = new GLNode("Texture");
					node.setResizeMode(GLNodeResizeMode.BOTH);
					node.addInput("feedback", new GLLabel("Feedback"));
					node.addInput("scale", GLSlider.fixed("Scale", 0.1, 8, 1));
					node.addOutput("color", new GLLabel("Mapped Color"));
					yield node;
				}
				case "showcase.material", "showcase.material-stack" -> {
					GLNode node = new GLNode("Material");
					node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
					node.addInput("roughness",
							GLSlider.fixed("Roughness", 0, 1, 0.42));
					GLComboBox<String> blend = new GLComboBox<>(
							List.of("Linear", "Multiply", "Screen"));
					GLNodePorts base = node.addInputOutput("base", blend, "result");
					base.input().setContentHiddenWhenConnected(true);
					yield node;
				}
				case "showcase.tint" -> {
					GLNode node = new GLNode("Tint");
					node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
					node.addInput("base", new GLLabel("Base Color"));
					node.addOutput("result", new GLLabel("Tinted Color"));
					yield node;
				}
				case "showcase.output" -> {
					GLNode node = new GLNode("Output");
					node.setResizeMode(GLNodeResizeMode.NONE);
					node.addInput("surface", new GLLabel("Surface"));
					node.addInput("alpha", GLSlider.fixed("Alpha", 0, 1, 1));
					yield node;
				}
				default -> throw new IllegalArgumentException(
						"Unsupported showcase node type: " + type.id());
			};
		}

		private static void showNodeValidation(GLLabel label,
				com.vingame.glswing.node.model.GLNodeGraphValidationResult result) {
			if (result.isValid()) {
				label.setText("Valid graph - ready for compilation");
				return;
			}
			String messages = result.diagnostics().stream()
					.map(problem -> problem.message()).distinct()
					.reduce((first, second) -> first + "; " + second)
					.orElse("Invalid graph");
			label.setText("Invalid - " + messages);
		}

		private void openShowcaseDialog(GLParent owner,
				GLDialog.Modality modality, int level) {
			AtomicReference<GLDialog> self = new AtomicReference<>();
			GLContainer content = dialogContent(level, self);
			String title = level == 1 ? "GLSwing Dialog"
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
