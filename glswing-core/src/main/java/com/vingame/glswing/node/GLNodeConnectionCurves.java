package com.vingame.glswing.node;
import java.util.IdentityHashMap;
import java.util.Map;
import com.vingame.glswing.rendering.GLContext2D;
/** Connection tessellation, cache invalidation and per-render diagnostics. */
final class GLNodeConnectionCurves {
    private final Map<GLNodeConnection,GLNodeConnectionCurve> cache=new IdentityHashMap<>();
    volatile int tessellations,hits,segments;
    void remove(GLNodeConnection connection){cache.remove(connection);}
	float[] points(GLContext2D context,
			GLNodeConnection connection, float x0, float y0, float c1x, float c1y,
			float c2x, float c2y, float x1, float y1, int minimumSegments) {
		if (connection == null)
			return context.tessellateCubicBezier(x0, y0, c1x, c1y,
					c2x, c2y, x1, y1, minimumSegments);
		float tolerance = context.getCurveTolerance();
		float scale = context.getTransform().getMaximumScale();
		GLNodeConnectionCurve cached = cache.get(connection);
		if (cached != null && cached.matches(x0, y0, c1x, c1y,
				c2x, c2y, x1, y1, tolerance, scale, minimumSegments)) {
			hits++;
			segments += cached.points().length / 2 - 1;
			return cached.points();
		}
		float[] points = context.tessellateCubicBezier(x0, y0, c1x, c1y,
				c2x, c2y, x1, y1, minimumSegments);
		cache.put(connection, new GLNodeConnectionCurve(
				x0, y0, c1x, c1y, c2x, c2y, x1, y1,
				tolerance, scale, minimumSegments, points));
		tessellations++;
		segments += points.length / 2 - 1;
		return points;
	}

}
