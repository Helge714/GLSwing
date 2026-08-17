package com.vingame.glswing.rendering.batch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.UIQuadInstanceFormat;
import com.vingame.glswing.rendering.internal.VertexModifier;

/** Instanced batch shared by standard UI shapes, images and MSDF glyphs. */
public final class UIQuadBatch {

	private static final int MAX_INSTANCES = 65_536;
	private static final int STRIDE = UIQuadInstanceFormat.FLOATS_PER_INSTANCE;
	private static final float TEXT_PICK_ALPHA_THRESHOLD = 0.1f;
	private static final float[] UNIT_QUAD = {
			0, 0, 1, 0, 1, 1,
			0, 0, 1, 1, 0, 1
	};

	private final FloatBuffer instances = Buffers.newDirectFloatBuffer(
			MAX_INSTANCES * STRIDE);
	private final IntBuffer pickIds = Buffers.newDirectIntBuffer(MAX_INSTANCES);
	private final ShaderProgram program;
	private int vao;
	private int cornerVbo;
	private int instanceVbo;
	private int pickVbo;
	private int instanceCount;
	private GLTexture texture;
	private boolean hasPickableInstances;

	private UIQuadBatch(ShaderProgram program, int vao, int cornerVbo,
			int instanceVbo, int pickVbo) {
		this.program = program;
		this.vao = vao;
		this.cornerVbo = cornerVbo;
		this.instanceVbo = instanceVbo;
		this.pickVbo = pickVbo;
	}

