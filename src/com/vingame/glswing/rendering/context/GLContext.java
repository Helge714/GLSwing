package com.vingame.glswing.rendering.context;

import java.awt.Color;

import com.jogamp.opengl.GL2;
import com.vingame.glswing.rendering.batch.BatchManager;

public class GLContext {

	private final BatchManager manager;

	private Color color;
	private GLProjection projection = GLProjection.DEFAULT_PROJECTION;
	private static final float[] defaultTextureColor = { 1, 1, 1, 1 };

	public GLContext(BatchManager manager) {
		this.manager = manager;
	}

//	@Deprecated
//	private int calculateOptimalSegments(float r1, float r2, float zoom) {
//		float circumference = (float) (Math.PI * (r1 + r2));
//		float screenCircumference = circumference * zoom;
//		return Math.max(8, Math.min(64, Math.round(screenCircumference / 5f)));
//	}

	public void drawPolygon(float[] pts, boolean closed) {
		drawPolygon(pts, closed, 0);
	}

	public void drawPolygon(float[] pts, boolean closed, Color c) {
		setColor(c);
		drawPolygon(pts, closed, 0);
	}

	public void drawPolygon(float[] pts, boolean closed, Color c, int id) {
		setColor(c);
		drawPolygon(pts, closed, 0);
	}

	public void drawPolygon(float[] pts, boolean closed, int id) {
		manager.drawLine(pts, closed, colorToRGBA(color), id);
	}
	
	//TODO vertexColor

	public void fillPolygon(float[] pts) {
		fillPolygon(pts, 0);
	}

	public void fillPolygon(float[] pts, Color c) {
		setColor(c);
		fillPolygon(pts, 0);
	}

	public void fillPolygon(float[] pts, Color c, int id) {
		setColor(c);
		fillPolygon(pts, id);
	}

	public void fillPolygon(float[] pts, int id) {
		var triangles = PolygonTriangulator.triangulate(pts);
		for (float[] tri : triangles) {
			float[] col = color != null ? colorToRGBA(color) : colorToRGBA(Color.BLACK);
			manager.drawFilled(tri, col, id);
		}
	}
	
	public void fillPolygon(float[] pts, float[] vertexColors, int id) {
		var triangles = PolygonTriangulator.triangulate(pts);
		var colors = PolygonTriangulator.triangulateColors(pts, vertexColors);
		for (int i = 0; i < triangles.size(); i++) {
		    float[] triangle = triangles.get(i);
		    float[] color    = colors.get(i);
			manager.drawFilled(triangle, color, id);
		}
	}

	public void drawRect(float x, float y, float w, float h) {
		drawPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, true);
	}

	public void drawRect(float x, float y, float w, float h, int id) {
		drawPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, true, id);
	}

	public void drawRect(float x, float y, float w, float h, Color c) {
		drawPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, true, c);
	}

	public void drawRect(float x, float y, float w, float h, Color c, int id) {
		drawPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, true, c, id);
	}
	
