package com.vingame.glswing.rendering.internal;

import java.awt.Color;

import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLTransform;

/** CPU-side layout and compilers for instanced standard UI quads. */
public final class UIQuadInstanceFormat {

	public static final int FLOATS_PER_INSTANCE = 28;
	public static final float MODE_ROUNDED_FILL = 0;
	public static final float MODE_ROUNDED_STROKE = 1;
	public static final float MODE_MSDF_TEXT = 2;
	public static final float MODE_TEXTURE = 3;
	public static final float MODE_CHECK_MARK = 4;
	public static final float MODE_HIT_AREA = 5;
	public static final float MODE_ROUNDED_FILL_BORDER = 6;
	public static final float MODE_PROGRESS_GRADIENT = 7;
	public static final float MODE_CHEVRON = 8;
	public static final float MODE_ASYMMETRIC_ROUNDED_FILL = 9;
	public static final float MODE_ASYMMETRIC_ROUNDED_STROKE = 10;
	public static final float MODE_VERTICAL_CORNER_PROGRESS_GRADIENT = 11;
	public static final float MODE_ROUNDED_SHADOW = 12;
	public static final float MODE_ASYMMETRIC_ROUNDED_FILL_BORDER = 13;
	public static final float MODE_INNER_PROGRESS_GRADIENT = 14;
	public static final float MODE_SLIDER_FILL_PROGRESS_BORDER = 15;

	private UIQuadInstanceFormat() {
	}

