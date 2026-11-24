package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

/** Renderer-owned lazy image resolver with RGBA atlas pages and standalone fallback. */
final class GLTextureManager {

	private static final int DESIRED_PAGE_SIZE = 2048;
	private static final int NORMAL_PADDING = 1;
	private static final int MIPMAP_PADDING = 8;
	private static final int MAX_ATLAS_MIP_LEVEL = 3;

	private final Map<GLImage, CacheEntry> cache = new IdentityHashMap<>();
	private final List<AtlasPage> linearPages = new ArrayList<>();
	private final List<AtlasPage> nearestPages = new ArrayList<>();
	private final List<AtlasPage> mipmappedPages = new ArrayList<>();
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

	boolean isResolved(GLImage image) {
		CacheEntry entry = cache.get(image);
		return entry != null && entry.version == image.version();
	}

	void dispose(GL3 gl) {
		for (CacheEntry entry : cache.values())
			if (!entry.region.atlased() && !entry.region.texture().isDisposed())
				entry.region.texture().dispose(gl);
		for (AtlasPage page : linearPages)
			page.texture.dispose(gl);
		for (AtlasPage page : nearestPages)
			page.texture.dispose(gl);
		for (AtlasPage page : mipmappedPages)
			page.texture.dispose(gl);
		cache.clear();
		linearPages.clear();
		nearestPages.clear();
		mipmappedPages.clear();
	}

	/** Generates dirty atlas mip chains only immediately before their first use. */
	void prepareForSampling(GL3 gl) {
		for (AtlasPage page : mipmappedPages)
			page.prepareForSampling(gl);
	}

