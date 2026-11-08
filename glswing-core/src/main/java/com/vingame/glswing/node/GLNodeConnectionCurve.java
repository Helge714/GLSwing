package com.vingame.glswing.node;

/** Immutable tessellation-cache key; geometry depends on scale and tolerance. */
record GLNodeConnectionCurve(float x0, float y0, float c1x,
			float c1y, float c2x, float c2y, float x1, float y1,
			float tolerance, float scale, int minimumSegments, float[] points) {
		boolean matches(float nextX0, float nextY0, float nextC1x, float nextC1y,
				float nextC2x, float nextC2y, float nextX1, float nextY1,
				float nextTolerance, float nextScale, int nextMinimumSegments) {
			return x0 == nextX0 && y0 == nextY0 && c1x == nextC1x
					&& c1y == nextC1y && c2x == nextC2x && c2y == nextC2y
					&& x1 == nextX1 && y1 == nextY1
					&& tolerance == nextTolerance && scale == nextScale
					&& minimumSegments == nextMinimumSegments;
		}
	}
