package com.vingame.glswing.node;
import java.util.IdentityHashMap;
import java.util.Map;
import java.awt.Color;
import com.vingame.glswing.rendering.GLColorInterpolation;
import com.vingame.glswing.rendering.GLColorInterpolator;
import com.vingame.glswing.rendering.GLContext2D;
/** Connection tessellation, cache invalidation and per-render diagnostics. */
final class GLNodeConnectionCurves {
    private final Map<GLNodeConnection,GLNodeConnectionCurve> cache=new IdentityHashMap<>();
    private record Gradient(int count,int start,int end,GLColorInterpolator interpolation,Color[] colors){}
    private final Map<GLNodeConnection,Gradient> gradients=new IdentityHashMap<>();
    volatile int tessellations,hits,segments;
    void remove(GLNodeConnection connection){cache.remove(connection);gradients.remove(connection);}
    Color[] colors(GLNodeConnection connection,int count,Color start,Color end,GLColorInterpolator interpolation){
        // Custom functions may carry mutable state; retain their existing evaluation behavior.
        boolean retain=connection!=null&&interpolation instanceof GLColorInterpolation;
        var cached=retain?gradients.get(connection):null;
        if(cached!=null&&cached.count()==count&&cached.start()==start.getRGB()&&cached.end()==end.getRGB()&&cached.interpolation()==interpolation)return cached.colors();
        var result=new Color[count];
        for(int i=0;i<count;i++)result[i]=interpolation.interpolate(start,end,count>1?i/(float)(count-1):0);
        if(retain)gradients.put(connection,new Gradient(count,start.getRGB(),end.getRGB(),interpolation,result));
        return result;
    }

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