	public static UIQuadBatch create(GL3 gl) {
		ShaderProgram program = ShaderProgram.fromBuiltInResources(
				gl, "ui_quad.vert", "ui_quad.frag");
		int[] ids = new int[1];
		int vao = 0;
		int cornerVbo = 0;
		int instanceVbo = 0;
		int pickVbo = 0;
		try {
			gl.glGenVertexArrays(1, ids, 0);
			vao = ids[0];
			gl.glBindVertexArray(vao);

			gl.glGenBuffers(1, ids, 0);
			cornerVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, cornerVbo);
			FloatBuffer corners = Buffers.newDirectFloatBuffer(UNIT_QUAD);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) UNIT_QUAD.length * Float.BYTES, corners, GL.GL_STATIC_DRAW);
			gl.glEnableVertexAttribArray(0);
			gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 2 * Float.BYTES, 0);

			gl.glGenBuffers(1, ids, 0);
			instanceVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instanceVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) MAX_INSTANCES * STRIDE * Float.BYTES,
					null, GL.GL_DYNAMIC_DRAW);
			int byteStride = STRIDE * Float.BYTES;
			attribute(gl, 1, 2, byteStride, 0);
			attribute(gl, 2, 2, byteStride, 2);
			attribute(gl, 3, 2, byteStride, 4);
			attribute(gl, 4, 4, byteStride, 6);
			attribute(gl, 5, 4, byteStride, 10);
			attribute(gl, 6, 4, byteStride, 14);
			attribute(gl, 7, 2, byteStride, 18);
			attribute(gl, 9, 4, byteStride, 20);

			gl.glGenBuffers(1, ids, 0);
			pickVbo = ids[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) MAX_INSTANCES * Integer.BYTES, null, GL.GL_DYNAMIC_DRAW);
			gl.glEnableVertexAttribArray(8);
			gl.glVertexAttribIPointer(8, 1, GL.GL_UNSIGNED_INT, Integer.BYTES, 0);
			gl.glVertexAttribDivisor(8, 1);
			return new UIQuadBatch(program, vao, cornerVbo, instanceVbo, pickVbo);
		} catch (RuntimeException error) {
			if (pickVbo != 0)
				gl.glDeleteBuffers(1, new int[] { pickVbo }, 0);
			if (instanceVbo != 0)
				gl.glDeleteBuffers(1, new int[] { instanceVbo }, 0);
			if (cornerVbo != 0)
				gl.glDeleteBuffers(1, new int[] { cornerVbo }, 0);
			if (vao != 0)
				gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
			program.dispose(gl);
			throw error;
		} finally {
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
			gl.glBindVertexArray(0);
		}
	}

	private static void attribute(GL3 gl, int location, int size,
			int byteStride, int floatOffset) {
		gl.glEnableVertexAttribArray(location);
		gl.glVertexAttribPointer(location, size, GL.GL_FLOAT, false,
				byteStride, (long) floatOffset * Float.BYTES);
		gl.glVertexAttribDivisor(location, 1);
	}

	public void begin() {
		instances.clear();
		pickIds.clear();
		instanceCount = 0;
		texture = null;
		hasPickableInstances = false;
	}

	public boolean canAdd(int count) {
		return count >= 0 && instanceCount + count <= MAX_INSTANCES;
	}

	public int remainingCapacity() {
		return MAX_INSTANCES - instanceCount;
	}

	/** Texture-less instances remain compatible with an already selected atlas. */
	public boolean accepts(GLTexture candidate) {
		return candidate == null || texture == null || texture == candidate;
	}

	public void add(GLTexture candidate, float[] values, int offset,
			int count, int pickId, VertexModifier modifier) {
		if (candidate != null && candidate.isDisposed())
			throw new IllegalArgumentException("UI texture must be live");
		if (values == null || values.length % STRIDE != 0 || offset < 0 || count < 0
				|| offset + count * STRIDE > values.length)
			throw new IllegalArgumentException("UI instance range is invalid");
		if (!accepts(candidate))
			throw new IllegalStateException("UI batch requires a flush before changing texture");
		if (!canAdd(count))
			throw new IllegalStateException("UI batch capacity exceeded");
		if (candidate != null)
			texture = candidate;
		for (int instance = 0; instance < count; instance++) {
			int source = offset + instance * STRIDE;
			putInstance(values, source, modifier);
			pickIds.put(pickId);
		}
		hasPickableInstances |= pickId != 0;
		instanceCount += count;
	}

	private void putInstance(float[] values, int source, VertexModifier modifier) {
		if (modifier == null || modifier.isIdentity()) {
			instances.put(values, source, STRIDE);
			return;
		}
		for (int corner = 0; corner < 3; corner++) {
			float x = values[source + corner * 2];
			float y = values[source + corner * 2 + 1];
			instances.put(modifier.transformX(x, y));
			instances.put(modifier.transformY(x, y));
		}
		instances.put(values, source + 6, 4);
		if (modifier.changesColor()) {
			instances.put(modifier.red(values[source + 10]));
			instances.put(modifier.green(values[source + 11]));
			instances.put(modifier.blue(values[source + 12]));
			instances.put(modifier.alpha(values[source + 13]));
		} else {
			instances.put(values, source + 10, 4);
		}
		instances.put(values, source + 14, 6);
		int mode = (int) values[source + 14];
		if (mode == (int) UIQuadInstanceFormat.MODE_ASYMMETRIC_ROUNDED_FILL
				|| mode == (int) UIQuadInstanceFormat.MODE_ASYMMETRIC_ROUNDED_STROKE
				|| mode == (int) UIQuadInstanceFormat.MODE_ROUNDED_SHADOW) {
			// These four slots carry geometry rather than a secondary color.
			instances.put(values, source + 20, 4);
		} else {
			instances.put(modifier.secondaryRed(values[source + 20]));
			instances.put(modifier.secondaryGreen(values[source + 21]));
			instances.put(modifier.secondaryBlue(values[source + 22]));
			instances.put(modifier.secondaryAlpha(values[source + 23]));
		}
	}

	public int flush(GL3 gl, float[] projection, PickFramebuffer picking,
			boolean renderColor, boolean renderPicking, float textureAlphaThreshold,
			float deviceScaleX, float deviceScaleY) {
		if (instanceCount == 0)
			return 0;
		boolean drawPicking = renderPicking && hasPickableInstances;
		if (!renderColor && !drawPicking) {
			reset();
			return 0;
		}
		instances.flip();
		pickIds.flip();
		gl.glBindVertexArray(vao);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instanceVbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) instances.remaining() * Float.BYTES,
				instances, GL3.GL_STREAM_DRAW);
		if (drawPicking) {
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) pickIds.remaining() * Integer.BYTES,
					pickIds, GL3.GL_STREAM_DRAW);
		}
		if (texture != null)
			texture.bind(gl, 0);
		int drawCalls = 0;
		if (renderColor) {
			program.use(gl);
			program.setMatrix4(gl, "uProjection", projection);
			program.setInteger(gl, "uTexture", 0);
			program.setVector2(gl, "uDeviceScale", deviceScaleX, deviceScaleY);
			gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, instanceCount);
			drawCalls++;
		}
		if (drawPicking) {
			picking.drawUI(gl, vao, instanceCount, projection, texture,
					TEXT_PICK_ALPHA_THRESHOLD, textureAlphaThreshold);
			drawCalls++;
		}
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindVertexArray(0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		gl.glUseProgram(0);
		reset();
		return drawCalls;
	}

	private void reset() {
		instances.clear();
		pickIds.clear();
		instanceCount = 0;
		texture = null;
		hasPickableInstances = false;
	}

	public void dispose(GL3 gl) {
		if (pickVbo != 0)
			gl.glDeleteBuffers(1, new int[] { pickVbo }, 0);
		if (instanceVbo != 0)
			gl.glDeleteBuffers(1, new int[] { instanceVbo }, 0);
		if (cornerVbo != 0)
			gl.glDeleteBuffers(1, new int[] { cornerVbo }, 0);
		if (vao != 0)
			gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
		pickVbo = 0;
		instanceVbo = 0;
		cornerVbo = 0;
		vao = 0;
		program.dispose(gl);
	}
}
