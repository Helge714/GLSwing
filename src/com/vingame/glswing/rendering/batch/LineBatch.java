package com.vingame.glswing.rendering.batch;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.vingame.glswing.rendering.context.GLStroke;
import com.vingame.glswing.rendering.context.Mesh;

public class LineBatch extends Batch {

	private static final int VERTEX_SIZE = 11; // pos(2)+prev(2)+next(2)+side(1)+color(4)
	private static final int MAX_VERTICES = BatchManager.BUFFER_SIZE / VERTEX_SIZE;
	private GLStroke stroke = GLStroke.DEFAULT_STROKE;

	public LineBatch(BatchManager manager) {
		super(manager);
		vertexBuffer = Buffers.newDirectFloatBuffer(MAX_VERTICES * VERTEX_SIZE);
		indexBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES * 3); // Dreiecke
		idBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES);
	}

	@Override
	public boolean canAdd() {
		return false;
	}

	public boolean canAdd(float[] geometry) {
		int verts = geometry.length / 2;
		// TODO check for real bounds
		return (vertexCount + verts) <= MAX_VERTICES && (indexCount + verts) <= indexBuffer.capacity()
				&& (idCount + verts) <= idBuffer.capacity();
	}

	@Override
	public void begin(GL2 gl) {
		super.begin(gl);
		stroke = GLStroke.DEFAULT_STROKE;
	}

	public void setStroke(GLStroke stroke) {
		this.stroke = stroke;
	}

	public GLStroke getStroke() {
		return stroke;
	}

	/**
	 * Add a stroked polyline (via GLStroke.buildMesh).
	 */
	public void addLine(float[] geometry, boolean closed, float[] colors, int iD) {
		Mesh mesh = GLStroke.buildMesh(geometry, closed);
		if (geometry.length % 2 != 0) {
			throw new IllegalArgumentException("geometry must contain xy pairs");
		}
		int verts = mesh.vertices.length / 7;

		if (colors.length != 4 && colors.length != verts * 4) {
			throw new IllegalArgumentException("colors must be RGBA (4) or verts*4");
		}

		int base = vertexCount;

		for (int v = 0; v < verts; v++) {
			vertexBuffer.put(mesh.vertices[v * 7 + 0]); // pos.x
			vertexBuffer.put(mesh.vertices[v * 7 + 1]); // pos.y
			vertexBuffer.put(mesh.vertices[v * 7 + 2]); // prev.x
			vertexBuffer.put(mesh.vertices[v * 7 + 3]); // prev.y
			vertexBuffer.put(mesh.vertices[v * 7 + 4]); // next.x
			vertexBuffer.put(mesh.vertices[v * 7 + 5]); // next.y
			vertexBuffer.put(mesh.vertices[v * 7 + 6]); // side

			// color (global oder per vertex)
			if (colors.length == 4) {
				vertexBuffer.put(colors[0]).put(colors[1]).put(colors[2]).put(colors[3]);
			} else {
				int off = v * 4;
				vertexBuffer.put(colors[off]).put(colors[off + 1]).put(colors[off + 2]).put(colors[off + 3]);
			}

			idBuffer.put(iD);
		}

		for (int i = 0; i < mesh.indices.length; i++) {
			indexBuffer.put(mesh.indices[i] + base);
		}

		vertexCount += verts;
		indexCount += mesh.indices.length;
		idCount += verts;
	}

	@Override
	public int flushMeshes(GL2 gl, float[] projection, int meshVbo, int meshIbo) {
		if (vertexCount == 0)
			return 0;

		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

		vertexBuffer.flip();
		indexBuffer.flip();

		int locProjection = gl.glGetUniformLocation(colorProgram, "u_Projection");
		gl.glUniformMatrix4fv(locProjection, 1, false, projection, 0);
		int locAlign = gl.glGetUniformLocation(colorProgram, "u_align");
		gl.glUniform1f(locAlign, stroke.lineAlign);
		int locThickness = gl.glGetUniformLocation(colorProgram, "u_Thickness");
		gl.glUniform1f(locThickness, stroke.thickness);

		// Upload vertices
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
		gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, vertexBuffer.limit() * Float.BYTES, vertexBuffer);

		// Upload indices
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
		gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0, indexBuffer.limit() * Integer.BYTES, indexBuffer);

		// Attributes
		int stride = VERTEX_SIZE * Float.BYTES;

		int locPos = gl.glGetAttribLocation(colorProgram, "a_position");
		gl.glEnableVertexAttribArray(locPos);
		gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

		int locPrev = gl.glGetAttribLocation(colorProgram, "a_prev");
		gl.glEnableVertexAttribArray(locPrev);
		gl.glVertexAttribPointer(locPrev, 2, GL.GL_FLOAT, false, stride, 2 * Float.BYTES);

		int locNext = gl.glGetAttribLocation(colorProgram, "a_next");
		gl.glEnableVertexAttribArray(locNext);
		gl.glVertexAttribPointer(locNext, 2, GL.GL_FLOAT, false, stride, 4 * Float.BYTES);

		int locSide = gl.glGetAttribLocation(colorProgram, "a_side");
		gl.glEnableVertexAttribArray(locSide);
		gl.glVertexAttribPointer(locSide, 1, GL.GL_FLOAT, false, stride, 6 * Float.BYTES);

		int locCol = gl.glGetAttribLocation(colorProgram, "a_color");
		gl.glEnableVertexAttribArray(locCol);
		gl.glVertexAttribPointer(locCol, 4, GL.GL_FLOAT, false, stride, 7 * Float.BYTES);

		gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

		// Cleanup
		gl.glDisableVertexAttribArray(locPos);
		gl.glDisableVertexAttribArray(locPrev);
		gl.glDisableVertexAttribArray(locNext);
		gl.glDisableVertexAttribArray(locSide);
		gl.glDisableVertexAttribArray(locCol);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glDisable(GL.GL_BLEND);

		return 1;
	}

	@Override
	public int flushIds(GL2 gl, float[] projection, int meshVbo, int meshIbo, int idVbo) {
		if (vertexCount == 0)
			return 0;

		gl.glDisable(GL.GL_BLEND);
		gl.glEnable(GL.GL_CULL_FACE);
		gl.glCullFace(GL.GL_BACK);
		gl.glFrontFace(GL.GL_CW);

		vertexBuffer.flip();
		indexBuffer.flip();
		idBuffer.flip();

		int locProjection = gl.glGetUniformLocation(idProgram, "u_Projection");
		gl.glUniformMatrix4fv(locProjection, 1, false, projection, 0);
		int locAlign = gl.glGetUniformLocation(idProgram, "u_align");
		gl.glUniform1f(locAlign, stroke.lineAlign);
		int locThickness = gl.glGetUniformLocation(idProgram, "u_Thickness");
		gl.glUniform1f(locThickness, stroke.thickness);

		// Upload vertices
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
		gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, vertexBuffer.limit() * Float.BYTES, vertexBuffer);

		// Upload indices
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
		gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0, indexBuffer.limit() * Integer.BYTES, indexBuffer);

		// Upload IDs
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
		gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, idBuffer.limit() * Integer.BYTES, idBuffer);

		// Attributes
		int stride = VERTEX_SIZE * Float.BYTES;

		int locPos = gl.glGetAttribLocation(idProgram, "a_position");
		gl.glEnableVertexAttribArray(locPos);
		gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

		int locPrev = gl.glGetAttribLocation(idProgram, "a_prev");
		gl.glEnableVertexAttribArray(locPrev);
		gl.glVertexAttribPointer(locPrev, 2, GL.GL_FLOAT, false, stride, 2 * Float.BYTES);

		int locNext = gl.glGetAttribLocation(idProgram, "a_next");
		gl.glEnableVertexAttribArray(locNext);
		gl.glVertexAttribPointer(locNext, 2, GL.GL_FLOAT, false, stride, 4 * Float.BYTES);

		int locSide = gl.glGetAttribLocation(idProgram, "a_side");
		gl.glEnableVertexAttribArray(locSide);
		gl.glVertexAttribPointer(locSide, 1, GL.GL_FLOAT, false, stride, 6 * Float.BYTES);

		int locId = gl.glGetAttribLocation(idProgram, "a_id");
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
		gl.glEnableVertexAttribArray(locId);
		gl.glVertexAttribIPointer(locId, 1, GL.GL_UNSIGNED_INT, 0, 0);

		gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

		// Cleanup
		gl.glDisableVertexAttribArray(locPos);
		gl.glDisableVertexAttribArray(locPrev);
		gl.glDisableVertexAttribArray(locNext);
		gl.glDisableVertexAttribArray(locSide);
		gl.glDisableVertexAttribArray(locId);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

		return 1;
	}
}
