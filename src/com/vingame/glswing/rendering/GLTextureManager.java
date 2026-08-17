package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

/** Renderer-owned lazy image resolver with RGBA atlas pages and standalone fallback. */
final class GLTextureManager {

	private static final int DESIRED_PAGE_SIZE = 2048;
	private static final int PADDING = 1;

	private final Map<GLImage, CacheEntry> cache = new IdentityHashMap<>();
	private final List<AtlasPage> linearPages = new ArrayList<>();
	private final List<AtlasPage> nearestPages = new ArrayList<>();
	private int pageSize;

	GLTextureRegion resolve(GL3 gl, GLImage image) {
		if (image == null)
			throw new IllegalArgumentException("Image must not be null");
		CacheEntry cached = cache.get(image);
		long version = image.version();
		BufferedImage pixels = image.pixels();
		if (cached != null && cached.version == version)
			return cached.region;

		GLTextureRegion region;
		if (cached != null && !cached.region.atlased()
				&& cached.width == pixels.getWidth()
				&& cached.height == pixels.getHeight()) {
			cached.region.texture().upload(gl, 0, 0, pixels);
			region = cached.region;
		} else {
			if (cached != null && !cached.region.atlased())
				cached.region.texture().dispose(gl);
			region = shouldAtlas(gl, image, pixels)
					? placeInAtlas(gl, image, pixels)
					: createStandalone(gl, image, pixels);
		}
		cache.put(image, new CacheEntry(region, version,
				pixels.getWidth(), pixels.getHeight()));
		return region;
	}

	void dispose(GL3 gl) {
		for (CacheEntry entry : cache.values())
			if (!entry.region.atlased() && !entry.region.texture().isDisposed())
				entry.region.texture().dispose(gl);
		for (AtlasPage page : linearPages)
			page.texture.dispose(gl);
		for (AtlasPage page : nearestPages)
			page.texture.dispose(gl);
		cache.clear();
		linearPages.clear();
		nearestPages.clear();
	}

	private boolean shouldAtlas(GL3 gl, GLImage image, BufferedImage pixels) {
		GLImageOptions options = image.getOptions();
		if (options.storage() == GLImageOptions.Storage.STANDALONE
				|| options.wrap() == GLImageOptions.Wrap.REPEAT
				|| options.usage() == GLImageOptions.Usage.DYNAMIC)
			return false;
		int size = pageSize(gl);
		boolean fits = pixels.getWidth() + PADDING * 2 <= size
				&& pixels.getHeight() + PADDING * 2 <= size;
		if (options.storage() == GLImageOptions.Storage.ATLAS) {
			if (!fits)
				throw new IllegalArgumentException("Image " + pixels.getWidth() + "x"
						+ pixels.getHeight() + " does not fit the " + size + "px atlas");
			return true;
		}
		return fits && pixels.getWidth() <= size / 2 && pixels.getHeight() <= size / 2;
	}

	private GLTextureRegion placeInAtlas(GL3 gl, GLImage image,
			BufferedImage pixels) {
		boolean linear = image.getOptions().filter() == GLImageOptions.Filter.LINEAR;
		List<AtlasPage> pages = linear ? linearPages : nearestPages;
		Placement placement = null;
		AtlasPage selected = null;
		for (AtlasPage page : pages) {
			placement = page.allocate(pixels.getWidth(), pixels.getHeight());
			if (placement != null) {
				selected = page;
				break;
			}
		}
		if (selected == null) {
			selected = new AtlasPage(GLTexture.createEmpty(
					gl, pageSize(gl), pageSize(gl), linear), pageSize(gl));
			pages.add(selected);
			placement = selected.allocate(pixels.getWidth(), pixels.getHeight());
		}
		BufferedImage padded = withExtrudedBorder(pixels);
		selected.texture.upload(gl, placement.x - PADDING,
				placement.y - PADDING, padded);
		float inverse = 1f / selected.size;
		return new GLTextureRegion(selected.texture,
				placement.x * inverse, placement.y * inverse,
				(placement.x + placement.width) * inverse,
				(placement.y + placement.height) * inverse, true);
	}

	private static GLTextureRegion createStandalone(GL3 gl, GLImage image,
			BufferedImage pixels) {
		GLImageOptions options = image.getOptions();
		GLTexture texture = GLTexture.create(gl, pixels,
				options.filter() == GLImageOptions.Filter.LINEAR,
				options.wrap() == GLImageOptions.Wrap.REPEAT);
		return new GLTextureRegion(texture, 0, 0, 1, 1, false);
	}

	private int pageSize(GL3 gl) {
		if (pageSize == 0) {
			int[] maximum = new int[1];
			gl.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, maximum, 0);
			pageSize = Math.min(DESIRED_PAGE_SIZE, maximum[0]);
			if (pageSize < 64)
				throw new IllegalStateException("OpenGL texture size is too small for an atlas");
		}
		return pageSize;
	}

	private static BufferedImage withExtrudedBorder(BufferedImage source) {
		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage result = new BufferedImage(width + 2, height + 2,
				BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++)
				result.setRGB(x + 1, y + 1, source.getRGB(x, y));
			result.setRGB(0, y + 1, source.getRGB(0, y));
			result.setRGB(width + 1, y + 1, source.getRGB(width - 1, y));
		}
		for (int x = 0; x < width; x++) {
			result.setRGB(x + 1, 0, source.getRGB(x, 0));
			result.setRGB(x + 1, height + 1, source.getRGB(x, height - 1));
		}
		result.setRGB(0, 0, source.getRGB(0, 0));
		result.setRGB(width + 1, 0, source.getRGB(width - 1, 0));
		result.setRGB(0, height + 1, source.getRGB(0, height - 1));
		result.setRGB(width + 1, height + 1, source.getRGB(width - 1, height - 1));
		return result;
	}

	private record CacheEntry(GLTextureRegion region, long version,
			int width, int height) {
	}

	private record Placement(int x, int y, int width, int height) {
	}

	private static final class AtlasPage {
		private final GLTexture texture;
		private final int size;
		private int x = PADDING;
		private int y = PADDING;
		private int rowHeight;

		private AtlasPage(GLTexture texture, int size) {
			this.texture = texture;
			this.size = size;
		}

		private Placement allocate(int width, int height) {
			int paddedWidth = width + PADDING * 2;
			int paddedHeight = height + PADDING * 2;
			if (paddedWidth > size || paddedHeight > size)
				return null;
			if (x + width + PADDING > size) {
				x = PADDING;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + height + PADDING > size)
				return null;
			Placement result = new Placement(x, y, width, height);
			x += paddedWidth;
			rowHeight = Math.max(rowHeight, paddedHeight);
			return result;
		}
	}
}
