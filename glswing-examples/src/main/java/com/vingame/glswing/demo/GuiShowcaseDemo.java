package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLLabelStyle;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuBarItem;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLPopupTextOverflow;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLSliderGroup;
import com.vingame.glswing.ui.GLTabbedPane;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLTextInputFormats;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeWatcher;
import com.vingame.glswing.ui.GLThemes;

/** Extensible fixed-layout showcase for every public GLSwing GUI control. */
public final class GuiShowcaseDemo {

	private static final int TARGET_FPS = 144;
	static final float DESIGN_WIDTH = 1180;
	static final float DESIGN_HEIGHT = 720;

	private GuiShowcaseDemo() {
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
		void watchShowcaseTheme(String fileName, GLTheme fallback, String nameKey);
		GLLocalizer localizer();
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GUI Showcase / GLJPanel");
		SwingUtilities.invokeLater(GuiShowcaseDemo::showWindow);
	}

	private static void showWindow() {
		ShowcasePanel panel = new ShowcasePanel();
		panel.setPreferredSize(new Dimension(1260, 800));
		JLabel status = new JLabel("GUI-Showcase startet ...",
				SwingConstants.CENTER);
		FPSAnimator animator = new FPSAnimator(panel, TARGET_FPS, true);
		GLDemoErrorReporter.attach(animator);
		Timer statistics = new Timer(250,
				event -> status.setText(panel.performanceText()));

		AtomicBoolean closing = new AtomicBoolean();
		JFrame frame = new JFrame("GLSwingNext - GUI Showcase");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (!closing.compareAndSet(false, true))
					return;
				panel.closeThemeWatcher();
				statistics.stop();
				Thread shutdown = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "GL-gui-showcase-stop");
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

