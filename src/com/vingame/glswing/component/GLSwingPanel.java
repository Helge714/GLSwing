package com.vingame.glswing.component;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.nio.FloatBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.glsl.ShaderUtil;
import com.vingame.glswing.rendering.batch.BatchManager;
import com.vingame.glswing.rendering.context.GLContext;
import com.vingame.glswing.rendering.context.GLProgram;
import com.vingame.glswing.rendering.context.GLShaderUtil;

public abstract class GLSwingPanel extends GLJPanel implements GLEventListener {

	private static final long serialVersionUID = -3675783971669171273L;

	protected BatchManager batchManager = new BatchManager();

	public int lastID;

	int blitProgram;
	int quadVbo;

	Point mousePos;

	public GLSwingPanel() {
		super(createGLCapabilities());
		addGLEventListener(this);
		new FPSAnimator(this, 60).start();

		addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {
				mousePos = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				mousePos = e.getPoint();
			}
		});
	}

	public static GLCapabilities createGLCapabilities() {
		GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL2));
		caps.setSampleBuffers(true);
		caps.setNumSamples(8); // oder 8
		caps.setStencilBits(8);
		return caps;
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glClearColor(1f, 1f, 1f, 1f);
		batchManager.init(gl, getWidth(), getHeight());

		GLProgram blitProg = GLProgram.createDefault("blit.vert", "blit.frag");
		blitProgram = GLShaderUtil.createProgram(gl, blitProg.vert, blitProg.frag);

		float[] quadVerts = {
				// x, y, u, v
				-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f };
		FloatBuffer quadBuf = Buffers.newDirectFloatBuffer(quadVerts);

		int[] vbo = new int[1];
		gl.glGenBuffers(1, vbo, 0);
		quadVbo = vbo[0];
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, quadVbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, quadVerts.length * Float.BYTES, quadBuf, GL.GL_STATIC_DRAW);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		batchManager.dispose(gl);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		batchManager.begin(gl);

		GLContext context = new GLContext(batchManager);
		setupDisplay(context);
		context.flush();

		if (mousePos != null) {
			lastID = batchManager.idBuffer.readIdAt(gl, mousePos.x, mousePos.y);
//			System.out.println("Picked ID: " + lastID);
		}
	}

	protected abstract void setupDisplay(GLContext gl);

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glViewport(0, 0, width, height);
	}
}
