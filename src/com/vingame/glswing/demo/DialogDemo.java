package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;

/** Owned and recursively nested GLCanvas dialog demo. */
public final class DialogDemo {
	private DialogDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GLDialog Demo");
		SwingUtilities.invokeLater(DialogDemo::showCanvas);
	}

	private static void showCanvas() {
		DemoCanvas canvas = new DemoCanvas();
		canvas.setPreferredSize(new Dimension(760, 440));
		FPSAnimator animator = new FPSAnimator(canvas, 144, true);
		JFrame frame = new JFrame("GLSwingNext - Canvas Dialog Owner");
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				Thread stop = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "dialog-demo-canvas-stop");
				stop.start();
			}
		});
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		animator.start();
	}

	private static GLContainer dialogContent(int level,
			AtomicReference<GLDialog> self) {
		return new GLContainer() {
			{
				GLLabel title = new GLLabel(level == 1
						? "Erster GLSwing-Fensterdialog"
						: "Verschachtelter Dialog – Ebene " + level);
				title.setBounds(28, 28, 420, 26);
				addGL(title);

				GLLabel description = new GLLabel(
						"Jede sichtbare Ebene bereitet ihren direkten Kinddialog vor.");
				description.setBounds(28, 74, 460, 24);
				addGL(description);

				GLButton nested = new GLButton("Unterdialog öffnen");
				nested.setBounds(28, 246, 180, 28);
				nested.addActionListener(event -> openDialog(this, level + 1));
				addGL(nested);

				GLButton close = new GLButton("Schließen");
				close.setBounds(354, 246, 120, 28);
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

	private static void openDialog(GLParent owner) {
		openDialog(owner, 1);
	}

	private static void openDialog(GLParent owner, int level) {
		AtomicReference<GLDialog> self = new AtomicReference<>();
		GLDialog dialog = new GLDialog(
				level == 1 ? "Einstellungen" : "Unterdialog – Ebene " + level,
				dialogContent(level, self));
		dialog.setSize(520, 320);
		dialog.setModality(GLDialog.Modality.OWNER_MODAL);
		self.set(dialog);
		dialog.show(owner);
	}

	private static final class DemoCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;

		private DemoCanvas() {
			GLLabel label = new GLLabel("Canvas-Hauptfenster");
			label.setBounds(32, 30, 280, 28);
			addGL(label);
			GLButton button = new GLButton("Modale Einstellungen öffnen");
			button.setBounds(32, 92, 240, 30);
			button.addActionListener(event -> openDialog(this));
			addGL(button);
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(20, 25, 35));
		}
	}

}
