package com.vingame.glswing.rendering.batch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.jogamp.opengl.GL2;

abstract class Batch {

	enum ProgramType {
		COLOR, ID
	}

	protected FloatBuffer vertexBuffer;
	protected IntBuffer indexBuffer;
	protected IntBuffer idBuffer;
	protected int vertexCount = 0;
	protected int indexCount = 0;
	protected int idCount = 0;
	protected int colorProgram, idProgram, defaultColorProgram, defaultIdProgram;
	protected final BatchManager manager;

	protected Map<String, float[]> colorUniforms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	public Batch(BatchManager manager) {
		this.manager = manager;
	}

	public void setColorProgram(int colorProgram) {
		this.colorProgram = colorProgram;
		colorUniforms.clear();
	}

	public void setIdProgram(int idProgram) {
		this.idProgram = idProgram;
	}

	public void setDefaultColorProgram(int defaultColorProgram) {
		this.defaultColorProgram = defaultColorProgram;
	}

	public void setDefaultIDProgram(int defaultIdProgram) {
		this.defaultIdProgram = defaultIdProgram;
	}

	public void setColorUniform(String id, float[] values) {
		colorUniforms.put(id, values);
	}

	public void begin(GL2 gl) {
		vertexBuffer.clear();
		indexBuffer.clear();
		idBuffer.clear();
		vertexCount = 0;
		indexCount = 0;
		idCount = 0;
		
		colorUniforms.clear();
		colorProgram = 0;
		idProgram = 0;
	}

	public abstract boolean canAdd();

	protected void setProgram(GL2 gl, ProgramType type) {
		if (type == null)
			gl.glUseProgram(0);
		else if (type == ProgramType.COLOR) {
			boolean programSet = colorProgram <= 0;
			colorProgram = programSet ? defaultColorProgram : colorProgram;
			gl.glUseProgram(colorProgram);
			if (!programSet)
				setColorUniforms(gl, colorProgram);
		} else if (type == ProgramType.ID) {
			idProgram = idProgram <= 0 ? defaultIdProgram : idProgram;
			gl.glUseProgram(idProgram);
		}
	}

	protected void setColorUniforms(GL2 gl, int programID) {
		for (Entry<String, float[]> entry : colorUniforms.entrySet()) {
			String name = entry.getKey();
			float[] value = entry.getValue();

			int loc = gl.glGetUniformLocation(programID, name);
			if (loc < 0)
//				throw new IllegalArgumentException("Unsupported uniform id: " + name);
				continue; // uniform nicht gefunden

			switch (value.length) {
			case 1:
				gl.glUniform1f(loc, value[0]);
				break;
			case 2:
				gl.glUniform2f(loc, value[0], value[1]);
				break;
			case 3:
				gl.glUniform3f(loc, value[0], value[1], value[2]);
				break;
			case 4:
				gl.glUniform4f(loc, value[0], value[1], value[2], value[3]);
				break;
			case 9:
				gl.glUniformMatrix3fv(loc, 1, false, value, 0);
				break;
			case 16:
				gl.glUniformMatrix4fv(loc, 1, false, value, 0);
				break;
			default:
				throw new IllegalArgumentException("Unsupported uniform size for " + name + ": " + value.length);
			}
		}
	}

	public int flush(GL2 gl, float[] projection, int meshVbo, int meshIbo, int idVbo, boolean colorRendering,
			boolean idRendering) {
		int drawCalls = 0;
		if (colorRendering && vertexCount > 0) {
			if (manager.currentFBO != null)
				manager.currentFBO.bind(gl);
			setProgram(gl, ProgramType.COLOR);
			drawCalls += flushMeshes(gl, projection, meshVbo, meshIbo);
			setProgram(gl, null);
			if (manager.currentFBO != null)
				manager.currentFBO.unbind(gl);
		}
		if (idRendering && vertexCount > 0) {
			manager.idBuffer.bind(gl);
			setProgram(gl, ProgramType.ID);
			drawCalls += flushIds(gl, projection, meshVbo, meshIbo, idVbo);
			setProgram(gl, null);
			manager.idBuffer.unbind(gl);
		}
		begin(gl);
		return drawCalls;
	}

	public abstract int flushMeshes(GL2 gl, float[] projection, int meshVbo, int meshIbo);

	public abstract int flushIds(GL2 gl, float[] projection, int meshVbo, int meshIbo, int idVbo);
}
