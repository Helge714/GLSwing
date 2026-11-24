package com.vingame.glswing.rendering.batch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.ShaderProgram;
import com.vingame.glswing.rendering.internal.FilledVertexFormat;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.VertexModifier;

/** Internal dynamic batch for solid-color triangles. */
public final class FilledBatch {

	public static final int FLOATS_PER_VERTEX = FilledVertexFormat.FLOATS_PER_VERTEX;
	private static final int MAX_VERTICES = 65_536;

	private final FloatBuffer vertices = Buffers.newDirectFloatBuffer(MAX_VERTICES * FLOATS_PER_VERTEX);
	private final IntBuffer pickIds = Buffers.newDirectIntBuffer(MAX_VERTICES);
	private final ShaderProgram program;
	private int vao;
	private int vbo;
	private int pickVbo;
	private int vertexCount;
	private boolean hasPickableVertices;

	private FilledBatch(ShaderProgram program, int vao, int vbo, int pickVbo) {
		this.program = program;
		this.vao = vao;
		this.vbo = vbo;
		this.pickVbo = pickVbo;
	}

	public static FilledBatch create(GL3 gl) {
		ShaderProgram program = ShaderProgram.fromBuiltInResources(gl,
				"filled.vert", "filled.frag");
		int[] ids = new int[1];
		int vao = 0;
		int vbo = 0;
		int pickVbo = 0;
		try {
			gl.glGenVertexArrays(1, ids, 0);
			vao = ids[0];
			gl.glBindVertexArray(vao);

			gl.glGenBuffers(1, ids, 0);
			vbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) verticesByteCapacity(), null, GL.GL_DYNAMIC_DRAW);

