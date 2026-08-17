package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.util.Animator;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLPopupMenu;

/** Direct-GLCanvas counterpart of {@link MenuDemo}. */
public final class CanvasMenuDemo {

	private CanvasMenuDemo() {
	}

	public static void main(String[] args) {
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("CanvasMenuDemo benoetigt diese VM-Argumente:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			System.err.println("Starte 'Canvas Menu Demo.launch'.");
			return;
		}
		SwingUtilities.invokeLater(CanvasMenuDemo::showWindow);
	}

	private static void showWindow() {
		MenuCanvas canvas = new MenuCanvas();
		canvas.setPreferredSize(new Dimension(820, 520));
		JLabel status = new JLabel("Direkte Canvas startet ...",
				SwingConstants.CENTER);
		Timer statistics = new Timer(250, event -> status.setText(String.format(
				"%s  |  %.0f FPS  |  %.2f ms CPU  |  %d Drawcalls  |  Swap %d",
				canvas.getLastAction(), canvas.getFramesPerSecond(),
				canvas.getAverageFrameTimeMillis(), canvas.getLastDrawCallCount(),
				canvas.getActualSwapInterval())));

		Animator animator = new Animator(canvas);
		animator.setRunAsFastAsPossible(true);
		AtomicBoolean closing = new AtomicBoolean();
		JFrame frame = new JFrame("GLSwingNext - Direct Canvas Menues");
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (!closing.compareAndSet(false, true))
					return;
				statistics.stop();
				Thread shutdown = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "GL-canvas-menu-stop");
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

	private static final class MenuCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final GLButton menuButton = new GLButton("Menue oeffnen");
		private final GLPopupMenu menu;
		private volatile String lastAction =
				"Button oder rechte Maustaste verwenden";

		private MenuCanvas() {
			super(4, 0);
			menuButton.setBounds(28, 25, 140, 28);
			addGL(menuButton);
			menu = MenuDemo.createShowcaseMenu(message -> lastAction = message);
			menuButton.addActionListener(event -> menu.show(this,
					menuButton.getX(), menuButton.getY() + menuButton.getHeight() + 4));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event))
						menu.show(MenuCanvas.this, event.getX(), event.getY());
				}
			});
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			MenuDemo.drawShowcase(context);
		}

		private String getLastAction() {
			return lastAction;
		}
	}
}
