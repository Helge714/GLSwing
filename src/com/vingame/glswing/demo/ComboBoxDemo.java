package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLPopupTextOverflow;
import com.vingame.glswing.ui.GLSlider;

/** Demonstrates combo-box placement, scrolling, titles, sorting and zoom. */
public final class ComboBoxDemo {
	private static final int TARGET_FPS = 144;

	private ComboBoxDemo() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(ComboBoxDemo::showWindow);
	}

	private static void showWindow() {
		JLabel status = new JLabel("ComboBox-Demo startet ...",
				SwingConstants.CENTER);
		ComboPanel panel = new ComboPanel(status);
		panel.setPreferredSize(new Dimension(720, 500));
		FPSAnimator animator = new FPSAnimator(panel, TARGET_FPS, true);
		Timer statistics = new Timer(250, event -> panel.updateStatistics());

		JCheckBox exactWidth = new JCheckBox("Popup exakt so breit", true);
		exactWidth.addActionListener(event ->
				panel.setExactPopupWidth(exactWidth.isSelected()));
		JButton sort = new JButton("Eintraege sortieren");
		sort.addActionListener(event -> panel.sortItems());
		JComboBox<GLPopupTextOverflow> overflow = new JComboBox<>(
				GLPopupTextOverflow.values());
		overflow.setSelectedItem(GLPopupTextOverflow.ELLIPSIS);
		overflow.addActionListener(event -> panel.setTextOverflow(
				(GLPopupTextOverflow) overflow.getSelectedItem()));
		JPanel controls = new JPanel();
		controls.add(exactWidth);
		controls.add(new JLabel("Text:"));
		controls.add(overflow);
		controls.add(sort);
		JPanel footer = new JPanel(new BorderLayout());
		footer.add(controls, BorderLayout.WEST);
		footer.add(status, BorderLayout.CENTER);

		JFrame frame = new JFrame("GLSwingNext - ComboBoxen");
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.add(footer, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				statistics.stop();
				Thread shutdown = new Thread(animator::stop,
						"GL-combo-demo-stop");
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

	private static final class ComboPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;
		private final JLabel status;
		private final GLComboBox<String> compact = new GLComboBox<>(List.of(
				"Rot", "Gruen", "Blau", "Orange", "Violett", "extra langes Testitem Supraultramarin"));
		private final GLComboBox<String> longList = new GLComboBox<>();
		private final GLComboBox<String> opensAbove = new GLComboBox<>();
		private final GLButton narrowButton = new GLButton(
				"Dieser Buttontext ist absichtlich viel zu lang");
		private final GLSlider narrowSlider = GLSlider.fixed(
				"Sehr lange Sliderbeschriftung, viel zu lang", 0, 10000, 9876);
		private float zoom = 1;
		private String lastAction = "Eine ComboBox oeffnen";
		private boolean reverseSort;

		private ComboPanel(JLabel status) {
			this.status = status;
			compact.setPopupTitle("Farbe waehlen");
			compact.setBounds(70, 65, 170, compact.getPreferredGLSize().height());
			compact.addActionListener(event -> selected("Farbe", compact));
			addGL(compact);

			for (int index = 1; index <= 26; index++)
				longList.addItem(String.format("Sortierbarer Eintrag %02d mit zusaetzlich sehr langem Text", index));
			GLComboBox<String>.SubMenu regions = longList.addSubMenu("Regionen");
			regions.addItem("Europa").addItem("Asien");
			GLComboBox<String>.SubMenu cities = regions.addSubMenu("Staedte");
			cities.addItem("Berlin").addItem("Hamburg").addItem("Muenchen");
			longList.setPopupTitle("Viele Eintraege (Titel bleibt stehen)");
			longList.setBounds(290, 185, 250,
					longList.getPreferredGLSize().height());
			longList.addActionListener(event -> selected("Mitte", longList));
			addGL(longList);

			for (int index = 1; index <= 18; index++)
				opensAbove.addItem("Option " + index);
			opensAbove.setPopupTitle("Oeffnet oberhalb");
			opensAbove.addActionListener(event -> selected("Unten", opensAbove));
			addGL(opensAbove);
			positionBottomCombo();

			narrowButton.setBounds(70, 125, 170,
					narrowButton.getPreferredGLSize().height());
			addGL(narrowButton);
			narrowSlider.setBounds(290, 245, 250,
					narrowSlider.getPreferredGLSize().height());
			addGL(narrowSlider);

			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					positionBottomCombo();
				}
			});

			MouseAdapter zoomControl = new MouseAdapter() {
				@Override
				public void mouseWheelMoved(MouseWheelEvent event) {
					if (compact.isPopupVisible() || longList.isPopupVisible()
							|| opensAbove.isPopupVisible())
						return;
					double factor = Math.pow(1.15,
							-event.getPreciseWheelRotation());
					zoom = Math.max(0.6f, Math.min(1.8f,
							(float) (zoom * factor)));
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event)) {
						zoom = 1;
						repaint();
					}
				}
			};
			addMouseWheelListener(zoomControl);
			addMouseListener(zoomControl);
		}

		@Override
		protected void record(GLContext2D context) {
			context.clear(new Color(24, 29, 40));
			context.fillRoundRect(35, 28, Math.max(0, getWidth() - 70),
					Math.max(0, getHeight() - 56), 18, new Color(38, 46, 62));
		}

		@Override
		protected void render(GLContext2D context) {
			super.render(context);
			float centerX = getWidth() * 0.5f;
			float centerY = getHeight() * 0.5f;
			context.translate(centerX, centerY);
			context.scale(zoom);
			context.translate(-centerX, -centerY);
		}

		private void positionBottomCombo() {
			float height = opensAbove.getPreferredGLSize().height();
			opensAbove.setBounds(70, Math.max(115, getHeight() - 95), 200, height);
		}

		private void selected(String name, GLComboBox<String> combo) {
			lastAction = name + ": " + combo.getSelectedItem();
		}

		private void setExactPopupWidth(boolean exact) {
			compact.setPopupMatchesComboWidth(exact);
			longList.setPopupMatchesComboWidth(exact);
			opensAbove.setPopupMatchesComboWidth(exact);
			lastAction = exact ? "Exakte Popupbreite aktiv"
					: "Popupbreite folgt dem Inhalt";
		}

		private void sortItems() {
			reverseSort = !reverseSort;
			Comparator<String> comparator = Comparator.naturalOrder();
			if (reverseSort)
				comparator = comparator.reversed();
			longList.sortItems(comparator);
			lastAction = reverseSort ? "Absteigend sortiert"
					: "Aufsteigend sortiert";
		}

		private void setTextOverflow(GLPopupTextOverflow overflow) {
			compact.setPopupTextOverflow(overflow);
			longList.setPopupTextOverflow(overflow);
			opensAbove.setPopupTextOverflow(overflow);
			lastAction = "Popup-Textmodus: " + overflow;
		}

		private void updateStatistics() {
			status.setText(String.format(
					"%s | Zoom %.2fx | %.0f FPS | %.2f ms CPU | %d Drawcalls",
					lastAction, zoom, getFramesPerSecond(),
					getAverageFrameTimeMillis(), getLastDrawCallCount()));
		}
	}
}
