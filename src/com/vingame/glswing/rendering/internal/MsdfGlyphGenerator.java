package com.vingame.glswing.rendering.internal;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Generates a compact multi-channel signed-distance field from a glyph outline. */
public final class MsdfGlyphGenerator {

	private static final double FLATNESS = 0.2;
	private static final double CORNER_COSINE = Math.cos(Math.toRadians(30));
	private static final int RED = 1;
	private static final int GREEN = 2;
	private static final int BLUE = 4;
	private static final int WHITE = RED | GREEN | BLUE;
	private static final int[] EDGE_COLORS = { GREEN | BLUE, RED | BLUE, RED | GREEN };

	private MsdfGlyphGenerator() {
	}

	public static GlyphBitmap generate(Shape outline, float scale, float pixelRange) {
		if (outline == null)
			throw new IllegalArgumentException("Glyph outline must not be null");
		if (!(scale > 0) || !Float.isFinite(scale))
			throw new IllegalArgumentException("MSDF scale must be finite and positive");
		if (!(pixelRange >= 2) || !Float.isFinite(pixelRange))
			throw new IllegalArgumentException("MSDF pixel range must be finite and at least 2");

		Shape scaledOutline = AffineTransform.getScaleInstance(scale, scale)
				.createTransformedShape(outline);
		Rectangle2D visualBounds = scaledOutline.getBounds2D();
		if (visualBounds.isEmpty())
			return GlyphBitmap.empty();

		float halfRange = pixelRange * 0.5f;
		int padding = (int) Math.ceil(halfRange) + 2;
		int minimumX = (int) Math.floor(visualBounds.getMinX()) - padding;
		int minimumY = (int) Math.floor(visualBounds.getMinY()) - padding;
		int maximumX = (int) Math.ceil(visualBounds.getMaxX()) + padding;
		int maximumY = (int) Math.ceil(visualBounds.getMaxY()) + padding;
		int width = maximumX - minimumX;
		int height = maximumY - minimumY;

		List<Edge> edges = coloredEdges(scaledOutline);
		if (edges.isEmpty())
			return GlyphBitmap.empty();

		int[] pixels = new int[width * height];
		for (int y = 0; y < height; y++) {
			double sampleY = minimumY + y + 0.5;
			for (int x = 0; x < width; x++) {
				double sampleX = minimumX + x + 0.5;
				double redDistance = halfRange;
				double greenDistance = halfRange;
				double blueDistance = halfRange;
				for (Edge edge : edges) {
					double distance = edge.distanceTo(sampleX, sampleY);
					if ((edge.color & RED) != 0)
						redDistance = Math.min(redDistance, distance);
					if ((edge.color & GREEN) != 0)
						greenDistance = Math.min(greenDistance, distance);
					if ((edge.color & BLUE) != 0)
						blueDistance = Math.min(blueDistance, distance);
				}

				double sign = scaledOutline.contains(sampleX, sampleY) ? 1 : -1;
				int red = encode(sign * redDistance, pixelRange);
				int green = encode(sign * greenDistance, pixelRange);
				int blue = encode(sign * blueDistance, pixelRange);
				pixels[y * width + x] = 0xff000000 | red << 16 | green << 8 | blue;
			}
		}

		return new GlyphBitmap(width, height, minimumX / scale, minimumY / scale, pixels);
	}

	private static List<Edge> coloredEdges(Shape outline) {
		List<List<Point>> contours = contours(outline);
		List<Edge> result = new ArrayList<>();
		for (List<Point> contour : contours) {
			int count = contour.size();
			if (count < 2)
				continue;

			boolean[] corners = new boolean[count];
			int firstCorner = -1;
			int cornerCount = 0;
			for (int index = 0; index < count; index++) {
				Point previous = contour.get((index + count - 1) % count);
				Point current = contour.get(index);
				Point next = contour.get((index + 1) % count);
				corners[index] = isCorner(previous, current, next);
				if (corners[index]) {
					cornerCount++;
					if (firstCorner < 0)
						firstCorner = index;
				}
			}

			Edge[] contourEdges = new Edge[count];
			if (firstCorner < 0) {
				for (int index = 0; index < count; index++)
					contourEdges[index] = edge(contour, index, WHITE);
			} else {
				int spanIndex = 0;
				for (int step = 0; step < count; step++) {
					int index = (firstCorner + step) % count;
					contourEdges[index] = edge(contour, index,
							EDGE_COLORS[colorForSpan(spanIndex, cornerCount)]);
					int nextIndex = (index + 1) % count;
					if (corners[nextIndex])
						spanIndex++;
				}
			}
			for (Edge contourEdge : contourEdges)
				result.add(contourEdge);
		}
		return result;
	}

