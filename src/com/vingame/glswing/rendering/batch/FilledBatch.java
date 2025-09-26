package com.vingame.glswing.rendering.batch;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

public class FilledBatch extends Batch {

    private static final int VERTEX_SIZE = 6; // x,y,r,g,b,a
    private static final int MAX_VERTICES = BatchManager.BUFFER_SIZE / VERTEX_SIZE;

    public FilledBatch(BatchManager manager) {
    	super(manager);
        vertexBuffer = Buffers.newDirectFloatBuffer(MAX_VERTICES * VERTEX_SIZE);
        indexBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES * 3); // Triangles, 3 Indices pro
        idBuffer = Buffers.newDirectIntBuffer(MAX_VERTICES);        // 1 ID pro Vertex
    }
    
	@Override
	public boolean canAdd() {
		// TODO Auto-generated method stub
		return false;
	}

    public boolean canAdd(float[] geometry) {
    	int verts = geometry.length / 2;
    	// TODO check for real bounds
        return (vertexCount + verts) <= MAX_VERTICES
                && (indexCount + verts) <= indexBuffer.capacity()
                && (idCount + verts) <= idBuffer.capacity();
    }

    /**
     * Add a filled polygon (geometry as xy pairs).
     * colors.length == 4 → one color for all vertices
     * colors.length == geometry.length * 2 → per-vertex RGBA
     */
    public void addPolygon(float[] geometry, float[] colors, int iD) {
        if (geometry.length % 2 != 0) {
            throw new IllegalArgumentException("geometry must contain xy pairs");
        }
        int verts = geometry.length / 2;

        if (colors.length != 4 && colors.length != verts * 4) {
            throw new IllegalArgumentException("colors must be RGBA (4) or verts*4");
        }

        int base = vertexCount;

        // Vertices
        for (int v = 0; v < verts; v++) {
            float x = geometry[v * 2];
            float y = geometry[v * 2 + 1];

            vertexBuffer.put(x).put(y);

            if (colors.length == 4) {
                vertexBuffer.put(colors[0]).put(colors[1]).put(colors[2]).put(colors[3]);
            } else {
                int off = v * 4;
                vertexBuffer.put(colors[off]).put(colors[off + 1]).put(colors[off + 2]).put(colors[off + 3]);
            }

            idBuffer.put(iD);
        }

        // Indices (triangulate as fan)
        for (int t = 1; t < verts - 1; t++) {
            indexBuffer.put(base);
            indexBuffer.put(base + t);
            indexBuffer.put(base + t + 1);
        }

        vertexCount += verts;
        indexCount += (verts - 2) * 3;
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

        // Upload vertices
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
                vertexBuffer.limit() * Float.BYTES, vertexBuffer);

        // Upload indices
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0,
                indexBuffer.limit() * Integer.BYTES, indexBuffer);

        // --- Attributes ---
        int stride = VERTEX_SIZE * Float.BYTES;

        int locPos = gl.glGetAttribLocation(colorProgram, "a_position");
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
        gl.glEnableVertexAttribArray(locPos);
        gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

        int locCol = gl.glGetAttribLocation(colorProgram, "a_color");
        gl.glEnableVertexAttribArray(locCol);
        gl.glVertexAttribPointer(locCol, 4, GL.GL_FLOAT, false, stride, 2 * Float.BYTES);

        gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

        // Cleanup
        gl.glDisableVertexAttribArray(locPos);
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

        vertexBuffer.flip();
        indexBuffer.flip();
        idBuffer.flip();

        int locProjection = gl.glGetUniformLocation(idProgram, "u_Projection");
        gl.glUniformMatrix4fv(locProjection, 1, false, projection, 0);

        // Upload vertices
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
                vertexBuffer.limit() * Float.BYTES, vertexBuffer);

        // Upload indices
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0,
                indexBuffer.limit() * Integer.BYTES, indexBuffer);

        // Upload IDs
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0,
                idBuffer.limit() * Integer.BYTES, idBuffer);

        // Attributes
        int stride = VERTEX_SIZE * Float.BYTES;

        int locPos = gl.glGetAttribLocation(idProgram, "a_position");
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
        gl.glEnableVertexAttribArray(locPos);
        gl.glVertexAttribPointer(locPos, 2, GL.GL_FLOAT, false, stride, 0);

        int locId = gl.glGetAttribLocation(idProgram, "a_id");
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
        gl.glEnableVertexAttribArray(locId);
        gl.glVertexAttribIPointer(locId, 1, GL.GL_UNSIGNED_INT, 0, 0);

        gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);

        // Cleanup
        gl.glDisableVertexAttribArray(locPos);
        gl.glDisableVertexAttribArray(locId);

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        return 1;
    }

}