	public static float[] roundedRectangle(float x, float y, float width,
			float height, float radius, float strokeWidth, boolean stroke,
			boolean screenSpaceStroke, Color color, GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"UI rectangle bounds must be finite and positive");
		if (!Float.isFinite(radius) || radius < 0 || !Float.isFinite(strokeWidth)
				|| strokeWidth < 0)
			throw new IllegalArgumentException(
					"UI radius and stroke width must be finite and non-negative");
		if (color == null || transform == null)
			throw new IllegalArgumentException("UI color and transform must not be null");
		float[] result = new float[FLOATS_PER_INSTANCE];
		putGeometry(result, 0, x, y, width, height, transform);
		putUv(result, 0, 0, 0, 1, 1);
		putColor(result, 0, color);
		result[14] = stroke ? MODE_ROUNDED_STROKE : MODE_ROUNDED_FILL;
		result[15] = radius;
		result[16] = strokeWidth;
		result[17] = screenSpaceStroke ? 1 : 0;
		result[18] = width;
		result[19] = height;
		return result;
	}

	/** Builds an analytic inner stroke with one radius per corner. */
	public static float[] roundedRectangleStroke(float x, float y, float width,
			float height, GLCornerRadii radii, float strokeWidth,
			boolean screenSpaceStroke, Color color, GLTransform transform) {
		if (radii == null)
			throw new IllegalArgumentException("UI corner radii must not be null");
		float[] result = roundedRectangle(x, y, width, height, 0, strokeWidth,
				true, screenSpaceStroke, color, transform);
		result[14] = MODE_ASYMMETRIC_ROUNDED_STROKE;
		result[20] = radii.topLeft();
		result[21] = radii.topRight();
		result[22] = radii.bottomRight();
		result[23] = radii.bottomLeft();
		return result;
	}

	/** Builds an analytic fill with an independent radius at every corner. */
	public static float[] roundedRectangle(float x, float y, float width,
			float height, GLCornerRadii radii, Color color,
			GLTransform transform) {
		if (radii == null)
			throw new IllegalArgumentException("UI corner radii must not be null");
		float[] result = roundedRectangle(x, y, width, height, 0, 0,
				false, false, color, transform);
		result[14] = MODE_ASYMMETRIC_ROUNDED_FILL;
		result[20] = radii.topLeft();
		result[21] = radii.topRight();
		result[22] = radii.bottomRight();
		result[23] = radii.bottomLeft();
		return result;
	}

	/** Builds one rounded rectangle whose fill and inner border never overlap. */
	public static float[] roundedRectangleWithBorder(float x, float y,
			float width, float height, float radius, float strokeWidth,
			boolean screenSpaceStroke, Color fillColor, Color borderColor,
			GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"UI rectangle bounds must be finite and positive");
		if (!Float.isFinite(radius) || radius < 0 || !Float.isFinite(strokeWidth)
				|| strokeWidth <= 0)
			throw new IllegalArgumentException(
					"UI radius and border width must be finite and positive");
		if (fillColor == null || borderColor == null || transform == null)
			throw new IllegalArgumentException("UI colors and transform must not be null");
		float[] result = new float[FLOATS_PER_INSTANCE];
		putGeometry(result, 0, x, y, width, height, transform);
		putUv(result, 0, 0, 0, 1, 1);
		putColor(result, 0, fillColor);
		result[14] = MODE_ROUNDED_FILL_BORDER;
		result[15] = radius;
		result[16] = strokeWidth;
		result[17] = screenSpaceStroke ? 1 : 0;
		result[18] = width;
		result[19] = height;
		putSecondaryColor(result, 0, borderColor);
		return result;
	}

	/** Builds one asymmetric rounded fill and inner border without overlap. */
	public static float[] roundedRectangleWithBorder(float x, float y,
			float width, float height, GLCornerRadii radii, float strokeWidth,
			boolean screenSpaceStroke, Color fillColor, Color borderColor,
			GLTransform transform) {
		if (radii == null)
			throw new IllegalArgumentException("UI corner radii must not be null");
		float[] result = roundedRectangleWithBorder(x, y, width, height, 0,
				strokeWidth, screenSpaceStroke, fillColor, borderColor, transform);
		result[14] = MODE_ASYMMETRIC_ROUNDED_FILL_BORDER;
		result[6] = radii.topLeft();
		result[7] = radii.topRight();
		result[8] = radii.bottomRight();
		result[9] = radii.bottomLeft();
		return result;
	}

	/**
	 * Builds a soft analytic shadow around an offset rounded rectangle. The
	 * returned quad contains only the shadow and is intended to be followed by
	 * the opaque UI body in the same ordered batch.
	 */
	public static float[] roundedRectangleShadow(float x, float y,
			float width, float height, float radius, float softness,
			float offsetX, float offsetY, Color color, GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"UI shadow bounds must be finite and positive");
		if (!Float.isFinite(radius) || radius < 0
				|| !Float.isFinite(softness) || softness < 0
				|| !Float.isFinite(offsetX) || !Float.isFinite(offsetY))
			throw new IllegalArgumentException(
					"UI shadow geometry must be finite and non-negative");
		if (color == null || transform == null)
			throw new IllegalArgumentException(
					"UI shadow color and transform must not be null");

		float padding = Math.max(softness, 0.5f);
		float outerX = x + offsetX - padding;
		float outerY = y + offsetY - padding;
		float outerWidth = width + padding * 2;
		float outerHeight = height + padding * 2;
		float[] result = roundedRectangle(outerX, outerY,
				outerWidth, outerHeight, 0, 0, false, false, color, transform);
		result[14] = MODE_ROUNDED_SHADOW;
		result[15] = radius;
		result[16] = softness;
		result[18] = outerWidth;
		result[19] = outerHeight;
		// Secondary slots carry the rounded caster bounds within the outer quad.
		result[20] = padding;
		result[21] = padding;
		result[22] = width;
		result[23] = height;
		return result;
	}

	/** Builds one antialiased, two-segment check mark inside the given bounds. */
	public static float[] checkMark(float x, float y, float width,
			float height, float strokeWidth, Color color, GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"UI check mark bounds must be finite and positive");
		if (!Float.isFinite(strokeWidth) || strokeWidth <= 0)
			throw new IllegalArgumentException(
					"UI check mark stroke width must be finite and positive");
		if (color == null || transform == null)
			throw new IllegalArgumentException("UI color and transform must not be null");
		float[] result = new float[FLOATS_PER_INSTANCE];
		putGeometry(result, 0, x, y, width, height, transform);
		putUv(result, 0, 0, 0, 1, 1);
		putColor(result, 0, color);
		result[14] = MODE_CHECK_MARK;
		result[15] = 0;
		result[16] = strokeWidth;
		result[17] = 0;
		result[18] = width;
		result[19] = height;
		return result;
	}

	/** Builds a vertically shaded progress region clipped by one outer round rect. */
	public static float[] progressGradient(float x, float y, float width,
			float height, float radius, float progress, Color topColor,
			Color bottomColor, GLTransform transform) {
		if (!Float.isFinite(progress) || progress < 0 || progress > 1)
			throw new IllegalArgumentException("UI progress must be between zero and one");
		float[] result = roundedRectangle(x, y, width, height, radius, 0,
				false, false, topColor, transform);
		result[14] = MODE_PROGRESS_GRADIENT;
		result[16] = progress;
		putSecondaryColor(result, 0, bottomColor);
		return result;
	}

	/** Progress fill whose rounded outer contour is hard-clipped inside a border. */
	public static float[] innerProgressGradient(float x, float y, float width,
			float height, float radius, float progress, Color topColor,
			Color bottomColor, GLTransform transform) {
		float[] result = progressGradient(x, y, width, height, radius, progress,
				topColor, bottomColor, transform);
		result[14] = MODE_INNER_PROGRESS_GRADIENT;
		return result;
	}

	/** Builds one gap-free slider background, progress gradient, and border. */
	public static float[] slider(float x, float y, float width, float height,
			float radius, float progress, float strokeWidth,
			boolean screenSpaceStroke, Color background, Color progressTop,
			Color progressBottom, Color border, GLTransform transform) {
		float[] result = progressGradient(x, y, width, height, radius, progress,
				progressTop, progressBottom, transform);
		result[14] = MODE_SLIDER_FILL_PROGRESS_BORDER;
		result[17] = screenSpaceStroke ? -strokeWidth : strokeWidth;
		putUv(result, 0, border.getRed() / 255f, border.getGreen() / 255f,
				border.getBlue() / 255f, border.getAlpha() / 255f);
		putTertiaryColor(result, 0, background);
		return result;
	}

	/** Progress gradient with one shared radius for each horizontal edge. */
	public static float[] progressGradient(float x, float y, float width,
			float height, float topRadius, float bottomRadius, float progress,
			Color topColor, Color bottomColor, GLTransform transform) {
		if (!Float.isFinite(topRadius) || topRadius < 0
				|| !Float.isFinite(bottomRadius) || bottomRadius < 0)
			throw new IllegalArgumentException(
					"UI progress radii must be finite and non-negative");
		if (!Float.isFinite(progress) || progress < 0 || progress > 1)
			throw new IllegalArgumentException(
					"UI progress must be between zero and one");
		float[] result = roundedRectangle(x, y, width, height, topRadius, 0,
				false, false, topColor, transform);
		result[14] = MODE_VERTICAL_CORNER_PROGRESS_GRADIENT;
		result[15] = topRadius;
		result[16] = progress;
		result[17] = bottomRadius;
		putSecondaryColor(result, 0, bottomColor);
		return result;
	}

	/** Builds an antialiased left- or right-pointing two-segment chevron. */
	public static float[] chevron(float x, float y, float width, float height,
			boolean pointsRight, float strokeWidth, boolean screenSpaceStroke,
			Color color, GLTransform transform) {
		if (!Float.isFinite(strokeWidth) || strokeWidth <= 0)
			throw new IllegalArgumentException(
					"UI chevron stroke width must be finite and positive");
		float[] result = roundedRectangle(x, y, width, height, 0, strokeWidth,
				false, screenSpaceStroke, color, transform);
		result[14] = MODE_CHEVRON;
		result[15] = pointsRight ? 1 : -1;
		result[16] = strokeWidth;
		result[17] = screenSpaceStroke ? 1 : 0;
		return result;
	}

	/** Builds an invisible color instance that covers a complete picking area. */
	public static float[] hitArea(float x, float y, float width,
			float height, GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"UI hit area bounds must be finite and positive");
		if (transform == null)
			throw new IllegalArgumentException("UI transform must not be null");
		float[] result = new float[FLOATS_PER_INSTANCE];
		putGeometry(result, 0, x, y, width, height, transform);
		putUv(result, 0, 0, 0, 1, 1);
		// The visible shader discards this mode. Opaque alpha lets replay opacity
		// still disable the hit area in the picking shader.
		putColor(result, 0, Color.WHITE);
		result[14] = MODE_HIT_AREA;
		result[18] = width;
		result[19] = height;
		return result;
	}

	/** Converts normalized six-vertex glyph quads into one instance per glyph. */
	public static float[] msdfGlyphs(float[] vertices, float pixelRange) {
		return msdfGlyphs(vertices, pixelRange, 0, 0, false);
	}

	/** Converts glyph quads and optionally retains a replay-safe screen offset. */
	public static float[] msdfGlyphs(float[] vertices, float pixelRange,
			float screenOffsetX, float screenOffsetY, boolean screenOffset) {
		if (vertices == null
				|| vertices.length % (6 * TexturedVertexFormat.FLOATS_PER_VERTEX) != 0)
			throw new IllegalArgumentException("MSDF glyph vertices are invalid");
		if (!Float.isFinite(pixelRange) || pixelRange <= 0)
			throw new IllegalArgumentException("MSDF pixel range must be positive");
		int glyphCount = vertices.length
				/ (6 * TexturedVertexFormat.FLOATS_PER_VERTEX);
		float[] result = new float[glyphCount * FLOATS_PER_INSTANCE];
		int vertexStride = TexturedVertexFormat.FLOATS_PER_VERTEX;
		for (int glyph = 0; glyph < glyphCount; glyph++) {
			int source = glyph * 6 * vertexStride;
			int target = glyph * FLOATS_PER_INSTANCE;
			putCorners(result, target,
					vertices[source], vertices[source + 1],
					vertices[source + vertexStride], vertices[source + vertexStride + 1],
					vertices[source + 5 * vertexStride],
					vertices[source + 5 * vertexStride + 1]);
			putUv(result, target,
					vertices[source + 2], vertices[source + 3],
					vertices[source + 2 * vertexStride + 2],
					vertices[source + 2 * vertexStride + 3]);
			result[target + 10] = vertices[source + 4];
			result[target + 11] = vertices[source + 5];
			result[target + 12] = vertices[source + 6];
			result[target + 13] = vertices[source + 7];
			result[target + 14] = MODE_MSDF_TEXT;
			result[target + 15] = 0;
			result[target + 16] = 0;
			result[target + 17] = pixelRange;
			result[target + 18] = Math.abs(
					vertices[source + vertexStride] - vertices[source]);
			result[target + 19] = Math.abs(
					vertices[source + 5 * vertexStride + 1] - vertices[source + 1]);
			result[target + 20] = screenOffsetX;
			result[target + 21] = screenOffsetY;
			result[target + 22] = screenOffset ? 1 : 0;
		}
		return result;
	}

	/** Builds one atlas or standalone RGBA image instance. */
	public static float[] texture(float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, Color tint,
			GLTransform transform) {
		return sampledQuad(x, y, width, height, u0, v0, u1, v1,
				tint, MODE_TEXTURE, 0, transform);
	}

	/** Builds one monochrome distance-field icon instance. */
	public static float[] distanceField(float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, float pixelRange,
			Color color, GLTransform transform) {
		if (!Float.isFinite(pixelRange) || pixelRange <= 0)
			throw new IllegalArgumentException("Distance-field pixel range must be positive");
		return sampledQuad(x, y, width, height, u0, v0, u1, v1,
				color, MODE_MSDF_TEXT, pixelRange, transform);
	}

	private static float[] sampledQuad(float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, Color color,
			float mode, float pixelRange, GLTransform transform) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width == 0 || height == 0)
			throw new IllegalArgumentException("Sampled UI bounds must be finite and non-zero");
		if (color == null || transform == null)
			throw new IllegalArgumentException("Sampled UI color and transform are required");
		float[] result = new float[FLOATS_PER_INSTANCE];
		putGeometry(result, 0, x, y, width, height, transform);
		putUv(result, 0, u0, v0, u1, v1);
		putColor(result, 0, color);
		result[14] = mode;
		result[17] = pixelRange;
		result[18] = Math.abs(width);
		result[19] = Math.abs(height);
		return result;
	}

	private static void putGeometry(float[] target, int offset, float x, float y,
			float width, float height, GLTransform transform) {
		putCorners(target, offset,
				transform.transformX(x, y), transform.transformY(x, y),
				transform.transformX(x + width, y), transform.transformY(x + width, y),
				transform.transformX(x, y + height), transform.transformY(x, y + height));
	}

	private static void putCorners(float[] target, int offset,
			float x00, float y00, float x10, float y10, float x01, float y01) {
		target[offset] = x00;
		target[offset + 1] = y00;
		target[offset + 2] = x10;
		target[offset + 3] = y10;
		target[offset + 4] = x01;
		target[offset + 5] = y01;
	}

	private static void putUv(float[] target, int offset,
			float u0, float v0, float u1, float v1) {
		target[offset + 6] = u0;
		target[offset + 7] = v0;
		target[offset + 8] = u1;
		target[offset + 9] = v1;
	}

	private static void putColor(float[] target, int offset, Color color) {
		target[offset + 10] = color.getRed() / 255f;
		target[offset + 11] = color.getGreen() / 255f;
		target[offset + 12] = color.getBlue() / 255f;
		target[offset + 13] = color.getAlpha() / 255f;
	}

	private static void putSecondaryColor(float[] target, int offset, Color color) {
		target[offset + 20] = color.getRed() / 255f;
		target[offset + 21] = color.getGreen() / 255f;
		target[offset + 22] = color.getBlue() / 255f;
		target[offset + 23] = color.getAlpha() / 255f;
	}

	private static void putTertiaryColor(float[] target, int offset, Color color) {
		target[offset + 24] = color.getRed() / 255f;
		target[offset + 25] = color.getGreen() / 255f;
		target[offset + 26] = color.getBlue() / 255f;
		target[offset + 27] = color.getAlpha() / 255f;
	}
}