	static final class ShowcasePanel extends GLSwingPanel
			implements ShowcaseHost {
		private static final long serialVersionUID = 1L;
		private static final float MIN_ZOOM = 0.45f;
		private static final float MAX_ZOOM = 2.5f;
		private static final String SHOWCASE_BUNDLE =
			"com.vingame.glswing.demo.languages.Showcase";
		private static final Path THEME_DIRECTORY = Path.of("resources", "com",
				"vingame", "glswing", "themes").toAbsolutePath().normalize();

		private final ShowcaseSurface surface = new ShowcaseSurface(this);
		private final ZoomLayer zoomLayer = new ZoomLayer(this, surface);
		private volatile float zoom = 1;
		private volatile String lastAction =
				"Strg + Mausrad: Zoom | Strg + 0: 100 %";
		private GLThemeWatcher themeWatcher;

		ShowcasePanel() {
			this(DEFAULT_ANTIALIASING_SAMPLES);
		}

		ShowcasePanel(int antialiasingSamples) {
			super(antialiasingSamples);
			surface.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
			zoomLayer.setBounds(0, 0, DESIGN_WIDTH, DESIGN_HEIGHT);
			zoomLayer.addGL(surface);
			addGL(zoomLayer);
			addGL(surface.menuBar);
			watchShowcaseTheme("dark.gltheme", GLThemes.DARK,
					"showcase.theme.dark");
			setLanguage(Locale.GERMAN);
			addMouseWheelListener(event -> {
				if (!event.isControlDown())
					return;
				event.consume();
				double factor = Math.pow(1.15,
						-event.getPreciseWheelRotation());
				setZoom((float) (zoom * factor));
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
			float menuHeight = surface.menuBar.getPreferredGLSize().height();
			surface.menuBar.setBounds(0, 0, getWidth(), menuHeight);
		}

		@Override
		public void setZoom(float zoom) {
			float resolved = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
			if (Float.compare(this.zoom, resolved) == 0)
				return;
			surface.closeTransientPopups();
			this.zoom = resolved;
			lastAction = String.format("Zoom auf %.0f %% gesetzt", resolved * 100);
			repaint();
		}

		@Override
		public void report(String message) {
			lastAction = message;
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
				boolean hotReloaded) {
			float radius = (float) surface.themeRadiusSlider.getValue();
			setGLTheme(theme.withCornerRadius(radius));
			lastAction = getGLLocalizer().text("showcase.status.theme",
					getGLLocalizer().text(nameKey))
					+ (hotReloaded ? " | Hot-Reload" : "");
		}

		@Override
		public void watchShowcaseTheme(String fileName, GLTheme fallback,
				String nameKey) {
			closeThemeWatcher();
			Path file = THEME_DIRECTORY.resolve(fileName);
			if (!Files.isRegularFile(file)) {
				applyShowcaseTheme(fallback, nameKey, false);
				return;
			}
			try {
				themeWatcher = GLThemeWatcher.watch(file,
						theme -> applyShowcaseTheme(theme, nameKey, true),
						error -> lastAction = "Theme-Fehler: " + error.getMessage());
			} catch (GLThemeLoadException error) {
				applyShowcaseTheme(fallback, nameKey, false);
				lastAction = "Theme-Watcher nicht aktiv: " + error.getMessage();
			}
		}

		void closeThemeWatcher() {
			if (themeWatcher == null)
				return;
			themeWatcher.close();
			themeWatcher = null;
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
			return formatPerformance("GLJPanel", lastAction, zoom,
					getFramesPerSecond(), getFrameProfile(), getLastDrawCallCount(),
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
			float menuHeight = surface.menuBar.getHeight();
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
		private final GLPopupMenu fileMenu = new GLPopupMenu();
		private final GLPopupMenu viewMenu = new GLPopupMenu();
		private final GLPopupMenu helpMenu = new GLPopupMenu();
		private final GLPopupMenu languageMenu = new GLPopupMenu();
		private final GLPopupMenu themeMenu = new GLPopupMenu();
		private final GLPopupMenu standalonePopup = new GLPopupMenu();
		private final GLComboBox<String> normalCombo =
				new GLComboBox<>(List.of("Rot", "Gruen", "Blau", "Orange"));
		private final GLComboBox<String> nestedCombo = new GLComboBox<>();
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
		private GLMenuItem defaultThemeItem;
		private GLMenuItem darkThemeItem;
		private GLMenuItem lightThemeItem;

		ShowcaseSurface(ShowcaseHost host) {
			this.host = host;
			buildMenuBar();

			addLabel(this, "GLSwingNext GUI Showcase", TITLE_STYLE,
					28, 43, 500, 34);
			addLabel(this,
					"Feste 1180 x 720 Designflaeche - alle Zustandswechsel sind interaktiv",
					SUBTITLE_STYLE, 30, 75, 490, 22);
			GLButton resetZoom = new GLButton("Zoom 100 %");
			resetZoom.addActionListener(event -> host.setZoom(1));
			addControl(this, resetZoom, 1015, 52, 130, 26);
			themeRadiusSlider.setStep(1);
			themeRadiusSlider.setDefaultValue(4);
			themeRadiusSlider.setValueFormatter(
					value -> String.format("%.1f px", value));
			themeRadiusSlider.addChangeListener(event ->
					host.setThemeCornerRadius((float) themeRadiusSlider.getValue()));
			addControl(this, themeRadiusSlider, 765, 52, 225, 26);
			renderResolutionSlider.setStep(5);
			renderResolutionSlider.setDefaultValue(100);
			renderResolutionSlider.setValueFormatter(
					value -> String.format("%.0f %%", value));
			renderResolutionSlider.addChangeListener(event ->
					host.setRenderResolutionScale(
							(float) renderResolutionSlider.getValue() / 100f));
			addControl(this, renderResolutionSlider, 535, 52, 215, 26);

			buildBasicsSection();
			buildValueSection();
			buildSelectionSection();
		}

		private void buildMenuBar() {
			GLMenuItem create = fileMenu.addItem("Neu",
					event -> host.report("Datei > Neu"));
			create.setAcceleratorText("Ctrl+N");
			fileMenu.addItem("Oeffnen ...",
					event -> host.report("Datei > Oeffnen"));
			fileMenu.addSeparator();
			GLPopupMenu export = new GLPopupMenu();
			export.addItem("PNG",
					event -> host.report("Export > PNG"));
			export.addItem("SVG",
					event -> host.report("Export > SVG"));
			fileMenu.addSubMenu("Exportieren", export);
			GLMenuItem unavailable = fileMenu.addItem("Nicht verfuegbar");
			unavailable.setEnabled(false);

			viewMenu.addItem("Zoom 100 %",
					event -> host.setZoom(1));
			viewMenu.addItem("Zoom 150 %",
					event -> host.setZoom(1.5f));
			viewMenu.addItem("Zoom 75 %",
					event -> host.setZoom(0.75f));
			helpMenu.addItem("Ueber GLSwingNext",
					event -> host.report("GPU-gerenderte Lightweight-GUI"));

			menuBar.addMenu("Datei", fileMenu);
			menuBar.addMenu("Ansicht", viewMenu);
			menuBar.addMenu("Hilfe", helpMenu);
			englishItem = languageMenu.addItem("English",
					event -> host.setLanguage(Locale.ENGLISH));
			germanItem = languageMenu.addItem("Deutsch",
					event -> host.setLanguage(Locale.GERMAN));
			languageBarItem = menuBar.addMenu("Sprache", languageMenu);
			defaultThemeItem = themeMenu.addItem("Default",
					event -> host.watchShowcaseTheme("default.gltheme",
						GLThemes.DEFAULT,
						"showcase.theme.default"));
			darkThemeItem = themeMenu.addItem("Dunkel",
					event -> host.watchShowcaseTheme("dark.gltheme",
						GLThemes.DARK,
						"showcase.theme.dark"));
			lightThemeItem = themeMenu.addItem("Hell",
					event -> host.watchShowcaseTheme("light.gltheme",
						GLThemes.LIGHT,
						"showcase.theme.light"));
			themeBarItem = menuBar.addMenu("Theme", themeMenu);
			GLPopupMenu disabledMenu = new GLPopupMenu();
			var disabledItem = menuBar.addMenu("Deaktiviert", disabledMenu);
			disabledItem.setEnabled(false);
		}

		void updateLocalizedMenuText() {
			GLLocalizer text = host.localizer();
			languageBarItem.setText(text.text("showcase.menu.language"));
			themeBarItem.setText(text.text("showcase.menu.theme"));
			englishItem.setText(text.text("showcase.language.english"));
			germanItem.setText(text.text("showcase.language.german"));
			defaultThemeItem.setText(text.text("showcase.theme.default"));
			darkThemeItem.setText(text.text("showcase.theme.dark"));
			lightThemeItem.setText(text.text("showcase.theme.light"));
		}

		private void buildBasicsSection() {
			Section section = section("Labels, Buttons & Checkboxen",
					24, 105, 350, 590);
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

		private void buildValueSection() {
			Section section = section("Slider & Texteditoren",
					400, 105, 360, 590);
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

			addLabel(section, "Texteditoren", SECTION_STYLE,
					18, 294, 200, 25);
			caption(section, "NORMAL", 18, 330);
			GLTextEditor normal = new GLTextEditor("Editierbarer Text");
			normal.addActionListener(event -> host.report(
					"Text uebernommen: " + normal.getText()));
			addControl(section, normal, 18, 351, 324, 26);

			caption(section, "NUR ZAHLEN / RESTORE_LAST_VALID", 18, 387);
			GLTextEditor numeric = new GLTextEditor("12.5");
			numeric.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
			numeric.setInputVerifier(ShowcaseSurface::isFiniteNumber);
			numeric.setValidationFailureMode(
					GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
			addControl(section, numeric, 18, 408, 324, 26);

			caption(section, "INVALID-MARKIERUNG / DISABLED", 18, 444);
			GLTextEditor invalid = new GLTextEditor("Ungueltiger Zustand");
			invalid.setInvalid(true);
			addControl(section, invalid, 18, 465, 200, 26);
			GLTextEditor editorDisabled = new GLTextEditor("Deaktiviert");
			editorDisabled.setEnabled(false);
			addControl(section, editorDisabled, 230, 465, 112, 26);

			caption(section, "FREE-GRUPPE: vertikal ueberstreichen", 18, 510);
			GLSlider red = GLSlider.fixed("Rot", 0, 1, 128);
			GLSlider green = GLSlider.fixed("Gruen", 0, 1, 96);
			GLSlider blue = GLSlider.fixed("Blau", 0, 1, 160);
			red.setDragSensitivity(1);
			green.setDragSensitivity(1);
			blue.setDragSensitivity(1);
			GLSliderGroup colorGroup = new GLSliderGroup(red, green, blue);
			addControl(section, colorGroup, 18, 530, 324, 60);
		}

		private void buildSelectionSection() {
			Section section = section("ComboBoxen, Menues & Popups",
					786, 105, 370, 590);
			caption(section, "STANDARD MIT TITEL", 18, 50);
			normalCombo.setPopupTitle("Farbe waehlen");
			normalCombo.addActionListener(event -> host.report(
					"Farbe: " + normalCombo.getSelectedItem()));
			addControl(section, normalCombo, 18, 72, 334, 26);

			caption(section, "DISABLED", 18, 108);
			GLComboBox<String> disabled = new GLComboBox<>(
					List.of("Nicht", "Verfuegbar"));
			disabled.setEnabled(false);
			addControl(section, disabled, 18, 130, 334, 26);

			caption(section, "LANGER TEXT MIT ELLIPSIS", 18, 166);
			GLComboBox<String> longText = new GLComboBox<>(List.of(
					"Ein extrem langer Eintrag, der nicht in die ComboBox passt",
					"Kurz"));
			longText.setPopupMatchesComboWidth(true);
			longText.setPopupTextOverflow(GLPopupTextOverflow.ELLIPSIS);
			addControl(section, longText, 18, 188, 235, 26);

			caption(section, "VERSCHACHTELTE EINTRAEGE", 18, 224);
			nestedCombo.addItem("Direkter Eintrag");
			GLComboBox<String>.SubMenu regionen =
					nestedCombo.addSubMenu("Regionen");
			regionen.addItem("Europa").addItem("Asien");
			regionen.addSubMenu("Staedte")
					.addItem("Berlin").addItem("Hamburg");
			nestedCombo.setPopupTitle("Hierarchische Auswahl");
			addControl(section, nestedCombo, 18, 246, 334, 26);

			addLabel(section, "Eigenstaendiges Popup", SECTION_STYLE,
					18, 294, 260, 25);
			caption(section, "Titel, Scrolling, Separator, Disabled, Submenu",
					18, 330);
			buildStandalonePopup();
			GLButton openPopup = new GLButton("Popup-Menue oeffnen");
			openPopup.addActionListener(event -> openPopup(openPopup));
			addControl(section, openPopup, 18, 354, 220, 27);

			caption(section,
					"Die Menueleiste oben zeigt Accelerator und aktive Rundungen.",
					18, 402);
			GLButton openCombo = new GLButton("ComboBox-Popup programmgesteuert");
			openCombo.addActionListener(event -> normalCombo.showPopup());
			addControl(section, openCombo, 18, 430, 300, 27);

			caption(section, "TABS / DIALOGE / OVERFLOW-SCROLLBAR", 18, 474);
			GLTabbedPane tabs = new GLTabbedPane();
			tabs.addTab("Dialoge", dialogTabPage());
			tabs.addTab("Status", tabPage(
					"Auswahl, Hover und Scrollen sind aktiv."));
			tabs.addTab("Deaktiviert", tabPage("Nicht erreichbar"));
			tabs.setEnabledAt(2, false);
			tabs.addTab("Sehr langer Tabtitel", tabPage(
					"Dieser Titel erzwingt den horizontalen Overflow."));
			tabs.addTab("Weiterer Tab", tabPage(
					"Mausrad ueber der Tableiste scrollt."));
			tabs.addTab("Letzter", tabPage("Ende der Tableiste"));
			tabs.addSelectionListener((source, oldIndex, newIndex) ->
					host.report("Tab '" + source.getTitleAt(newIndex)
							+ "' gewaehlt"));
			addControl(section, tabs, 18, 496, 334, 78);
		}

		private GLContainer dialogTabPage() {
			GLContainer page = tabPage("");
			GLButton modeless = new GLButton("Modeless");
			modeless.addActionListener(event -> openShowcaseDialog(
					host.parent(), GLDialog.Modality.MODELESS, 1));
			addControl(page, modeless, 7, 6, 142, 25);

			GLButton modal = new GLButton("Owner-modal");
			modal.addActionListener(event -> openShowcaseDialog(
					host.parent(), GLDialog.Modality.OWNER_MODAL, 1));
			addControl(page, modal, 158, 6, 142, 25);
			return page;
		}

		private static GLContainer tabPage(String text) {
			GLContainer page = new GLContainer() {
				@Override
				protected void record(GLContext2D context) {
				}
			};
			if (!text.isEmpty()) {
				GLLabel label = new GLLabel(text);
				label.setBounds(7, 7, 305, 23);
				page.addGL(label);
			}
			return page;
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
			nestedCombo.getPopupMenu().close();
			fixedSlider.getContextMenu().close();
			freeSlider.getContextMenu().close();
		}

		GLMenuBar menuBar() {
			return menuBar;
		}

		float themeRadius() {
			return (float) themeRadiusSlider.getValue();
		}

		private Section section(String title, float x, float y,
				float width, float height) {
			Section section = new Section(title);
			section.setBounds(x, y, width, height);
			addGL(section);
			return section;
		}

		private static void caption(GLContainer parent, String text,
				float x, float y) {
			addLabel(parent, text, CAPTION_STYLE, x, y, 330, 18);
		}

		private static void addLabel(GLContainer parent, String text,
				GLLabelStyle style, float x, float y, float width, float height) {
			GLLabel label = new GLLabel(text);
			label.setGLStyle(style);
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
			context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
					18, 1.5f, SURFACE, new Color(75, 91, 119),
					GLStroke.WidthSpace.SCREEN);
		}

		private static final class Section extends GLContainer {
			private Section(String title) {
				addLabel(this, title, SECTION_STYLE, 18, 15, 320, 25);
			}

			@Override
			protected void record(GLContext2D context) {
				context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
						14, 1, CARD, CARD_BORDER,
						GLStroke.WidthSpace.SCREEN);
			}
		}
	}

	static String formatPerformance(String backend, String action, float zoom,
			double fps, GLFrameProfile profile, int drawCalls, int samples,
			int swapInterval, float renderScale, int renderWidth, int renderHeight,
			int surfaceWidth, int surfaceHeight) {
		String swap = swapInterval < 0 ? "" : " | Swap " + swapInterval;
		double framePeriod = fps > 0 ? 1000.0 / fps : 0;
		double scheduleWait = Math.max(0,
				framePeriod - profile.endToEndMillis());
		return String.format("<html><center>"
				+ "%s | %s | Zoom %.0f %% | Render %.0f %% (%dx%d -> %dx%d) | %.0f FPS | Periode %.2f ms | Call E2E %.2f ms | Wait %.2f ms<br>"
				+ "Callback %.2f | Backend-Rest %.2f ms &nbsp; "
				+ "CPU: House %.3f | Scene %.3f | Present %.3f ms &nbsp; "
				+ "GPU: Scene %.3f | Present %.3f ms | %d Drawcalls | MSAA %dx%s"
				+ "</center></html>", backend, action, zoom * 100,
				renderScale * 100, renderWidth, renderHeight,
				surfaceWidth, surfaceHeight, fps,
				framePeriod, profile.endToEndMillis(), scheduleWait,
				profile.callbackCpuMillis(), profile.backendRemainderMillis(),
				profile.housekeepingCpuMillis(),
				profile.sceneCpuMillis(), profile.presentCpuMillis(),
				profile.sceneGpuMillis(), profile.presentGpuMillis(), drawCalls,
				samples, swap);
	}
}
