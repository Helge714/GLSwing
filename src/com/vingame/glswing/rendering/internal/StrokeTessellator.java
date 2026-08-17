package com.vingame.glswing.rendering.internal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vingame.glswing.rendering.GLStroke;

/** Converts styled and offset strokes to non-overlapping colored triangles. */
public final class StrokeTessellator {

	private static final float EPSILON = 1e-6f;

	private StrokeTessellator() {
	}

	public static float[] tessellateLine(float x1, float y1, float x2, float y2,
			GLStroke stroke, Color color) {
		return tessellateUniform(new float[] { x1, y1, x2, y2 }, false, stroke, color);
	}

	public static float[] tessellateLine(float x1, float y1, Color color1,
			float x2, float y2, Color color2, GLStroke stroke) {
		return tessellateColored(new float[] { x1, y1, x2, y2 }, false,
				stroke, new Color[] { color1, color2 });
	}

	public static float[] tessellatePolyline(float[] points, GLStroke stroke, Color color) {
		return tessellateUniform(points, false, stroke, color);
	}

	public static float[] tessellatePolyline(float[] points, GLStroke stroke, Color[] colors) {
		return tessellateColored(points, false, stroke, colors);
	}

	public static float[] tessellatePolygon(float[] points, GLStroke stroke, Color color) {
		return tessellateUniform(points, true, stroke, color);
	}

	public static float[] tessellatePolygon(float[] points, GLStroke stroke, Color[] colors) {
		return tessellateColored(points, true, stroke, colors);
	}

	private static float[] tessellateUniform(float[] points, boolean closed,
			GLStroke stroke, Color color) {
		validateGeometry(points, closed, stroke);
		if (color == null)
			throw new IllegalArgumentException("Stroke color must not be null");
		StrokeOutline outline = createOutline(points, null, closed, stroke);
		if (outline == null || outline.contours.isEmpty())
			return new float[0];
		return PolygonTessellator.tessellateContours(outline.contours, color);
	}

	private static float[] tessellateColored(float[] points, boolean closed,
			GLStroke stroke, Color[] colors) {
		validateGeometry(points, closed, stroke);
		validateColors(colors, points.length / 2);
		StrokeOutline outline = createOutline(points, colors, closed, stroke);
		if (outline == null || outline.contours.isEmpty())
			return new float[0];
		List<Color[]> contourColors = createContourColors(
				outline.contours, outline.centerline, outline.pointColors, closed);
		return PolygonTessellator.tessellateContours(outline.contours, contourColors);
	}

	private static StrokeOutline createOutline(float[] points, Color[] colors,
			boolean closed, GLStroke stroke) {
		PathData pathData = removeConsecutiveDuplicates(points, colors, closed);
		int minimumPoints = closed ? 3 : 2;
		if (pathData.points.length / 2 < minimumPoints)
			return null;

		float halfWidth = stroke.getWidth() / 2;
		float leftDistance = stroke.getOffset() + halfWidth;
		float rightDistance = stroke.getOffset() - halfWidth;
		float[] directions = createDirections(pathData.points, closed);
		List<float[]> contours;
		if (closed) {
			float[] left = createClosedBoundary(
					pathData.points, directions, leftDistance, stroke);
			float[] right = createClosedBoundary(
					pathData.points, directions, rightDistance, stroke);
			boolean counterClockwise = signedArea(pathData.points) > 0;
			float[] outer = counterClockwise ? right : left;
			float[] inner = counterClockwise ? reverse(left) : reverse(right);
			contours = new ArrayList<>(2);
			if (outer.length >= 6)
				contours.add(outer);
			if (inner.length >= 6)
				contours.add(inner);
		} else {
			contours = List.of(createOpenOutline(pathData.points, directions,
					leftDistance, rightDistance, stroke));
		}
		return contours.isEmpty()
				? null
				: new StrokeOutline(pathData.points, pathData.colors, contours);
	}

