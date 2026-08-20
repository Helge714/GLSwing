package com.vingame.glswing.rendering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;

/**
 * Mutable, fluent description of a two-dimensional path containing straight,
 * quadratic and cubic segments and any number of independent subpaths.
 */
public final class GLPath2D implements GLShape2D {

	public enum FillRule {
		/** Every crossing toggles whether a point lies inside the path. */
		EVEN_ODD,
		/** Directed crossings accumulate; opposite contours can form holes. */
		NON_ZERO
	}

	/** Immutable axis-aligned path bounds in local coordinates. */
	public record Bounds(float x, float y, float width, float height) {
		public float maximumX() { return x + width; }
		public float maximumY() { return y + height; }
	}

	private static final byte MOVE = 0;
	private static final byte LINE = 1;
	private static final byte QUAD = 2;
	private static final byte CUBIC = 3;
	private static final byte CLOSE = 4;
	private static final int MAXIMUM_FLATTENING_DEPTH = 12;
	private static final float CUBIC_CIRCLE_FACTOR = 0.55228475f;

	private byte[] commands = new byte[16];
	private float[] coordinates = new float[48];
	private int commandCount;
	private int coordinateCount;
	private int segmentCount;
	private FillRule fillRule;
	private boolean hasCurrentPoint;
	private float currentX;
	private float currentY;
	private float subpathStartX;
	private float subpathStartY;
	private boolean currentSubpathClosed;

	public GLPath2D() {
		this(FillRule.NON_ZERO);
	}

	public GLPath2D(FillRule fillRule) {
		this.fillRule = requireFillRule(fillRule);
	}

	/**
	 * Parses the {@code d} attribute of an SVG path using the SVG default
	 * non-zero fill rule.
	 */
	public static GLPath2D fromSvgPath(String data) {
		return GLSvgPathParser.parse(data);
	}

	/** Parses an SVG {@code d} attribute with the requested fill rule. */
	public static GLPath2D fromSvgPath(String data, FillRule fillRule) {
		return GLSvgPathParser.parse(data, fillRule);
	}

	/** Builds a non-zero path through a deliberately path-only callback. */
	public static GLPath2D build(Consumer<GLPath2D> builder) {
		return build(FillRule.NON_ZERO, builder);
	}

	/** Builds a path with the requested fill rule through a path-only callback. */
	public static GLPath2D build(FillRule fillRule, Consumer<GLPath2D> builder) {
		if (builder == null)
			throw new IllegalArgumentException("Path builder must not be null");
		GLPath2D path = new GLPath2D(fillRule);
		builder.accept(path);
		return path;
	}

	/** Appends the contours of all shapes to one non-zero path. */
	public static GLPath2D combine(GLShape2D... shapes) {
		if (shapes == null)
			throw new IllegalArgumentException("Shapes must not be null");
		GLPath2D result = new GLPath2D();
		for (GLShape2D shape : shapes)
			result.append(shape);
		return result;
	}

	public static GLPath2D rectangle(float x, float y,
			float width, float height) {
		requireShapeBounds(x, y, width, height);
		return new GLPath2D().moveTo(x, y)
				.lineTo(x + width, y)
				.lineTo(x + width, y + height)
				.lineTo(x, y + height)
				.closePath();
	}

	public static GLPath2D roundedRectangle(float x, float y,
			float width, float height, float radius) {
		return roundedRectangle(x, y, width, height, GLCornerRadii.all(radius));
	}

