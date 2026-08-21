package com.vingame.glswing.rendering.batch;

import java.util.Arrays;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;

/** Ordered identity set of textures bound to one multi-texture draw. */
final class TextureSlotSet {

	static final int MAX_SLOTS = 16;

	private final GLTexture[] textures = new GLTexture[MAX_SLOTS];
	private int size;

	boolean accepts(GLTexture texture, int limit) {
		return texture == null || find(texture) >= 0 || size < Math.min(limit, MAX_SLOTS);
	}

	int slot(GLTexture texture, int limit) {
		if (texture == null)
			return 0;
		int existing = find(texture);
		if (existing >= 0)
			return existing;
		if (size >= Math.min(limit, MAX_SLOTS))
			throw new IllegalStateException("Texture slot capacity exceeded");
		textures[size] = texture;
		return size++;
	}

	int size() {
		return size;
	}

	GLTexture texture(int slot) {
		return textures[slot];
	}

	GLTexture[] snapshot() {
		return Arrays.copyOf(textures, size);
	}

	void bind(GL3 gl) {
		for (int slot = 0; slot < size; slot++)
			textures[slot].bind(gl, slot);
	}

	void setSamplerUniforms(GL3 gl, ShaderProgram program, String arrayName) {
		for (int slot = 0; slot < MAX_SLOTS; slot++)
			program.setInteger(gl, arrayName + "[" + slot + "]", slot);
	}

	void unbind(GL3 gl) {
		for (int slot = 0; slot < size; slot++) {
			gl.glActiveTexture(GL.GL_TEXTURE0 + slot);
			gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		}
		gl.glActiveTexture(GL.GL_TEXTURE0);
	}

	void clear() {
		Arrays.fill(textures, 0, size, null);
		size = 0;
	}

	private int find(GLTexture texture) {
		for (int slot = 0; slot < size; slot++)
			if (textures[slot] == texture)
				return slot;
		return -1;
	}
}
