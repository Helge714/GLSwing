package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator;
import com.vingame.glswing.rendering.internal.MsdfGlyphGenerator.GlyphBitmap;

/** Atlas-ready icon rendered either as RGBA resolution buckets or tintable MSDF. */
public final class GLIcon {

	public enum Format { AUTO, RGBA, MSDF }

	private static final int DEFAULT_RESOLUTION = 128;
	private static final float DEFAULT_PIXEL_RANGE = 8;
	private static final System.Logger LOGGER =
			System.getLogger(GLIcon.class.getName());
	private static final ScheduledThreadPoolExecutor RASTERIZER =
			createRasterizer();

	private final GLImage fixedImage;
	private final GLSvgDocument svg;
	private final Format format;
	private final float pixelRange;
	private final GLIconOptions options;
	private final Map<Integer, GLImage> variants = new LinkedHashMap<>();
	private final Map<Integer, CompletableFuture<GLImage>> inFlight =
			new LinkedHashMap<>();
	private final Map<Integer, Set<Runnable>> repaintCallbacks =
			new LinkedHashMap<>();
	private int scheduledResolution = -1;
	private ScheduledFuture<?> scheduledRequest;

	private GLIcon(GLImage fixedImage, GLSvgDocument svg, Format format,
			float pixelRange, GLIconOptions options) {
		this.fixedImage = fixedImage;
		this.svg = svg;
		this.format = format;
		this.pixelRange = pixelRange;
		this.options = options;
		if (svg != null && fixedImage != null)
			variants.put(fixedImage.getWidth(), fixedImage);
	}

	public static GLIcon fromImage(GLImage image) {
		if (image == null)
			throw new IllegalArgumentException("Icon image must not be null");
		return new GLIcon(image, null, Format.RGBA, 0,
				GLIconOptions.fixed(image.getWidth()));
	}

	public static GLIcon fromSvgResource(String resourcePath) {
		return fromSvg(GLSvgDocument.loadResource(resourcePath),
				Format.AUTO, GLIconOptions.DEFAULT);
	}

	public static GLIcon fromSvgResource(String resourcePath, Format format) {
		return fromSvg(GLSvgDocument.loadResource(resourcePath),
				format, GLIconOptions.DEFAULT);
	}

	public static GLIcon fromSvgResource(String resourcePath, Format format,
			GLIconOptions options) {
		return fromSvg(GLSvgDocument.loadResource(resourcePath), format, options);
	}

	public static GLIcon fromSvg(GLSvgDocument document, Format format) {
		return fromSvg(document, format, GLIconOptions.DEFAULT);
	}

	public static GLIcon fromSvg(GLSvgDocument document, Format requested,
			GLIconOptions options) {
		return createSvg(document, requested, requireOptions(options),
				DEFAULT_PIXEL_RANGE);
	}

	/** Compatibility overload creating one fixed-resolution representation. */
	public static GLIcon fromSvg(GLSvgDocument document, Format requested,
			int resolution, float pixelRange) {
		return createSvg(document, requested, GLIconOptions.fixed(resolution), pixelRange);
	}

	private static GLIcon createSvg(GLSvgDocument document, Format requested,
			GLIconOptions options, float pixelRange) {
		if (document == null || requested == null)
			throw new IllegalArgumentException("SVG document and icon format are required");
		Format resolved = requested == Format.AUTO
				? (document.isMonochrome() ? Format.MSDF : Format.RGBA) : requested;
		if (resolved == Format.RGBA) {
			int initialResolution = options.rasterPolicy()
					== GLIconOptions.RasterPolicy.FIXED
						? options.maximumResolution()
						: options.resolutionFor(DEFAULT_RESOLUTION);
			GLImage initial = rgbaVariant(document, initialResolution);
			return new GLIcon(initial,
					options.rasterPolicy() == GLIconOptions.RasterPolicy.AUTO
							? document : null,
					resolved, 0, options);
		}
		if (!Float.isFinite(pixelRange) || pixelRange < 2)
			throw new IllegalArgumentException("MSDF pixel range must be at least 2");
		int resolution = options.rasterPolicy() == GLIconOptions.RasterPolicy.FIXED
				? options.maximumResolution() : DEFAULT_RESOLUTION;
		GLImage field = msdfImage(document, resolution, pixelRange);
		return new GLIcon(field, null, resolved, pixelRange,
				GLIconOptions.fixed(resolution));
	}

	public Format getFormat() {
		return format;
	}

	/** Initial/fixed raster resolution; AUTO variants are generated around it. */
	public int getRasterResolution() {
		return fixedImage.getWidth();
	}

	public GLIconOptions getOptions() {
		return options;
	}

	public synchronized int getCachedVariantCount() {
		return variants.isEmpty() ? 1 : variants.size();
	}

	/**
	 * Rasterizes one adaptive RGBA bucket in the shared background worker.
	 * GPU allocation remains lazy and occurs when a renderer next draws the icon.
	 */
	public CompletableFuture<GLImage> prepareAsync(float physicalPixels) {
		if (svg == null)
			return CompletableFuture.completedFuture(fixedImage);
		int resolution = options.resolutionFor(physicalPixels);
		synchronized (this) {
			GLImage existing = variants.get(resolution);
			if (existing != null)
				return CompletableFuture.completedFuture(existing);
			CompletableFuture<GLImage> existingTask = inFlight.get(resolution);
			if (existingTask != null)
				return existingTask;
			if (scheduledResolution == resolution)
				cancelScheduledRequest(false);
			CompletableFuture<GLImage> task = new CompletableFuture<>();
			inFlight.put(resolution, task);
			RASTERIZER.execute(() -> rasterize(resolution, task));
			return task;
		}
	}

