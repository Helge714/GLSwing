package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.function.Consumer;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;

/** Popup-menu showcase with arbitrarily nested GPU-rendered submenus. */
public final class MenuDemo {
	private static final int TARGET_FPS = 240;

	private MenuDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(MenuDemo::showWindow);
	}

	private static void showWindow() {
		JLabel status = new JLabel(
				"GLSwingPanel-Messung startet ...",
				SwingConstants.CENTER);
		MenuPanel panel = new MenuPanel();
		panel.setPreferredSize(new Dimension(820, 520));
		JCheckBox cpuPicking = new JCheckBox("CPU-Picking", true);
		cpuPicking.setToolTipText(
				"Aus: asynchroner GPU-Picking-Snapshot fuer die Menues");
		cpuPicking.addActionListener(event ->
				panel.setCpuPickingEnabled(cpuPicking.isSelected()));
		JPanel footer = new JPanel(new BorderLayout());
		footer.add(cpuPicking, BorderLayout.WEST);
		footer.add(status, BorderLayout.CENTER);
		FPSAnimator animator = new FPSAnimator(panel, TARGET_FPS, true);
		Timer statistics = new Timer(250,
				event -> status.setText(panel.performanceText()));

		JFrame frame = new JFrame("GLSwingNext - Menues & Submenues");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(footer, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				statistics.stop();
				Thread shutdown = new Thread(animator::stop,
						"GL-panel-menu-stop");
				shutdown.setDaemon(true);
				shutdown.start();
			}
		});
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		statistics.start();
		animator.start();
	}

	static void drawShowcase(GLContext2D context) {
		context.clear(new Color(24, 29, 40));
		context.fillRoundRect(64, 115, 690, 320, 20,
				new Color(38, 46, 62));
		context.setStroke(new GLStroke(2)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		context.drawRoundRect(64, 115, 690, 320, 20,
				new Color(69, 82, 106));
		for (int x = 105; x <= 690; x += 65)
			context.fillCircle(x, 275, 17,
					new Color(70 + x % 90, 115 + x % 70, 185, 185));
	}

	static GLPopupMenu createShowcaseMenu(Consumer<String> selected) {
		GLPopupMenu recent = new GLPopupMenu();
		recent.addItem("GLSwingNext", event -> selected.accept("GLSwingNext geoeffnet"));
		recent.addItem("Brettchendrehen", event ->
				selected.accept("Brettchendrehen geoeffnet"));

		GLPopupMenu exportImage = new GLPopupMenu();
		exportImage.addItem("PNG", event -> selected.accept("Als PNG exportiert"));
		exportImage.addItem("JPEG", event -> selected.accept("Als JPEG exportiert"));
		exportImage.addItem("WebP", event -> selected.accept("Als WebP exportiert"));

		GLPopupMenu export = new GLPopupMenu();
		export.addSubMenu("Bild", exportImage);
		export.addItem("PDF", event -> selected.accept("Als PDF exportiert"));
		export.addItem("SVG", event -> selected.accept("Als SVG exportiert"));

		GLPopupMenu theme = new GLPopupMenu();
		theme.addItem("Dunkel", event -> selected.accept("Dunkles Theme gewaehlt"));
		theme.addItem("Hell", event -> selected.accept("Helles Theme gewaehlt"));
		theme.addItem("System", event -> selected.accept("System-Theme gewaehlt"));

		GLPopupMenu root = new GLPopupMenu();
		GLMenuItem create = root.addItem("Neu", event ->
				selected.accept("Neues Dokument angelegt"));
		create.setAcceleratorText("Ctrl+N");
		GLMenuItem open = root.addItem("Oeffnen ...", event ->
				selected.accept("Oeffnen ausgewaehlt"));
		open.setAcceleratorText("Ctrl+O");
		root.addSubMenu("Zuletzt verwendet", recent);
		root.addSeparator();
		root.addSubMenu("Exportieren", export);
		root.addSubMenu("Darstellung", theme);
		root.addSeparator();
		GLMenuItem unavailable = root.addItem("Noch nicht verfuegbar");
		unavailable.setEnabled(false);
		root.addItem("Schliessen", event -> selected.accept("Menue geschlossen"));
		return root;
	}

	private static final class MenuPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final GLMenuBar menuBar = new GLMenuBar();
		private final GLPopupMenu fileMenu;
		private final GLPopupMenu editMenu;
		private final GLPopupMenu viewMenu;
		private final GLPopupMenu contextMenu;
		private final List<GLPopupMenu> menus;
		private volatile String lastAction =
				"Menueleiste oder rechte Maustaste verwenden";

		private MenuPanel() {
			fileMenu = createShowcaseMenu(this::selected);
			editMenu = createEditMenu(this::selected);
			viewMenu = createViewMenu(this::selected);
			contextMenu = createShowcaseMenu(this::selected);
			menus = List.of(fileMenu, editMenu, viewMenu, contextMenu);
			menuBar.addMenu("Datei", fileMenu);
			menuBar.addMenu("Bearbeiten", editMenu);
			menuBar.addMenu("Ansicht", viewMenu);
			menuBar.setBounds(0, 0, 820, menuBar.getPreferredGLSize().height());
			addGL(menuBar);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					menuBar.setSize(getWidth(), menuBar.getPreferredGLSize().height());
				}
			});

			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event))
						contextMenu.show(MenuPanel.this, event.getX(), event.getY());
				}
			});
		}

		private static GLPopupMenu createEditMenu(Consumer<String> selected) {
			GLPopupMenu menu = new GLPopupMenu();
			GLMenuItem undo = menu.addItem("Rueckgaengig",
					event -> selected.accept("Rueckgaengig ausgewaehlt"));
			undo.setAcceleratorText("Ctrl+Z");
			return menu;
		}

		private static GLPopupMenu createViewMenu(Consumer<String> selected) {
			GLPopupMenu zoom = new GLPopupMenu();
			zoom.addItem("Vergroessern",
					event -> selected.accept("Ansicht vergroessert"));
			zoom.addItem("Verkleinern",
					event -> selected.accept("Ansicht verkleinert"));
			zoom.addItem("100 %",
					event -> selected.accept("Zoom auf 100 % gesetzt"));
			GLPopupMenu toolWindows = new GLPopupMenu();
			for (int index = 1; index <= 30; index++) {
				int number = index;
				toolWindows.addItem("Werkzeugfenster " + index,
						event -> selected.accept(
								"Werkzeugfenster " + number + " ausgewaehlt"));
			}
			GLPopupMenu menu = new GLPopupMenu();
			menu.addSubMenu("Zoom", zoom);
			menu.addSubMenu("Werkzeugfenster", toolWindows);
			menu.addItem("Vollbild",
					event -> selected.accept("Vollbild ausgewaehlt"));
			return menu;
		}

		@Override
		protected void record(GLContext2D context) {
			drawShowcase(context);
		}

		private void selected(String message) {
			lastAction = message;
		}

		private void setCpuPickingEnabled(boolean enabled) {
			for (GLPopupMenu menu : menus)
				menu.setImmediateCpuPickingEnabled(enabled);
			lastAction = enabled
					? "CPU-Picking aktiv"
					: "GPU-Snapshot-Picking aktiv";
		}

		private String performanceText() {
			return String.format(
					"%s  |  %s  |  Ziel %d  |  %.0f FPS  |  %.2f ms Render-CPU  |  %d Drawcalls  |  MSAA %dx",
					lastAction,
					fileMenu.isImmediateCpuPickingEnabled() ? "CPU" : "GPU",
					TARGET_FPS, getFramesPerSecond(),
					getAverageFrameTimeMillis(), getLastDrawCallCount(),
					getActualAntialiasingSamples());
		}
	}
}
