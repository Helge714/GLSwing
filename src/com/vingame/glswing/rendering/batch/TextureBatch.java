package com.vingame.glswing.rendering.batch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;
import com.vingame.glswing.rendering.internal.TexturedVertexFormat;
import com.vingame.glswing.rendering.internal.VertexModifier;

/** Internal dynamic batch for tinted quads using one texture at a time. */
public final class TextureBatch {

	private static final int MAX_VERTICES = 65_536;

	private final FloatBuffer vertices = Buffers.newDirectFloatBuffer(
			MAX_VERTICES * TexturedVertexFormat.FLOATS_PER_VERTEX);
	private final IntBuffer pickIds = Buffers.newDirectIntBuffer(MAX_VERTICES);
	private final IntBuffer textureSlots = Buffers.newDirectIntBuffer(MAX_VERTICES);
	private final TextureSlotSet textures = new TextureSlotSet();
	private final ShaderProgram program;
	private int vao;
	private int vbo;
	private int pickVbo;
	private int slotVbo;
	private int vertexCount;
	private boolean hasPickableVertices;

	private TextureBatch(ShaderProgram program, int vao, int vbo, int pickVbo,
			int slotVbo) {
		this.program = program;
		this.vao = vao;
		this.vbo = vbo;
		this.pickVbo = pickVbo;
		this.slotVbo = slotVbo;
	}

	public static TextureBatch create(GL3 gl) {
		ShaderProgram program = ShaderProgram.fromBuiltInResources(gl,
				"texture.vert", "texture.frag");
		int[] ids = new int[1];
		int vao = 0;
		int vbo = 0;
		int pickVbo = 0;
		int slotVbo = 0;
		try {
			gl.glGenVertexArrays(1, ids, 0);
			vao = ids[0];
			gl.glBindVertexArray(vao);

			gl.glGenBuffers(1, ids, 0);
			vbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) verticesByteCapacity(), null, GL.GL_DYNAMIC_DRAW);