	private static List<Color[]> createContourColors(List<float[]> contours,
			float[] centerline, Color[] pointColors, boolean closed) {
		List<Color[]> result = new ArrayList<>(contours.size());
		for (float[] contour : contours) {
			Color[] colors = new Color[contour.length / 2];
			for (int i = 0; i < colors.length; i++) {
				colors[i] = colorAtClosestPathPosition(
						contour[i * 2], contour[i * 2 + 1], centerline, pointColors, closed);
			}
			result.add(colors);
		}
		return result;
	}

	private static Color colorAtClosestPathPosition(float pointX, float pointY,
			float[] centerline, Color[] colors, boolean closed) {
		int pointCount = centerline.length / 2;
		int segmentCount = closed ? pointCount : pointCount - 1;
		float closestDistanceSquared = Float.POSITIVE_INFINITY;
		int closestSegment = 0;
		float closestPosition = 0;

		for (int segment = 0; segment < segmentCount; segment++) {
			int next = (segment + 1) % pointCount;
			float startX = x(centerline, segment);
			float startY = y(centerline, segment);
			float dx = x(centerline, next) - startX;
			float dy = y(centerline, next) - startY;
			float lengthSquared = dx * dx + dy * dy;
			float position = ((pointX - startX) * dx + (pointY - startY) * dy) / lengthSquared;
			position = Math.max(0, Math.min(1, position));
			float projectedX = startX + dx * position;
			float projectedY = startY + dy * position;
			float distanceX = pointX - projectedX;
			float distanceY = pointY - projectedY;
			float distanceSquared = distanceX * distanceX + distanceY * distanceY;
			if (distanceSquared < closestDistanceSquared) {
				closestDistanceSquared = distanceSquared;
				closestSegment = segment;
				closestPosition = position;
			}
		}

		int nextColor = (closestSegment + 1) % colors.length;
		return interpolate(colors[closestSegment], colors[nextColor], closestPosition);
	}

	private static Color interpolate(Color first, Color second, float position) {
		float inverse = 1 - position;
		float red = (first.getRed() * inverse + second.getRed() * position) / 255f;
		float green = (first.getGreen() * inverse + second.getGreen() * position) / 255f;
		float blue = (first.getBlue() * inverse + second.getBlue() * position) / 255f;
		float alpha = (first.getAlpha() * inverse + second.getAlpha() * position) / 255f;
		return new Color(red, green, blue, alpha);
	}

	private static float[] createDirections(float[] points, boolean closed) {
		int pointCount = points.length / 2;
		int segmentCount = closed ? pointCount : pointCount - 1;
		float[] directions = new float[segmentCount * 2];
		for (int i = 0; i < segmentCount; i++) {
			int next = (i + 1) % pointCount;
			float dx = x(points, next) - x(points, i);
			float dy = y(points, next) - y(points, i);
			float length = (float) Math.hypot(dx, dy);
			directions[i * 2] = dx / length;
			directions[i * 2 + 1] = dy / length;
		}
		return directions;
	}

	private static float[] createOpenOutline(float[] points, float[] directions,
			float leftDistance, float rightDistance, GLStroke stroke) {
		float halfWidth = stroke.getWidth() / 2;
		float extension = stroke.getCap() == GLStroke.Cap.SQUARE ? halfWidth : 0;
		float[] left = createOpenBoundary(points, directions, leftDistance, extension, stroke);
		float[] right = createOpenBoundary(points, directions, rightDistance, extension, stroke);
		PointAccumulator outline = new PointAccumulator();
		outline.addAll(left, false);

		int lastPoint = points.length / 2 - 1;
		int lastSegment = directions.length / 2 - 1;
		if (stroke.getCap() == GLStroke.Cap.ROUND) {
			float normalX = -directionY(directions, lastSegment);
			float normalY = directionX(directions, lastSegment);
			float centerX = x(points, lastPoint) + normalX * stroke.getOffset();
			float centerY = y(points, lastPoint) + normalY * stroke.getOffset();
			appendArc(outline, centerX, centerY,
					lastX(left), lastY(left), lastX(right), lastY(right),
					-1, stroke.getCurveTolerance());
		}

		outline.addAll(right, true);
		if (stroke.getCap() == GLStroke.Cap.ROUND) {
			float normalX = -directionY(directions, 0);
			float normalY = directionX(directions, 0);
			float centerX = x(points, 0) + normalX * stroke.getOffset();
			float centerY = y(points, 0) + normalY * stroke.getOffset();
			appendArc(outline, centerX, centerY,
					right[0], right[1], left[0], left[1],
					-1, stroke.getCurveTolerance());
		}
		return outline.toArrayWithoutRepeatedEnd();
	}