	public static GLPath2D roundedRectangle(float x, float y,
			float width, float height, GLCornerRadii radii) {
		requireShapeBounds(x, y, width, height);
		if (radii == null)
			throw new IllegalArgumentException("Corner radii must not be null");
		float topLeft = radii.topLeft();
		float topRight = radii.topRight();
		float bottomRight = radii.bottomRight();
		float bottomLeft = radii.bottomLeft();
		float scale = Math.min(1, Math.min(
				pairScale(width, topLeft, topRight),
				Math.min(pairScale(width, bottomLeft, bottomRight),
						Math.min(pairScale(height, topLeft, bottomLeft),
								pairScale(height, topRight, bottomRight)))));
		topLeft *= scale;
		topRight *= scale;
		bottomRight *= scale;
		bottomLeft *= scale;

		float maximumX = x + width;
		float maximumY = y + height;
		GLPath2D path = new GLPath2D().moveTo(x + topLeft, y)
				.lineTo(maximumX - topRight, y);
		appendTopRight(path, maximumX, y, topRight);
		path.lineTo(maximumX, maximumY - bottomRight);
		appendBottomRight(path, maximumX, maximumY, bottomRight);
		path.lineTo(x + bottomLeft, maximumY);
		appendBottomLeft(path, x, maximumY, bottomLeft);
		path.lineTo(x, y + topLeft);
		appendTopLeft(path, x, y, topLeft);
		return path.closePath();
	}

	public static GLPath2D ellipse(float x, float y,
			float width, float height) {
		requireShapeBounds(x, y, width, height);
		float centerX = x + width * 0.5f;
		float centerY = y + height * 0.5f;
		float radiusX = width * 0.5f;
		float radiusY = height * 0.5f;
		float controlX = radiusX * CUBIC_CIRCLE_FACTOR;
		float controlY = radiusY * CUBIC_CIRCLE_FACTOR;
		return new GLPath2D().moveTo(centerX, y)
				.cubicTo(centerX + controlX, y,
						x + width, centerY - controlY, x + width, centerY)
				.cubicTo(x + width, centerY + controlY,
						centerX + controlX, y + height, centerX, y + height)
				.cubicTo(centerX - controlX, y + height,
						x, centerY + controlY, x, centerY)
				.cubicTo(x, centerY - controlY,
						centerX - controlX, y, centerX, y)
				.closePath();
	}

