package com.vingame.glswing.test;

import java.awt.Dimension;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.rendering.GLContext2D;

/** Native lifecycle and API regression test for the managed standalone host. */
public final class GLFrameSelfTest {
	private GLFrameSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		String noEraseSettingBefore = System.getProperty(
				GLSwingCanvas.AWT_NO_ERASE_BACKGROUND_PROPERTY);
		GLFrame[] holder = new GLFrame[1];
		GLContainer root = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
		SwingUtilities.invokeAndWait(() -> {
			GLFrame.RenderingSettings settings = GLFrame.RenderingSettings.DEFAULT
					.withAntialiasingSamples(0).withVSync(false).withTargetFps(120);
			GLFrame frame = new GLFrame("GLFrame test", settings);
			frame.getGLCanvas().setPreferredSize(new Dimension(480, 300));
			frame.addGL(root);
			frame.pack();
			frame.setVisible(true);
			holder[0] = frame;
		});
		GLFrame frame = holder[0];
		try {
			if (noEraseSettingBefore == null && !Boolean.parseBoolean(
					System.getProperty(
							GLSwingCanvas.AWT_NO_ERASE_BACKGROUND_PROPERTY)))
				throw new AssertionError(
						"AWT background erase was not disabled for GLCanvas resize");
			await(() -> frame.getAnimator().isAnimating(),
					"GLFrame animator did not start");
			if (frame.getRequestedAntialiasingSamples() != 0
					|| frame.getRequestedSwapInterval() != 0
					|| frame.getTargetFps() != 120)
				throw new AssertionError("GLFrame rendering settings were not applied");
			Method add = GLFrame.class.getMethod("add", java.awt.Component.class);
			if (!add.isAnnotationPresent(Deprecated.class))
				throw new AssertionError("Inherited add(Component) is not deprecated");

			GLDialog dialog = new GLDialog("GLFrame dialog", new GLContainer() {
				@Override protected void record(GLContext2D context) { }
			});
			dialog.setSize(260, 150);
			dialog.setModality(GLDialog.Modality.MODELESS);
			dialog.show(frame);
			await(dialog::isShowing, "GLFrame dialog did not become visible");
			dialog.close();
			dialog.closedFuture().get(5, TimeUnit.SECONDS);
			frame.close();
			frame.closedFuture().get(5, TimeUnit.SECONDS);
			System.out.println("GLFrame self-test passed.");
		} finally {
			frame.close();
		}
	}

	private static void await(Check condition, String failure)
			throws InterruptedException {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (!condition.value() && System.nanoTime() < deadline)
			Thread.sleep(10);
		if (!condition.value())
			throw new AssertionError(failure);
	}

	@FunctionalInterface
	private interface Check { boolean value(); }
}
