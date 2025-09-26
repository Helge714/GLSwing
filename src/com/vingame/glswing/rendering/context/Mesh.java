package com.vingame.glswing.rendering.context;

public class Mesh {
	public final float[] vertices; // VBO-Daten
	public final int[] indices; // IBO-Daten

	Mesh(float[] v, int[] i) {
		this.vertices = v;
		this.indices = i;
	}
}
