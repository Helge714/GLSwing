package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;

/** Minimal standalone application using the managed GLFrame convenience host. */
public final class GLFrameDemo {
	private GLFrameDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GLFrame Demo");
		SwingUtilities.invokeLater(GLFrameDemo::show);
	}

	private static void show() {
		GLFrame.RenderingSettings rendering = GLFrame.RenderingSettings.DEFAULT
				.withAntialiasingSamples(4)
				.withVSync(true)
				.withTargetFps(144);
		GLFrame frame = new GLFrame("GLSwingNext – GLFrame", rendering);
		frame.setGLClearColor(new Color(20, 25, 35));
		frame.setMinimumSize(new Dimension(520, 320));
		frame.setSize(760, 440);
		frame.setLocationRelativeTo(null);

		GLLabel heading = new GLLabel("GLFrame verwaltet Canvas und Animator");
		heading.setBounds(32, 32, 360, 28);
		frame.addGL(heading);

		GLLabel settings = new GLLabel("4× MSAA · VSync · Ziel 144 FPS");
		settings.setBounds(32, 72, 300, 24);
		frame.addGL(settings);

		GLButton dialogButton = new GLButton("Dialog öffnen");
		dialogButton.setBounds(32, 126, 150, 30);
		dialogButton.addActionListener(event -> showDialog(frame));
		frame.addGL(dialogButton);
		frame.setVisible(true);
	}

	private static void showDialog(GLFrame frame) {
		GLContainer content = new GLContainer() {
			{
				GLLabel label = new GLLabel("Owned GLCanvas-Dialog aus GLFrame");
				label.setBounds(24, 24, 320, 26);
				addGL(label);
			}

			@Override
			protected void record(GLContext2D context) {
			}
		};
		GLDialog dialog = new GLDialog("GLFrame-Dialog", content);
		dialog.setSize(420, 220);
		dialog.show(frame);
	}
}
