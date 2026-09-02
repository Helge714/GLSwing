package com.vingame.glswing.demo;

import java.awt.Color;

import com.vingame.glswing.application.GLApplication;
import com.vingame.glswing.application.GLApplicationContext;
import com.vingame.glswing.application.GLBackend;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;

/** Minimal standalone application using the managed startup lifecycle. */
public final class GLFrameDemo {
	private GLFrameDemo() { }

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GLApplication Demo");
		GLApplication.builder()
				.title("GLSwing – Application startup")
				.backend(GLBackend.GLJPANEL)
				.size(760, 440)
				.minimumSize(520, 320)
				.clearColor(new Color(20, 25, 35))
				.splash(splash -> splash
						.addTitle("GLSwing")
						.addGap(4)
						.addSubtitle("Preparing the interface")
						.addGap(22)
						.addCurrentTask()
						.addGap(8)
						.addProgressBar(400)
						.addGap(16)
						.addTaskHistory(400))
				.prepare(startup -> startup.provide("session", "Opening session",
						() -> "Ready"))
				.surface(GLFrameDemo::createSurface)
				.launch();
	}

	private static GLContainer createSurface(GLApplicationContext application) {
		GLContainer root = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
		GLLabel heading = new GLLabel("GLApplication manages startup and handoff");
		heading.setBounds(32, 32, 420, 28);
		root.addGL(heading);

		GLLabel status = new GLLabel(application.require("session", String.class));
		status.setBounds(32, 72, 300, 24);
		root.addGL(status);

		GLButton dialogButton = new GLButton("Open dialog");
		dialogButton.setBounds(32, 126, 150, 30);
		dialogButton.addActionListener(event -> showDialog(application));
		root.addGL(dialogButton);
		return root;
	}

	private static void showDialog(GLApplicationContext application) {
		GLContainer content = new GLContainer() {
			{
				GLLabel label = new GLLabel("Owned GLSwing dialog");
				label.setBounds(24, 24, 320, 26);
				addGL(label);
			}
			@Override protected void record(GLContext2D context) { }
		};
		GLDialog dialog = new GLDialog("Application dialog", content);
		dialog.setSize(420, 220);
		dialog.show(application.parent());
	}
}