	public static GLPath2D circle(float centerX, float centerY, float radius) {
		if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
				|| !Float.isFinite(radius) || radius <= 0)
			throw new IllegalArgumentException(
					"Circle center and radius must be finite and radius positive");
		return ellipse(centerX - radius, centerY - radius, radius * 2, radius * 2);
	}

	public static GLPath2D polygon(float[] points) {
		if (points == null || points.length < 6 || points.length % 2 != 0)
			throw new IllegalArgumentException(
					"A polygon requires at least three x/y coordinate pairs");
		for (float value : points)
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Polygon coordinates must be finite");
		GLPath2D path = new GLPath2D().moveTo(points[0], points[1]);
		for (int index = 2; index < points.length; index += 2)
			path.lineTo(points[index], points[index + 1]);
		return path.closePath();
	}

	private GLPath2D(GLPath2D source) {
		commands = Arrays.copyOf(source.commands, Math.max(16, source.commandCount));
		coordinates = Arrays.copyOf(source.coordinates,
				Math.max(48, source.coordinateCount));
		commandCount = source.commandCount;
		coordinateCount = source.coordinateCount;
		segmentCount = source.segmentCount;
		fillRule = source.fillRule;
		hasCurrentPoint = source.hasCurrentPoint;
		currentX = source.currentX;
		currentY = source.currentY;
		subpathStartX = source.subpathStartX;
		subpathStartY = source.subpathStartY;
		currentSubpathClosed = source.currentSubpathClosed;
	}

	public FillRule getFillRule() {
		return fillRule;
	}

	public GLPath2D setFillRule(FillRule fillRule) {
		this.fillRule = requireFillRule(fillRule);
		return this;
	}

	public GLPath2D moveTo(float x, float y) {
		requireFinite(x, y);
		add(MOVE, x, y);
		hasCurrentPoint = true;
		currentX = subpathStartX = x;
		currentY = subpathStartY = y;
		currentSubpathClosed = false;
		return this;
	}

	public GLPath2D lineTo(float x, float y) {
		requireCurrentPoint();
		requireFinite(x, y);
		add(LINE, x, y);
		currentX = x;
		currentY = y;
		currentSubpathClosed = false;
		segmentCount++;
		return this;
	}

	public GLPath2D quadTo(float controlX, float controlY, float x, float y) {
		requireCurrentPoint();
		requireFinite(controlX, controlY, x, y);
		add(QUAD, controlX, controlY, x, y);
		currentX = x;
		currentY = y;
		currentSubpathClosed = false;
		segmentCount++;
		return this;
	}

	public GLPath2D cubicTo(float control1X, float control1Y,
			float control2X, float control2Y, float x, float y) {
		requireCurrentPoint();
		requireFinite(control1X, control1Y, control2X, control2Y, x, y);
		add(CUBIC, control1X, control1Y, control2X, control2Y, x, y);
		currentX = x;
		currentY = y;
		currentSubpathClosed = false;
		segmentCount++;
		return this;
	}

	public GLPath2D closePath() {
		requireCurrentPoint();
		if (currentSubpathClosed)
			return this;
		add(CLOSE);
		currentX = subpathStartX;
		currentY = subpathStartY;
		currentSubpathClosed = true;
		return this;
	}

	/**
	 * Appends every contour of {@code shape}. The receiving path keeps its own
	 * fill rule; this operation concatenates contours and is not a boolean union.
	 */
	public GLPath2D append(GLShape2D shape) {
		if (shape == null)
			throw new IllegalArgumentException("Appended shape must not be null");
		GLPath2D source = shape == this ? copy() : shape.toPath();
		if (source == null)
			throw new IllegalArgumentException("Shape returned a null path");
		int coordinate = 0;
		for (int command = 0; command < source.commandCount; command++) {
			switch (source.commands[command]) {
			case MOVE -> moveTo(source.coordinates[coordinate++],
					source.coordinates[coordinate++]);
			case LINE -> lineTo(source.coordinates[coordinate++],
					source.coordinates[coordinate++]);
			case QUAD -> quadTo(source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++]);
			case CUBIC -> cubicTo(source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++],
					source.coordinates[coordinate++]);
			case CLOSE -> closePath();
			default -> throw new IllegalStateException("Unknown path command");
			}
		}
		return this;
	}

	/** Fluent synonym for {@link #append(GLShape2D)}. */
	public GLPath2D add(GLShape2D shape) {
		return append(shape);
	}

	public boolean isEmpty() {
		return segmentCount == 0;
	}

	public int getSegmentCount() {
		return segmentCount;
	}

	public int getCommandCount() {
		return commandCount;
	}

	public GLPath2D reset() {
		commandCount = 0;
		coordinateCount = 0;
		segmentCount = 0;
		hasCurrentPoint = false;
		currentSubpathClosed = false;
		return this;
	}

	/** Returns an independent mutable copy. */
	public GLPath2D copy() {
		return new GLPath2D(this);
	}

	/** Returns an independent mutable path representation of this shape. */
	@Override
	public GLPath2D toPath() {
		return copy();
	}

	/** Returns a new path with every command transformed by {@code transform}. */
	public GLPath2D transformed(GLTransform transform) {
		if (transform == null)
			throw new IllegalArgumentException("Path transform must not be null");
		GLPath2D result = new GLPath2D(fillRule);
		int coordinate = 0;
		for (int command = 0; command < commandCount; command++) {
			switch (commands[command]) {
			case MOVE -> appendTransformedMove(result, transform,
					coordinates[coordinate++], coordinates[coordinate++]);
			case LINE -> appendTransformedLine(result, transform,
					coordinates[coordinate++], coordinates[coordinate++]);
			case QUAD -> {
				float controlX = coordinates[coordinate++];
				float controlY = coordinates[coordinate++];
				float x = coordinates[coordinate++];
				float y = coordinates[coordinate++];
				result.quadTo(transform.transformX(controlX, controlY),
						transform.transformY(controlX, controlY),
						transform.transformX(x, y), transform.transformY(x, y));
			}
			case CUBIC -> {
				float control1X = coordinates[coordinate++];
				float control1Y = coordinates[coordinate++];
				float control2X = coordinates[coordinate++];
				float control2Y = coordinates[coordinate++];
				float x = coordinates[coordinate++];
				float y = coordinates[coordinate++];
				result.cubicTo(transform.transformX(control1X, control1Y),
						transform.transformY(control1X, control1Y),
						transform.transformX(control2X, control2Y),
						transform.transformY(control2X, control2Y),
						transform.transformX(x, y), transform.transformY(x, y));
			}
			case CLOSE -> result.closePath();
			default -> throw new IllegalStateException("Unknown path command");
			}
		}
		return result;
	}

	Path2D.Float toAwtPath() {
		Path2D.Float result = new Path2D.Float(fillRule == FillRule.EVEN_ODD
				? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
		int coordinate = 0;
		for (int command = 0; command < commandCount; command++) {
			switch (commands[command]) {
			case MOVE -> result.moveTo(coordinates[coordinate++], coordinates[coordinate++]);
			case LINE -> result.lineTo(coordinates[coordinate++], coordinates[coordinate++]);
			case QUAD -> result.quadTo(coordinates[coordinate++], coordinates[coordinate++],
					coordinates[coordinate++], coordinates[coordinate++]);
			case CUBIC -> result.curveTo(coordinates[coordinate++], coordinates[coordinate++],
					coordinates[coordinate++], coordinates[coordinate++],
					coordinates[coordinate++], coordinates[coordinate++]);
			case CLOSE -> result.closePath();
			default -> throw new IllegalStateException("Unknown path command");
			}
		}
		return result;
	}

	static GLPath2D fromAwtPath(PathIterator iterator) {
		if (iterator == null)
			throw new IllegalArgumentException("AWT path iterator must not be null");
		GLPath2D result = new GLPath2D(iterator.getWindingRule() == PathIterator.WIND_EVEN_ODD
				? FillRule.EVEN_ODD : FillRule.NON_ZERO);
		float[] coordinates = new float[6];
		while (!iterator.isDone()) {
			switch (iterator.currentSegment(coordinates)) {
			case PathIterator.SEG_MOVETO -> result.moveTo(coordinates[0], coordinates[1]);
			case PathIterator.SEG_LINETO -> result.lineTo(coordinates[0], coordinates[1]);
			case PathIterator.SEG_QUADTO -> result.quadTo(coordinates[0], coordinates[1],
					coordinates[2], coordinates[3]);
			case PathIterator.SEG_CUBICTO -> result.cubicTo(coordinates[0], coordinates[1],
					coordinates[2], coordinates[3], coordinates[4], coordinates[5]);
			case PathIterator.SEG_CLOSE -> result.closePath();
			default -> throw new IllegalArgumentException("Unsupported AWT path segment");
			}
			iterator.next();
		}
		return result;
	}

	/** Computes exact curve-extrema bounds in local path coordinates. */
	public Bounds getBounds() {
		if (commandCount == 0)
			return new Bounds(0, 0, 0, 0);
		BoundsAccumulator bounds = new BoundsAccumulator();
		int coordinate = 0;
		float x = 0;
		float y = 0;
		float startX = 0;
		float startY = 0;
		for (int command = 0; command < commandCount; command++) {
			switch (commands[command]) {
			case MOVE -> {
				x = startX = coordinates[coordinate++];
				y = startY = coordinates[coordinate++];
				bounds.include(x, y);
			}
			case LINE -> {
				float endX = coordinates[coordinate++];
				float endY = coordinates[coordinate++];
				bounds.include(x, y);
				bounds.include(endX, endY);
				x = endX;
				y = endY;
			}
			case QUAD -> {
				float controlX = coordinates[coordinate++];
				float controlY = coordinates[coordinate++];
				float endX = coordinates[coordinate++];
				float endY = coordinates[coordinate++];
				includeQuadraticBounds(bounds, x, y, controlX, controlY, endX, endY);
				x = endX;
				y = endY;
			}
			case CUBIC -> {
				float control1X = coordinates[coordinate++];
				float control1Y = coordinates[coordinate++];
				float control2X = coordinates[coordinate++];
				float control2Y = coordinates[coordinate++];
				float endX = coordinates[coordinate++];
				float endY = coordinates[coordinate++];
				includeCubicBounds(bounds, x, y, control1X, control1Y,
						control2X, control2Y, endX, endY);
				x = endX;
				y = endY;
			}
			case CLOSE -> {
				bounds.include(startX, startY);
				x = startX;
				y = startY;
			}
			default -> throw new IllegalStateException("Unknown path command");
			}
		}
		return bounds.toBounds();
	}

	FlattenedPath flatten(GLTransform localToScreen, float tolerance) {
		if (localToScreen == null)
			throw new IllegalArgumentException("Path screen transform must not be null");
		if (!Float.isFinite(tolerance) || tolerance <= 0)
			throw new IllegalArgumentException("Path tolerance must be finite and positive");
		List<Subpath> subpaths = new ArrayList<>();
		PointBuffer points = null;
		int coordinate = 0;
		float x = 0;
		float y = 0;
		float startX = 0;
		float startY = 0;
		for (int command = 0; command < commandCount; command++) {
			switch (commands[command]) {
			case MOVE -> {
				addSubpath(subpaths, points, false);
				x = startX = coordinates[coordinate++];
				y = startY = coordinates[coordinate++];
				points = new PointBuffer();
				points.add(x, y);
			}
			case LINE -> {
				if (points == null) {
					points = new PointBuffer();
					points.add(x, y);
				}
				x = coordinates[coordinate++];
				y = coordinates[coordinate++];
				points.add(x, y);
			}
			case QUAD -> {
				if (points == null) {
					points = new PointBuffer();
					points.add(x, y);
				}
				float controlX = coordinates[coordinate++];
				float controlY = coordinates[coordinate++];
				float endX = coordinates[coordinate++];
				float endY = coordinates[coordinate++];
				appendQuadratic(points, localToScreen, tolerance,
						x, y, controlX, controlY, endX, endY, 0);
				x = endX;
				y = endY;
			}
			case CUBIC -> {
				if (points == null) {
					points = new PointBuffer();
					points.add(x, y);
				}
				float control1X = coordinates[coordinate++];
				float control1Y = coordinates[coordinate++];
				float control2X = coordinates[coordinate++];
				float control2Y = coordinates[coordinate++];
				float endX = coordinates[coordinate++];
				float endY = coordinates[coordinate++];
				appendCubic(points, localToScreen, tolerance,
						x, y, control1X, control1Y, control2X, control2Y,
						endX, endY, 0);
				x = endX;
				y = endY;
			}
			case CLOSE -> {
				addSubpath(subpaths, points, true);
				points = null;
				x = startX;
				y = startY;
			}
			default -> throw new IllegalStateException("Unknown path command");
			}
		}
		addSubpath(subpaths, points, false);
		return new FlattenedPath(List.copyOf(subpaths));
	}

	private void add(byte command, float... values) {
		if (commandCount == commands.length)
			commands = Arrays.copyOf(commands, commands.length * 2);
		if (coordinateCount + values.length > coordinates.length)
			coordinates = Arrays.copyOf(coordinates,
					Math.max(coordinates.length * 2, coordinateCount + values.length));
		commands[commandCount++] = command;
		System.arraycopy(values, 0, coordinates, coordinateCount, values.length);
		coordinateCount += values.length;
	}

	private static void appendTransformedMove(GLPath2D path,
			GLTransform transform, float x, float y) {
		path.moveTo(transform.transformX(x, y), transform.transformY(x, y));
	}

	private static void appendTransformedLine(GLPath2D path,
			GLTransform transform, float x, float y) {
		path.lineTo(transform.transformX(x, y), transform.transformY(x, y));
	}

	private void requireCurrentPoint() {
		if (!hasCurrentPoint)
			throw new IllegalStateException("A path must begin with moveTo()");
	}

	private static FillRule requireFillRule(FillRule fillRule) {
		if (fillRule == null)
			throw new IllegalArgumentException("Path fill rule must not be null");
		return fillRule;
	}

	private static void requireFinite(float... values) {
		for (float value : values)
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Path coordinates must be finite");
	}

	private static void addSubpath(List<Subpath> subpaths,
			PointBuffer points, boolean closed) {
		if (closed && points != null)
			points.removeRepeatedClosingPoint();
		if (points == null || points.size() < 2)
			return;
		subpaths.add(new Subpath(points.toArray(), closed));
	}

	private static void appendQuadratic(PointBuffer points, GLTransform transform,
			float tolerance, float x0, float y0, float controlX, float controlY,
			float x1, float y1, int depth) {
		float screenX0 = transform.transformX(x0, y0);
		float screenY0 = transform.transformY(x0, y0);
		float screenControlX = transform.transformX(controlX, controlY);
		float screenControlY = transform.transformY(controlX, controlY);
		float screenX1 = transform.transformX(x1, y1);
		float screenY1 = transform.transformY(x1, y1);
		if (depth >= MAXIMUM_FLATTENING_DEPTH
				|| pointLineDistance(screenControlX, screenControlY,
						screenX0, screenY0, screenX1, screenY1) <= tolerance) {
			points.add(x1, y1);
			return;
		}
		float x01 = (x0 + controlX) * 0.5f;
		float y01 = (y0 + controlY) * 0.5f;
		float x12 = (controlX + x1) * 0.5f;
		float y12 = (controlY + y1) * 0.5f;
		float middleX = (x01 + x12) * 0.5f;
		float middleY = (y01 + y12) * 0.5f;
		appendQuadratic(points, transform, tolerance,
				x0, y0, x01, y01, middleX, middleY, depth + 1);
		appendQuadratic(points, transform, tolerance,
				middleX, middleY, x12, y12, x1, y1, depth + 1);
	}

	private static void appendCubic(PointBuffer points, GLTransform transform,
			float tolerance, float x0, float y0, float control1X, float control1Y,
			float control2X, float control2Y, float x1, float y1, int depth) {
		float screenX0 = transform.transformX(x0, y0);
		float screenY0 = transform.transformY(x0, y0);
		float screenControl1X = transform.transformX(control1X, control1Y);
		float screenControl1Y = transform.transformY(control1X, control1Y);
		float screenControl2X = transform.transformX(control2X, control2Y);
		float screenControl2Y = transform.transformY(control2X, control2Y);
		float screenX1 = transform.transformX(x1, y1);
		float screenY1 = transform.transformY(x1, y1);
		float distance1 = pointLineDistance(screenControl1X, screenControl1Y,
				screenX0, screenY0, screenX1, screenY1);
		float distance2 = pointLineDistance(screenControl2X, screenControl2Y,
				screenX0, screenY0, screenX1, screenY1);
		if (depth >= MAXIMUM_FLATTENING_DEPTH
				|| Math.max(distance1, distance2) <= tolerance) {
			points.add(x1, y1);
			return;
		}
		float x01 = (x0 + control1X) * 0.5f;
		float y01 = (y0 + control1Y) * 0.5f;
		float x12 = (control1X + control2X) * 0.5f;
		float y12 = (control1Y + control2Y) * 0.5f;
		float x23 = (control2X + x1) * 0.5f;
		float y23 = (control2Y + y1) * 0.5f;
		float x012 = (x01 + x12) * 0.5f;
		float y012 = (y01 + y12) * 0.5f;
		float x123 = (x12 + x23) * 0.5f;
		float y123 = (y12 + y23) * 0.5f;
		float middleX = (x012 + x123) * 0.5f;
		float middleY = (y012 + y123) * 0.5f;
		appendCubic(points, transform, tolerance,
				x0, y0, x01, y01, x012, y012, middleX, middleY, depth + 1);
		appendCubic(points, transform, tolerance,
				middleX, middleY, x123, y123, x23, y23, x1, y1, depth + 1);
	}

	private static float pointLineDistance(float x, float y,
			float lineX0, float lineY0, float lineX1, float lineY1) {
		float deltaX = lineX1 - lineX0;
		float deltaY = lineY1 - lineY0;
		float length = (float) Math.hypot(deltaX, deltaY);
		if (!(length > 0))
			return (float) Math.hypot(x - lineX0, y - lineY0);
		return Math.abs(deltaY * x - deltaX * y
				+ lineX1 * lineY0 - lineY1 * lineX0) / length;
	}

	private static void includeQuadraticBounds(BoundsAccumulator bounds,
			float x0, float y0, float controlX, float controlY, float x1, float y1) {
		bounds.include(x0, y0);
		bounds.include(x1, y1);
		includeQuadraticExtremum(bounds, x0, controlX, x1, true,
				y0, controlY, y1);
		includeQuadraticExtremum(bounds, y0, controlY, y1, false,
				x0, controlX, x1);
	}

	private static void includeQuadraticExtremum(BoundsAccumulator bounds,
			float start, float control, float end, boolean xDimension,
			float otherStart, float otherControl, float otherEnd) {
		float denominator = start - 2 * control + end;
		if (denominator == 0)
			return;
		float t = (start - control) / denominator;
		if (t <= 0 || t >= 1)
			return;
		float value = quadratic(start, control, end, t);
		float other = quadratic(otherStart, otherControl, otherEnd, t);
		bounds.include(xDimension ? value : other, xDimension ? other : value);
	}

	private static void includeCubicBounds(BoundsAccumulator bounds,
			float x0, float y0, float control1X, float control1Y,
			float control2X, float control2Y, float x1, float y1) {
		bounds.include(x0, y0);
		bounds.include(x1, y1);
		includeCubicExtrema(bounds, x0, control1X, control2X, x1, true,
				y0, control1Y, control2Y, y1);
		includeCubicExtrema(bounds, y0, control1Y, control2Y, y1, false,
				x0, control1X, control2X, x1);
	}

	private static void includeCubicExtrema(BoundsAccumulator bounds,
			float start, float control1, float control2, float end,
			boolean xDimension, float otherStart, float otherControl1,
			float otherControl2, float otherEnd) {
		double a = -start + 3d * control1 - 3d * control2 + end;
		double b = 2d * (start - 2d * control1 + control2);
		double c = control1 - start;
		if (Math.abs(a) < 1e-12) {
			if (Math.abs(b) > 1e-12)
				includeCubicExtremum(bounds, -c / b, start, control1,
						control2, end, xDimension, otherStart, otherControl1,
						otherControl2, otherEnd);
			return;
		}
		double discriminant = b * b - 4 * a * c;
		if (discriminant < 0)
			return;
		double root = Math.sqrt(discriminant);
		includeCubicExtremum(bounds, (-b + root) / (2 * a), start, control1,
				control2, end, xDimension, otherStart, otherControl1,
				otherControl2, otherEnd);
		includeCubicExtremum(bounds, (-b - root) / (2 * a), start, control1,
				control2, end, xDimension, otherStart, otherControl1,
				otherControl2, otherEnd);
	}

	private static void includeCubicExtremum(BoundsAccumulator bounds, double t,
			float start, float control1, float control2, float end,
			boolean xDimension, float otherStart, float otherControl1,
			float otherControl2, float otherEnd) {
		if (t <= 0 || t >= 1)
			return;
		float value = cubic(start, control1, control2, end, (float) t);
		float other = cubic(otherStart, otherControl1, otherControl2,
				otherEnd, (float) t);
		bounds.include(xDimension ? value : other, xDimension ? other : value);
	}

	private static float quadratic(float start, float control, float end, float t) {
		float inverse = 1 - t;
		return inverse * inverse * start + 2 * inverse * t * control + t * t * end;
	}

	private static float cubic(float start, float control1,
			float control2, float end, float t) {
		float inverse = 1 - t;
		return inverse * inverse * inverse * start
				+ 3 * inverse * inverse * t * control1
				+ 3 * inverse * t * t * control2 + t * t * t * end;
	}

	private static void requireShapeBounds(float x, float y,
			float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"Shape bounds must be finite and have a positive size");
	}

	private static float pairScale(float available, float first, float second) {
		float sum = first + second;
		return sum <= 0 ? 1 : available / sum;
	}

	private static void appendTopRight(GLPath2D path,
			float maximumX, float y, float radius) {
		if (radius == 0) {
			path.lineTo(maximumX, y);
			return;
		}
		float control = radius * CUBIC_CIRCLE_FACTOR;
		path.cubicTo(maximumX - radius + control, y,
				maximumX, y + radius - control, maximumX, y + radius);
	}

	private static void appendBottomRight(GLPath2D path,
			float maximumX, float maximumY, float radius) {
		if (radius == 0) {
			path.lineTo(maximumX, maximumY);
			return;
		}
		float control = radius * CUBIC_CIRCLE_FACTOR;
		path.cubicTo(maximumX, maximumY - radius + control,
				maximumX - radius + control, maximumY,
				maximumX - radius, maximumY);
	}

	private static void appendBottomLeft(GLPath2D path,
			float x, float maximumY, float radius) {
		if (radius == 0) {
			path.lineTo(x, maximumY);
			return;
		}
		float control = radius * CUBIC_CIRCLE_FACTOR;
		path.cubicTo(x + radius - control, maximumY,
				x, maximumY - radius + control, x, maximumY - radius);
	}

	private static void appendTopLeft(GLPath2D path,
			float x, float y, float radius) {
		if (radius == 0) {
			path.lineTo(x, y);
			return;
		}
		float control = radius * CUBIC_CIRCLE_FACTOR;
		path.cubicTo(x, y + radius - control,
				x + radius - control, y, x + radius, y);
	}

	static final class FlattenedPath {
		private final List<Subpath> subpaths;

		FlattenedPath(List<Subpath> subpaths) {
			this.subpaths = subpaths;
		}

		List<Subpath> subpaths() {
			return subpaths;
		}
	}

	static final class Subpath {
		private final float[] points;
		private final boolean closed;

		Subpath(float[] points, boolean closed) {
			this.points = points;
			this.closed = closed;
		}

		float[] points() { return points; }
		boolean closed() { return closed; }
	}

	private static final class PointBuffer {
		private float[] values = new float[32];
		private int size;

		void add(float x, float y) {
			if (size >= 2 && values[size - 2] == x && values[size - 1] == y)
				return;
			if (size + 2 > values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = x;
			values[size++] = y;
		}

		int size() { return size / 2; }

		void removeRepeatedClosingPoint() {
			if (size >= 4 && values[0] == values[size - 2]
					&& values[1] == values[size - 1])
				size -= 2;
		}

		float[] toArray() { return Arrays.copyOf(values, size); }
	}

	private static final class BoundsAccumulator {
		private float minimumX = Float.POSITIVE_INFINITY;
		private float minimumY = Float.POSITIVE_INFINITY;
		private float maximumX = Float.NEGATIVE_INFINITY;
		private float maximumY = Float.NEGATIVE_INFINITY;

		void include(float x, float y) {
			minimumX = Math.min(minimumX, x);
			minimumY = Math.min(minimumY, y);
			maximumX = Math.max(maximumX, x);
			maximumY = Math.max(maximumY, y);
		}

		Bounds toBounds() {
			if (minimumX == Float.POSITIVE_INFINITY)
				return new Bounds(0, 0, 0, 0);
			return new Bounds(minimumX, minimumY,
					maximumX - minimumX, maximumY - minimumY);
		}
	}
}