	private static float[] createOpenBoundary(float[] points, float[] directions,
			float distance, float extension, GLStroke stroke) {
		int pointCount = points.length / 2;
		PointAccumulator boundary = new PointAccumulator();
		float firstDirectionX = directionX(directions, 0);
		float firstDirectionY = directionY(directions, 0);
		boundary.add(x(points, 0) - firstDirectionX * extension - firstDirectionY * distance,
				y(points, 0) - firstDirectionY * extension + firstDirectionX * distance);
		for (int point = 1; point < pointCount - 1; point++)
			appendJoin(boundary, points, directions, point, point - 1, point, distance, stroke);

		int lastPoint = pointCount - 1;
		int lastSegment = directions.length / 2 - 1;
		float lastDirectionX = directionX(directions, lastSegment);
		float lastDirectionY = directionY(directions, lastSegment);
		boundary.add(x(points, lastPoint) + lastDirectionX * extension - lastDirectionY * distance,
				y(points, lastPoint) + lastDirectionY * extension + lastDirectionX * distance);
		return boundary.toArrayWithoutRepeatedEnd();
	}

	private static float[] createClosedBoundary(float[] points, float[] directions,
			float distance, GLStroke stroke) {
		int pointCount = points.length / 2;
		PointAccumulator boundary = new PointAccumulator();
		for (int point = 0; point < pointCount; point++) {
			int previousSegment = (point + pointCount - 1) % pointCount;
			int nextSegment = point;
			appendJoin(boundary, points, directions, point,
					previousSegment, nextSegment, distance, stroke);
		}
		return boundary.toArrayWithoutRepeatedEnd();
	}

	private static void appendJoin(PointAccumulator boundary, float[] points, float[] directions,
			int point, int previousSegment, int nextSegment, float distance, GLStroke stroke) {
		float vertexX = x(points, point);
		float vertexY = y(points, point);
		float previousDirectionX = directionX(directions, previousSegment);
		float previousDirectionY = directionY(directions, previousSegment);
		float nextDirectionX = directionX(directions, nextSegment);
		float nextDirectionY = directionY(directions, nextSegment);
		float previousX = vertexX - previousDirectionY * distance;
		float previousY = vertexY + previousDirectionX * distance;
		float nextX = vertexX - nextDirectionY * distance;
		float nextY = vertexY + nextDirectionX * distance;
		float cross = cross(previousDirectionX, previousDirectionY,
				nextDirectionX, nextDirectionY);
		float dot = previousDirectionX * nextDirectionX
				+ previousDirectionY * nextDirectionY;

		if (Math.abs(cross) <= EPSILON) {
			if (dot >= 0)
				boundary.add((previousX + nextX) / 2, (previousY + nextY) / 2);
			else {
				boundary.add(previousX, previousY);
				boundary.add(nextX, nextY);
			}
			return;
		}

		float[] intersection = lineIntersection(previousX, previousY,
				previousDirectionX, previousDirectionY,
				nextX, nextY, nextDirectionX, nextDirectionY);
		boolean outside = cross * distance < 0;
		if (!outside || stroke == null) {
			boundary.add(intersection[0], intersection[1]);
			return;
		}

		switch (stroke.getJoin()) {
		case BEVEL -> {
			boundary.add(previousX, previousY);
			boundary.add(nextX, nextY);
		}
		case ROUND -> appendArc(boundary, vertexX, vertexY,
				previousX, previousY, nextX, nextY,
				cross > 0 ? 1 : -1, stroke.getCurveTolerance());
		case MITER -> {
			float radius = Math.abs(distance);
			float miterLength = (float) Math.hypot(
					intersection[0] - vertexX, intersection[1] - vertexY);
			if (radius > EPSILON && miterLength <= radius * stroke.getMiterLimit()) {
				boundary.add(intersection[0], intersection[1]);
			} else {
				boundary.add(previousX, previousY);
				boundary.add(nextX, nextY);
			}
		}
		}
	}

