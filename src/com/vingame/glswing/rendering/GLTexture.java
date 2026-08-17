package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

/** Owns one OpenGL 2D texture created from a {@link BufferedImage}. */
public final class GLTexture {

	private int id;
	private final int width;
	private final int height;

	private GLTexture(int id, int width, int height) {
		this.id = id;
		this.width = width;
		this.height = height;
	}

	/** Creates a linearly filtered RGBA texture. Must run with a current GL context. */
	public static GLTexture create(GL3 gl, BufferedImage image) {
		return create(gl, image, true, false);
	}

	static GLTexture create(GL3 gl, BufferedImage image,
			boolean linearFiltering, boolean repeating) {
		if (gl == null)
			throw new IllegalArgumentException("GL context must not be null");
		if (image == null)
			throw new IllegalArgumentException("Texture image must not be null");
		if (image.getWidth() <= 0 || image.getHeight() <= 0)
			throw new IllegalArgumentException("Texture image must have a positive size");

		return create(gl, image.getWidth(), image.getHeight(), rgbaPixels(image),
				linearFiltering, repeating);
	}

	static GLTexture createEmpty(GL3 gl, int width, int height,
			boolean linearFiltering) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Texture dimensions must be positive");
		return create(gl, width, height, null, linearFiltering, false);
	}

	private static GLTexture create(GL3 gl, int width, int height,
			ByteBuffer pixels, boolean linearFiltering, boolean repeating) {
		int[] textureId = new int[1];
		gl.glGenTextures(1, textureId, 0);
		if (textureId[0] == 0)
			throw new IllegalStateException("Could not create an OpenGL texture");

		int[] previousActiveTexture = new int[1];
		int[] previousBinding = new int[1];
		int[] previousUnpackAlignment = new int[1];
		gl.glGetIntegerv(GL.GL_ACTIVE_TEXTURE, previousActiveTexture, 0);
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glGetIntegerv(GL.GL_TEXTURE_BINDING_2D, previousBinding, 0);
		gl.glGetIntegerv(GL.GL_UNPACK_ALIGNMENT, previousUnpackAlignment, 0);

		try {
			gl.glBindTexture(GL.GL_TEXTURE_2D, textureId[0]);
			gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
			int filter = linearFiltering ? GL.GL_LINEAR : GL.GL_NEAREST;
			int wrap = repeating ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE;
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, filter);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filter);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrap);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wrap);
			gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height,
					0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixels);
			return new GLTexture(textureId[0], width, height);
		} catch (RuntimeException error) {
			gl.glDeleteTextures(1, textureId, 0);
			throw error;
		} finally {
			gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, previousUnpackAlignment[0]);
			gl.glBindTexture(GL.GL_TEXTURE_2D, previousBinding[0]);
			gl.glActiveTexture(previousActiveTexture[0]);
		}
	}

	void upload(GL3 gl, int x, int y, BufferedImage image) {
		if (id == 0)
			throw new IllegalStateException("Texture has already been disposed");
		if (image == null || x < 0 || y < 0
				|| x + image.getWidth() > width || y + image.getHeight() > height)
			throw new IllegalArgumentException("Texture upload lies outside the texture");
		int[] previousActive = new int[1];
		int[] previousBinding = new int[1];
		int[] previousAlignment = new int[1];
		gl.glGetIntegerv(GL.GL_ACTIVE_TEXTURE, previousActive, 0);
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glGetIntegerv(GL.GL_TEXTURE_BINDING_2D, previousBinding, 0);
		gl.glGetIntegerv(GL.GL_UNPACK_ALIGNMENT, previousAlignment, 0);
		try {
			gl.glBindTexture(GL.GL_TEXTURE_2D, id);
			gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
			gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, x, y,
					image.getWidth(), image.getHeight(), GL.GL_RGBA,
					GL.GL_UNSIGNED_BYTE, rgbaPixels(image));
		} finally {
			gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, previousAlignment[0]);
			gl.glBindTexture(GL.GL_TEXTURE_2D, previousBinding[0]);
			gl.glActiveTexture(previousActive[0]);
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isDisposed() {
		return id == 0;
	}

	/** Binds this texture to the requested zero-based texture unit. */
	public void bind(GL3 gl, int textureUnit) {
		if (id == 0)
			throw new IllegalStateException("Texture has already been disposed");
		if (textureUnit < 0)
			throw new IllegalArgumentException("Texture unit must not be negative");
		gl.glActiveTexture(GL.GL_TEXTURE0 + textureUnit);
		gl.glBindTexture(GL.GL_TEXTURE_2D, id);
	}

	/** Deletes the GPU texture. Must run with its owning GL context current. */
	public void dispose(GL3 gl) {
		if (id != 0) {
			gl.glDeleteTextures(1, new int[] { id }, 0);
			id = 0;
		}
	}

	static ByteBuffer rgbaPixels(BufferedImage image) {
		ByteBuffer pixels = Buffers.newDirectByteBuffer(image.getWidth() * image.getHeight() * 4);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				pixels.put((byte) ((argb >>> 16) & 0xff));
				pixels.put((byte) ((argb >>> 8) & 0xff));
				pixels.put((byte) (argb & 0xff));
				pixels.put((byte) ((argb >>> 24) & 0xff));
			}
		}
		pixels.flip();
		return pixels;
	}
}
