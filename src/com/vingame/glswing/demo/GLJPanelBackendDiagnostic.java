package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.util.Locale;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.Animator;

/**
 * Starts a real {@link GLJPanel}, waits for backend initialization and prints
 * the presentation path selected by JOGL. This deliberately uses reflection:
 * JOGL does not expose its chosen GLJPanel backend through a public API.
 */
public final class GLJPanelBackendDiagnostic {
	private static final long READY_TIMEOUT_MILLIS = 30_000;

	private GLJPanelBackendDiagnostic() {
	}

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.ROOT);
		// Required before GLJPanel's Java2D interop static initializer asks JOGL
		// for the shared AWT/OpenGL context.
		GLProfile.initSingleton();
		GuiShowcaseDemo.ShowcasePanel[] panel = new GuiShowcaseDemo.ShowcasePanel[1];
		JFrame[] frame = new JFrame[1];
		Animator[] animator = new Animator[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new GuiShowcaseDemo.ShowcasePanel(0);
			panel[0].setPreferredSize(new Dimension(1260, 800));
			frame[0] = new JFrame("GLJPanel backend diagnostic");
			frame[0].setLayout(new BorderLayout());
			frame[0].add(panel[0], BorderLayout.CENTER);
			frame[0].setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			frame[0].pack();
			frame[0].setLocationRelativeTo(null);
			frame[0].setVisible(true);
			animator[0] = new Animator(panel[0]);
			animator[0].setRunAsFastAsPossible(true);
			animator[0].start();
		});

		try {
			awaitBackend(panel[0]);
			Thread.sleep(1_000);
			printReport(panel[0]);
		} finally {
			panel[0].closeThemeWatcher();
			Thread stopper = new Thread(animator[0]::stop,
					"gljpanel-diagnostic-stop");
			stopper.start();
			stopper.join(5_000);
			SwingUtilities.invokeAndWait(frame[0]::dispose);
		}
	}

	private static void awaitBackend(GLJPanel panel) throws InterruptedException {
		long started = System.nanoTime();
		int stableWindows = 0;
		while ((System.nanoTime() - started) / 1_000_000L < READY_TIMEOUT_MILLIS) {
			boolean stable = field(panel, "backend") != null
					&& panel.getContext() != null
					&& panel instanceof GuiShowcaseDemo.ShowcasePanel showcase
					&& showcase.getFramesPerSecond() > 5
					&& showcase.getFrameProfile().endToEndMillis() > 0
					&& showcase.getFrameProfile().endToEndMillis() < 250;
			stableWindows = stable ? stableWindows + 1 : 0;
			if (stableWindows >= 4)
				return;
			Thread.sleep(250);
		}
		throw new IllegalStateException("GLJPanel backend was not initialized");
	}

	private static void printReport(GuiShowcaseDemo.ShowcasePanel panel) {
		Object backend = field(panel, "backend");
		boolean java2dAllowed = booleanField(GLJPanel.class,
				"java2dOGLEnabledByProp");
		boolean java2dSelected = booleanField(GLJPanel.class,
				"useJava2DGLPipeline");
		boolean java2dHealthy = booleanField(GLJPanel.class,
				"java2DGLPipelineOK");

		System.out.println();
		System.out.println("=== GLJPanel backend diagnostic ===");
		System.out.println("Java                 : " + System.getProperty("java.version"));
		System.out.println("OS                   : " + System.getProperty("os.name") + " "
				+ System.getProperty("os.version"));
		property("sun.java2d.opengl");
		property("sun.java2d.d3d");
		property("sun.java2d.noddraw");
		property("sun.java2d.uiScale");
		property("jogl.gljpanel.noogl");
		System.out.println("JOGL Java2D allowed  : " + java2dAllowed);
		System.out.println("JOGL Java2D selected : " + java2dSelected);
		System.out.println("JOGL Java2D healthy  : " + java2dHealthy);

		GraphicsConfiguration configuration = panel.getGraphicsConfiguration();
		System.out.println("AWT configuration    : " + className(configuration));
		if (configuration != null)
			System.out.println("AWT device           : "
					+ configuration.getDevice().getIDstring());
		System.out.println("Logical size         : " + panel.getWidth() + " x "
				+ panel.getHeight());
		System.out.println("Physical surface     : " + panel.getSurfacePixelWidth()
				+ " x " + panel.getSurfacePixelHeight());
		float[] scale = panel.getCurrentSurfaceScale(new float[2]);
		System.out.printf("Surface scale        : %.2f x %.2f%n", scale[0], scale[1]);
		System.out.println("Selected backend     : " + className(backend));

		if (backend != null && backend.getClass().getSimpleName().equals(
				"OffscreenBackend")) {
			printOffscreenDetails(backend, panel);
			System.out.println("Conclusion           : GPU offscreen rendering + pixel readback"
					+ " into an AWT image is active.");
		} else if (backend != null && backend.getClass().getSimpleName().equals(
				"J2DOGLBackend")) {
			System.out.println("Conclusion           : direct Java2D/OpenGL interop is active;"
					+ " no ordinary BufferedImage readback backend.");
		} else {
			System.out.println("Conclusion           : unknown/no initialized JOGL backend.");
		}

		String[] gl = new String[4];
		panel.invoke(true, drawable -> {
			GL current = drawable.getGL();
			gl[0] = current.glGetString(GL.GL_VENDOR);
			gl[1] = current.glGetString(GL.GL_RENDERER);
			gl[2] = current.glGetString(GL.GL_VERSION);
			gl[3] = current.isGL2ES2()
					? current.getGL2ES2().glGetString(GL2ES2.GL_SHADING_LANGUAGE_VERSION)
					: "n/a";
			return true;
		});
		System.out.println("GL vendor            : " + gl[0]);
		System.out.println("GL renderer          : " + gl[1]);
		System.out.println("GL version           : " + gl[2]);
		System.out.println("GLSL                 : " + gl[3]);
		System.out.printf("Observed FPS         : %.2f%n", panel.getFramesPerSecond());
		System.out.println("===================================");
	}

	private static void printOffscreenDetails(Object backend,
			GuiShowcaseDemo.ShowcasePanel panel) {
		Object provider = field(backend, "pixelBufferProvider");
		Object pixelBuffer = field(backend, "pixelBuffer");
		Object alignedImage = field(backend, "alignedImage");
		Object cpuFlip = field(backend, "readBackIntsForCPUVFlip");
		System.out.println("Offscreen uses FBO   : " + field(backend, "offscreenIsFBO"));
		System.out.println("Vertical flip        : " + field(backend, "flipVertical"));
		System.out.println("Pixel provider       : " + className(provider));
		System.out.println("Pixel buffer         : " + className(pixelBuffer));
		if (pixelBuffer != null) {
			System.out.println("Buffer dimensions    : " + field(pixelBuffer, "width")
					+ " x " + field(pixelBuffer, "height"));
			System.out.println("Buffer byte size     : " + field(pixelBuffer, "byteSize"));
			Object nioBuffer = field(pixelBuffer, "buffer");
			if (nioBuffer instanceof Buffer buffer)
				System.out.println("NIO pixel buffer     : " + className(buffer)
						+ " (direct=" + buffer.isDirect() + ", capacity="
						+ buffer.capacity() + ")");
		}
		if (alignedImage instanceof BufferedImage image)
			System.out.println("AWT transfer image   : BufferedImage " + image.getWidth()
					+ " x " + image.getHeight() + " (type=" + image.getType() + ")");
		System.out.println("CPU flip buffer      : " + className(cpuFlip));
		long bytes = (long) panel.getSurfacePixelWidth()
				* panel.getSurfacePixelHeight() * Integer.BYTES;
		System.out.printf("RGBA transfer/frame  : %.2f MiB%n", bytes / 1_048_576.0);
	}

	private static void property(String name) {
		System.out.printf("%-21s: %s%n", name,
				System.getProperty(name, "<not set>"));
	}

	private static boolean booleanField(Class<?> type, String name) {
		Object value = field(type, null, name);
		return value instanceof Boolean bool && bool;
	}

	private static Object field(Object instance, String name) {
		return field(instance.getClass(), instance, name);
	}

	private static Object field(Class<?> start, Object instance, String name) {
		for (Class<?> type = start; type != null; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				if (!field.trySetAccessible())
					throw new IllegalStateException("Cannot access " + type.getName()
							+ "." + name + "; add --add-opens=jogamp.fat/"
							+ type.getPackageName() + "=GLSwingNext");
				return field.get(instance);
			} catch (NoSuchFieldException ignored) {
				// Continue with the superclass.
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Cannot read " + type.getName()
						+ "." + name, exception);
			}
		}
		return null;
	}

	private static String className(Object value) {
		return value == null ? "<none>" : value.getClass().getName();
	}
}
