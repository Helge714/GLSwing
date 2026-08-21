package com.vingame.glswing.test;

import java.awt.geom.Path2D;

import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator;
import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator.GlyphBitmap;

/** Minimal CPU-only checks for MSDF sign, channel separation and empty glyphs. */
public final class MsdfGlyphGeneratorSelfTest {

	private MsdfGlyphGeneratorSelfTest() {
	}

	public static void main(String[] args) {
		Path2D.Float rectangle = new Path2D.Float();
		rectangle.moveTo(0, 0);
		rectangle.lineTo(20, 0);
		rectangle.lineTo(20, 16);
		rectangle.lineTo(0, 16);
		rectangle.closePath();

		float scale = 2;
		GlyphBitmap bitmap = MsdfGlyphGenerator.generate(rectangle, scale, 8);
		check(bitmap.hasPixels(), "Rectangle must generate pixels");
		check(median(sample(bitmap, scale, 10, 8)) > 127,
				"MSDF center must be inside");
		check(median(bitmap.pixels()[0]) < 127,
				"MSDF padded corner must be outside");

		int topEdge = sample(bitmap, scale, 7, 0.75f);
		int rightEdge = sample(bitmap, scale, 19.25f, 6);
		check(hasSeparatedChannels(topEdge), "Top edge must contain multiple distances");
		check(hasSeparatedChannels(rightEdge), "Right edge must contain multiple distances");

		GlyphBitmap empty = MsdfGlyphGenerator.generate(new Path2D.Float(), 1, 8);
		check(!empty.hasPixels(), "Empty glyph must not generate a quad");
		System.out.println("MSDF glyph generator self-test passed.");
	}

	private static int sample(GlyphBitmap bitmap, float scale, float x, float y) {
		int pixelX = (int) Math.floor((x - bitmap.offsetX()) * scale);
		int pixelY = (int) Math.floor((y - bitmap.offsetY()) * scale);
		return bitmap.pixels()[pixelY * bitmap.width() + pixelX];
	}

	private static int median(int pixel) {
		int red = pixel >>> 16 & 0xff;
		int green = pixel >>> 8 & 0xff;
		int blue = pixel & 0xff;
		return Math.max(Math.min(red, green), Math.min(Math.max(red, green), blue));
	}

	private static boolean hasSeparatedChannels(int pixel) {
		int red = pixel >>> 16 & 0xff;
		int green = pixel >>> 8 & 0xff;
		int blue = pixel & 0xff;
		return red != green || green != blue;
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
