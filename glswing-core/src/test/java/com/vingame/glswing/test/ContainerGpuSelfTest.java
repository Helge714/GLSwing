package com.vingame.glswing.test;

import java.awt.Color;
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRenderer2D;
import com.vingame.glswing.rendering.Projection2D;

/** GPU checks for local recordings, component transforms and child lifecycle. */
public final class ContainerGpuSelfTest {

	private static final int WIDTH = 180;
	private static final int HEIGHT = 120;

	private ContainerGpuSelfTest() {
	}

	public static void main(String[] args) {
		GLProfile profile = GLProfile.get(GLProfile.GL3);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setOnscreen(false);
		GLOffscreenAutoDrawable drawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, capabilities, null, WIDTH, HEIGHT);
		drawable.display();
		GLContext nativeContext = drawable.getContext();
		if (nativeContext.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
			throw new IllegalStateException("Could not make OpenGL context current");

		GLRenderer2D renderer = null;
		try {
			GL3 gl = drawable.getGL().getGL3();
			gl.glViewport(0, 0, WIDTH, HEIGHT);
			renderer = GLRenderer2D.create(gl);
			testTree(gl, renderer);
			testZOrder(gl, renderer);
			testRendererReplacement(gl, renderer);
			System.out.println("Container GPU self-test passed.");
		} finally {
			GL3 gl = drawable.getGL().getGL3();
			if (renderer != null)
				renderer.dispose(gl);
			nativeContext.release();
			drawable.destroy();
		}
	}

	private static void testRendererReplacement(GL3 gl, GLRenderer2D renderer) {
        SolidContainer root = new SolidContainer(Color.RED);
        SolidContainer child = new SolidContainer(Color.GREEN);
        root.setBounds(0, 0, WIDTH, HEIGHT);
        child.setBounds(20, 20, 40, 40);
        root.addGL(child);
        draw(gl, renderer, root);
        draw(gl, renderer, root);
        if (root.recordCalls != 1 || child.recordCalls != 1)
            throw new AssertionError("Stable renderer must reuse recordings");
        GLRenderer2D replacement = GLRenderer2D.create(gl);
        try {
            draw(gl, replacement, root);
            draw(gl, replacement, root);
            checkColor(gl, 25, 25, Color.GREEN, "replacement renderer child");
            checkPick(replacement.readPickResult(gl, 25, 25), child, 5, 5);
            if (root.recordCalls != 2 || child.recordCalls != 2)
                throw new AssertionError("Renderer replacement must rebuild each recording once");
        } finally {
            replacement.dispose(gl);
        }
        draw(gl, renderer, root);
        if (root.recordCalls != 3 || child.recordCalls != 3)
            throw new AssertionError("Returning to an earlier renderer must rebuild recordings");
    }

	private static void testZOrder(GL3 gl, GLRenderer2D renderer) {
		SolidContainer root = new SolidContainer(Color.BLACK);
		SolidContainer back = new SolidContainer(Color.RED);
		SolidContainer front = new SolidContainer(Color.BLUE);
		root.setBounds(0, 0, WIDTH, HEIGHT);
		back.setBounds(30, 25, 60, 45);
		front.setBounds(45, 35, 60, 45);
		root.addGL(back);
		root.addGL(front);

		draw(gl, renderer, root);
		checkColor(gl, 55, 45, Color.BLUE, "initial frontmost child");
		checkPick(renderer.readPickResult(gl, 55, 45), front, 10, 10);

		back.bringToFront();
		draw(gl, renderer, root);
		checkColor(gl, 55, 45, Color.RED, "reordered frontmost child");
		checkPick(renderer.readPickResult(gl, 55, 45), back, 25, 20);
	}

	private static void testTree(GL3 gl, GLRenderer2D renderer) {
		SolidContainer root = new SolidContainer(Color.RED);
		SolidContainer child = new SolidContainer(Color.GREEN);
		SolidContainer grandchild = new SolidContainer(Color.BLUE);
		root.setBounds(10, 10, 100, 80);
		child.setBounds(20, 15, 30, 25);
		grandchild.setBounds(5, 5, 8, 8);
		root.addGL(child);
		child.addGL(grandchild);

		draw(gl, renderer, root);
		if (renderer.drawCallCount() != 2)
			throw new AssertionError("Compatible component recordings used "
					+ renderer.drawCallCount()
					+ " draw calls instead of one color and one picking call");
		checkColor(gl, 12, 12, Color.RED, "root local recording");
		checkColor(gl, 31, 26, Color.GREEN, "nested child transform");
		checkColor(gl, 36, 31, Color.BLUE, "nested grandchild transform");
		checkPick(renderer.readPickResult(gl, 36, 31), grandchild, 1, 1);

		child.setLocation(40, 20);
		draw(gl, renderer, root);
		if (root.recordCalls != 1 || child.recordCalls != 1 || grandchild.recordCalls != 1)
			throw new AssertionError("Moving a component unexpectedly rebuilt geometry");
		checkColor(gl, 51, 31, Color.GREEN, "moved child without re-recording");

		child.setSize(35, 25);
		draw(gl, renderer, root);
		if (child.recordCalls != 2)
			throw new AssertionError("Resizing did not rebuild child geometry exactly once");

		child.removeGL(grandchild);
		draw(gl, renderer, root);
		GLPickResult formerGrandchildPixel = renderer.readPickResult(gl, 56, 36);
		if (formerGrandchildPixel.getTarget() != child)
			throw new AssertionError("A detached child remained in the rendered pick tree");
	}

	private static void draw(GL3 gl, GLRenderer2D renderer, GLContainer root) {
		GLContext2D context = renderer.beginFrame(gl, WIDTH, HEIGHT,
				Projection2D.TOP_LEFT_PIXELS.create(WIDTH, HEIGHT));
		try {
			context.clear(Color.BLACK);
			root.renderComponent(context);
			if (!context.getTransform().isIdentity() || context.getMouseTarget() != null)
				throw new AssertionError("Component rendering leaked context state");
		} finally {
			renderer.endFrame();
		}
	}

	private static void checkPick(GLPickResult result, GLContainer target,
			float expectedX, float expectedY) {
		if (result.getTarget() != target)
			throw new AssertionError("Unexpected picked component");
		if (Math.abs(result.getLocalX() - expectedX) > 0.01f
				|| Math.abs(result.getLocalY() - expectedY) > 0.01f)
			throw new AssertionError("Expected local pick " + expectedX + "/" + expectedY
					+ ", got " + result.getLocalX() + "/" + result.getLocalY());
	}

	private static void checkColor(GL3 gl, int x, int y,
			Color expected, String description) {
		ByteBuffer pixel = ByteBuffer.allocateDirect(4);
		gl.glReadPixels(x, HEIGHT - 1 - y, 1, 1,
				GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel);
		int red = pixel.get(0) & 0xff;
		int green = pixel.get(1) & 0xff;
		int blue = pixel.get(2) & 0xff;
		if (Math.abs(red - expected.getRed()) > 2
				|| Math.abs(green - expected.getGreen()) > 2
				|| Math.abs(blue - expected.getBlue()) > 2)
			throw new AssertionError(description + ": expected "
					+ expected.getRed() + "/" + expected.getGreen() + "/"
					+ expected.getBlue() + ", got " + red + "/" + green + "/" + blue);
	}

	private static final class SolidContainer extends GLContainer {
		private final Color color;
		private int recordCalls;

		private SolidContainer(Color color) {
			this.color = color;
		}

		@Override
		protected void record(GLContext2D context) {
			recordCalls++;
			context.fillRect(0, 0, getWidth(), getHeight(), color);
		}
	}
}
