package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLException;

public class ColorFBO extends FBO {

	@Override
	public void init(GL2GL3 gl, int width, int height, int samples) {
		this.width = width;
		this.height = height;
		this.samples = samples;
		int[] ids = new int[1];
		int[] previous = new int[2];
		gl.glGetIntegerv(GL2GL3.GL_READ_FRAMEBUFFER_BINDING, previous, 0);
		gl.glGetIntegerv(GL2GL3.GL_DRAW_FRAMEBUFFER_BINDING, previous, 1);

		// Framebuffer
		gl.glGenFramebuffers(1, ids, 0);
		fboId = ids[0];
		gl.glBindFramebuffer(GL2GL3.GL_FRAMEBUFFER, fboId);

		// Color Attachment
		gl.glGenTextures(1, ids, 0);
		textureId = ids[0];
		if (samples > 1) {
			gl.glBindTexture(GL2GL3.GL_TEXTURE_2D_MULTISAMPLE, textureId);
			gl.glTexImage2DMultisample(GL2GL3.GL_TEXTURE_2D_MULTISAMPLE, samples, GL2GL3.GL_RGBA8, width, height, true);
			gl.glFramebufferTexture2D(GL2GL3.GL_FRAMEBUFFER, GL2GL3.GL_COLOR_ATTACHMENT0,
					GL2GL3.GL_TEXTURE_2D_MULTISAMPLE, textureId, 0);
		} else {
			gl.glBindTexture(GL2GL3.GL_TEXTURE_2D, textureId);
			gl.glTexImage2D(GL2GL3.GL_TEXTURE_2D, 0, GL2GL3.GL_RGBA8, width, height, 0, GL2GL3.GL_RGBA,
					GL2GL3.GL_UNSIGNED_BYTE, null);
			gl.glTexParameteri(GL2GL3.GL_TEXTURE_2D, GL2GL3.GL_TEXTURE_MIN_FILTER, GL2GL3.GL_LINEAR);
			gl.glTexParameteri(GL2GL3.GL_TEXTURE_2D, GL2GL3.GL_TEXTURE_MAG_FILTER, GL2GL3.GL_LINEAR);
			gl.glFramebufferTexture2D(GL2GL3.GL_FRAMEBUFFER, GL2GL3.GL_COLOR_ATTACHMENT0, GL2GL3.GL_TEXTURE_2D,
					textureId, 0);
		}

		// Depth/Stencil Renderbuffer
		gl.glGenRenderbuffers(1, ids, 0);
		rboId = ids[0];
		gl.glBindRenderbuffer(GL2GL3.GL_RENDERBUFFER, rboId);

		if (samples > 1) {
			gl.glRenderbufferStorageMultisample(GL2GL3.GL_RENDERBUFFER, samples, GL2GL3.GL_DEPTH24_STENCIL8, width,
					height);
		} else {
			gl.glRenderbufferStorage(GL2GL3.GL_RENDERBUFFER, GL2GL3.GL_DEPTH24_STENCIL8, width, height);
		}
		gl.glFramebufferRenderbuffer(GL2GL3.GL_FRAMEBUFFER, GL2GL3.GL_DEPTH_STENCIL_ATTACHMENT, GL2GL3.GL_RENDERBUFFER,
				rboId);

		// Check FBO completeness
		int status = gl.glCheckFramebufferStatus(GL2GL3.GL_FRAMEBUFFER);
		if (status != GL2GL3.GL_FRAMEBUFFER_COMPLETE) {
			throw new GLException("Framebuffer not complete: " + status);
		}

		// Unbind
		gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, previous[0]);
		gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, previous[1]);
	}
}