			int stride = FLOATS_PER_VERTEX * Float.BYTES;
			gl.glEnableVertexAttribArray(0);
			gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, stride, 0);
			gl.glEnableVertexAttribArray(1);
			gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, stride, 2L * Float.BYTES);

			gl.glGenBuffers(1, ids, 0);
			pickVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) MAX_VERTICES * Integer.BYTES, null, GL.GL_DYNAMIC_DRAW);
			gl.glEnableVertexAttribArray(3);
			gl.glVertexAttribIPointer(3, 1, GL.GL_UNSIGNED_INT, Integer.BYTES, 0);

			return new FilledBatch(program, vao, vbo, pickVbo);
		} catch (RuntimeException error) {
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
		vertexCount = 0;
		hasPickableVertices = false;
	}

	public boolean canAdd(int additionalVertices) {
		return additionalVertices >= 0 && vertexCount + additionalVertices <= MAX_VERTICES;
	}

	public int remainingCapacity() {
		return MAX_VERTICES - vertexCount;
	}

	/** Adds one normalized, uniformly colored triangle. */
	public void addTriangle(float x1, float y1, float x2, float y2,
			float x3, float y3, float red, float green, float blue, float alpha,
			int pickId) {
		if (!canAdd(3))
			throw new IllegalStateException("Filled batch capacity exceeded");
		putVertex(x1, y1, red, green, blue, alpha, pickId);
		putVertex(x2, y2, red, green, blue, alpha, pickId);
		putVertex(x3, y3, red, green, blue, alpha, pickId);
		vertexCount += 3;
	}

	/** Adds one normalized triangle with an independently colored vertex triplet. */
	public void addTriangle(float x1, float y1,
			float red1, float green1, float blue1, float alpha1,
			float x2, float y2, float red2, float green2, float blue2, float alpha2,
			float x3, float y3, float red3, float green3, float blue3, float alpha3,
			int pickId) {
		if (!canAdd(3))
			throw new IllegalStateException("Filled batch capacity exceeded");
		putVertex(x1, y1, red1, green1, blue1, alpha1, pickId);
		putVertex(x2, y2, red2, green2, blue2, alpha2, pickId);
		putVertex(x3, y3, red3, green3, blue3, alpha3, pickId);
		vertexCount += 3;
	}

	/** Adds two normalized, uniformly colored triangles. */
	public void addTriangles(float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4, float x5, float y5,
			float x6, float y6, float red, float green, float blue, float alpha,
			int pickId) {
		if (!canAdd(6))
			throw new IllegalStateException("Filled batch capacity exceeded");
		putVertex(x1, y1, red, green, blue, alpha, pickId);
		putVertex(x2, y2, red, green, blue, alpha, pickId);
		putVertex(x3, y3, red, green, blue, alpha, pickId);
		putVertex(x4, y4, red, green, blue, alpha, pickId);
		putVertex(x5, y5, red, green, blue, alpha, pickId);
		putVertex(x6, y6, red, green, blue, alpha, pickId);
		vertexCount += 6;
	}

	public void addVertices(float[] triangleVertices, int pickId) {
		if (triangleVertices == null)
			throw new IllegalArgumentException("Polygon vertex data must not be null");
		addVertices(triangleVertices, 0,
				triangleVertices.length / FLOATS_PER_VERTEX, pickId);
	}

	/** Adds a range of already normalized interleaved triangle vertices. */
	public void addVertices(float[] triangleVertices, int offset,
			int additionalVertices, int pickId) {
		addVertices(triangleVertices, offset, additionalVertices, pickId, null);
	}

	/** Adds normalized vertices and optionally transforms their x/y coordinates. */
	public void addVertices(float[] triangleVertices, int offset,
			int additionalVertices, int pickId, VertexModifier modifier) {
		if (triangleVertices == null)
			throw new IllegalArgumentException("Polygon vertex data must not be null");
		if (triangleVertices.length % FLOATS_PER_VERTEX != 0)
			throw new IllegalArgumentException("Polygon vertex data has an invalid length");
		if (offset < 0 || additionalVertices < 0
				|| offset + additionalVertices * FLOATS_PER_VERTEX > triangleVertices.length)
			throw new IllegalArgumentException("Polygon vertex range lies outside the array");
		if (!canAdd(additionalVertices))
			throw new IllegalStateException("Filled batch capacity exceeded");
		if (modifier == null || modifier.isIdentity()) {
			vertices.put(triangleVertices, offset,
					additionalVertices * FLOATS_PER_VERTEX);
		} else {
			boolean translation = modifier.isTranslation();
			float translateX = modifier.getTranslateX();
			float translateY = modifier.getTranslateY();
			for (int vertex = 0; vertex < additionalVertices; vertex++) {
				int source = offset + vertex * FLOATS_PER_VERTEX;
				float x = triangleVertices[source];
				float y = triangleVertices[source + 1];
				vertices.put(translation ? x + translateX : modifier.transformX(x, y));
				vertices.put(translation ? y + translateY : modifier.transformY(x, y));
				if (modifier.changesColor()) {
					vertices.put(modifier.red(triangleVertices[source + 2]));
					vertices.put(modifier.green(triangleVertices[source + 3]));
					vertices.put(modifier.blue(triangleVertices[source + 4]));
					vertices.put(modifier.alpha(triangleVertices[source + 5]));
				} else {
					vertices.put(triangleVertices, source + 2, FLOATS_PER_VERTEX - 2);
				}
			}
		}
		for (int vertex = 0; vertex < additionalVertices; vertex++)
			pickIds.put(pickId);
		hasPickableVertices |= pickId != 0;
		vertexCount += additionalVertices;
	}

	/** Uploads all pending vertices and returns the number of issued draw calls. */
	public int flush(GL3 gl, float[] projection, ShaderProgram shaderOverride,
			Map<String, ShaderUniformValue> userUniforms, PickFramebuffer picking,
			boolean renderColor, boolean renderPicking) {
		if (vertexCount == 0)
			return 0;
		boolean drawPicking = renderPicking && hasPickableVertices;
		if (!renderColor && !drawPicking) {
			reset();
			return 0;
		}

		vertices.flip();
		pickIds.flip();
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
		int drawCalls = 0;
		if (renderColor) {
			ShaderProgram activeProgram = shaderOverride == null ? program : shaderOverride;
			activeProgram.use(gl);
			activeProgram.setMatrix4(gl, "uProjection", projection);
			for (Map.Entry<String, ShaderUniformValue> uniform : userUniforms.entrySet())
				uniform.getValue().apply(gl, activeProgram, uniform.getKey());
			gl.glDrawArrays(GL.GL_TRIANGLES, 0, vertexCount);
			drawCalls++;
		}
		if (drawPicking) {
			picking.drawFilled(gl, vao, vertexCount, projection);
			drawCalls++;
		}
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindVertexArray(0);
		gl.glUseProgram(0);

		reset();
		return drawCalls;
	}

	public void dispose(GL3 gl) {
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

	private void putVertex(float x, float y, float red, float green,
			float blue, float alpha, int pickId) {
		vertices.put(x).put(y);
		vertices.put(red).put(green).put(blue).put(alpha);
		pickIds.put(pickId);
		hasPickableVertices |= pickId != 0;
	}

	private void reset() {
		vertices.clear();
		pickIds.clear();
		vertexCount = 0;
		hasPickableVertices = false;
	}

	private static int verticesByteCapacity() {
		return MAX_VERTICES * FLOATS_PER_VERTEX * Float.BYTES;
	}
}