//	//TODO color as vertexColor;

	public void fillRect(float x, float y, float w, float h) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, 0);
	}

	public void fillRect(float x, float y, float w, float h, int id) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, id);
	}

	public void fillRect(float x, float y, float w, float h, Color c) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, c, 0);
	}

	public void fillRect(float x, float y, float w, float h, Color c, int id) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, c, id);
	}
	
	public void fillRect(float x, float y, float w, float h, float[] c) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, c, 0);
	}
	
	public void fillRect(float x, float y, float w, float h, float[] c, int id) {
		fillPolygon(new float[] { x, y, x + w, y, x + w, y + h, x, y + h }, c, id);
	}

	public void drawTexture(GLTexture texture, float x, float y) {
		drawTexture(texture, x, y, texture.width, texture.height, 0);
	}

	public void drawTexture(GLTexture texture, float x, float y, float w, float h) {
		drawTexture(texture, x, y, w, h, 0);
	}

	public void drawTexture(GLTexture texture, float x, float y, Color c) {
		setColor(c);
		drawTexture(texture, x, y, texture.width, texture.height, 0);
	}

	public void drawTexture(GLTexture texture, float x, float y, Color c, int ID) {
		setColor(c);
		drawTexture(texture, x, y, texture.width, texture.height, ID);
	}

	public void drawTexture(GLTexture texture, float x, float y, float w, float h, Color c) {
		setColor(c);
		drawTexture(texture, x, y, w, h, 0);
	}

	public void drawTexture(GLTexture texture, float x, float y, float w, float h, Color c, int ID) {
		setColor(c);
		drawTexture(texture, x, y, w, h, ID);
	}

	public void drawTexture(GLTexture texture, float x, float y, float w, float h, int ID) {
		float[] geometry = { x, y, x + w, y, x + w, y + h, x, y + h };
		float[] col = color != null ? colorToRGBA(color) : defaultTextureColor;
		manager.drawTexture(texture, geometry, col, ID);
	}
	
	public void drawTexture(GLTexture texture, float x, float y, float [] vertexColor) {
		drawTexture(texture, x, y, texture.width, texture.height, vertexColor, 0);
	}
	
	public void drawTexture(GLTexture texture, float x, float y, float [] vertexColor, int ID) {
		drawTexture(texture, x, y, texture.width, texture.height, vertexColor, ID);
	}

	public void drawTexture(GLTexture texture, float x, float y, float w, float h, float[] vertexColor, int ID) {
		float[] geometry = { x, y, x + w, y, x + w, y + h, x, y + h };
		if (vertexColor.length != 4 && vertexColor.length != 16)
			throw new IllegalArgumentException("length " + vertexColor.length
					+ " is not valid for colors (4 for whole vertex, 16 for individual vertex)");
		manager.drawTexture(texture, geometry, vertexColor, ID);
	}

	private float[] colorToRGBA(Color c) {
		return new float[] { c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f };
	}

	public void setColor(Color c) {
		if (c == null && color != null)
			color = null;
		else if (c != null && !c.equals(color))
			color = c;
	}

	public void setStroke(GLStroke stroke) {
		manager.setStroke(stroke);
	}
	
	public void getStroke() {
		manager.getStroke();
	}

	public void ortho(float width, float height) {
		ortho(0, width, height, 0, -1, 1);
	}

	public void ortho(float left, float right, float bottom, float top, float near, float far) {
		projection = new GLProjection(GLProjectionUtils.multiply(projection.projection,
				GLProjectionUtils.ortho(left, right, bottom, top, near, far)));
		manager.setProjection(projection);
	}

	public void scale(float sx, float sy) {
		projection = projection.scale(sx, sy);
		manager.setProjection(projection);
	}

	public void preScale(float sx, float sy) {
		projection = projection.preScale(sx, sy);
		manager.setProjection(projection);
	}

	public void rotate(float rz) {
		rotate(rz, 0, 0);
	}

	public void rotate(float rz, float x, float y) {
		projection = projection.rotate(rz, x, y);
		manager.setProjection(projection);
	}

	public void preRotate(float rz) {
		preRotate(rz, 0, 0);
	}

	public void preRotate(float rz, float x, float y) {
		projection = projection.preRotate(rz, x, y);
		manager.setProjection(projection);
	}

	public void translate(float x, float y) {
		projection = projection.translate(x, y);
		manager.setProjection(projection);
	}

	public void preTranslate(float x, float y) {
		projection = projection.preTranslate(x, y);
		manager.setProjection(projection);
	}

	public void setProjection(GLProjection projection) {
		projection = projection == null ? GLProjection.DEFAULT_PROJECTION : projection;
		manager.setProjection(projection);
	}

	public GLProjection getProjection() {
		return projection;
	}

	public void setTextureProgram(GLProgram program) {
		setColorProgram(BatchManager.Type.TEXTURE, program);
	}

	public void setFilledProgram(GLProgram program) {
		setColorProgram(BatchManager.Type.FILLED, program);
	}

	public void setLineProgram(GLProgram program) {
		setColorProgram(BatchManager.Type.LINE, program);
	}

	public void setColorProgram(BatchManager.Type type, GLProgram program) {
		manager.setColorProgram(type, program);
	}
	
	public void setTextureProgramUniform(String uniformID, float... values) {
		setColorUniform(BatchManager.Type.TEXTURE, uniformID, values);
	}

	public void setFilledProgramUniform(String uniformID, float... values) {
		setColorUniform(BatchManager.Type.FILLED, uniformID, values);
	}

	public void setLineProgramUniform(String uniformID, float... values) {
		setColorUniform(BatchManager.Type.LINE, uniformID, values);
	}

	public void setColorUniform(BatchManager.Type type, String uniformID, float... values) {
		manager.setColorUniform(type, uniformID, values);
	}

	public void clearStencil(boolean drawOutside) {
		manager.clearStencil(drawOutside);
	}
	
	public void setStencilMode(boolean drawOutside) {
		manager.setStencilMode(drawOutside);
	}

	public void beginStencilDrawing(boolean drawOutside) {
		manager.clearStencil(drawOutside);
		manager.setStencilDrawing(true);
	}

	public void beginStencilDrawing() {
		manager.setStencilDrawing(true);
	}

	public void endStencilDrawing() {
		manager.setStencilDrawing(false);
	}

	public void enableStencil() {
		manager.setStencil(true);
	}

	public void disableStencil() {
		manager.setStencil(false);
	}
	
	public void enableColorRendering() {
		manager.setColorRendering(true);
	};

	public void disableColorRendering() {
		manager.setColorRendering(false);
	};

	public boolean isColorRenderingEnabled() {
		return manager.isColorRenderingEnabled();
	}

	public void enableIdRendering() {
		manager.setIdRendering(true);
	};

	public void disableIdRendering() {
		manager.setIdRendering(false);
	};

	public boolean isIdRenderingEnabled() {
		return manager.isIdRenderingEnabled();
	}

	public GL2 getGL2() {
		return manager.gl;
	}

	public void flush() {
		manager.flush();
	}

	public int getDrawCallCount() {
		return manager.drawCalls;
	}
}
