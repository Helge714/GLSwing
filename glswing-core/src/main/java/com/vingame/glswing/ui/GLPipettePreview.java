package com.vingame.glswing.ui;

/**
 * Content of the screen eyedropper's pointer-following preview.
 * Disabling the preview does not disable pixel sampling or Escape cancellation.
 *
 * @param enabled whether any preview may be displayed
 * @param showColor whether to display a sampled-color swatch
 * @param showHex whether to display hexadecimal color notation
 * @param showRgb whether to display numeric RGB channels
 */
public record GLPipettePreview(boolean enabled, boolean showColor,
		boolean showHex, boolean showRgb) {
	/** Conventional swatch-and-hexadecimal preview. */
	public static final GLPipettePreview DEFAULT = new GLPipettePreview(true, true, true, false);
	/** Configuration that suppresses the pointer-following preview. */
	public static final GLPipettePreview NONE = new GLPipettePreview(false, false, false, false);
	/** Returns whether this configuration contains any visible preview content. */
	public boolean isVisible() { return enabled && (showColor || showHex || showRgb); }
}
