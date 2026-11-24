package com.vingame.glswing.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLException;

public abstract class FBO {

	protected int fboId = -1;
	protected int rboId;
	protected int textureId = -1;
	protected int samples;
	public int width, height;
	
	private int resolveFboId = 0;
	private int resolveTexId = 0;
	private int previousReadFramebufferId = 0;
	private int previousDrawFramebufferId = 0;
	private final int[] previousViewport = new int[4];

	public abstract void init(GL2GL3 gl, int w, int h, int samples);

	public void bind(GL2GL3 gl) {
		int[] binding = new int[1];
		gl.glGetIntegerv(GL2GL3.GL_READ_FRAMEBUFFER_BINDING, binding, 0);
		previousReadFramebufferId = binding[0];
		gl.glGetIntegerv(GL2GL3.GL_DRAW_FRAMEBUFFER_BINDING, binding, 0);
		previousDrawFramebufferId = binding[0];
		gl.glGetIntegerv(GL.GL_VIEWPORT, previousViewport, 0);
		gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboId);
		gl.glViewport(0, 0, width, height);
	}

	public void unbind(GL2GL3 gl) {
		gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, previousReadFramebufferId);
		gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, previousDrawFramebufferId);
		gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}

	public int getFramebufferId() {
		return fboId;
	}
	
	public int getResolvedTexture(GL2GL3 gl) {
	    // Kein Multisampling → ColorTex ist direkt nutzbar
	    if (samples <= 1) {
	        return textureId;
	    }
	    int[] previous = new int[2];
	    gl.glGetIntegerv(GL2GL3.GL_READ_FRAMEBUFFER_BINDING, previous, 0);
	    gl.glGetIntegerv(GL2GL3.GL_DRAW_FRAMEBUFFER_BINDING, previous, 1);

	    // Falls noch kein Resolve-FBO existiert, anlegen
	    if (resolveFboId == 0) {
	        int[] ids = new int[1];

	        // FBO
	        gl.glGenFramebuffers(1, ids, 0);
	        resolveFboId = ids[0];
	        gl.glBindFramebuffer(GL2GL3.GL_FRAMEBUFFER, resolveFboId);

	        // Color-Textur
	        gl.glGenTextures(1, ids, 0);
	        resolveTexId = ids[0];
	        gl.glBindTexture(GL2GL3.GL_TEXTURE_2D, resolveTexId);
	        gl.glTexImage2D(GL2GL3.GL_TEXTURE_2D, 0, GL2GL3.GL_RGBA8,
	                        width, height, 0,
	                        GL2GL3.GL_RGBA, GL2GL3.GL_UNSIGNED_BYTE, null);
	        gl.glTexParameteri(GL2GL3.GL_TEXTURE_2D, GL2GL3.GL_TEXTURE_MIN_FILTER, GL2GL3.GL_LINEAR);
	        gl.glTexParameteri(GL2GL3.GL_TEXTURE_2D, GL2GL3.GL_TEXTURE_MAG_FILTER, GL2GL3.GL_LINEAR);

	        gl.glFramebufferTexture2D(GL2GL3.GL_FRAMEBUFFER,
	                                  GL2GL3.GL_COLOR_ATTACHMENT0,
	                                  GL2GL3.GL_TEXTURE_2D,
	                                  resolveTexId, 0);

	        int status = gl.glCheckFramebufferStatus(GL2GL3.GL_FRAMEBUFFER);
	        if (status != GL2GL3.GL_FRAMEBUFFER_COMPLETE) {
	            throw new GLException("Resolve FBO incomplete: " + status);
	        }

	        gl.glBindFramebuffer(GL2GL3.GL_FRAMEBUFFER, 0);
	    }

	    // Multisample-FBO -> Resolve-FBO blitten
	    gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, fboId);
	    gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, resolveFboId);

	    gl.glBlitFramebuffer(0, 0, width, height,
	                         0, 0, width, height,
	                         GL2GL3.GL_COLOR_BUFFER_BIT,
	                         GL2GL3.GL_NEAREST);

	    gl.glBindFramebuffer(GL2GL3.GL_READ_FRAMEBUFFER, previous[0]);
	    gl.glBindFramebuffer(GL2GL3.GL_DRAW_FRAMEBUFFER, previous[1]);

	    // Die fertige "normale" Textur zurückgeben
	    return resolveTexId;
	}

	public void dispose(GL2GL3 gl) {
	    int[] ids = new int[1];

	    // normale Color-Textur
	    if (textureId > 0) {
	        ids[0] = textureId;
	        gl.glDeleteTextures(1, ids, 0);
	        textureId = 0;
	    }

	    // Depth/Stencil-Renderbuffer
	    if (rboId > 0) {
	        ids[0] = rboId;
	        gl.glDeleteRenderbuffers(1, ids, 0);
	        rboId = 0;
	    }

	    // Haupt-FBO
	    if (fboId > 0) {
	        ids[0] = fboId;
	        gl.glDeleteFramebuffers(1, ids, 0);
	        fboId = 0;
	    }

	    // --- Resolve-Ressourcen (falls Multisample) ---
	    if (resolveTexId != 0) {
	        ids[0] = resolveTexId;
	        gl.glDeleteTextures(1, ids, 0);
	        resolveTexId = 0;
	    }

	    if (resolveFboId != 0) {
	        ids[0] = resolveFboId;
	        gl.glDeleteFramebuffers(1, ids, 0);
	        resolveFboId = 0;
	    }
	}


}
