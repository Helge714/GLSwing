package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;

public class IdFBO extends FBO {

	@Override
	public void init(GL2GL3 gl, int w, int h, int samples) {
		this.width = w;
		this.height = h;
		this.samples = 1;
		int[] previous = new int[2];
		gl.glGetIntegerv(GL2GL3.GL_READ_FRAMEBUFFER_BINDING, previous, 0);
		gl.glGetIntegerv(GL2GL3.GL_DRAW_FRAMEBUFFER_BINDING, previous, 1);

		// ---- Framebuffer erzeugen ----
		int[] fbo = new int[1];
		gl.glGenFramebuffers(1, fbo, 0);
		fboId = fbo[0];
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboId);

		// ---- ID-Textur (Color Attachment 0) ----
		int[] texId = new int[1];
		gl.glGenTextures(1, texId, 0);
		textureId = texId[0];

		gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
		gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL2GL3.GL_R32UI, // 32 Bit unsigned integer pro Pixel
				w, h, 0, GL2GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, null);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);

		// An FBO hängen
		gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, textureId, 0);

		// ---- Depth+Stencil-Renderbuffer ----
		int[] rbId = new int[1];
		gl.glGenRenderbuffers(1, rbId, 0);
		rboId = rbId[0];

		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, rboId);
		gl.glRenderbufferStorage(GL.GL_RENDERBUFFER, GL.GL_DEPTH24_STENCIL8, w, h);

		// Depth- und Stencil-Attachments binden
		gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, rboId);
		gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_STENCIL_ATTACHMENT, GL.GL_RENDERBUFFER, rboId);

		// ---- FBO prüfen ----
		int status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
		if (status != GL.GL_FRAMEBUFFER_COMPLETE) {
			throw new RuntimeException("ID Buffer Framebuffer not complete: status = " + status);
		}

		// ---- Cleanup ----
		gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, previous[0]);
		gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, previous[1]);
		gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, 0);
	}

	public int readIdAt(GL2 gl, int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height)
			return 0;
		bind(gl);
		gl.glReadBuffer(GL.GL_COLOR_ATTACHMENT0);

		int[] pixel = new int[1];
		gl.glReadPixels(x, height - 1 - y, 1, 1, GL2GL3.GL_RED_INTEGER, GL.GL_UNSIGNED_INT, java.nio.IntBuffer.wrap(pixel));

		unbind(gl);
		return pixel[0];
	}
}
