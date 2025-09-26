package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;

public class IDBuffer {

	private int fbo = -1;
	private int depthStencilRb = -1;
	private int idTexture = -1;
	public int width, height;

	public void init(GL2 gl, int w, int h) {
		this.width = w;
		this.height = h;

		// ---- Framebuffer erzeugen ----
		int[] fboId = new int[1];
		gl.glGenFramebuffers(1, fboId, 0);
		fbo = fboId[0];
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);

		// ---- ID-Textur (Color Attachment 0) ----
		int[] texId = new int[1];
		gl.glGenTextures(1, texId, 0);
		idTexture = texId[0];

		gl.glBindTexture(GL.GL_TEXTURE_2D, idTexture);
		gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL2GL3.GL_R32UI, // 32 Bit unsigned integer pro Pixel
				w, h, 0, GL2GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, null);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);

		// An FBO hängen
		gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, idTexture, 0);

		// ---- Depth+Stencil-Renderbuffer ----
		int[] rbId = new int[1];
		gl.glGenRenderbuffers(1, rbId, 0);
		depthStencilRb = rbId[0];

		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, depthStencilRb);
		gl.glRenderbufferStorage(GL.GL_RENDERBUFFER, GL.GL_DEPTH24_STENCIL8, w, h);

		// Depth- und Stencil-Attachments binden
		gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, depthStencilRb);
		gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_STENCIL_ATTACHMENT, GL.GL_RENDERBUFFER, depthStencilRb);

		// ---- FBO prüfen ----
		int status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
		if (status != GL.GL_FRAMEBUFFER_COMPLETE) {
			throw new RuntimeException("ID Buffer Framebuffer not complete: status = " + status);
		}

		// ---- Cleanup ----
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, 0);
	}

	public void bind(GL2 gl) {
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
		gl.glViewport(0, 0, width, height);
	}

	public void unbind(GL2 gl) {
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
	}

	public int readIdAt(GL2 gl, int x, int y) {
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
		gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);

		int[] pixel = new int[1];
		gl.glReadPixels(x, height - y, 1, 1, GL2GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, java.nio.IntBuffer.wrap(pixel));

		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
		return pixel[0];
	}

	public int getTextureId() {
		return idTexture;
	}

	public int getFramebufferId() {
		return fbo;
	}
}
