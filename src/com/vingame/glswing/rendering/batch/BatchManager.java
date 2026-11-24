package com.vingame.glswing.rendering.batch;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.vingame.glswing.rendering.FBO;
import com.vingame.glswing.rendering.IdFBO;
import com.vingame.glswing.rendering.context.GLProgram;
import com.vingame.glswing.rendering.context.GLProjection;
import com.vingame.glswing.rendering.context.GLShaderUtil;
import com.vingame.glswing.rendering.context.GLStroke;
import com.vingame.glswing.rendering.context.GLTexture;
import com.vingame.glswing.rendering.context.GLTextureUtil;
import com.vingame.glswing.rendering.context.GLStroke.LineJoin;

public class BatchManager {

	public static final int BUFFER_SIZE = 1_000_000;

	public enum Type {
		NONE, TEXTURE, LINE, FILLED
	}

	private Type activeType = Type.NONE;

	private final TextureBatch textureBatch = new TextureBatch(this);
	private final LineBatch lineBatch = new LineBatch(this);
	private final FilledBatch filledBatch = new FilledBatch(this);
	public final IdFBO idBuffer = new IdFBO();

	protected FBO currentFBO = null;

	protected Map<BufferedImage, Integer> textureCache = new IdentityHashMap<>();
	protected Map<GLProgram, Integer> programCache = new HashMap<>();

	private int defaultTextureProgram;
	private int defaultLineProgram;
	private int defaultFilledProgram;
	private int defaultTextureIDProgram;
	private int defaultLineIDProgram;
	private int defaultFilledIDProgram;
	private final static GLStroke DEFAULT_STROKE = new GLStroke(0.5f, LineJoin.MITER, 1);

	private int meshVbo, meshIbo, idVbo;

	private GLProjection projection;
	private boolean colorRendering;
	private boolean idRendering;
	private boolean isStencilEnabled = false;
	private boolean isStencilDrawing = false;
	private boolean whiteStencil = false;

	public int drawCalls = 0;

	public GL2 gl;

