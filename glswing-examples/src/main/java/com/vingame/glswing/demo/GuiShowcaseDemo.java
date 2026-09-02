package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
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

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileDialogs;
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
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLabelStyle;
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
		lspCommand = languageServerCommand(args);
		SwingUtilities.invokeLater(GuiShowcaseDemo::showWindow);
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
			@Override
			public void windowClosing(WindowEvent event) {
				statistics.stop();
				panel.closeThemeWatcher();
				panel.closeLanguageServer();
				panel.destroy();
				frame.dispose();
				System.exit(0);
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

	static final class ShowcaseSurface extends GLContainer {
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
		private GLMenuItem undoItem;
		private GLMenuItem redoItem;
		private boolean syncingThemeRadius;
		private GLCodeEditor codeEditor;
		private final GLTabbedPane codeTabs = new GLTabbedPane();
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
			headerLayer.setBounds(0, 0, DESIGN_WIDTH, 105);
			addGL(headerLayer);
			buildMenuBar();
			buildHeaderLayer();
			buildBasicsLayer();
			buildValueLayer();
			buildSelectionLayer();
			buildContainersPage();
			buildNodeEditorPage();
			buildCodePage();
			workspaceTabs.addTab("Controls", controlsPage);
			workspaceTabs.addTab("Menus & Containers", containersPage);
			workspaceTabs.addTab("Node Editor", nodeEditorPage);
			workspaceTabs.addTab("Code Editor", codePage);
			workspaceTabs.addSelectionListener((source, oldIndex, newIndex) -> {
				closeTransientPopups();
				host.report("Showcase: " + source.getTitleAt(newIndex));
			});
			workspaceTabs.setBounds(12, 105, DESIGN_WIDTH - 24,
					DESIGN_HEIGHT - 111);
			addGL(workspaceTabs);
			host.historyManager().addListener(manager -> updateHistoryActions());
			updateHistoryActions();
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
			codeTabs.addTab("HelloGLSwing.java", codeEditor);
			editorsByUri.put(initialUri, codeEditor);
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
				lspStatus.setText("Java LSP · Definition is in "
						+ tabName(location.uri()));
				return;
			}
			openProfileSource(location);
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
						int tab = codeTabs.addTab(tabName(location.uri()), editor);
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
			GLMenuItem create = fileMenu.addItem("Neu",
					event -> host.report("Datei > Neu"));
			create.setIcon(ACTION_ICON);
			create.setAcceleratorText("Ctrl+N");
			GLPopupMenu openDialogs = new GLPopupMenu();
			openDialogs.addItem("Systemdialog ...",
					event -> showOpenDialog(nativeFileDialogs, "Native"));
			openDialogs.addItem("GLSwing-Dialog ...",
					event -> showOpenDialog(themedFileDialogs, "Themed"));
			GLMenuItem open = fileMenu.addSubMenu("Öffnen", openDialogs);
			open.setIcon(LINK_ICON);
			GLPopupMenu saveDialogs = new GLPopupMenu();
			saveDialogs.addItem("Systemdialog ...",
					event -> showSaveDialog(nativeFileDialogs, "Native"));
			saveDialogs.addItem("GLSwing-Dialog ...",
					event -> showSaveDialog(themedFileDialogs, "Themed"));
			GLMenuItem saveAs = fileMenu.addSubMenu("Speichern unter", saveDialogs);
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

			undoItem = editMenu.addItem("Rueckgaengig",
					event -> host.historyManager().undo());
			undoItem.setAcceleratorText("Ctrl+Z");
			redoItem = editMenu.addItem("Wiederholen",
					event -> host.historyManager().redo());
			redoItem.setAcceleratorText("Ctrl+Shift+Z");

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

		private void showOpenDialog(GLFileDialogs dialogs, String backendName) {
			dialogs.show(GLFileDialogRequest.open().title("Open source file")
					.filter("Java source", "java").filter("All text files", "txt", "md")
					.build(), host.parent()).whenComplete((result, failure) ->
					SwingUtilities.invokeLater(() -> host.report(backendName + ": "
							+ (failure != null ? "file dialog failed: " + failure.getMessage()
							: result.approved() ? "selected " + result.selectedPath()
									: "file dialog cancelled"))));
		}

		private void showSaveDialog(GLFileDialogs dialogs, String backendName) {
			dialogs.show(GLFileDialogRequest.save().title("Save example")
					.suggestedFileName("example.java").filter("Java source", "java")
					.build(), host.parent()).whenComplete((result, failure) ->
					SwingUtilities.invokeLater(() -> host.report(backendName + ": "
							+ (failure != null ? "file dialog failed: " + failure.getMessage()
							: result.approved() ? "save target " + result.selectedPath()
									: "file dialog cancelled"))));
		}

		private void updateHistoryActions() {
			if (undoItem == null || redoItem == null)
				return;
			GLHistoryManager history = host.historyManager();
			undoItem.setEnabled(history.canUndo());
			redoItem.setEnabled(history.canRedo());
			undoItem.setText(history.canUndo()
					? "Rueckgaengig: " + history.getUndoName() : "Rueckgaengig");
			redoItem.setText(history.canRedo()
					? "Wiederholen: " + history.getRedoName() : "Wiederholen");
		}

		void updateLocalizedMenuText() {
			GLLocalizer text = host.localizer();
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

			ControlLayer scrolling = controlLayer("ScrollPane",
					610, 28, 450, 500, containersPage);
			GLContainer content = new PassiveLayer();
			content.setPreferredGLSize(760, 620);
			for (int index = 0; index < 10; index++) {
				GLButton item = new GLButton("Scrollbarer Inhalt " + (index + 1));
				addControl(content, item, 24 + (index % 2) * 360,
						24 + (index / 2) * 105, 300, 34);
			}
			GLScrollPane scrollPane = new GLScrollPane(content);
			scrollPane.setScrollUnit(24);
			addControl(scrolling, scrollPane, 24, 56, 402, 400);
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
