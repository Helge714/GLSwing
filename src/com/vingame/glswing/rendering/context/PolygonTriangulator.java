package com.vingame.glswing.rendering.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PolygonTriangulator {

	/**
	 * Trianguliert ein 2D-Polygon (flaches Array: x0,y0,x1,y1,...). Voraussetzung:
	 * einfaches, NICHT selbstüberschneidendes Polygon.
	 */
	public static List<float[]> triangulate(float[] polygon) {
		List<float[]> triangles = new ArrayList<>();

		if (polygon.length < 6)
			return triangles; // min. 3 Punkte

		int n = polygon.length / 2;
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < n; i++)
			indices.add(i);

		// Check orientation and reverse if clockwise
		if (isClockwise(polygon))
			Collections.reverse(indices);

		while (indices.size() >= 3) {
			boolean earFound = false;

			for (int i = 0; i < indices.size(); i++) {
				int i0 = indices.get((i + indices.size() - 1) % indices.size());
				int i1 = indices.get(i);
				int i2 = indices.get((i + 1) % indices.size());

				float x0 = polygon[i0 * 2], y0 = polygon[i0 * 2 + 1];
				float x1 = polygon[i1 * 2], y1 = polygon[i1 * 2 + 1];
				float x2 = polygon[i2 * 2], y2 = polygon[i2 * 2 + 1];

				if (!isConvex(x0, y0, x1, y1, x2, y2))
					continue;

				boolean containsPoint = false;
				for (int j = 0; j < indices.size(); j++) {
					int ij = indices.get(j);
					if (ij == i0 || ij == i1 || ij == i2)
						continue;
					float px = polygon[ij * 2];
					float py = polygon[ij * 2 + 1];
					if (pointInTriangle(px, py, x0, y0, x1, y1, x2, y2)) {
						containsPoint = true;
						break;
					}
				}
				if (containsPoint)
					continue;

				triangles.add(new float[] { x0, y0, x1, y1, x2, y2 });
				indices.remove(i);
				earFound = true;
				break;
			}

			if (!earFound)
				break; // Fehlerhafte Geometrie?
		}

		return triangles;
	}
	
	public static List<float[]> triangulateColors(float[] polygon, float[] vertexColors) {
	    List<float[]> trianglesColors = new ArrayList<>();

	    if (polygon.length < 6)
	        return trianglesColors;

	    int n = polygon.length / 2;
	    List<Integer> indices = new ArrayList<>();
	    for (int i = 0; i < n; i++)
	        indices.add(i);

	    if (isClockwise(polygon))
	        Collections.reverse(indices);

	    boolean perVertex = vertexColors.length >= n * 4;

	    while (indices.size() >= 3) {
	        boolean earFound = false;

	        for (int i = 0; i < indices.size(); i++) {
	            int i0 = indices.get((i + indices.size() - 1) % indices.size());
	            int i1 = indices.get(i);
	            int i2 = indices.get((i + 1) % indices.size());

	            float x0 = polygon[i0 * 2], y0 = polygon[i0 * 2 + 1];
	            float x1 = polygon[i1 * 2], y1 = polygon[i1 * 2 + 1];
	            float x2 = polygon[i2 * 2], y2 = polygon[i2 * 2 + 1];

	            if (!isConvex(x0, y0, x1, y1, x2, y2))
	                continue;

	            boolean containsPoint = false;
	            for (int j = 0; j < indices.size(); j++) {
	                int ij = indices.get(j);
	                if (ij == i0 || ij == i1 || ij == i2)
	                    continue;
	                float px = polygon[ij * 2];
	                float py = polygon[ij * 2 + 1];
	                if (pointInTriangle(px, py, x0, y0, x1, y1, x2, y2)) {
	                    containsPoint = true;
	                    break;
	                }
	            }
	            if (containsPoint)
	                continue;

	            float[] colors = new float[12];

	            if (perVertex) {
	                // jeder Vertex hat eigene Farbe
	                copyColor(vertexColors, i0, colors, 0);
	                copyColor(vertexColors, i1, colors, 4);
	                copyColor(vertexColors, i2, colors, 8);
	            } else {
	                // nur eine Farbe angegeben -> alle Vertices gleich
	                for (int k = 0; k < 3; k++)
	                    System.arraycopy(vertexColors, 0, colors, k * 4, 4);
	            }

	            trianglesColors.add(colors);
	            indices.remove(i);
	            earFound = true;
	            break;
	        }

	        if (!earFound)
	            break;
	    }

	    return trianglesColors;
	}

	private static void copyColor(float[] src, int vertexIndex, float[] dst, int dstPos) {
	    System.arraycopy(src, vertexIndex * 4, dst, dstPos, 4);
	}

	private static boolean isConvex(float x0, float y0, float x1, float y1, float x2, float y2) {
		float cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
		return cross < 0; // gegen Uhrzeigersinn
	}

	private static boolean pointInTriangle(float px, float py, float ax, float ay, float bx, float by, float cx,
			float cy) {
		float v0x = cx - ax;
		float v0y = cy - ay;
		float v1x = bx - ax;
		float v1y = by - ay;
		float v2x = px - ax;
		float v2y = py - ay;

		float dot00 = v0x * v0x + v0y * v0y;
		float dot01 = v0x * v1x + v0y * v1y;
		float dot02 = v0x * v2x + v0y * v2y;
		float dot11 = v1x * v1x + v1y * v1y;
		float dot12 = v1x * v2x + v1y * v2y;

		float invDenom = 1f / (dot00 * dot11 - dot01 * dot01);
		float u = (dot11 * dot02 - dot01 * dot12) * invDenom;
		float v = (dot00 * dot12 - dot01 * dot02) * invDenom;

		return (u >= 0) && (v >= 0) && (u + v < 1);
	}

	private static boolean isClockwise(float[] poly) {
		int n = poly.length / 2;
		int minIndex = 0;
		for (int i = 1; i < n; i++) {
			float yi = poly[i * 2 + 1];
			float yMin = poly[minIndex * 2 + 1];
			float xi = poly[i * 2];
			float xMin = poly[minIndex * 2];
			if (yi < yMin || (yi == yMin && xi > xMin)) {
				minIndex = i;
			}
		}

		int prev = (minIndex + n - 1) % n;
		int next = (minIndex + 1) % n;

		float ax = poly[prev * 2] - poly[minIndex * 2];
		float ay = poly[prev * 2 + 1] - poly[minIndex * 2 + 1];
		float bx = poly[next * 2] - poly[minIndex * 2];
		float by = poly[next * 2 + 1] - poly[minIndex * 2 + 1];

		float cross = ax * by - ay * bx;
		return cross < 0; // Uhrzeigersinn → umdrehen
	}
}
