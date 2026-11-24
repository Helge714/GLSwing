package com.vingame.glswing.demo;

import com.vingame.glswing.application.GLBackend;
import com.vingame.glswing.component.GLSwingCanvas;

/** Direct-GLCanvas host for exactly the same scene as {@link GuiShowcaseDemo}. */
public final class CanvasGuiShowcaseDemo {
	private CanvasGuiShowcaseDemo() { }

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GUI Showcase / GLCanvas");
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("CanvasGuiShowcaseDemo requires these VM arguments:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			return;
		}
		GuiShowcaseDemo.launchShowcase(args, GLBackend.CANVAS,
				"GLSwing – GUI Showcase (Direct Canvas)");
	}
}
