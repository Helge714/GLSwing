package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.ColorFBO;
import com.vingame.glswing.rendering.context.GLContext;

/** Opens a real JOGL panel and verifies both its color and integer ID buffers. */
public final class RenderingSmokeTest {

	private RenderingSmokeTest() {
	}

	public static void main(String[] args) throws Exception {
		CountDownLatch completed = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		SwingUtilities.invokeAndWait(() -> {
			JFrame frame = new JFrame("GLSwing rendering smoke test");
			SmokePanel panel = new SmokePanel(frame, completed, failure);
			panel.setPreferredSize(new Dimension(320, 240));
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setContentPane(panel);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});

		if (!completed.await(10, TimeUnit.SECONDS))
			throw new AssertionError("JOGL did not render a frame within ten seconds");
		if (failure.get() != null)
			throw new AssertionError("GPU rendering verification failed", failure.get());
		System.out.println("GLSwing rendering smoke test passed.");
	}

	private static final class SmokePanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		private final JFrame frame;
		private final CountDownLatch completed;
		private final AtomicReference<Throwable> failure;
		private final ColorFBO readback = new ColorFBO();
		private boolean verified;

		SmokePanel(JFrame frame, CountDownLatch completed, AtomicReference<Throwable> failure) {
			this.frame = frame;
			this.completed = completed;
			this.failure = failure;
		}

		@Override
		public void init(GLAutoDrawable drawable) {
			super.init(drawable);
			readback.init(drawable.getGL().getGL2(), drawable.getSurfaceWidth(), drawable.getSurfaceHeight(), 1);
		}

		@Override
		protected void setupDisplay(GLContext gl) {
			gl.ortho(getWidth(), getHeight());
			gl.enableIdRendering();
			gl.fillRect(40, 40, 160, 120, new Color(55, 125, 220), 42);
		}

		@Override
		public void display(GLAutoDrawable drawable) {
			super.display(drawable);
			if (verified)
				return;
			verified = true;

			try {
				GL2 gl = drawable.getGL().getGL2();
				float scaleX = drawable.getSurfaceWidth() / (float) getWidth();
				float scaleY = drawable.getSurfaceHeight() / (float) getHeight();
				int x = Math.round(100 * scaleX);
				int topY = Math.round(100 * scaleY);

				ByteBuffer rgba = Buffers.newDirectByteBuffer(4);
				int[] framebuffer = new int[2];
				gl.glGetIntegerv(GL2GL3.GL_READ_FRAMEBUFFER_BINDING, framebuffer, 0);
				gl.glGetIntegerv(GL2GL3.GL_DRAW_FRAMEBUFFER_BINDING, framebuffer, 1);
				gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, framebuffer[1]);
				gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, readback.getFramebufferId());
				gl.glBlitFramebuffer(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight(), 0, 0,
						drawable.getSurfaceWidth(), drawable.getSurfaceHeight(), GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
				gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, readback.getFramebufferId());
				gl.glReadPixels(x, drawable.getSurfaceHeight() - 1 - topY, 1, 1, GL.GL_RGBA,
						GL.GL_UNSIGNED_BYTE, rgba);
				int readError = gl.glGetError();
				gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, framebuffer[0]);
				gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, framebuffer[1]);
				int red = Byte.toUnsignedInt(rgba.get(0));
				int green = Byte.toUnsignedInt(rgba.get(1));
				int blue = Byte.toUnsignedInt(rgba.get(2));
				if (!(blue > green && green > red))
					throw new AssertionError("unexpected color pixel: " + red + ", " + green + ", " + blue
							+ " (read FBO " + framebuffer[1] + ", glGetError 0x"
							+ Integer.toHexString(readError) + ")");

				int id = batchManager.idBuffer.readIdAt(gl, x, topY);
				if (id != 42)
					throw new AssertionError("expected picking ID 42 but got " + id);
			} catch (Throwable error) {
				failure.set(error);
			} finally {
				completed.countDown();
				SwingUtilities.invokeLater(frame::dispose);
			}
		}

		@Override
		public void dispose(GLAutoDrawable drawable) {
			readback.dispose(drawable.getGL().getGL2());
			super.dispose(drawable);
		}
	}
}