	private boolean shouldAtlas(GL3 gl, GLImage image, BufferedImage pixels) {
		GLImageOptions options = image.getOptions();
		if (options.storage() == GLImageOptions.Storage.STANDALONE
				|| options.wrap() == GLImageOptions.Wrap.REPEAT
				|| options.usage() == GLImageOptions.Usage.DYNAMIC)
			return false;
		int size = pageSize(gl);
		int padding = usesMipmaps(options) ? MIPMAP_PADDING : NORMAL_PADDING;
		boolean fits = pixels.getWidth() + padding * 2 <= size
				&& pixels.getHeight() + padding * 2 <= size;
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
		GLImageOptions options = image.getOptions();
		boolean linear = options.filter() == GLImageOptions.Filter.LINEAR;
		boolean mipmapped = usesMipmaps(options);
		int padding = mipmapped ? MIPMAP_PADDING : NORMAL_PADDING;
		List<AtlasPage> pages = mipmapped ? mipmappedPages
				: linear ? linearPages : nearestPages;
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
			int maximumMipLevel = mipmapped
					? safeAtlasMipLevelCount(pixels.getWidth(), pixels.getHeight()) : 0;
			selected = new AtlasPage(GLTexture.createEmpty(
					gl, pageSize(gl), pageSize(gl), linear, mipmapped),
					pageSize(gl), padding, mipmapped, maximumMipLevel);
			pages.add(selected);
			placement = selected.allocate(pixels.getWidth(), pixels.getHeight());
		}
		selected.upload(gl, placement, pixels);
		float inverse = 1f / selected.size;
		return new GLTextureRegion(selected.texture,
				placement.x * inverse, placement.y * inverse,
				(placement.x + placement.width) * inverse,
				(placement.y + placement.height) * inverse, true);
	}

	private static GLTextureRegion createStandalone(GL3 gl, GLImage image,
			BufferedImage pixels) {
		GLImageOptions options = image.getOptions();
		boolean mipmapped = usesMipmaps(options);
		GLTexture texture = GLTexture.create(gl, pixels,
				options.filter() == GLImageOptions.Filter.LINEAR,
				options.wrap() == GLImageOptions.Wrap.REPEAT, mipmapped);
		if (mipmapped)
			texture.generateMipmaps(gl, fullMipLevelCount(
					pixels.getWidth(), pixels.getHeight()));
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

	private static BufferedImage withExtrudedBorder(BufferedImage source,
			int padding) {
		int width = source.getWidth();
		int height = source.getHeight();
		int resultWidth = width + padding * 2;
		int resultHeight = height + padding * 2;
		int[] sourcePixels = source.getRGB(0, 0, width, height,
				null, 0, width);
		int[] resultPixels = new int[resultWidth * resultHeight];
		for (int y = 0; y < resultHeight; y++) {
			int sourceY = Math.max(0, Math.min(height - 1, y - padding));
			int sourceOffset = sourceY * width;
			int targetOffset = y * resultWidth;
			int first = sourcePixels[sourceOffset];
			int last = sourcePixels[sourceOffset + width - 1];
			Arrays.fill(resultPixels, targetOffset,
					targetOffset + padding, first);
			System.arraycopy(sourcePixels, sourceOffset, resultPixels,
					targetOffset + padding, width);
			Arrays.fill(resultPixels, targetOffset + padding + width,
					targetOffset + resultWidth, last);
		}
		BufferedImage result = new BufferedImage(resultWidth, resultHeight,
				BufferedImage.TYPE_INT_ARGB);
		result.setRGB(0, 0, resultWidth, resultHeight,
				resultPixels, 0, resultWidth);
		return result;
	}

	private static boolean usesMipmaps(GLImageOptions options) {
		return switch (options.mipmapping()) {
		case NONE -> false;
		case ENABLED -> true;
		case AUTO -> options.filter() == GLImageOptions.Filter.LINEAR
				&& options.usage() == GLImageOptions.Usage.STATIC;
		};
	}

	private static int fullMipLevelCount(int width, int height) {
		return 31 - Integer.numberOfLeadingZeros(Math.max(width, height));
	}

	private static int safeAtlasMipLevelCount(int width, int height) {
		int imageLevels = 31 - Integer.numberOfLeadingZeros(Math.min(width, height));
		int paddingLevels = 31 - Integer.numberOfLeadingZeros(MIPMAP_PADDING);
		return Math.min(MAX_ATLAS_MIP_LEVEL, Math.min(imageLevels, paddingLevels));
	}

	private record CacheEntry(GLTextureRegion region, long version,
			int width, int height) {
	}

	private record Placement(int x, int y, int width, int height) {
	}

	private static final class AtlasPage {
		private final GLTexture texture;
		private final int size;
		private final int padding;
		private final boolean mipmapped;
		private final int maximumMipLevel;
		private int x;
		private int y;
		private int rowHeight;
		private boolean mipmapsDirty;

		private AtlasPage(GLTexture texture, int size, int padding,
				boolean mipmapped, int maximumMipLevel) {
			this.texture = texture;
			this.size = size;
			this.padding = padding;
			this.mipmapped = mipmapped;
			this.maximumMipLevel = maximumMipLevel;
			x = padding;
			y = padding;
		}

		private Placement allocate(int width, int height) {
			if (mipmapped
					&& safeAtlasMipLevelCount(width, height) != maximumMipLevel)
				return null;
			int paddedWidth = width + padding * 2;
			int paddedHeight = height + padding * 2;
			if (paddedWidth > size || paddedHeight > size)
				return null;
			if (x + width + padding > size) {
				x = padding;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + height + padding > size)
				return null;
			Placement result = new Placement(x, y, width, height);
			x += paddedWidth;
			rowHeight = Math.max(rowHeight, paddedHeight);
			return result;
		}

		private void upload(GL3 gl, Placement placement, BufferedImage pixels) {
			texture.upload(gl, placement.x - padding, placement.y - padding,
					withExtrudedBorder(pixels, padding));
			if (mipmapped) {
				mipmapsDirty = true;
			}
		}

		private void prepareForSampling(GL3 gl) {
			if (!mipmapsDirty)
				return;
			texture.generateMipmaps(gl, maximumMipLevel);
			mipmapsDirty = false;
		}
	}
}