			int stride = TexturedVertexFormat.FLOATS_PER_VERTEX * Float.BYTES;
			gl.glEnableVertexAttribArray(0);
			gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, stride, 0);
			gl.glEnableVertexAttribArray(1);
			gl.glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, stride, 2L * Float.BYTES);
			gl.glEnableVertexAttribArray(2);
			gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, stride, 4L * Float.BYTES);

			gl.glGenBuffers(1, ids, 0);
			pickVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) MAX_VERTICES * Integer.BYTES, null, GL.GL_DYNAMIC_DRAW);
			gl.glEnableVertexAttribArray(3);
			gl.glVertexAttribIPointer(3, 1, GL.GL_UNSIGNED_INT, Integer.BYTES, 0);

			gl.glGenBuffers(1, ids, 0);
			slotVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, slotVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) MAX_VERTICES * Integer.BYTES, null, GL.GL_DYNAMIC_DRAW);
			gl.glEnableVertexAttribArray(4);
			gl.glVertexAttribIPointer(4, 1, GL.GL_UNSIGNED_INT, Integer.BYTES, 0);

			return new TextureBatch(program, vao, vbo, pickVbo, slotVbo);
		} catch (RuntimeException error) {
			if (slotVbo != 0)
				gl.glDeleteBuffers(1, new int[] { slotVbo }, 0);
			if (pickVbo != 0)
				gl.glDeleteBuffers(1, new int[] { pickVbo }, 0);
			if (vbo != 0)
				gl.glDeleteBuffers(1, new int[] { vbo }, 0);
			if (vao != 0)
				gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
			program.dispose(gl);
			throw error;
		} finally {
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
			gl.glBindVertexArray(0);
		}
	}

	public void begin() {
		vertices.clear();
		pickIds.clear();
		textureSlots.clear();
		vertexCount = 0;
		textures.clear();
		hasPickableVertices = false;
	}

	public boolean canAdd(int additionalVertices) {
		return additionalVertices >= 0 && vertexCount + additionalVertices <= MAX_VERTICES;
	}

	public int remainingCapacity() {
		return MAX_VERTICES - vertexCount;
	}

	public boolean accepts(GLTexture candidate) {
		return accepts(candidate, TextureSlotSet.MAX_SLOTS);
	}

	public boolean accepts(GLTexture candidate, int textureLimit) {
		return textures.accepts(candidate, textureLimit);
	}

	public void addQuad(GLTexture texture,
			float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float u0, float v0, float u1, float v1,
			float red, float green, float blue, float alpha, int pickId) {
		addQuad(texture, x1, y1, x2, y2, x3, y3, x4, y4,
				u0, v0, u1, v1, red, green, blue, alpha, pickId,
				TextureSlotSet.MAX_SLOTS);
	}

	public void addQuad(GLTexture texture,
			float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float u0, float v0, float u1, float v1,
			float red, float green, float blue, float alpha, int pickId,
			int textureLimit) {
		if (texture == null || texture.isDisposed())
			throw new IllegalArgumentException("Texture must be live");
		if (!accepts(texture, textureLimit))
			throw new IllegalStateException("Texture batch requires a flush before changing texture");
		if (!canAdd(6))
			throw new IllegalStateException("Texture batch capacity exceeded");

		int slot = textures.slot(texture, textureLimit);
		putVertex(x1, y1, u0, v0, red, green, blue, alpha, pickId, slot);
		putVertex(x2, y2, u1, v0, red, green, blue, alpha, pickId, slot);
		putVertex(x3, y3, u1, v1, red, green, blue, alpha, pickId, slot);
		putVertex(x1, y1, u0, v0, red, green, blue, alpha, pickId, slot);
		putVertex(x3, y3, u1, v1, red, green, blue, alpha, pickId, slot);
		putVertex(x4, y4, u0, v1, red, green, blue, alpha, pickId, slot);
		vertexCount += 6;
	}

	/** Adds a range of normalized textured triangle vertices. */
	public void addVertices(GLTexture texture, float[] texturedVertices,
			int offset, int additionalVertices, int pickId) {
		addVertices(texture, texturedVertices, offset, additionalVertices, pickId, null);
	}

	/** Adds textured vertices and optionally transforms their x/y coordinates. */
	public void addVertices(GLTexture texture, float[] texturedVertices,
			int offset, int additionalVertices, int pickId, VertexModifier modifier) {
		addVertices(texture, texturedVertices, offset, additionalVertices,
				pickId, modifier, TextureSlotSet.MAX_SLOTS);
	}

	public void addVertices(GLTexture texture, float[] texturedVertices,
			int offset, int additionalVertices, int pickId, VertexModifier modifier,
			int textureLimit) {
		if (texture == null || texture.isDisposed())
			throw new IllegalArgumentException("Texture must be live");
		if (texturedVertices == null
				|| texturedVertices.length % TexturedVertexFormat.FLOATS_PER_VERTEX != 0)
			throw new IllegalArgumentException("Texture vertex data has an invalid length");
		if (offset < 0 || additionalVertices < 0
				|| offset + additionalVertices * TexturedVertexFormat.FLOATS_PER_VERTEX
						> texturedVertices.length)
			throw new IllegalArgumentException("Texture vertex range lies outside the array");
		if (!accepts(texture, textureLimit))
			throw new IllegalStateException("Texture batch requires a flush before changing texture");
		if (!canAdd(additionalVertices))
			throw new IllegalStateException("Texture batch capacity exceeded");
		int slot = textures.slot(texture, textureLimit);
		int stride = TexturedVertexFormat.FLOATS_PER_VERTEX;
		if (modifier == null || modifier.isIdentity()) {
			vertices.put(texturedVertices, offset, additionalVertices * stride);
		} else {
			boolean translation = modifier.isTranslation();
			float translateX = modifier.getTranslateX();
			float translateY = modifier.getTranslateY();
			for (int vertex = 0; vertex < additionalVertices; vertex++) {
				int source = offset + vertex * stride;
				float x = texturedVertices[source];
				float y = texturedVertices[source + 1];
				vertices.put(translation ? x + translateX : modifier.transformX(x, y));
				vertices.put(translation ? y + translateY : modifier.transformY(x, y));
				vertices.put(texturedVertices, source + 2, 2);
				if (modifier.changesColor()) {
					vertices.put(modifier.red(texturedVertices[source + 4]));
					vertices.put(modifier.green(texturedVertices[source + 5]));
					vertices.put(modifier.blue(texturedVertices[source + 6]));
					vertices.put(modifier.alpha(texturedVertices[source + 7]));
				} else {
					vertices.put(texturedVertices, source + 4, 4);
				}
			}
		}
		for (int vertex = 0; vertex < additionalVertices; vertex++) {
			pickIds.put(pickId);
			textureSlots.put(slot);
		}
		hasPickableVertices |= pickId != 0;
		vertexCount += additionalVertices;
	}

	/** Uploads all pending vertices and returns the number of issued draw calls. */
	public int flush(GL3 gl, float[] projection, ShaderProgram shaderOverride,
			Map<String, ShaderUniformValue> userUniforms, PickFramebuffer picking,
			boolean renderColor, boolean renderPicking, float pickAlphaThreshold) {
		if (vertexCount == 0)
			return 0;
		boolean drawPicking = renderPicking && hasPickableVertices;
		if (!renderColor && !drawPicking) {
			reset();
			return 0;
		}

		vertices.flip();
		pickIds.flip();
		textureSlots.flip();
		textures.bind(gl);
		gl.glBindVertexArray(vao);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) vertices.remaining() * Float.BYTES, vertices, GL3.GL_STREAM_DRAW);
		if (drawPicking) {
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) pickIds.remaining() * Integer.BYTES,
					pickIds, GL3.GL_STREAM_DRAW);
		}
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, slotVbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) textureSlots.remaining() * Integer.BYTES,
				textureSlots, GL3.GL_STREAM_DRAW);
		int drawCalls = 0;
		if (renderColor) {
			ShaderProgram activeProgram = shaderOverride == null ? program : shaderOverride;
			activeProgram.use(gl);
			activeProgram.setMatrix4(gl, "uProjection", projection);
			if (shaderOverride == null)
				textures.setSamplerUniforms(gl, activeProgram, "uTextures");
			else
				activeProgram.setInteger(gl, "uTexture", 0);
			for (Map.Entry<String, ShaderUniformValue> uniform : userUniforms.entrySet())
				uniform.getValue().apply(gl, activeProgram, uniform.getKey());
			gl.glDrawArrays(GL.GL_TRIANGLES, 0, vertexCount);
			drawCalls++;
		}
		if (drawPicking) {
			picking.drawTexture(gl, vao, vertexCount, projection,
					textures.snapshot(), pickAlphaThreshold);
			drawCalls++;
		}
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindVertexArray(0);
		textures.unbind(gl);
		gl.glUseProgram(0);

		reset();
		return drawCalls;
	}

	public void dispose(GL3 gl) {
		if (slotVbo != 0) {
			gl.glDeleteBuffers(1, new int[] { slotVbo }, 0);
			slotVbo = 0;
		}
		if (pickVbo != 0) {
			gl.glDeleteBuffers(1, new int[] { pickVbo }, 0);
			pickVbo = 0;
		}
		if (vbo != 0) {
			gl.glDeleteBuffers(1, new int[] { vbo }, 0);
			vbo = 0;
		}
		if (vao != 0) {
			gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
			vao = 0;
		}
		program.dispose(gl);
	}

	private void putVertex(float x, float y, float u, float v,
			float red, float green, float blue, float alpha, int pickId, int slot) {
		vertices.put(x).put(y).put(u).put(v);
		vertices.put(red).put(green).put(blue).put(alpha);
		pickIds.put(pickId);
		textureSlots.put(slot);
		hasPickableVertices |= pickId != 0;
	}

	private void reset() {
		vertices.clear();
		pickIds.clear();
		textureSlots.clear();
		vertexCount = 0;
		textures.clear();
		hasPickableVertices = false;
	}

	private static int verticesByteCapacity() {
		return MAX_VERTICES * TexturedVertexFormat.FLOATS_PER_VERTEX * Float.BYTES;
	}
}
