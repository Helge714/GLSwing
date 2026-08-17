package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;

import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator;
import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator.GlyphBitmap;

/** Atlas-ready icon rendered either as RGBA pixels or a tintable MSDF shape. */
public final class GLIcon {

	public enum Format { AUTO, RGBA, MSDF }

	private static final int DEFAULT_RESOLUTION = 128;
	private static final float DEFAULT_PIXEL_RANGE = 8;

	private final GLImage image;
	private final Format format;
	private final float pixelRange;

	private GLIcon(GLImage image, Format format, float pixelRange) {
		this.image = image;
		this.format = format;
		this.pixelRange = pixelRange;
	}

	public static GLIcon fromImage(GLImage image) {
		if (image == null)
			throw new IllegalArgumentException("Icon image must not be null");
		return new GLIcon(image, Format.RGBA, 0);
	}

	public static GLIcon fromSvgResource(String resourcePath) {
		return fromSvg(GLSvgDocument.loadResource(resourcePath), Format.AUTO);
	}

	public static GLIcon fromSvgResource(String resourcePath, Format format) {
		return fromSvg(GLSvgDocument.loadResource(resourcePath), format);
	}

	public static GLIcon fromSvg(GLSvgDocument document, Format format) {
		return fromSvg(document, format, DEFAULT_RESOLUTION, DEFAULT_PIXEL_RANGE);
	}

	public static GLIcon fromSvg(GLSvgDocument document, Format requested,
			int resolution, float pixelRange) {
		if (document == null || requested == null)
			throw new IllegalArgumentException("SVG document and icon format are required");
		if (resolution < 16)
			throw new IllegalArgumentException("Icon resolution must be at least 16 pixels");
		Format resolved = requested == Format.AUTO
				? (document.isMonochrome() ? Format.MSDF : Format.RGBA) : requested;
		if (resolved == Format.RGBA) {
			GLImage pixels = GLImage.of(document.rasterize(resolution, resolution),
					GLImageOptions.atlas());
			return new GLIcon(pixels, resolved, 0);
		}
		if (!Float.isFinite(pixelRange) || pixelRange < 2)
			throw new IllegalArgumentException("MSDF pixel range must be at least 2");
		float margin = pixelRange + 3;
		GlyphBitmap bitmap = MsdfGlyphGenerator.generate(
				document.monochromeOutline(resolution, margin), 1, pixelRange);
		BufferedImage field = new BufferedImage(resolution, resolution,
				BufferedImage.TYPE_INT_ARGB);
		if (bitmap.hasPixels()) {
			int targetX = Math.round(bitmap.offsetX());
			int targetY = Math.round(bitmap.offsetY());
			for (int y = 0; y < bitmap.height(); y++) {
				int destinationY = targetY + y;
				if (destinationY < 0 || destinationY >= resolution)
					continue;
				for (int x = 0; x < bitmap.width(); x++) {
					int destinationX = targetX + x;
					if (destinationX >= 0 && destinationX < resolution)
						field.setRGB(destinationX, destinationY,
								bitmap.pixels()[y * bitmap.width() + x]);
				}
			}
		}
		return new GLIcon(GLImage.of(field, GLImageOptions.atlas()), resolved, pixelRange);
	}

	public Format getFormat() {
		return format;
	}

	public int getRasterResolution() {
		return image.getWidth();
	}

	GLImage image() {
		return image;
	}

	float pixelRange() {
		return pixelRange;
	}
}