	private static float[] lineIntersection(float firstX, float firstY,
			float firstDirectionX, float firstDirectionY,
			float secondX, float secondY, float secondDirectionX, float secondDirectionY) {
		float denominator = cross(firstDirectionX, firstDirectionY,
				secondDirectionX, secondDirectionY);
		float deltaX = secondX - firstX;
		float deltaY = secondY - firstY;
		float position = cross(deltaX, deltaY, secondDirectionX, secondDirectionY)
				/ denominator;
		return new float[] {
				firstX + firstDirectionX * position,
				firstY + firstDirectionY * position
		};
	}

	private static void appendArc(PointAccumulator points, float centerX, float centerY,
			float startX, float startY, float endX, float endY,
			int direction, float curveTolerance) {
		float radius = (float) Math.hypot(startX - centerX, startY - centerY);
		if (radius <= EPSILON) {
			points.add(endX, endY);
			return;
		}

		double startAngle = Math.atan2(startY - centerY, startX - centerX);
		double endAngle = Math.atan2(endY - centerY, endX - centerX);
		double sweep = endAngle - startAngle;
		if (direction > 0) {
			while (sweep <= 0)
				sweep += Math.PI * 2;
		} else {
			while (sweep >= 0)
				sweep -= Math.PI * 2;
		}

		double ratio = Math.min(1, curveTolerance / radius);
		double maximumAngle = ratio >= 1
				? Math.PI / 2
				: Math.min(Math.PI / 2, 2 * Math.acos(1 - ratio));
		int segments = Math.max(1, (int) Math.ceil(Math.abs(sweep) / maximumAngle));
		points.add(startX, startY);
		for (int segment = 1; segment < segments; segment++) {
			double angle = startAngle + sweep * segment / segments;
			points.add(centerX + (float) Math.cos(angle) * radius,
					centerY + (float) Math.sin(angle) * radius);
		}
		points.add(endX, endY);
	}

	private static float signedArea(float[] points) {
		double area = 0;
		int pointCount = points.length / 2;
		for (int point = 0; point < pointCount; point++) {
			int next = (point + 1) % pointCount;
			area += (double) x(points, point) * y(points, next)
					- (double) x(points, next) * y(points, point);
		}
		return (float) (area / 2);
	}

	private static float[] reverse(float[] points) {
		float[] reversed = new float[points.length];
		int pointCount = points.length / 2;
		for (int point = 0; point < pointCount; point++) {
			reversed[point * 2] = x(points, pointCount - 1 - point);
			reversed[point * 2 + 1] = y(points, pointCount - 1 - point);
		}
		return reversed;
	}

	private static float cross(float firstX, float firstY, float secondX, float secondY) {
		return firstX * secondY - firstY * secondX;
	}

	private static float lastX(float[] points) {
		return points[points.length - 2];
	}

	private static float lastY(float[] points) {
		return points[points.length - 1];
	}