	public void init(GL2 gl, int width, int height) {
		width = Math.max(1, width);
		height = Math.max(1, height);
		idBuffer.init(gl, width, height, 8);

		// init shaders
		defaultTextureProgram = getOrUploadProgram(gl, GLProgram.createDefault("texture.vert", "texture.frag"));
		defaultTextureIDProgram = getOrUploadProgram(gl, GLProgram.createDefault("texture.vert", "textureID.frag"));
		defaultLineProgram = getOrUploadProgram(gl, GLProgram.createDefault("line.vert", "line.frag"));
		defaultLineIDProgram = getOrUploadProgram(gl, GLProgram.createDefault("line.vert", "lineID.frag"));
		defaultFilledProgram = getOrUploadProgram(gl, GLProgram.createDefault("filled.vert", "filled.frag"));
		defaultFilledIDProgram = getOrUploadProgram(gl, GLProgram.createDefault("filled.vert", "filledID.frag"));

		// init DynamicBuffers
		int[] buffers = new int[2];
		gl.glGenBuffers(2, buffers, 0);
		meshVbo = buffers[0];
		meshIbo = buffers[1];

		// Speicher reservieren (z. B. für 1e6 floats/indices)
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, meshVbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, BUFFER_SIZE * 4, null, GL.GL_DYNAMIC_DRAW);

		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, meshIbo);
		gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, BUFFER_SIZE * 4, null, GL.GL_DYNAMIC_DRAW);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

		int[] idBuffers = new int[1];
		gl.glGenBuffers(1, idBuffers, 0);
		idVbo = idBuffers[0];

		// Speicher reservieren, z. B. Platz für 1 Million IDs:
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, idVbo);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, BUFFER_SIZE * 4, null, GL.GL_DYNAMIC_DRAW);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

		// init defaultPrograms
		textureBatch.setDefaultColorProgram(defaultTextureProgram);
		textureBatch.setDefaultIDProgram(defaultTextureIDProgram);
		filledBatch.setDefaultColorProgram(defaultFilledProgram);
		filledBatch.setDefaultIDProgram(defaultFilledIDProgram);
		lineBatch.setDefaultColorProgram(defaultLineProgram);
		lineBatch.setDefaultIDProgram(defaultLineIDProgram);

		begin(gl);
	}

	public void dispose(GL2 gl) {
		for (int textureID : textureCache.values())
			gl.glDeleteTextures(1, new int[] { textureID }, 0);
		for (int programID : programCache.values())
			gl.glDeleteProgram(programID);
		textureCache.clear();
		programCache.clear();
		gl.glDeleteBuffers(3, new int[] { meshVbo, meshIbo, idVbo }, 0);
		meshVbo = meshIbo = idVbo = 0;
		idBuffer.dispose(gl);
	}

	public void resize(GL2 gl, int width, int height) {
		if (width <= 0 || height <= 0 || (idBuffer.width == width && idBuffer.height == height))
			return;
		flush();
		idBuffer.dispose(gl);
		idBuffer.init(gl, width, height, 1);
		gl.glViewport(0, 0, width, height);
	}

	public void begin(GL2 gl) {
		this.gl = gl;

		// reset drawCalls
		drawCalls = 0;

		// reset current values, fbo, stroke, stencil ...
		currentFBO = null;
		projection = GLProjection.DEFAULT_PROJECTION;
		colorRendering = true;
		idRendering = false;

		setStroke(DEFAULT_STROKE);
		setStencil(false);
		setStencilDrawing(false);

		// clearFBO
		gl.glClearColor(1, 1, 1, 1);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);

		// clearIdFBO
		idBuffer.bind(gl);
		gl.glClearColor(0, 0, 0, 1);
		gl.glClearStencil(1);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
		idBuffer.unbind(gl);
		clearStencil(true);

		// begin batches
		textureBatch.begin(gl);
		filledBatch.begin(gl);
		lineBatch.begin(gl);

	}

	public void drawTexture(GLTexture texture, float[] geom, float[] cs, int ID) {
		switchBatch(Type.TEXTURE);
		int textureID = getOrUploadTexture(gl, texture.img);
		drawTexture(textureID, texture.uv, geom, cs, ID);
	}

	public void drawTexture(int textureID, float[] uv, float[] geom, float[] cs, int ID) {
		if (textureBatch.textureId != textureID) {
			flush();
			textureBatch.textureId = textureID;
		}
		if (!textureBatch.canAdd())
			flush();
		textureBatch.addQuad(geom, uv, cs, ID);
	}

	public void drawFBO(FBO fbo, float[] uv, float[] geom, float[] cs, int ID) {
		switchBatch(Type.TEXTURE);
		textureBatch.textureId = fbo.getResolvedTexture(gl);
		textureBatch.addQuad(geom, uv, cs, ID);
		flush();
//		drawTexture(fbo.getResolvedTexture(gl), uv, geom, cs, ID);
	}

	public void drawLine(float[] geom, boolean closed, float[] cs, int ID) {
		switchBatch(Type.LINE);
		if (!lineBatch.canAdd(geom))
			flush();
		lineBatch.addLine(geom, closed, cs, ID);

	}

	public void drawFilled(float[] geom, float[] cs, int ID) {
		switchBatch(Type.FILLED);
		if (!filledBatch.canAdd(geom))
			flush();
		filledBatch.addPolygon(geom, cs, ID);
	}

	public void setMipMap(GLTexture texture, float lodBias) {
		gl.glBindTexture(GL.GL_TEXTURE_2D, getOrUploadTexture(gl, texture.img));
		gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
		gl.glTexParameterf(GL.GL_TEXTURE_2D, GL2.GL_TEXTURE_LOD_BIAS, lodBias); // -1
	}

	public void setStroke(GLStroke stroke) {
		if (!lineBatch.getStroke().equals(stroke) && activeType == Type.LINE)
			flush();
		lineBatch.setStroke(stroke);
	}

	public GLStroke getStroke() {
		return lineBatch.getStroke();
	}

	public void setColorProgram(Type type, GLProgram program) {
		if (type == null || type == Type.NONE)
			return;
		int programID = getOrUploadProgram(gl, program);
		if (activeType == type)
			flush();
		switch (type) {
		case FILLED:
			filledBatch.setColorProgram(programID);
			break;
		case LINE:
			lineBatch.setColorProgram(programID);
			break;
		case TEXTURE:
			textureBatch.setColorProgram(programID);
			break;
		default:
			break;
		}
	}

	public void setColorUniform(Type type, String uniformID, float[] values) {
		if (type == null || type == Type.NONE)
			return;
		if (activeType == type)
			flush();
		switch (type) {
		case FILLED:
			filledBatch.setColorUniform(uniformID, values);
			break;
		case LINE:
			lineBatch.setColorUniform(uniformID, values);
			break;
		case TEXTURE:
			textureBatch.setColorUniform(uniformID, values);
			break;
		default:
			break;
		}
	}

	public void clearStencil(boolean clearWhite) {
		flush();
		whiteStencil = clearWhite;
		gl.glClearStencil(clearWhite ? 1 : 0);
		gl.glClear(GL.GL_STENCIL_BUFFER_BIT);

		idBuffer.bind(gl);
		gl.glClearStencil(clearWhite ? 1 : 0);
		gl.glClear(GL.GL_STENCIL_BUFFER_BIT);
		idBuffer.unbind(gl);
	}

	public void setStencilDrawing(boolean stencilDrawingEnabled) {
		if (isStencilDrawing != stencilDrawingEnabled)
			flush();
		isStencilDrawing = stencilDrawingEnabled;
		if (isStencilDrawing) {
			gl.glColorMask(false, false, false, false);
			gl.glDepthMask(false);

			gl.glStencilFunc(GL.GL_ALWAYS, whiteStencil ? 0 : 1, 0xFF);
			gl.glStencilOp(GL.GL_KEEP, GL.GL_KEEP, GL.GL_REPLACE);
		} else {
			gl.glColorMask(true, true, true, true);
			gl.glDepthMask(true);

			gl.glStencilFunc(GL.GL_EQUAL, 1, 0xFF);
			gl.glStencilOp(GL.GL_KEEP, GL.GL_KEEP, GL.GL_KEEP);
		}
	}

	public void setStencilMode(boolean whiteStencil) {
		if (this.whiteStencil != whiteStencil)
			flush();
		this.whiteStencil = whiteStencil;
		if (isStencilDrawing)
			setStencilDrawing(true);
	}

	public void setStencil(boolean stencilEnabled) {
		if (isStencilEnabled != stencilEnabled)
			flush();
		isStencilEnabled = stencilEnabled;
		if (isStencilEnabled)
			gl.glEnable(GL.GL_STENCIL_TEST);
		else
			gl.glDisable(GL.GL_STENCIL_TEST);
	}

	public void setProjection(GLProjection projection) {
		if (!this.projection.equals(projection))
			flush();
		this.projection = projection;
	}

	public void setFBO(FBO fbo) {
		flush();
		currentFBO = fbo;
	}

	public void clearFBO(float[] color) {
		if (currentFBO == null)
			return;
		flush();
		currentFBO.bind(gl);
		gl.glClearColor(color[0], color[1], color[2], color[3]);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
		currentFBO.unbind(gl);
	}

	public FBO getFBO() {
		return currentFBO;
	}

	public void setColorRendering(boolean colorRendering) {
		if (this.colorRendering != colorRendering)
			flush();
		this.colorRendering = colorRendering;
	};

	public boolean isColorRenderingEnabled() {
		return colorRendering;
	}

	public void setIdRendering(boolean idRendering) {
		if (this.idRendering != idRendering)
			flush();
		this.idRendering = idRendering;
	};

	public boolean isIdRenderingEnabled() {
		return idRendering;
	}

	private void switchBatch(Type newType) {
		if (activeType != newType) {
			flush();
			activeType = newType;
		}
	}

	public void flush() {
		switch (activeType) {
		case TEXTURE:
			drawCalls += textureBatch.flush(gl, projection.projection, meshVbo, meshIbo, idVbo, colorRendering, idRendering);
			break;
		case FILLED:
			drawCalls += filledBatch.flush(gl, projection.projection, meshVbo, meshIbo, idVbo, colorRendering, idRendering);
			break;
		case LINE:
			drawCalls += lineBatch.flush(gl, projection.projection, meshVbo, meshIbo, idVbo, colorRendering, idRendering);
			break;
		case NONE:
			break;
		default:
			break;
		}
		activeType = Type.NONE;
	}

	public int getOrUploadProgram(GL2 context, GLProgram program) {
		if (program == null)
			return 0;
		Integer prog = programCache.get(program);
		if (prog != null)
			return prog;

		prog = GLShaderUtil.create_R32UI_Color_Program(context, program.vert, program.frag);
		programCache.put(program, prog);
		return prog;
	}

	public int getOrUploadTexture(GL2 context, BufferedImage img) {
		Integer tex = textureCache.get(img);
		if (tex != null)
			return tex;

		tex = GLTextureUtil.loadTexture(context, img);
		textureCache.put(img, tex);
		return tex;
	}

}