	GLImage imageFor(float physicalPixels, Runnable repaintWhenReady) {
		if (svg == null)
			return fixedImage;
		int resolution = options.resolutionFor(physicalPixels);
		synchronized (this) {
			GLImage exact = variants.get(resolution);
			if (exact != null) {
				if (scheduledResolution != -1
						&& scheduledResolution != resolution)
					cancelScheduledRequest(true);
				return exact;
			}
			registerRepaintCallback(resolution, repaintWhenReady);
			requestVariant(resolution);
			return bestVariant(resolution, image -> true);
		}
	}

	synchronized GLImage bestImageFor(float physicalPixels,
			Predicate<GLImage> acceptable) {
		if (acceptable == null)
			throw new IllegalArgumentException("Image predicate must not be null");
		if (svg == null)
			return acceptable.test(fixedImage) ? fixedImage : null;
		return bestVariant(options.resolutionFor(physicalPixels), acceptable);
	}

	private void requestVariant(int resolution) {
		if (variants.containsKey(resolution) || inFlight.containsKey(resolution)
				|| scheduledResolution == resolution)
			return;
		cancelScheduledRequest(true);
		scheduledResolution = resolution;
		scheduledRequest = RASTERIZER.schedule(
				() -> beginScheduledRasterization(resolution),
				options.rasterizationDelayMillis(), TimeUnit.MILLISECONDS);
	}

	private void beginScheduledRasterization(int resolution) {
		CompletableFuture<GLImage> task;
		synchronized (this) {
			if (scheduledResolution != resolution)
				return;
			scheduledResolution = -1;
			scheduledRequest = null;
			GLImage existing = variants.get(resolution);
			if (existing != null) {
				notifyRepaintCallbacks(resolution);
				return;
			}
			task = inFlight.get(resolution);
			if (task != null)
				return;
			task = new CompletableFuture<>();
			inFlight.put(resolution, task);
		}
		rasterize(resolution, task);
	}

	private void rasterize(int resolution, CompletableFuture<GLImage> task) {
		try {
			GLImage image = rgbaVariant(svg, resolution);
			Set<Runnable> callbacks;
			synchronized (this) {
				variants.putIfAbsent(resolution, image);
				inFlight.remove(resolution);
				callbacks = repaintCallbacks.remove(resolution);
			}
			task.complete(image);
			notifyRepaintCallbacks(callbacks);
		} catch (Throwable error) {
			synchronized (this) {
				inFlight.remove(resolution);
				repaintCallbacks.remove(resolution);
			}
			task.completeExceptionally(error);
			LOGGER.log(System.Logger.Level.ERROR,
					"Could not rasterize adaptive SVG icon at "
							+ resolution + "px", error);
		}
	}

	private GLImage bestVariant(int desiredResolution,
			Predicate<GLImage> acceptable) {
		GLImage lower = null;
		int lowerResolution = -1;
		GLImage upper = null;
		int upperResolution = Integer.MAX_VALUE;
		for (Map.Entry<Integer, GLImage> entry : variants.entrySet()) {
			if (!acceptable.test(entry.getValue()))
				continue;
			int resolution = entry.getKey();
			if (resolution <= desiredResolution && resolution > lowerResolution) {
				lower = entry.getValue();
				lowerResolution = resolution;
			} else if (resolution > desiredResolution
					&& resolution < upperResolution) {
				upper = entry.getValue();
				upperResolution = resolution;
			}
		}
		return lower != null ? lower : upper;
	}

	private void registerRepaintCallback(int resolution, Runnable callback) {
		if (callback == null)
			return;
		repaintCallbacks.computeIfAbsent(resolution, ignored ->
				Collections.newSetFromMap(new IdentityHashMap<>())).add(callback);
	}

	private void cancelScheduledRequest(boolean forgetCallbacks) {
		if (scheduledRequest != null)
			scheduledRequest.cancel(false);
		if (forgetCallbacks && scheduledResolution != -1)
			repaintCallbacks.remove(scheduledResolution);
		scheduledRequest = null;
		scheduledResolution = -1;
	}

	private void notifyRepaintCallbacks(int resolution) {
		Set<Runnable> callbacks = repaintCallbacks.remove(resolution);
		notifyRepaintCallbacks(callbacks);
	}

	private static void notifyRepaintCallbacks(Set<Runnable> callbacks) {
		if (callbacks == null)
			return;
		for (Runnable callback : callbacks) {
			try {
				callback.run();
			} catch (RuntimeException error) {
				LOGGER.log(System.Logger.Level.WARNING,
						"Adaptive icon repaint callback failed", error);
			}
		}
	}

	float pixelRange() {
		return pixelRange;
	}

	private static GLImage rgbaVariant(GLSvgDocument document, int resolution) {
		return GLImage.of(document.rasterize(resolution, resolution),
				GLImageOptions.atlas());
	}

	private static GLImage msdfImage(GLSvgDocument document, int resolution,
			float pixelRange) {
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
		return GLImage.of(field, GLImageOptions.atlas()
				.withMipmapping(GLImageOptions.Mipmapping.NONE));
	}

	private static GLIconOptions requireOptions(GLIconOptions options) {
		if (options == null)
			throw new IllegalArgumentException("Icon options must not be null");
		return options;
	}

	private static ScheduledThreadPoolExecutor createRasterizer() {
		AtomicInteger threadNumber = new AtomicInteger();
		ThreadFactory factory = task -> {
			Thread thread = new Thread(task,
					"GLSwing-SVG-Rasterizer-" + threadNumber.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
		ScheduledThreadPoolExecutor executor =
				new ScheduledThreadPoolExecutor(1, factory);
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		return executor;
	}
}