	private static PathData removeConsecutiveDuplicates(float[] points, Color[] colors, boolean closed) {
		float[] compactPoints = new float[points.length];
		Color[] compactColors = colors == null ? null : new Color[colors.length];
		int pointCount = 0;
		for (int i = 0; i < points.length; i += 2) {
			int sourcePoint = i / 2;
			if (pointCount > 0 && points[i] == compactPoints[(pointCount - 1) * 2]
					&& points[i + 1] == compactPoints[(pointCount - 1) * 2 + 1]) {
				if (compactColors != null)
					compactColors[pointCount - 1] = colors[sourcePoint];
				continue;
			}
			compactPoints[pointCount * 2] = points[i];
			compactPoints[pointCount * 2 + 1] = points[i + 1];
			if (compactColors != null)
				compactColors[pointCount] = colors[sourcePoint];
			pointCount++;
		}

		if (closed && pointCount >= 2 && compactPoints[0] == compactPoints[(pointCount - 1) * 2]
				&& compactPoints[1] == compactPoints[(pointCount - 1) * 2 + 1])
			pointCount--;

		return new PathData(Arrays.copyOf(compactPoints, pointCount * 2),
				compactColors == null ? null : Arrays.copyOf(compactColors, pointCount));
	}

	private static void validateGeometry(float[] points, boolean closed, GLStroke stroke) {
		if (points == null)
			throw new IllegalArgumentException("Stroke points must not be null");
		int minimumCoordinates = closed ? 6 : 4;
		if (points.length < minimumCoordinates || points.length % 2 != 0)
			throw new IllegalArgumentException(closed
					? "A stroked polygon requires at least three x/y coordinate pairs"
					: "A polyline requires at least two x/y coordinate pairs");
		for (float value : points) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Stroke coordinates must be finite");
		}
		if (stroke == null)
			throw new IllegalArgumentException("Stroke must not be null");
	}

	private static void validateColors(Color[] colors, int pointCount) {
		if (colors == null)
			throw new IllegalArgumentException("Stroke colors must not be null");
		if (colors.length != pointCount)
			throw new IllegalArgumentException("A stroke requires exactly one color per point");
		for (Color color : colors) {
			if (color == null)
				throw new IllegalArgumentException("Stroke colors must not contain null");
		}
	}

	private static float x(float[] points, int index) {
		return points[index * 2];
	}

	private static float y(float[] points, int index) {
		return points[index * 2 + 1];
	}

	private static float directionX(float[] directions, int index) {
		return directions[index * 2];
	}

	private static float directionY(float[] directions, int index) {
		return directions[index * 2 + 1];
	}

	private static final class PathData {
		private final float[] points;
		private final Color[] colors;

		PathData(float[] points, Color[] colors) {
			this.points = points;
			this.colors = colors;
		}
	}

	private static final class StrokeOutline {
		private final float[] centerline;
		private final Color[] pointColors;
		private final List<float[]> contours;

		StrokeOutline(float[] centerline, Color[] pointColors, List<float[]> contours) {
			this.centerline = centerline;
			this.pointColors = pointColors;
			this.contours = contours;
		}
	}

	private static final class PointAccumulator {
		private float[] points = new float[32];
		private int size;

		void add(float x, float y) {
			if (size >= 2 && points[size - 2] == x && points[size - 1] == y)
				return;
			if (size + 2 > points.length)
				points = Arrays.copyOf(points, points.length * 2);
			points[size++] = x;
			points[size++] = y;
		}

		void addAll(float[] source, boolean reversed) {
			if (reversed) {
				for (int index = source.length - 2; index >= 0; index -= 2)
					add(source[index], source[index + 1]);
			} else {
				for (int index = 0; index < source.length; index += 2)
					add(source[index], source[index + 1]);
			}
		}

		float[] toArrayWithoutRepeatedEnd() {
			int resultSize = size;
			if (resultSize >= 4 && points[0] == points[resultSize - 2]
					&& points[1] == points[resultSize - 1])
				resultSize -= 2;
			return Arrays.copyOf(points, resultSize);
		}
	}
}
