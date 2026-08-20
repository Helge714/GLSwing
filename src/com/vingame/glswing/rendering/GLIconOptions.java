package com.vingame.glswing.rendering;

/** Controls resolution buckets generated for multicolor SVG icons. */
public record GLIconOptions(RasterPolicy rasterPolicy,
		int minimumResolution, int maximumResolution,
		int resolutionStep, float oversampling,
		int rasterizationDelayMillis) {

	public enum RasterPolicy { AUTO, FIXED }

	public static final GLIconOptions DEFAULT = new GLIconOptions(
			RasterPolicy.AUTO, 32, 1024, 2, 1, 150);

	/** Compatibility constructor using the default automatic request delay. */
	public GLIconOptions(RasterPolicy rasterPolicy,
			int minimumResolution, int maximumResolution,
			int resolutionStep, float oversampling) {
		this(rasterPolicy, minimumResolution, maximumResolution,
				resolutionStep, oversampling, 150);
	}

	public GLIconOptions {
		if (rasterPolicy == null)
			throw new IllegalArgumentException("Icon raster policy must not be null");
		if (minimumResolution < 16 || maximumResolution < minimumResolution)
			throw new IllegalArgumentException(
					"Icon resolutions must satisfy 16 <= minimum <= maximum");
		if (resolutionStep < 2)
			throw new IllegalArgumentException("Icon resolution step must be at least 2");
		if (!Float.isFinite(oversampling) || oversampling <= 0 || oversampling > 4)
			throw new IllegalArgumentException(
					"Icon oversampling must be finite and between 0 and 4");
		if (rasterizationDelayMillis < 0 || rasterizationDelayMillis > 2_000)
			throw new IllegalArgumentException(
					"Icon rasterization delay must be between 0 and 2000 ms");
		if (rasterPolicy == RasterPolicy.FIXED
				&& minimumResolution != maximumResolution)
			throw new IllegalArgumentException(
					"A fixed icon policy requires one identical minimum/maximum size");
	}

	public static GLIconOptions fixed(int resolution) {
		return new GLIconOptions(RasterPolicy.FIXED,
				resolution, resolution, 2, 1, 0);
	}

	public GLIconOptions withRasterPolicy(RasterPolicy policy) {
		if (policy == RasterPolicy.FIXED)
			return fixed(maximumResolution);
		return new GLIconOptions(policy, minimumResolution, maximumResolution,
				resolutionStep, oversampling, rasterizationDelayMillis);
	}

	public GLIconOptions withResolutionRange(int minimum, int maximum) {
		return new GLIconOptions(rasterPolicy, minimum, maximum,
				resolutionStep, oversampling, rasterizationDelayMillis);
	}

	public GLIconOptions withMinimumResolution(int resolution) {
		return withResolutionRange(resolution, maximumResolution);
	}

	public GLIconOptions withMaximumResolution(int resolution) {
		return withResolutionRange(minimumResolution, resolution);
	}

	public GLIconOptions withResolutionStep(int step) {
		return new GLIconOptions(rasterPolicy, minimumResolution,
				maximumResolution, step, oversampling, rasterizationDelayMillis);
	}

	public GLIconOptions withOversampling(float factor) {
		return new GLIconOptions(rasterPolicy, minimumResolution,
				maximumResolution, resolutionStep, factor, rasterizationDelayMillis);
	}

	/** Delay used to coalesce rapid zoom changes before background rasterization. */
	public GLIconOptions withRasterizationDelayMillis(int milliseconds) {
		return new GLIconOptions(rasterPolicy, minimumResolution,
				maximumResolution, resolutionStep, oversampling, milliseconds);
	}

	int resolutionFor(float physicalPixels) {
		if (rasterPolicy == RasterPolicy.FIXED)
			return maximumResolution;
		int required = Math.max(minimumResolution,
				(int) Math.ceil(Math.max(0, physicalPixels) * oversampling));
		if (required >= maximumResolution)
			return maximumResolution;
		int resolution = minimumResolution;
		while (resolution < required) {
			long next = (long) resolution * resolutionStep;
			if (next >= maximumResolution)
				return maximumResolution;
			resolution = (int) next;
		}
		return resolution;
	}
}