	/* Properly colors a closed cycle: its final span must differ from its first. */
	private static int colorForSpan(int spanIndex, int spanCount) {
		if ((spanCount & 1) == 0)
			return spanIndex & 1;
		if (spanIndex == spanCount - 1)
			return 2;
		return spanIndex & 1;
	}

	private static Edge edge(List<Point> contour, int index, int color) {
		return new Edge(contour.get(index), contour.get((index + 1) % contour.size()), color);
	}

	private static boolean isCorner(Point previous, Point current, Point next) {
		double incomingX = current.x - previous.x;
		double incomingY = current.y - previous.y;
		double outgoingX = next.x - current.x;
		double outgoingY = next.y - current.y;
		double incomingLength = Math.hypot(incomingX, incomingY);
		double outgoingLength = Math.hypot(outgoingX, outgoingY);
		if (incomingLength == 0 || outgoingLength == 0)
			return false;
		double cosine = (incomingX * outgoingX + incomingY * outgoingY)
				/ (incomingLength * outgoingLength);
		return cosine < CORNER_COSINE;
	}

	private static List<List<Point>> contours(Shape outline) {
		List<List<Point>> contours = new ArrayList<>();
		List<Point> current = null;
		double[] coordinates = new double[6];
		PathIterator iterator = outline.getPathIterator(null, FLATNESS);
		while (!iterator.isDone()) {
			switch (iterator.currentSegment(coordinates)) {
			case PathIterator.SEG_MOVETO -> {
				finishContour(contours, current);
				current = new ArrayList<>();
				current.add(new Point(coordinates[0], coordinates[1]));
			}
			case PathIterator.SEG_LINETO -> {
				if (current != null)
					addDistinct(current, coordinates[0], coordinates[1]);
			}
			case PathIterator.SEG_CLOSE -> {
				finishContour(contours, current);
				current = null;
			}
			default -> throw new IllegalStateException("Glyph path was not flattened");
			}
			iterator.next();
		}
		finishContour(contours, current);
		return contours;
	}

	private static void addDistinct(List<Point> points, double x, double y) {
		Point last = points.getLast();
		if (last.x != x || last.y != y)
			points.add(new Point(x, y));
	}

	private static void finishContour(List<List<Point>> contours, List<Point> contour) {
		if (contour == null || contour.size() < 2)
			return;
		Point first = contour.getFirst();
		Point last = contour.getLast();
		if (first.x == last.x && first.y == last.y)
			contour.removeLast();
		if (contour.size() >= 2)
			contours.add(contour);
	}

	private static int encode(double signedDistance, float pixelRange) {
		double value = 0.5 + signedDistance / pixelRange;
		return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
	}

	private record Point(double x, double y) {
	}

	private static final class Edge {
		private final double x1;
		private final double y1;
		private final double dx;
		private final double dy;
		private final double lengthSquared;
		private final int color;

		Edge(Point start, Point end, int color) {
			x1 = start.x;
			y1 = start.y;
			dx = end.x - start.x;
			dy = end.y - start.y;
			lengthSquared = dx * dx + dy * dy;
			this.color = color;
		}

		double distanceTo(double x, double y) {
			double projection = ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
			double t = Math.max(0, Math.min(1, projection));
			return Math.hypot(x - (x1 + t * dx), y - (y1 + t * dy));
		}
	}

	public static final class GlyphBitmap {
		private static final GlyphBitmap EMPTY = new GlyphBitmap(0, 0, 0, 0, new int[0]);

		private final int width;
		private final int height;
		private final float offsetX;
		private final float offsetY;
		private final int[] pixels;

		GlyphBitmap(int width, int height, float offsetX, float offsetY, int[] pixels) {
			this.width = width;
			this.height = height;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.pixels = pixels;
		}

		static GlyphBitmap empty() {
			return EMPTY;
		}

		public boolean hasPixels() {
			return width > 0 && height > 0;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public float offsetX() {
			return offsetX;
		}

		public float offsetY() {
			return offsetY;
		}

		public int[] pixels() {
			return pixels;
		}
	}
}
