package com.vingame.glswing.rendering.context;

import com.vingame.mathutils.MathUtils;

public class GLStroke {
	
	public static final GLStroke DEFAULT_STROKE = new GLStroke(0.5f, LineJoin.MITER, 1f);

	public enum LineJoin {
		MITER, BEVEL, ROUND
	}

	public final float lineAlign; //0 ... 1
	public final LineJoin lineJoin;
	public final float thickness;

	public GLStroke(float lineAlign, LineJoin lineJoin, float thickness) {
		this.lineAlign = lineAlign;
		this.lineJoin = lineJoin;
		this.thickness = thickness;
	}

	public GLStroke deriveLineAlign(float lineAlign) {
		return new GLStroke(lineAlign, lineJoin, thickness);
	}

	public GLStroke deriveLineJoin(LineJoin lineJoin) {
		return new GLStroke(lineAlign, lineJoin, thickness);
	}

	public GLStroke deriveThickness(float thickness) {
		return new GLStroke(lineAlign, lineJoin, thickness);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof GLStroke other))
			return false;
		return other.lineAlign == lineAlign && other.lineJoin == lineJoin && other.thickness == thickness;
	}
	
	public static Mesh buildMesh(float[] pts, boolean closed) {
		int n = pts.length / 2;
		if (n < 2)
			return new Mesh(new float[0], new int[0]);

		int segs = closed ? n : (n - 1);

		// 4 Vertices pro Segment, 8 Floats pro Vertex
		float[] verts = new float[segs * 4 * 7];
		int[] inds = new int[segs * 6];

		for (int s = 0; s < segs; s++) {
			int i0 = s;
			int ip = !closed && s == 0 ? s : MathUtils.mod(s - 1, n);
			int in = MathUtils.mod(s + 1, n);
			int in1 = !closed && s == segs - 1 ? in : MathUtils.mod(s + 2, n);

			float x, y, px, py, nx, ny;
			float nx1, ny1;

			x = pts[2 * i0];
			y = pts[2 * i0 + 1];
			px = pts[2 * ip];
			py = pts[2 * ip + 1];
			nx = pts[2 * in];
			ny = pts[2 * in + 1];
			nx1 = pts[2 * in1];
			ny1 = pts[2 * in1 + 1];

			// ---- Vertices schreiben ----
			int vbase = s * 4;

			// Start-Vertices (cap=1 = Start)
			writeVertex(verts, (vbase + 0) * 7, x, y, px, py, nx, ny, -1);
			writeVertex(verts, (vbase + 1) * 7, x, y, px, py, nx, ny, +1);

			// End-Vertices (cap=2 = End)
			writeVertex(verts, (vbase + 2) * 7, nx, ny, x, y, nx1, ny1, -1);
			writeVertex(verts, (vbase + 3) * 7, nx, ny, x, y, nx1, ny1, +1);

			// ---- Indices (2 Dreiecke) ----
			int bi = s * 6;
			inds[bi + 0] = vbase + 0;
			inds[bi + 1] = vbase + 1;
			inds[bi + 2] = vbase + 2;
			inds[bi + 3] = vbase + 2;
			inds[bi + 4] = vbase + 1;
			inds[bi + 5] = vbase + 3;
		}

		return new Mesh(verts, inds);
	}

	private static void writeVertex(float[] out, int base, float x, float y, float px, float py, float nx, float ny,
			float side) {
		out[base + 0] = x; // position.x
		out[base + 1] = y; // position.y
		out[base + 2] = px; // prev.x
		out[base + 3] = py; // prev.y
		out[base + 4] = nx; // next.x
		out[base + 5] = ny; // next.y
		out[base + 6] = side; // -1 oder +1
	}

}
