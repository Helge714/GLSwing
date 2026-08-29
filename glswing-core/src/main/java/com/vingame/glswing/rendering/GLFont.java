package com.vingame.glswing.rendering;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator;
import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator.GlyphBitmap;

/** Lazily initialized scalable MSDF font and glyph atlas. */
public final class GLFont {

	public static final String DIALOG = Font.DIALOG;
	public static final String DIALOG_INPUT = Font.DIALOG_INPUT;
	public static final String MONOSPACED = Font.MONOSPACED;
	public static final String SANS_SERIF = Font.SANS_SERIF;
	public static final String SERIF = Font.SERIF;

	public static final int PLAIN = Font.PLAIN;
	public static final int BOLD = Font.BOLD;
	public static final int ITALIC = Font.ITALIC;

	public static final float DEFAULT_SIZE = 12f;

	private static final int ATLAS_GAP = 2;
	private static final int INITIAL_ATLAS_SIZE = 128;
	private static final float MSDF_EM_RESOLUTION = 64f;
	private static final float MSDF_PIXEL_RANGE = 8f;
	private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);
	private static final String DEFAULT_CHARACTERS = defaultCharacters();
	private static final int PREPARED_ATLAS_CACHE_SIZE = 32;
	private static final Map<AtlasKey, PreparedAtlas> PREPARED_ATLASES =
			new LinkedHashMap<>(16, 0.75f, true) {
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(
						Map.Entry<AtlasKey, PreparedAtlas> eldest) {
					return size() > PREPARED_ATLAS_CACHE_SIZE;
				}
			};

	private final Font font;
	private final String characters;
	private final int missingGlyphCode;
	private final float ascent;
	private final float descent;
	private final float lineHeight;
	private Map<Integer, Glyph> glyphs = Map.of();
	private GLTexture atlas;
	private boolean disposed;

	/** Creates a logical Java font at {@link #DEFAULT_SIZE}; GPU data is created lazily. */
	public GLFont(String family, int style) {
		this(family, style, DEFAULT_SIZE);
	}

	/** Creates a logical Java font; GPU data is created on first use. */
	public GLFont(String family, int style, float size) {
		this(createLogicalFont(family, style, size), DEFAULT_CHARACTERS);
	}

	/** Creates a logical font with an explicit set of characters to place in its atlas. */
	public GLFont(String family, int style, float size, String characters) {
		this(createLogicalFont(family, style, size), characters);
	}

	/** Wraps an AWT font for interoperability; GPU data is created on first use. */
	public GLFont(Font font) {
		this(font, DEFAULT_CHARACTERS);
	}

	/** Wraps an AWT font with an explicit set of characters to place in its atlas. */
	public GLFont(Font font, String characters) {
		if (font == null)
			throw new IllegalArgumentException("Font must not be null");
		if (!(font.getSize2D() > 0) || !Float.isFinite(font.getSize2D()))
			throw new IllegalArgumentException("Font size must be finite and positive");
		if (characters == null)
			throw new IllegalArgumentException("Font characters must not be null");
		this.font = font;
		this.characters = characters;
		missingGlyphCode = font.getMissingGlyphCode();
		LineMetrics metrics = font.getLineMetrics("Ag", FONT_CONTEXT);
		ascent = metrics.getAscent();
		descent = metrics.getDescent();
		lineHeight = metrics.getHeight();
	}

	/** Loads a TrueType/OpenType font file at plain style. */
	public static GLFont fromFile(Path fontFile, float size)
			throws IOException, FontFormatException {
		return fromFile(fontFile, PLAIN, size);
	}

	/** Loads a TrueType/OpenType font file and derives the requested style and size. */
	public static GLFont fromFile(Path fontFile, int style, float size)
			throws IOException, FontFormatException {
		return fromFile(fontFile, style, size, DEFAULT_CHARACTERS);
	}

	/** Loads a font file with an explicit set of characters for its atlas. */
	public static GLFont fromFile(Path fontFile, int style, float size, String characters)
			throws IOException, FontFormatException {
		if (fontFile == null)
			throw new IllegalArgumentException("Font file must not be null");
		validateStyleAndSize(style, size);
		try (InputStream input = Files.newInputStream(fontFile)) {
			Font loaded = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(style, size);
			return new GLFont(loaded, characters);
		}
	}

	/**
	 * Builds and uploads the atlas now. Calling this is optional because the
	 * renderer invokes it lazily before the first draw.
	 */
	public GLFont initialize(GL3 gl) {
		if (gl == null)
			throw new IllegalArgumentException("GL context must not be null");
		requireNotDisposed();
		if (atlas != null)
			return this;

		int[] maximumTextureSize = new int[1];
		gl.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, maximumTextureSize, 0);
		AtlasKey key = new AtlasKey(font, characters, maximumTextureSize[0]);
		PreparedAtlas prepared;
		synchronized (PREPARED_ATLASES) {
			prepared = PREPARED_ATLASES.get(key);
			if (prepared == null) {
				prepared = prepareAtlas(font, characters, missingGlyphCode,
						maximumTextureSize[0]);
				PREPARED_ATLASES.put(key, prepared);
			}
		}
		atlas = GLTexture.create(gl, prepared.image);
		glyphs = prepared.glyphs;
		return this;
	}

	public Font getAwtFont() {
		return font;
	}

	public String getFamily() {
		return font.getFamily();
	}

	public int getStyle() {
		return font.getStyle();
	}

	public float getSize() {
		return font.getSize2D();
	}

	public float getAscent() {
		return ascent;
	}

	public float getDescent() {
		return descent;
	}

	public float getLineHeight() {
		return lineHeight;
	}

	/** Measures the largest line advance using fractional font metrics. */
	public float measureText(String text) {
		requireNotDisposed();
		if (text == null)
			throw new IllegalArgumentException("Text must not be null");
		float maximumWidth = 0;
		for (String line : normalizedLines(text)) {
			GlyphVector vector = layout(line);
			maximumWidth = Math.max(maximumWidth,
					(float) vector.getGlyphPosition(vector.getNumGlyphs()).getX());
		}
		return maximumWidth;
	}

	public boolean isInitialized() {
		return atlas != null;
	}

	public boolean isDisposed() {
		return disposed;
	}

	/** Deletes the atlas texture. Must run with its owning GL context current. */
	public void dispose(GL3 gl) {
		if (disposed)
			return;
		if (atlas != null) {
			atlas.dispose(gl);
			atlas = null;
		}
		glyphs = Map.of();
		disposed = true;
	}

	void ensureInitialized(GL3 gl) {
		initialize(gl);
	}

	GLTexture atlasTexture() {
		requireInitialized();
		return atlas;
	}

	float msdfPixelRange() {
		return MSDF_PIXEL_RANGE;
	}

	float[] createTextVertices(String text, float x, float baselineY, Color color) {
		requireInitialized();
		if (text == null)
			throw new IllegalArgumentException("Text must not be null");
		if (!Float.isFinite(x) || !Float.isFinite(baselineY))
			throw new IllegalArgumentException("Text position must be finite");
		if (color == null)
			throw new IllegalArgumentException("Text color must not be null");
		if (text.isEmpty())
			return new float[0];

		FloatBuilder vertices = new FloatBuilder(Math.max(48, text.length() * 48));
		String[] lines = normalizedLines(text);
		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			GlyphVector vector = layout(lines[lineIndex]);
			float lineBaseline = baselineY + lineIndex * lineHeight;
			for (int glyphIndex = 0; glyphIndex < vector.getNumGlyphs(); glyphIndex++) {
				Glyph glyph = glyphs.get(vector.getGlyphCode(glyphIndex));
				if (glyph == null)
					glyph = glyphs.get(missingGlyphCode);
				if (glyph == null || !glyph.hasPixels)
					continue;

				Point2D position = vector.getGlyphPosition(glyphIndex);
				float glyphX = x + (float) position.getX() + glyph.offsetX;
				float glyphY = lineBaseline + (float) position.getY() + glyph.offsetY;
				putQuad(vertices, glyphX, glyphY, glyph.width, glyph.height,
						glyph.u0, glyph.v0, glyph.u1, glyph.v1, color);
			}
		}
		return vertices.toArray();
	}

	private GlyphVector layout(String text) {
		char[] characters = text.toCharArray();
		return font.layoutGlyphVector(FONT_CONTEXT, characters, 0, characters.length,
				Font.LAYOUT_LEFT_TO_RIGHT);
	}

	private void requireNotDisposed() {
		if (disposed)
			throw new IllegalStateException("Font has already been disposed");
	}

	private void requireInitialized() {
		requireNotDisposed();
		if (atlas == null)
			throw new IllegalStateException("Font atlas has not been initialized");
	}

	private static Font createLogicalFont(String family, int style, float size) {
		if (family == null || family.isBlank())
			throw new IllegalArgumentException("Font family must not be blank");
		validateStyleAndSize(style, size);
		return new Font(family, style, 1).deriveFont(size);
	}

	private static void validateStyleAndSize(int style, float size) {
		if ((style & ~(BOLD | ITALIC)) != 0)
			throw new IllegalArgumentException("Unsupported font style: " + style);
		if (!(size > 0) || !Float.isFinite(size))
			throw new IllegalArgumentException("Font size must be finite and positive");
	}

	private static Map<Integer, GlyphRaster> collectGlyphs(Font font,
			String characters, int missingGlyphCode, float rasterScale) {
		Map<Integer, GlyphRaster> glyphs = new LinkedHashMap<>();
		characters.codePoints().forEach(codePoint -> {
			String character = new String(Character.toChars(codePoint));
			GlyphVector vector = font.createGlyphVector(FONT_CONTEXT, character);
			for (int i = 0; i < vector.getNumGlyphs(); i++)
				glyphs.computeIfAbsent(vector.getGlyphCode(i),
						code -> createRaster(font, code, rasterScale));
		});
		glyphs.computeIfAbsent(missingGlyphCode, code -> createRaster(font, code, rasterScale));
		return glyphs;
	}

	private static PreparedAtlas prepareAtlas(Font font, String characters,
			int missingGlyphCode, int maximumTextureSize) {
		float rasterScale = Math.max(1f, MSDF_EM_RESOLUTION / font.getSize2D());
		Map<Integer, GlyphRaster> rastersByCode = collectGlyphs(
				font, characters, missingGlyphCode, rasterScale);
		List<GlyphRaster> visibleRasters = rastersByCode.values().stream()
				.filter(GlyphRaster::hasPixels)
				.sorted(Comparator.comparingInt(GlyphRaster::cellHeight).reversed())
				.toList();
		int atlasSize = chooseAtlasSize(visibleRasters, maximumTextureSize);
		BufferedImage image = renderAtlas(visibleRasters, atlasSize);
		Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
		for (GlyphRaster raster : rastersByCode.values())
			glyphs.put(raster.glyphCode, raster.toGlyph(atlasSize, rasterScale));
		return new PreparedAtlas(image, Map.copyOf(glyphs));
	}

	private static GlyphRaster createRaster(Font font, int glyphCode, float rasterScale) {
		GlyphVector vector = font.createGlyphVector(FONT_CONTEXT, new int[] { glyphCode });
		Shape outline = vector.getGlyphOutline(0);
		GlyphBitmap bitmap = MsdfGlyphGenerator.generate(
				outline, rasterScale, MSDF_PIXEL_RANGE);
		return new GlyphRaster(glyphCode, bitmap);
	}

	private static int chooseAtlasSize(List<GlyphRaster> glyphs, int maximumTextureSize) {
		int atlasSize = INITIAL_ATLAS_SIZE;
		while (atlasSize <= maximumTextureSize) {
			if (pack(glyphs, atlasSize))
				return atlasSize;
			atlasSize *= 2;
		}
		throw new IllegalArgumentException("Font glyphs exceed the maximum texture size " + maximumTextureSize);
	}

	private static boolean pack(List<GlyphRaster> glyphs, int atlasSize) {
		int x = 0;
		int y = 0;
		int rowHeight = 0;
		for (GlyphRaster glyph : glyphs) {
			if (glyph.cellWidth() > atlasSize || glyph.cellHeight() > atlasSize)
				return false;
			if (x + glyph.cellWidth() > atlasSize) {
				x = 0;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + glyph.cellHeight() > atlasSize)
				return false;
			glyph.atlasX = x + ATLAS_GAP;
			glyph.atlasY = y + ATLAS_GAP;
			x += glyph.cellWidth();
			rowHeight = Math.max(rowHeight, glyph.cellHeight());
		}
		return true;
	}

	private static BufferedImage renderAtlas(List<GlyphRaster> glyphs, int atlasSize) {
		BufferedImage atlas = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
		for (GlyphRaster glyph : glyphs) {
			atlas.setRGB(glyph.atlasX, glyph.atlasY,
					glyph.bitmap.width(), glyph.bitmap.height(), glyph.bitmap.pixels(),
					0, glyph.bitmap.width());
		}
		return atlas;
	}

	private static void putQuad(FloatBuilder vertices, float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, Color color) {
		float right = x + width;
		float bottom = y + height;
		putVertex(vertices, x, y, u0, v0, color);
		putVertex(vertices, right, y, u1, v0, color);
		putVertex(vertices, right, bottom, u1, v1, color);
		putVertex(vertices, x, y, u0, v0, color);
		putVertex(vertices, right, bottom, u1, v1, color);
		putVertex(vertices, x, bottom, u0, v1, color);
	}

	private static void putVertex(FloatBuilder vertices, float x, float y,
			float u, float v, Color color) {
		vertices.put(x, y, u, v,
				color.getRed() / 255f, color.getGreen() / 255f,
				color.getBlue() / 255f, color.getAlpha() / 255f);
	}

	private static String[] normalizedLines(String text) {
		return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
	}

	private static String defaultCharacters() {
		StringBuilder characters = new StringBuilder();
		for (int codePoint = 32; codePoint <= 255; codePoint++)
			characters.append((char) codePoint);
		characters.append('\u2026');
		characters.append('\ufffd');
		return characters.toString();
	}

	private static final class GlyphRaster {
		private final int glyphCode;
		private final GlyphBitmap bitmap;
		private int atlasX;
		private int atlasY;

		GlyphRaster(int glyphCode, GlyphBitmap bitmap) {
			this.glyphCode = glyphCode;
			this.bitmap = bitmap;
		}

		boolean hasPixels() {
			return bitmap.hasPixels();
		}

		int cellWidth() {
			return bitmap.width() + ATLAS_GAP * 2;
		}

		int cellHeight() {
			return bitmap.height() + ATLAS_GAP * 2;
		}

		Glyph toGlyph(int atlasSize, float rasterScale) {
			if (!hasPixels())
				return new Glyph(0, 0, 0, 0, 0, 0, 0, 0, false);
			return new Glyph(bitmap.offsetX(), bitmap.offsetY(),
					bitmap.width() / rasterScale, bitmap.height() / rasterScale,
					atlasX / (float) atlasSize, atlasY / (float) atlasSize,
					(atlasX + bitmap.width()) / (float) atlasSize,
					(atlasY + bitmap.height()) / (float) atlasSize, true);
		}
	}

	private record AtlasKey(Font font, String characters, int maximumTextureSize) {
	}

	private record PreparedAtlas(BufferedImage image, Map<Integer, Glyph> glyphs) {
	}

	private static final class Glyph {
		private final float offsetX;
		private final float offsetY;
		private final float width;
		private final float height;
		private final float u0;
		private final float v0;
		private final float u1;
		private final float v1;
		private final boolean hasPixels;

		Glyph(float offsetX, float offsetY, float width, float height,
				float u0, float v0, float u1, float v1, boolean hasPixels) {
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.width = width;
			this.height = height;
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.hasPixels = hasPixels;
		}
	}

	private static final class FloatBuilder {
		private float[] values;
		private int size;

		FloatBuilder(int initialCapacity) {
			values = new float[initialCapacity];
		}

		void put(float... addedValues) {
			if (size + addedValues.length > values.length)
				values = Arrays.copyOf(values, Math.max(values.length * 2, size + addedValues.length));
			System.arraycopy(addedValues, 0, values, size, addedValues.length);
			size += addedValues.length;
		}

		float[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}
}
