package com.vingame.glswing.rendering.batch;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

public class TextureBatch extends Batch {

	private static final int VERTEX_SIZE = 8; // x,y,u,v,r,g,b,a
	private static final int MAX_VERTICES = BatchManager.BUFFER_SIZE / VERTEX_SIZE;

	public int textureId;

	public TextureBatch(BatchManager manager) {
		super(manager);
		vertexBuffer = Buffers.newDirectFloatBuffer(MAX_VERTICES * VERTEX_SIZE);
		indexBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES * 6 / 4); // 6 Indices pro Quad
		idBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES * 4 / 4); // 1 ID pro Quad
	}

	public boolean canAdd() {
		return (vertexCount + 4) <= MAX_VERTICES && (indexCount + 6) <= indexBuffer.capacity()
				&& (idCount + 4) <= idBuffer.capacity();
	}

	public void addQuad(float[] geom, float[] uv, float[] cs, int iD) {
		if (geom.length != 8)
			throw new IllegalArgumentException("length " + geom.length + " is not valid for texture geometry (8)");
		if (uv.length != 8)
			throw new IllegalArgumentException("length " + uv.length + " is not valid for texture uvs (8)");

		int base = vertexCount;

		// 4 vertices
		putVertex(geom[0], geom[1], uv[0], uv[1], cs, 0);
		putVertex(geom[2], geom[3], uv[2], uv[3], cs, 1);
		putVertex(geom[4], geom[5], uv[4], uv[5], cs, 2);
		putVertex(geom[6], geom[7], uv[6], uv[7], cs, 3);

		// 6 indices
		indexBuffer.put(base);
		indexBuffer.put(base + 1);
		indexBuffer.put(base + 2);
		indexBuffer.put(base);
		indexBuffer.put(base + 2);
		indexBuffer.put(base + 3);

		// 1 ID
		idBuffer.put(iD).put(iD).put(iD).put(iD);

		vertexCount += 4;
		indexCount += 6;
		idCount += 4;
	}

	private void putVertex(float x, float y, float u, float v, float[] cs, int corner) {
		vertexBuffer.put(x).put(y).put(u).put(v);
		if (cs.length == 4) {
			vertexBuffer.put(cs[0]).put(cs[1]).put(cs[2]).put(cs[3]);
		} else {
			int off = corner * 4;
			vertexBuffer.put(cs[off]).put(cs[off + 1]).put(cs[off + 2]).put(cs[off + 3]);
		}
	}
	
	@Override
	public int flushMeshes(GL2 gl, float[] projection, int meshVbo, int meshIbo) {
		gl.glEnable(GL.GL_BLEND);
	    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

	    vertexBuffer.flip();
	    indexBuffer.flip();

	    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
	    
	    int locProjection = gl.glGetUniformLocation(colorProgram, "u_Projection");
		gl.glUniformMatrix4fv(locProjection, 1, false, projection, 0);

	    // --- Upload Vertices (Float-Daten) ---
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
	    gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
	            vertexBuffer.limit() * Float.BYTES, vertexBuffer);

	    // --- Upload Indices ---
	    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
	    gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0,
	            indexBuffer.limit() * Integer.BYTES, indexBuffer);

	    // --- Vertex-Attribute setzen ---
	    int stride = 8 * Float.BYTES; // 8 floats pro Vertex

	    // Position (2 floats)
	    int locPos = gl.glGetAttribLocation(colorProgram, "a_position");
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
	    gl.glEnableVertexAttribArray(locPos);
	    gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

	    // Texcoords (2 floats)
	    int locUv = gl.glGetAttribLocation(colorProgram, "a_texcoord");
	    gl.glEnableVertexAttribArray(locUv);
	    gl.glVertexAttribPointer(locUv, 2, GL.GL_FLOAT, false, stride, 2 * Float.BYTES);

	    // Color (4 floats)
	    int locCol = gl.glGetAttribLocation(colorProgram, "a_color");
	    gl.glEnableVertexAttribArray(locCol);
	    gl.glVertexAttribPointer(locCol, 4, GL.GL_FLOAT, false, stride, 4 * Float.BYTES);

	    // --- Draw ---
	    gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

	    // Cleanup
	    gl.glDisableVertexAttribArray(locPos);
	    gl.glDisableVertexAttribArray(locUv);
	    gl.glDisableVertexAttribArray(locCol);

	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
	    gl.glDisable(GL.GL_BLEND);

	    return 1;
	}

	@Override
	public int flushIds(GL2 gl, float[] projection, int meshVbo, int meshIbo, int idVbo) {
		gl.glDisable(GL.GL_BLEND);

	    vertexBuffer.flip();
	    indexBuffer.flip();
	    idBuffer.flip();
	    
	    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
	    
	    int locProjection = gl.glGetUniformLocation(colorProgram, "u_Projection");
		gl.glUniformMatrix4fv(locProjection, 1, false, projection, 0);

	    // --- Upload Vertices (Float-Daten) ---
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
	    gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
	            vertexBuffer.limit() * Float.BYTES, vertexBuffer);

	    // --- Upload Indices ---
	    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
	    gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0,
	            indexBuffer.limit() * Integer.BYTES, indexBuffer);

	    // --- Upload IDs (Integer-Daten) ---
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
	    gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
	            idBuffer.limit() * Integer.BYTES, idBuffer);

	    // --- Vertex-Attribute setzen ---
	    int stride = 8 * Float.BYTES; // 8 floats pro Vertex

	    // Position (2 floats)
	    int locPos = gl.glGetAttribLocation(idProgram, "a_position");
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
	    gl.glEnableVertexAttribArray(locPos);
	    gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

	    // Texcoords (2 floats)
	    int locUv = gl.glGetAttribLocation(idProgram, "a_texcoord");
	    gl.glEnableVertexAttribArray(locUv);
	    gl.glVertexAttribPointer(locUv, 2, GL.GL_FLOAT, false, stride, 2 * Float.BYTES);

	    // IDs (1 uint aus separatem VBO!)
	    int locId = gl.glGetAttribLocation(idProgram, "a_id");
	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
	    gl.glEnableVertexAttribArray(locId);
	    gl.glVertexAttribIPointer(locId, 1, GL.GL_UNSIGNED_INT, 0, 0);

	    // --- Draw ---
	    gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

	    // Cleanup
	    gl.glDisableVertexAttribArray(locPos);
	    gl.glDisableVertexAttribArray(locUv);
	    gl.glDisableVertexAttribArray(locId);

	    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
	    
	    return 1;
	}

}
